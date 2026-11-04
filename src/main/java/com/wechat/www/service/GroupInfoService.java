package com.wechat.www.service;

import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.GroupInfo;
import com.wechat.www.enums.MessageTypeEnum;
import com.wechat.www.vo.GroupInfoVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 群信息业务接口
 */
public interface GroupInfoService {

  /**
   * 建群 / 改群
   * @param groupInfo 群信息（groupId 为空则新建）
   * @param avatarFile 群头像（新建时必传）
   * @param avatarCover 缩略图（前端一起传）
   */
  void saveGroup(GroupInfo groupInfo, MultipartFile avatarFile, MultipartFile avatarCover);

  /** 我创建的群（按创建时间倒序） */
  List<GroupInfo> loadMyGroup(String userId);

  /** 群详情 + 成员数（含"我在不在群里"的校验） */
  GroupInfo getGroupInfo(String userId, String groupId);

  /** 聊天面板右侧：群详情 + 成员列表 */
  GroupInfoVO getGroupInfo4Chat(String userId, String groupId);


  /** 退群 / 被踢。messageType 区分：LEAVE_GROUP(11) 主动退，REMOVE_GROUP(12) 被踢 */
  void leaveGroup(String userId, String groupId, MessageTypeEnum messageTypeEnum);

  /** 解散群（仅群主） */
  void dissolutionGroup(String userId, String groupId);

  /**
   * 群主拉人 / 踢人
   * @param opType 0=移除 1=添加
   * @param selectContacts 逗号分隔的用户ID串
   */
  void addOrRemoveGroupUser(TokenUserInfoDto tokenDto, String groupId,
                            String selectContacts, Integer opType);

}
