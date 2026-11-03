package com.wechat.www.netty;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wechat.www.constant.CommonConstants;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.WsInitData;
import com.wechat.www.entity.ChatMessage;
import com.wechat.www.entity.ChatSession;
import com.wechat.www.entity.ChatSessionUser;
import com.wechat.www.entity.UserContactApply;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.MessageTypeEnum;
import com.wechat.www.enums.UserContactApplyStatusEnum;
import com.wechat.www.enums.UserContactTypeEnum;
import com.wechat.www.mapper.ChatMessageMapper;
import com.wechat.www.mapper.ChatSessionMapper;
import com.wechat.www.mapper.ChatSessionUserMapper;
import com.wechat.www.mapper.UserContactApplyMapper;
import com.wechat.www.mapper.UserInfoMapper;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.utils.JsonUtils;
import com.wechat.www.utils.StringTools;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * WebSocket 在线连接管理
 * 维护  userId → Channel  的映射，发消息时按 userId 找到连接直接推
 */
@Slf4j
@Component
public class ChannelContextUtils {

  /** 在线用户：userId → Netty Channel（线程安全 Map） */
  public static final ConcurrentMap<String, Channel> USER_CONTEXT_MAP = new ConcurrentHashMap<>();

  /** 在线群连接：groupId → 该群所有在线成员 Channel（Netty 自带，写一次全员收到） */
  public static final ConcurrentMap<String, ChannelGroup> GROUP_CONTEXT_MAP = new ConcurrentHashMap<>();

  @Resource
  private RedisComponet redisComponet;

  @Resource
  private ChatSessionUserMapper chatSessionUserMapper;

  @Resource
  private ChatSessionMapper chatSessionMapper;

  @Resource
  private ChatMessageMapper chatMessageMapper;

  @Resource
  private UserInfoMapper userInfoMapper;

  @Resource
  private UserContactApplyMapper userContactApplyMapper;

