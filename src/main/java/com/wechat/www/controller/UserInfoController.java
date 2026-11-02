package com.wechat.www.controller;

import com.wechat.www.annotation.GlobalInterceptor;
import com.wechat.www.constant.CommonConstants;
import com.wechat.www.dto.SaveUserInfoDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.netty.ChannelContextUtils;
import com.wechat.www.service.UserInfoService;
import com.wechat.www.utils.CopyTools;
import com.wechat.www.vo.ResponseVO;
import com.wechat.www.vo.UserInfoVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息相关接口
 */
@Validated
@RestController
@RequestMapping("/userInfo")
public class UserInfoController extends BaseController {

  @Resource
  private UserInfoService userInfoService;

  @Resource
  private ChannelContextUtils channelContextUtils;   // ← 新增

  /**
   * 获取当前登录用户信息（进主界面时前端调一次，用来渲染右上角头像/昵称）
   */
  @PostMapping("/getUserInfo")
  @GlobalInterceptor
  public ResponseVO<UserInfoVO> getUserInfo(HttpServletRequest request) {
    // 1. 从 token 解析当前登录用户
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    // 2. 按 userId 查数据库
    UserInfo userInfo = userInfoService.getUserInfoByUserId(tokenDto.getUserId());
    // 3. 转成 VO（不带 password），补上 admin 标记
    UserInfoVO vo = CopyTools.copy(userInfo, UserInfoVO.class);
    vo.setAdmin(tokenDto.getAdmin());
    return getSuccessResponseVO(vo);
  }


  /**
   * 保存个人信息
   * 文本字段走 DTO；头像文件单独接（没换头像时前端传的是字符串 userId，放 DTO 会绑定失败）
   */
  @PostMapping("/saveUserInfo")
  @GlobalInterceptor
  public ResponseVO<UserInfoVO> saveUserInfo(HttpServletRequest request,
                                             @Valid SaveUserInfoDto dto,
                                             @RequestParam(required = false) MultipartFile avatarFile,
                                             @RequestParam(required = false) MultipartFile avatarCover) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    userInfoService.updateUserInfo(tokenDto.getUserId(), dto, avatarFile, avatarCover);
    // 直接复用 getUserInfo：返回最新资料，前端拿 result.data 刷新 store
    return getUserInfo(request);
  }


  /**
   * 修改密码
   * 前端传明文（UserInfoPassword.vue 没做 MD5），MD5 在 service 里做
   * 成功后立刻踢下线：清 Redis token + 关 WS，前端自己跳登录页
   * 注：前端同时提交的 rePassword 会被忽略（一致性前端已校验）
   */
  @PostMapping("/updatePassword")
  @GlobalInterceptor
  public ResponseVO<Void> updatePassword(HttpServletRequest request,
                                         @NotEmpty(message = "密码不能为空")
                                         @Pattern(regexp = CommonConstants.REGEX_PASSWORD,
                                           message = "密码只能是数字、字母、特殊字符8~18位") String password) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    userInfoService.updatePassword(tokenDto.getUserId(), password);
    channelContextUtils.closeContext(tokenDto.getUserId());
    return getSuccessResponseVO(null);
  }

  /**
   * 退出登录：只清服务端登录态（Redis token + WS 连接）
   * 前端是先跳登录页再 fire-and-forget 调这里，所以返回值不使用、也不能抛异常
   */
  @PostMapping("/logout")
  @GlobalInterceptor
  public ResponseVO<Void> logout(HttpServletRequest request) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    channelContextUtils.closeContext(tokenDto.getUserId());
    return getSuccessResponseVO(null);
  }
}
