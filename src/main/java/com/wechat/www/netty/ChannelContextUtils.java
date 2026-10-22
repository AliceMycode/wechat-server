package com.wechat.www.netty;

import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.utils.JsonUtils;
import com.wechat.www.utils.StringTools;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

  /**
   * 用户连接成功后调用：登记 Channel、保存心跳
   */
  public void addContext(String userId, Channel channel) {
    // 把 userId 绑到 Channel 的属性上，断开时能反查是谁
    String channelId = channel.id().toString();
    AttributeKey<String> attributeKey = AttributeKey.valueOf(channelId);
    channel.attr(attributeKey).set(userId);

    USER_CONTEXT_MAP.put(userId, channel);
    redisComponet.saveUserHeartBeat(userId);
    log.info("用户 {} 上线，当前在线 {} 人", userId, USER_CONTEXT_MAP.size());
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
  public void sendMsg(MessageSendDto msg, String receiveUserId) {
    if (receiveUserId == null) return;
    Channel channel = USER_CONTEXT_MAP.get(receiveUserId);
    if (channel == null) return;  // 用户不在线，不在线下发送
    channel.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(msg)));
  }
}
