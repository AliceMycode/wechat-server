package com.wechat.www;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;


import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 项目启动时执行的初始化任务
 * 实现 ApplicationRunner：Spring Boot 启动完成后自动执行 run()
 */
@Slf4j
@Component("initRun")
public class InitRun implements ApplicationRunner {

  @Resource
  private DataSource dataSource;

  @Resource
  // private NettyWebSocketStarter nettyWebSocketStarter;

  @Override
  public void run(ApplicationArguments args) {
    try {
      // 1. 先拿一下数据库连接，连不上说明配置错了
      dataSource.getConnection();
      // 2. 启动 Netty WebSocket 服务（新开线程，不阻塞主线程）
      // new Thread(nettyWebSocketStarter).start();
      log.error("服务启动成功，可以开始愉快的开发了");
    } catch (SQLException e) {
      log.error("数据库配置错误，请检查数据库配置");
    } catch (Exception e) {
      log.error("服务启动失败", e);
    }
  }
}
