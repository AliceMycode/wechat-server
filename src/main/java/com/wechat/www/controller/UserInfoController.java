package com.wechat.www.controller;

import com.wechat.www.annotation.GlobalInterceptor;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
