package com.wechat.www.controller;

import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.UserContact;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.enums.UserContactTypeEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.service.UserContactService;
import com.wechat.www.vo.ResponseVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 联系人相关接口
 */
@RestController
@RequestMapping("/contact")
public class UserContactController extends BaseController {

  @Resource
  private UserContactService userContactService;

  /**
   * 加载联系人列表
   * @param contactType "user"=好友  "group"=群组
   */
  @PostMapping("/loadContact")
  public ResponseVO<List<UserContact>> loadContact(HttpServletRequest request, @NotEmpty String contactType) {
    // 1. 解析当前登录用户
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    if (tokenDto == null) {
      throw new BusinessException(ResponseCodeEnum.CODE_901);
    }
    // 2. 把字符串转成枚举（前端传 "user" 或 "group"）
    UserContactTypeEnum typeEnum = UserContactTypeEnum.getByName(contactType);
    if (typeEnum == null) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }
    // 3. 查列表
    List<UserContact> list = userContactService.loadContact(tokenDto.getUserId(), typeEnum);
    return getSuccessResponseVO(list);
  }
}