  /**
   * 用户连接成功后调用：登记 Channel、保存心跳
   */
  public void addContext(String userId, Channel channel) {
    try {
      // ===== 第一步：登记 Channel =====
      // 把 userId 绑到 Channel 的属性上，断开时能反查是谁
      String channelId = channel.id().toString();
      AttributeKey<String> attributeKey = AttributeKey.valueOf(channelId);
      channel.attr(attributeKey).set(userId);

      // ===== 拉一次我在的所有群 =====
      // 下面"登记群通道"和"查离线消息"都要用，只查一次 Redis
      List<String> contactList = redisComponet.getUserContactList(userId);
      List<String> groupIdList = new ArrayList<>();
      if (contactList != null) {
        for (String contactId : contactList) {
          if (contactId.startsWith(UserContactTypeEnum.GROUP.getPrefix())) {
            groupIdList.add(contactId);
          }
        }
      }
      // 把当前连接登记进每个群的 ChannelGroup（群消息广播用）
      for (String groupId : groupIdList) {
        add2Group(groupId, channel);
      }

      // 同一用户重连：把旧连接顶掉并主动关掉，避免旧连接一直挂着变成僵尸。
      // 旧连接关闭后触发的 channelInactive 会被上面的 remove(userId, channel) 拦下，不会误伤新连接。
      Channel oldChannel = USER_CONTEXT_MAP.put(userId, channel);
      if (oldChannel != null && oldChannel != channel) {
        oldChannel.close();
      }
      redisComponet.saveUserHeartBeat(userId);
      log.info("用户 {} 上线，当前在线 {} 人", userId, USER_CONTEXT_MAP.size());

      // ===== 更新最后登录时间（对照源项目，资料页/头像缓存用）=====
      userInfoMapper.update(null,
        new LambdaUpdateWrapper<UserInfo>()
          .eq(UserInfo::getUserId, userId)
          .set(UserInfo::getLastLoginTime, new Date()));

      // ===== 第二步：查我的会话列表 =====
      // 从 chat_session_user 查当前用户的所有会话
      List<ChatSessionUser> sessionList = chatSessionUserMapper.selectList(
        new LambdaQueryWrapper<ChatSessionUser>()
          .eq(ChatSessionUser::getUserId, userId));

      // 联查 chat_session，把最后一条消息和时间填进去
      if (!sessionList.isEmpty()) {
        List<String> sessionIds = sessionList.stream()
          .map(ChatSessionUser::getSessionId).toList();
        List<ChatSession> sessionInfoList = chatSessionMapper.selectByIds(sessionIds);
        Map<String, ChatSession> sessionMap = sessionInfoList.stream()
          .collect(Collectors.toMap(ChatSession::getSessionId, s -> s));
        // 过滤掉 chat_session 里没有的会话（下面这几种场景下才有可能会产生chat_session_user没有对应sessionId的脏数据这时过滤是有效的）
        // 1、DBA 手工删 chat_session
        // 2、数据迁移中断
        // 3、未来新代码忘了加事务
        // 4、从备份恢复
        // 这几种场景都属于运维/人为事故，不是程序正常跑出来
        sessionList = sessionList.stream()
          .filter(csu -> sessionMap.containsKey(csu.getSessionId()))
          .collect(Collectors.toList());
        for (ChatSessionUser csu : sessionList) {
          ChatSession cs = sessionMap.get(csu.getSessionId());
          if (cs != null) {
            csu.setLastMessage(cs.getLastMessage());
            csu.setLastReceiveTime(cs.getLastReceiveTime());
          }
        }
        // 我们 chat_session_user 表没有 last_receive_time 列，
        // 用刚从 chat_session 填进去的时间在内存排序：最后消息时间倒序
        sessionList.sort((a, b) -> {
          Long ta = a.getLastReceiveTime() == null ? 0L : a.getLastReceiveTime();
          Long tb = b.getLastReceiveTime() == null ? 0L : b.getLastReceiveTime();
          return tb.compareTo(ta);
        });
      }

      // ===== 第三步：查离线消息（只拉"上次下线之后"的，超3天截断）=====
      UserInfo me = userInfoMapper.selectById(userId);
      Long sourceLastOffTime = me == null ? null : me.getLastOffTime();
      Long lastOffTime = sourceLastOffTime;
      long threeDaysAgo = System.currentTimeMillis() - CommonConstants.MILLISECOND_3DAYS_AGO;
      // 离线超过3天的，就只取最近3天，避免一次推一大堆历史
      if (sourceLastOffTime != null
        && threeDaysAgo > sourceLastOffTime) {
        lastOffTime = threeDaysAgo;
      }
      // LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<ChatMessage>()
      //   .eq(ChatMessage::getContactId, userId);   // TODO 群模块后补：加 in(我所在的群ID列表)

      // 单聊消息的 contact_id = 接收人 userId
      // 群消息的 contact_id = 群ID（不是接收人）
      // 所以要把"我"和"我所有的群"一起放进 in 条件
      List<String> contactIdList = new ArrayList<>();
      contactIdList.add(userId);
      contactIdList.addAll(groupIdList);

      LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<ChatMessage>()
        .in(ChatMessage::getContactId, contactIdList);


      if (lastOffTime != null) {
        msgWrapper.ge(ChatMessage::getSendTime, lastOffTime);
      }
      List<ChatMessage> messageList = chatMessageMapper.selectList(
        msgWrapper.orderByAsc(ChatMessage::getSendTime));

      // ===== 第四步：组装 INIT 数据 =====
      WsInitData initData = new WsInitData();
      initData.setChatSessionList(sessionList);
      initData.setChatMessageList(messageList);
      // 真实统计"待我处理的好友申请数"，前端右上角红点靠它
      // 源项目额外限定"上次离线之后"的新申请（last_apply_time >= 上次离线时间）
      LambdaQueryWrapper<UserContactApply> applyWrapper = new LambdaQueryWrapper<UserContactApply>()
        .eq(UserContactApply::getReceiveUserId, userId)
        .eq(UserContactApply::getStatus, UserContactApplyStatusEnum.INIT.getStatus());
      if (sourceLastOffTime != null) {
        applyWrapper.ge(UserContactApply::getLastApplyTime, sourceLastOffTime);
      }
      Long applyCount = userContactApplyMapper.selectCount(applyWrapper);
      initData.setApplyCount(applyCount == null ? 0 : applyCount.intValue());

      // ===== 第五步：推给前端 =====
      MessageSendDto<WsInitData> initMsg = new MessageSendDto<>();
      initMsg.setMessageType(MessageTypeEnum.INIT.getType());  // 0
      initMsg.setContactId(userId);// 初始化数据登陆时推给自己，所有联系人contactId填自己的用户userId就行
      initMsg.setExtendData(initData);
      sendMsg(initMsg, userId);
    } catch (Exception e) {
      log.error("用户 {} 上线初始化失败", userId, e);
    }


  }

