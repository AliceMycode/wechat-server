package com.wechat.www.controller;

import com.wechat.www.annotation.GlobalInterceptor;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.dto.UserContactSearchResultDto;
import com.wechat.www.dto.ApplyAddDto;
import com.wechat.www.entity.UserContact;
import com.wechat.www.entity.UserContactApply;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.enums.UserContactStatusEnum;
import com.wechat.www.enums.UserContactTypeEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.service.UserContactApplyService;
import com.wechat.www.service.UserContactService;
import com.wechat.www.service.UserInfoService;
import com.wechat.www.utils.CopyTools;
import com.wechat.www.vo.PaginationResultVO;
import com.wechat.www.vo.ResponseVO;
import com.wechat.www.vo.UserInfoVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 联系人相关接口
 */
@Validated   // ← 加这行：开启方法级参数校验，参数上的 @NotEmpty/@NotNull 才会生效
@RestController
@RequestMapping("/contact")
public class UserContactController extends BaseController {

  @Resource
  private UserContactService userContactService;

  @Resource
  private UserContactApplyService userContactApplyService;

  @Resource
  private UserInfoService userInfoService;

  /**
   * 加载联系人列表
   * @param contactType "user"=好友  "group"=群组
   */
  @PostMapping("/loadContact")
  @GlobalInterceptor
  public ResponseVO<List<UserContact>> loadContact(HttpServletRequest request, @NotEmpty String contactType) {
    // 1. 解析当前登录用户
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    // 2. 把字符串转成枚举（前端传 "user" 或 "group"）
    UserContactTypeEnum typeEnum = UserContactTypeEnum.getByName(contactType);
    if (typeEnum == null) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }
    if (UserContactTypeEnum.USER == typeEnum) {

    } else if (UserContactTypeEnum.GROUP == typeEnum) {
     // TODO 群聊
    }
    // 3. 查列表
    List<UserContact> list = userContactService.loadContact(tokenDto.getUserId(), typeEnum);
    return getSuccessResponseVO(list);
  }



  /**
   * 搜索联系人：按完整用户ID查人 + 我和TA的关系
   * （参数名 contactId 是搜索词=完整用户ID，前端写死，别改）
   */
  @PostMapping("/search")
  @GlobalInterceptor
  public ResponseVO<UserContactSearchResultDto> search(HttpServletRequest request,
                                                       @NotEmpty String contactId) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    UserContactSearchResultDto dto =
      userContactService.searchContact(tokenDto.getUserId(), contactId);
    return getSuccessResponseVO(dto);
  }

  /**
   * 发起加好友申请
   */
  @PostMapping("/applyAdd")
  @GlobalInterceptor
  public ResponseVO<Integer> applyAdd(HttpServletRequest request, @Valid ApplyAddDto applyAddDto) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    Integer joinType = userContactApplyService.applyAdd(tokenDto, applyAddDto.getContactId(), applyAddDto.getContactType(), applyAddDto.getApplyInfo());
    return getSuccessResponseVO(joinType);
  }


  /**
   * 加载发给我的好友申请列表（分页）
   * @param pageNo 页码，第一页可不传（默认1）
   */
  @PostMapping("/loadApply")
  @GlobalInterceptor
  public ResponseVO<PaginationResultVO<UserContactApply>> loadApply(HttpServletRequest request,
                                                                    Integer pageNo) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    PaginationResultVO<UserContactApply> result =
      userContactApplyService.loadApply(tokenDto.getUserId(), pageNo);
    return getSuccessResponseVO(result);
  }


  /**
   * 处理好友申请：同意 / 拒绝 / 拉黑
   * @param applyId 申请记录ID
   * @param status  处理结果：1同意 2拒绝 3拉黑（不能传0）
   */
  @PostMapping("/dealWithApply")
  @GlobalInterceptor
  public ResponseVO<Void> dealWithApply(HttpServletRequest request,
                                        @NotNull Integer applyId,
                                        @NotNull Integer status) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    userContactApplyService.dealWithApply(tokenDto.getUserId(), applyId, status);
    return getSuccessResponseVO(null);
  }

  /**
   * 获取用户资料（头像悬浮层用）：对方基本信息 + 我和TA的关系状态
   * 前端据 contactStatus 决定按钮：1 显示"发送消息"，其他显示"加为好友"
   * @param contactId 对方用户ID
   */
  @PostMapping("/getContactInfo")
  @GlobalInterceptor
  public ResponseVO<UserInfoVO> getContactInfo(HttpServletRequest request,
                                               @NotEmpty String contactId) {
    // 1. 当前登录用户
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    // 2. 查对方用户信息（service 内部已判空，查不到直接抛"用户不存在"）
    UserInfo userInfo = userInfoService.getUserInfoByUserId(contactId);
    // 3. 转 VO（不含 password）
    UserInfoVO vo = CopyTools.copy(userInfo, UserInfoVO.class);
    // 4. 关系状态先兜底为"非好友"，查到关系就覆盖成真实状态
    vo.setContactStatus(UserContactStatusEnum.NOT_FRIEND.getStatus());
    UserContact userContact = userContactService.getUserContactByUserIdAndContactId(
      tokenDto.getUserId(), contactId);
    if (userContact != null) {
      vo.setContactStatus(userContact.getStatus());
    }
    return getSuccessResponseVO(vo);
  }

  /**
   * 获取好友详情（好友资料页用）：必须存在有效关系才允许查看
   * @param contactId 好友用户ID
   */
  @PostMapping("/getContactUserInfo")
  @GlobalInterceptor
  public ResponseVO<UserInfoVO> getContactUserInfo(HttpServletRequest request,
                                                   @NotEmpty String contactId) {
    // 1. 当前登录用户
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    // 2. 查"我→TA"的关系：好友 / 被删 / 被拉黑 / 首次被拉黑 才允许看
    //    注意：6(首次被拉黑) 不会出现在 loadContact 里，好友列表点不进来；
    //    这里放行是为了群成员资料页（群成员不必是好友）等非列表入口
    UserContact userContact = userContactService.getUserContactByUserIdAndContactId(
      tokenDto.getUserId(), contactId);
    if (userContact == null || !List.of(
      UserContactStatusEnum.FRIEND.getStatus(),
      UserContactStatusEnum.DEL_BE.getStatus(),
      UserContactStatusEnum.BLACKLIST_BE.getStatus(),
      UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus()
    ).contains(userContact.getStatus())) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }
    // 3. 查对方用户信息，转 VO 返回
    UserInfo userInfo = userInfoService.getUserInfoByUserId(contactId);
    return getSuccessResponseVO(CopyTools.copy(userInfo, UserInfoVO.class));
  }


}
