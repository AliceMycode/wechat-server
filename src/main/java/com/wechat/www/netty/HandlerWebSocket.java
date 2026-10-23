package com.wechat.www.netty;

import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.utils.StringTools;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket 业务处理器
 * @ChannelHandler.Sharable：一个实例可以被多个 Channel 复用
 */

@Slf4j
@Component
@ChannelHandler.Sharable
public class HandlerWebSocket extends SimpleChannelInboundHandler<TextWebSocketFrame> {

  @Resource
  private ChannelContextUtils channelContextUtils;

  @Resource
  private RedisComponet redisComponet;

  /** 连接断开时 */
  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    channelContextUtils.removeContext(ctx.channel());
  }

  /** 收到消息（这里只用来收心跳包） */
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
    Channel channel = ctx.channel();
    String channelId = channel.id().toString();
    AttributeKey<String> attributeKey = AttributeKey.valueOf(channelId);
    String userId = channel.attr(attributeKey).get();
    if (userId != null) {
      redisComponet.saveUserHeartBeat(userId);  // 续约心跳
    }
  }

  /**
   *
   * HTTP 升级到 WebSocket 成功后触发
   * 在这里校验 token，校验通过才登记 Channel
   */
  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete complete) {
      String requestUri = complete.requestUri();   // 形如 /ws?token=xxx
      String token = getTokenFromUrl(requestUri);
      if (token == null) {
        ctx.channel().close();
        return;
      }
      // 去 Redis 查登录态
      TokenUserInfoDto tokenDto = redisComponet.getTokenUserInfoDto(token);
      if (tokenDto == null) {
        ctx.channel().close();  // token 无效，拒绝连接
        return;
      }
      // 校验通过，登记 Channel（D2 会在这里加推 INIT 数据）
      channelContextUtils.addContext(tokenDto.getUserId(), ctx.channel());
    }
  }

  /** 从 /ws?token=xxx 里取出 token */
  private String getTokenFromUrl(String url) {
    if (StringTools.isEmpty(url) || !url.contains("?")) return null;
    String query = url.substring(url.indexOf("?") + 1);
    for (String param : query.split("&")) {
      String[] kv = param.split("=");
      if (kv.length == 2 && "token".equals(kv[0])) {
        return kv[1];
      }
    }
    return null;
  }


  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    System.out.println("=== Netty异常: " + cause.getClass().getName() + " - " + cause.getMessage());
    cause.printStackTrace();
    ctx.close();
  }
}
