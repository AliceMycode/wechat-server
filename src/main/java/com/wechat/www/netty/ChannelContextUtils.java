package com.wechat.www.netty;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.WsInitData;
import com.wechat.www.entity.ChatMessage;
import com.wechat.www.entity.ChatSession;
import com.wechat.www.entity.ChatSessionUser;
import com.wechat.www.enums.MessageTypeEnum;
import com.wechat.www.mapper.ChatMessageMapper;
import com.wechat.www.mapper.ChatSessionMapper;
import com.wechat.www.mapper.ChatSessionUserMapper;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.utils.JsonUtils;
import com.wechat.www.utils.StringTools;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
      }

      // ===== 第三步：查离线消息 =====
      // 查发给我的消息（contact_id = 当前用户ID），按时间正序
      List<ChatMessage> messageList = chatMessageMapper.selectList(
        new LambdaQueryWrapper<ChatMessage>()
          .eq(ChatMessage::getContactId, userId)
          .orderByAsc(ChatMessage::getSendTime));

      // ===== 第四步：组装 INIT 数据 =====
      WsInitData initData = new WsInitData();
      initData.setChatSessionList(sessionList);
      initData.setChatMessageList(messageList);
      initData.setApplyCount(0);  // 好友申请数，后面做好友功能再补

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
    String json = JsonUtils.convertObj2Json(msg);
    System.out.println("=== sendMsg 推送给 " + receiveUserId + ": " + json);  // ← 加这行
    channel.writeAndFlush(new TextWebSocketFrame(json));
  }
}
