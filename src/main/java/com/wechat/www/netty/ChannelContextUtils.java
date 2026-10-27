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
import com.wechat.www.mapper.ChatMessageMapper;
import com.wechat.www.mapper.ChatSessionMapper;
import com.wechat.www.mapper.ChatSessionUserMapper;
import com.wechat.www.mapper.UserContactApplyMapper;
import com.wechat.www.mapper.UserInfoMapper;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.utils.JsonUtils;
import com.wechat.www.utils.StringTools;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

      USER_CONTEXT_MAP.put(userId, channel);
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
      // 离线超过3天的，就只取最近3天，避免一次推一大堆历史
      if (sourceLastOffTime != null
          && System.currentTimeMillis() - CommonConstants.MILLISECOND_3DAYS_AGO > sourceLastOffTime) {
        lastOffTime = System.currentTimeMillis() - CommonConstants.MILLISECOND_3DAYS_AGO;
      }
      LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<ChatMessage>()
        .eq(ChatMessage::getContactId, userId);   // TODO 群模块后补：加 in(我所在的群ID列表)
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
      Long applyCount = userContactApplyMapper.selectCount(
        new LambdaQueryWrapper<UserContactApply>()
          .eq(UserContactApply::getReceiveUserId, userId)
          .eq(UserContactApply::getStatus, UserContactApplyStatusEnum.INIT.getStatus()));
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
    if (!StringTools.isEmpty(userId)) {
      USER_CONTEXT_MAP.remove(userId);
      redisComponet.removeUserHeartBeat(userId);
      // 更新最后下线时间：下次上线时，离线消息就从这个时间点开始拉
      userInfoMapper.update(null,
        new LambdaUpdateWrapper<UserInfo>()
          .eq(UserInfo::getUserId, userId)
          .set(UserInfo::getLastOffTime, System.currentTimeMillis()));
      log.info("用户 {} 下线，当前在线 {} 人", userId, USER_CONTEXT_MAP.size());
    }
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
    Channel channel = USER_CONTEXT_MAP.get(receiveUserId);
    if (channel == null) return;  // 用户不在线，不在线下发送

    // 推送给接收方之前：把 contactId 从"接收人"改成"发送人"
    // 原因：对接收方来说，这条消息的"联系人"就是发送方
    // 比如 A 发消息给 B，msg.contactId 原本是 B（接收人），
    // 推给 B 之前要改成 A（发送人），否则 B 的前端以为联系人是自己
    if (msg.getSendUserId() != null) {  // INIT 推送时 sendUserId 是 null，跳过
      msg.setContactId(msg.getSendUserId());
      msg.setContactName(msg.getSendUserNickName());
    }

    String json = JsonUtils.convertObj2Json(msg);
    System.out.println("=== sendMsg 推送给 " + receiveUserId + ": " + json);  // ← 加这行
    channel.writeAndFlush(new TextWebSocketFrame(json));
  }
}