  /**
   * 连接断开时调用：移除 Channel、清除心跳
   * 调用场景：主网络断开、关窗口、心跳超时
   * 作用：只清心跳（在线状态），保留 token
   * 为什么正常掉线不删 token？---->用户可能只是**断网、掉线、关电脑 / 切后台**，几秒钟后重连，不该被要求重新登录
   */
  public void removeContext(Channel channel) {
    String channelId = channel.id().toString();
    AttributeKey<String> attributeKey = AttributeKey.valueOf(channelId);
    String userId = channel.attr(attributeKey).get();
    if (StringTools.isEmpty(userId)) {
      return;
    }
    // 【关键】带值删除：只有 map 里存的仍然是"这条连接"时才清理。
    // 用户"退出 → 立刻重新登录"时，旧连接的 channelInactive 是异步延迟触发的；
    // 如果无条件 remove(userId)，会把新连接刚 put 进去的 Channel 一起删掉，
    // 结果新连接明明活着，USER_CONTEXT_MAP 里却查不到 → 消息静默丢失。
    // ConcurrentHashMap.remove(key, value) 是原子的"比较并删除"，天然免疫这个竞态。
    if (!USER_CONTEXT_MAP.remove(userId, channel)) {
      return;   // 已被新连接顶替（或已被 closeContext 主动清理），不能碰新连接的心跳/下线时间
    }
    redisComponet.removeUserHeartBeat(userId);
    // 更新最后下线时间：下次上线时，离线消息就从这个时间点开始拉
    try {
      userInfoMapper.update(null,
        new LambdaUpdateWrapper<UserInfo>()
          .eq(UserInfo::getUserId, userId)
          .set(UserInfo::getLastOffTime, System.currentTimeMillis()));
    } catch (Exception e) {
      log.error("更新用户 {} 下线时间失败", userId, e);
    }
    log.info("用户 {} 下线，当前在线 {} 人", userId, USER_CONTEXT_MAP.size());
  }

  /**
   * 主动断开某个用户的连接（踢下线）
   * 调用场景：主动登出、管理员踢人、改密码
   * 作用：清 token（登录态）+ 关 Channel，什么都不保留
   */
  public void closeContext(String userId) {
    if (StringTools.isEmpty(userId)) return;
    redisComponet.cleanUserTokenByUserId(userId);
    Channel channel = USER_CONTEXT_MAP.get(userId);
    USER_CONTEXT_MAP.remove(userId);
    if (channel != null) {
      channel.close();
    }
  }

