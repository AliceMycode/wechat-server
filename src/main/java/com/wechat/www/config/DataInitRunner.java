package com.wechat.www.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.UserStatusEnum;
import com.wechat.www.mapper.UserInfoMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import java.util.Date;

/**
 * 项目启动时初始化基础数据
 * 目前只做一件事：确保机器人账号 Urobot 在 user_info 表里存在
 * （对应源项目作者手动插入的那条记录，我们改成启动自动补，换库不用再手动插）
 */
@Component
public class DataInitRunner implements ApplicationRunner {

  @Resource
  private UserInfoMapper userInfoMapper;

  @Override
  public void run(ApplicationArguments args) {
    initRobotAccount();
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
    System.out.println("=== 初始化机器人账号 Urobot 成功 ===");
  }
}
