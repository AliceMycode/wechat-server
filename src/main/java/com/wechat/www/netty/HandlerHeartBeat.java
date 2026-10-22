package com.wechat.www.netty;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 心跳处理器：60 秒没收到客户端消息就断开
 * 前端会定时发心跳包维持连接
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class HandlerHeartBeat extends ChannelDuplexHandler {

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent e) {
      if (e.state() == IdleState.READER_IDLE) {
        // 读超时：60 秒没收到任何消息，认为客户端掉线，关闭连接
        String channelId = ctx.channel().id().toString();
        AttributeKey<String> attributeKey = AttributeKey.valueOf(channelId);
        String userId = ctx.channel().attr(attributeKey).get();
        log.info("用户 {} 心跳超时，断开连接", userId);
        ctx.close();
      }
    }
  }
}
