package com.wechat.www.runner;

import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.UserStatusEnum;
import com.wechat.www.mapper.UserInfoMapper;
import com.wechat.www.netty.NettyWebSocketStarter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Date;

/**
 * 项目启动时执行的初始化任务
 * 实现 ApplicationRunner：Spring Boot 启动完成后自动执行 run()
 * 1. 确保机器人账号 Urobot 在 user_info 表里存在（换库不用手动插）
 * 2. 测试数据库连通后启动 Netty WebSocket 服务
 */
@Slf4j
@Component
public class InitRunner implements ApplicationRunner {

  @Resource
  private DataSource dataSource;

  @Resource
  private NettyWebSocketStarter nettyWebSocketStarter;

  @Resource
  private UserInfoMapper userInfoMapper;

  @Override
  public void run(ApplicationArguments args) {
    initRobotAccount();
    startNettyServe();
  }

  /**
   * 检查机器人账号是否存在，不存在就插入
   */
  private void initRobotAccount() {
    // 1. 按主键 Urobot 查一下
    UserInfo robot = userInfoMapper.selectById("Urobot");
    if (robot != null) {
      return;  // 已经存在，不用插
    }

    // 2. 不存在，插入一条机器人账号
    Date now = new Date();
    UserInfo robotUser = new UserInfo();
    robotUser.setUserId("Urobot");
    robotUser.setEmail("robot@easychat.com");
    robotUser.setNickName("EasyChat");
    robotUser.setJoinType(0);
    robotUser.setSex(1);
    robotUser.setPassword("e10adc3949ba59abbe56e057f20f883e"); // 123456 的 MD5，机器人不会登录
    robotUser.setPersonalSignature("我是机器人，有问题随时找我");
    robotUser.setStatus(UserStatusEnum.ENABLE.getStatus());
    robotUser.setCreateTime(now);
    robotUser.setLastOffTime(now.getTime());

    userInfoMapper.insert(robotUser);
    log.info("初始化机器人账号 Urobot 成功");
  }

  /**
   * 测试数据库连通后，新开线程启动 Netty（不阻塞主线程）
   */
  private void startNettyServe() {
    try {
      // 先拿一下数据库连接，连不上说明配置错了
      dataSource.getConnection();
      // 启动 Netty WebSocket 服务
      new Thread(nettyWebSocketStarter).start();
      log.info("服务启动成功，可以开始愉快的开发了");
    } catch (SQLException e) {
      log.error("数据库配置错误，请检查数据库配置");
    } catch (Exception e) {
      log.error("服务启动失败", e);
    }
  }
}
