package com.wechat.www.service;

import com.wechat.www.dto.TokenUserInfoDto;

/**
 * 好友申请业务接口
 * 对应源项目 UserContactApplyService；这个闭环只做"发起申请"，处理申请(同意/拒绝)是下一个闭环
 */
public interface UserContactApplyService {

  /**
   * 发起加好友申请
   * @param tokenDto   当前登录用户（申请人）
   * @param contactId  要加的人的ID
   * @param contactType "user"/"group"
   * @param applyInfo  申请附言（可为空，空了自动填"我是{昵称}"）
   * @return 对方的加入方式（0直接加入/1需要审核），前端拿它决定提示语
   */
  Integer applyAdd(TokenUserInfoDto tokenDto, String contactId, String contactType, String applyInfo);
}
