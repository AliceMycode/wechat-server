package com.wechat.www.controller;

import com.wechat.www.annotation.GlobalInterceptor;
import com.wechat.www.dto.SaveGroupDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.GroupInfo;
import com.wechat.www.enums.MessageTypeEnum;
import com.wechat.www.service.GroupInfoService;
import com.wechat.www.vo.GroupInfoVO;
import com.wechat.www.vo.ResponseVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 群聊相关接口
 */
@Validated
@RestController
@RequestMapping("/group")
public class GroupController extends BaseController {

  @Resource
  private GroupInfoService groupInfoService;

  /**
   * 建群 / 改群
   * groupId 为空 = 建群，非空 = 修改群
   * 头像单独接：前端没换头像时传的是字符串 groupId，放 DTO 会绑定失败
   */
  @PostMapping("/saveGroup")
  @GlobalInterceptor
  public ResponseVO<Void> saveGroup(HttpServletRequest request,
                                    @Valid SaveGroupDto dto,
                                    @RequestParam(required = false) MultipartFile avatarFile,
                                    @RequestParam(required = false) MultipartFile avatarCover) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    GroupInfo groupInfo = new GroupInfo();
    groupInfo.setGroupId(dto.getGroupId());
    groupInfo.setGroupOwnerId(tokenDto.getUserId());   // 群主永远是当前登录用户
    groupInfo.setGroupName(dto.getGroupName());
    groupInfo.setGroupNotice(dto.getGroupNotice());
    groupInfo.setJoinType(dto.getJoinType());
    groupInfoService.saveGroup(groupInfo, avatarFile, avatarCover);
    return getSuccessResponseVO(null);
  }

  /** 我创建的群（联系人 → 群聊 → 我创建的群） */
  @PostMapping("/loadMyGroup")
  @GlobalInterceptor
  public ResponseVO<List<GroupInfo>> loadMyGroup(HttpServletRequest request) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    return getSuccessResponseVO(groupInfoService.loadMyGroup(tokenDto.getUserId()));
  }

  /** 群详情（含成员数） */
  @PostMapping("/getGroupInfo")
  @GlobalInterceptor
  public ResponseVO<GroupInfo> getGroupInfo(HttpServletRequest request,
                                            @NotEmpty String groupId) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    return getSuccessResponseVO(groupInfoService.getGroupInfo(tokenDto.getUserId(), groupId));
  }

  /** 聊天面板右侧：群详情 + 成员列表 */
  @PostMapping("/getGroupInfo4Chat")
  @GlobalInterceptor
  public ResponseVO<GroupInfoVO> getGroupInfo4Chat(HttpServletRequest request,
                                                   @NotEmpty String groupId) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    return getSuccessResponseVO(groupInfoService.getGroupInfo4Chat(tokenDto.getUserId(), groupId));
  }



  /** 退出群聊 */
  @PostMapping("/leaveGroup")
  @GlobalInterceptor
  public ResponseVO<Void> leaveGroup(HttpServletRequest request, @NotEmpty String groupId) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    groupInfoService.leaveGroup(tokenDto.getUserId(), groupId, MessageTypeEnum.LEAVE_GROUP);
    return getSuccessResponseVO(null);
  }

  /** 解散群聊（仅群主） */
  @PostMapping("/dissolutionGroup")
  @GlobalInterceptor
  public ResponseVO<Void> dissolutionGroup(HttpServletRequest request, @NotEmpty String groupId) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    groupInfoService.dissolutionGroup(tokenDto.getUserId(), groupId);
    return getSuccessResponseVO(null);
  }

  /** 添加 / 移除群成员（opType: 0=移除 1=添加） */
  @PostMapping("/addOrRemoveGroupUser")
  @GlobalInterceptor
  public ResponseVO<Void> addOrRemoveGroupUser(HttpServletRequest request,
                                               @NotEmpty String groupId,
                                               @NotEmpty String selectContacts,
                                               @NotNull Integer opType) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    groupInfoService.addOrRemoveGroupUser(tokenDto, groupId, selectContacts, opType);
    return getSuccessResponseVO(null);
  }
}
