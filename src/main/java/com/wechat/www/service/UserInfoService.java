package com.wechat.www.service;

import com.wechat.www.vo.UserInfoVO;

/**
 * 用户信息业务接口
 * （对应源项目 UserInfoService；这里先列注册、登录，其余用到再加）
 */
public interface UserInfoService {

  /** 注册 */
  void register(String email, String nickName, String password);

  /** 登录，返回带 token 的用户信息 */
  UserInfoVO login(String email, String password);
}
