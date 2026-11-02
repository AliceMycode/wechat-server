package com.wechat.www.controller;

import com.wechat.www.annotation.GlobalInterceptor;
import com.wechat.www.dto.SaveUserInfoDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.service.UserInfoService;
import com.wechat.www.utils.CopyTools;
import com.wechat.www.vo.ResponseVO;
import com.wechat.www.vo.UserInfoVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息相关接口
 */
@RestController
@RequestMapping("/userInfo")
public class UserInfoController extends BaseController {

  @Resource
  private UserInfoService userInfoService;

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
}
