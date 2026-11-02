package com.wechat.www.service;

import com.wechat.www.dto.SaveUserInfoDto;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.vo.UserInfoVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息业务接口
 * （对应源项目 UserInfoService；这里先列注册、登录，其余用到再加）
 */
public interface UserInfoService {

  /** 注册 */
  void register(String email, String nickName, String password);

  /** 登录，返回带 token 的用户信息 */
  UserInfoVO login(String email, String password);

  /** 按用户ID查用户信息 */
  UserInfo getUserInfoByUserId(String userId);

  /**
   * 保存个人信息；昵称变化时会同步 Redis 登录态、会话冗余字段并通知好友
   */
  void updateUserInfo(String userId, SaveUserInfoDto dto,
                      MultipartFile avatarFile, MultipartFile avatarCover);

  /** 修改密码：入参是前端提交的明文，内部 MD5 后入库 */
  void updatePassword(String userId, String password);
}