  /**
   * 给指定用户发一条 WebSocket 消息
   */
  public void sendMsg(MessageSendDto<?> msg, String receiveUserId) {
    if (receiveUserId == null) return;
    Channel sendChannel = USER_CONTEXT_MAP.get(receiveUserId);
    if (sendChannel == null) return;  // 用户不在线，不在线下发送

    if (MessageTypeEnum.ADD_FRIEND_SELF.getType().equals(msg.getMessageType())) {
      // 特殊分支：打招呼消息发回给申请人自己。
      // 此时 sendUserId 也是申请人，常规覆写会让申请人看到"聊天对象是自己"→错乱。
      // 真正的联系人(审批人)在 extendData 里，取出来填进 contactId/contactName，
      // 然后把类型转回普通 ADD_FRIEND(1)、清掉 extendData——前端永远收不到 13
      // 用 instanceof 而不是强转：消息经 Redis RTopic 跨实例传输，万一 extendData
      // 反序列化得不对也只是跳过推送，不会抛 ClassCastException 炸掉监听线程
      if (msg.getExtendData() instanceof UserInfo userInfo) {
        msg.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());  // 转回普通打招呼消息
        msg.setContactId(userInfo.getUserId());
        msg.setContactName(userInfo.getNickName());
        msg.setExtendData(null);
      }
    } else  {
      // 推送给接收方之前：把 contactId 从"接收人"改成"发送人"
      // 原因：对接收方来说，这条消息的"联系人"就是发送方
      // 比如 A 发消息给 B，msg.contactId 原本是 B（接收人），
      // 推给 B 之前要改成 A（发送人），否则 B 的前端以为联系人是自己
      // 常规消息：推给接收方之前，contactId 从"接收人"改成"发送人"
      msg.setContactId(msg.getSendUserId());
      msg.setContactName(msg.getSendUserNickName());
    }

    String json = JsonUtils.convertObj2Json(msg);
    System.out.println("=== sendMsg 推送给 " + receiveUserId + ": " + json);  // ← 加这行
    sendChannel.writeAndFlush(new TextWebSocketFrame(json));
  }


  /**
   * 消息按 contactId 前缀分发：U 开头单人推，G 开头群推
   * 入口：MessageHandler 收到 Redis 广播后调用
   */
  public void sendMessage(MessageSendDto<?> msg) {
    UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(msg.getContactId());
    if (contactTypeEnum == null)  return;
    switch (contactTypeEnum) {
      case USER -> sendMsg(msg, msg.getContactId());   // 单人：原有逻辑（会覆写 contactId=发送人）
      case GROUP -> sendMsg2Group(msg);                // 群：原样广播，contactId 保持群ID
    }
  }

  /** 群消息：往 ChannelGroup 里写一次，群里所有在线成员都能收到 */
  private void sendMsg2Group(MessageSendDto<?> msg) {
    if (msg.getContactId() == null) return;
    ChannelGroup group = GROUP_CONTEXT_MAP.get(msg.getContactId());
    if (group == null) return;   // 群里没人 / 从没人上线过
    group.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(msg)));
  }

  /** 把一条连接加进群 ChannelGroup；群没有就新建一个 */
  private void add2Group(String groupId, Channel channel) {
    ChannelGroup group = GROUP_CONTEXT_MAP.get(groupId);
    if (group == null) {
      group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
      GROUP_CONTEXT_MAP.put(groupId, group);
    }
    if (channel == null) {
      return;   // 用户不在线，只把群容器建出来
    }
    group.add(channel);
  }

  /** 用户上线/入群时调用：把他的连接登记到群里 */
  public void addUser2Group(String userId, String groupId) {
    Channel channel = USER_CONTEXT_MAP.get(userId);
    add2Group(groupId, channel);
  }

  /**
   * 服务端关闭前调用：把所有在线用户的下线时间落库
   * 为什么不能只靠 removeContext？
   * 容器关闭时 Netty 是异步断连的，channelInactive 触发时数据源已经销毁，
   * 写库会失败 → last_off_time 停在很久以前 → 重启后 INIT 把已读消息重推一遍
   * → 前端未读数累加，红点复活
   */
  public void saveAllUserOffTime() {
    if (USER_CONTEXT_MAP.isEmpty()) return;
    long now = System.currentTimeMillis();
    for (String userId : USER_CONTEXT_MAP.keySet()) {
      userInfoMapper.update(null,
        new LambdaUpdateWrapper<UserInfo>()
          .eq(UserInfo::getUserId, userId)
          .set(UserInfo::getLastOffTime, now));
    }
    log.info("服务端关闭，已保存 {} 个在线用户的下线时间", USER_CONTEXT_MAP.size());
  }
}
