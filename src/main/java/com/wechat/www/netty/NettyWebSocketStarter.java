package com.wechat.www.netty;

import com.wechat.www.config.AppConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket 服务器启动器
 * 实现 Runnable，在 DataInitRunner 里 new Thread 启动
 */
@Slf4j
@Component
public class NettyWebSocketStarter implements Runnable {

  @Resource
  private AppConfig appConfig;

  @Resource
  private HandlerWebSocket handlerWebSocket;

  @Resource
  private HandlerHeartBeat handlerHeartBeat;
  /**
   * 1. 启动时，bossGroup 在 5051 端口上开了一个 ServerSocketChannel（监听端口）
   * 2. 它的线程一直轮询这个 ServerSocketChannel
   * 3. 新客户端连过来 → OS 完成 TCP 三次握手 → 连接放进 "已完成队列"
   * 4. bossGroup 线程从队列里取出这个连接 → 创建一个 NioSocketChannel（就是我们说的 Channel）→ 把这个 Channel 分配给 workerGroup 的某个线程
   * 5. bossGroup 立刻回去继续等下一个连接
   */
  private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);  // 接收连接
  private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();  // 处理读写

  @PreDestroy  // Spring 关闭时优雅释放
  public void close() {
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
  }

  @Override
  public void run() {
    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new HttpServerCodec());                    // HTTP 编解码
            p.addLast(new HttpObjectAggregator(64 * 1024));       // 请求聚合
            p.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS)); // 60秒读超时
            p.addLast(handlerHeartBeat);                    // 心跳处理
            p.addLast(new WebSocketServerProtocolHandler("/ws")); // 升级 WebSocket，路径 /ws
            p.addLast(handlerWebSocket);                          // 业务处理
          }
        });
      bootstrap.bind(appConfig.getWsPort()).sync();
      log.info("Netty WebSocket 启动成功，端口: {}", appConfig.getWsPort());
    } catch (Exception e) {
      log.error("Netty 启动失败", e);
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }
  }
}
