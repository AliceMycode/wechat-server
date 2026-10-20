package com.wechat.www.service;

import com.wechat.www.entity.UserContact;
import com.wechat.www.enums.UserContactTypeEnum;

import java.util.List;

/**
 * 联系人业务接口
 * （对应源项目 UserContactService；先只放注册时用的机器人好友方法，后面加好友/加群再加）
 */
public interface UserContactService {

  /**
   * 新用户注册后，自动加"机器人好友"，并初始化一个会话 + 一条欢迎消息
   * @param userId 新注册用户ID
   */
  void addContact4Robot(String userId);

  /**
   * 加载联系人列表（好友/群组）
   */
  List<UserContact> loadContact(
    String userId, UserContactTypeEnum contactType);
}
