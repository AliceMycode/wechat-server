package com.wechat.www.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.www.config.AppConfig;
import com.wechat.www.constant.FileConstants;
import com.wechat.www.dto.AddContactDto;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.SysSettingDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.*;
import com.wechat.www.enums.*;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.mapper.*;
import com.wechat.www.netty.ChannelContextUtils;
import com.wechat.www.netty.MessageHandler;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.service.GroupInfoService;
import com.wechat.www.service.UserContactService;
import com.wechat.www.utils.CopyTools;
import com.wechat.www.utils.StringTools;
import com.wechat.www.vo.GroupInfoVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 群信息业务实现（对照源项目 GroupInfoServiceImpl 的建群/查群部分）
 */
@Slf4j
@Service
public class GroupInfoServiceImpl implements GroupInfoService {

  @Resource
  private GroupInfoMapper groupInfoMapper;

  @Resource
  private UserContactMapper userContactMapper;

  @Resource
  private ChatSessionMapper chatSessionMapper;

  @Resource
  private ChatSessionUserMapper chatSessionUserMapper;

  @Resource
  private ChatMessageMapper chatMessageMapper;

  @Resource
  private RedisComponet redisComponet;

  @Resource
  private MessageHandler messageHandler;

  @Resource
  private ChannelContextUtils channelContextUtils;

  @Resource
  private AppConfig appConfig;

  @Resource
  private UserInfoMapper userInfoMapper;

  @Autowired
  private UserContactService userContactService;


  /**
   * 建群 / 改群
   * 建群要写 5 张表 + Redis + WS，必须同一事务
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void saveGroup(GroupInfo groupInfo, MultipartFile avatarFile, MultipartFile avatarCover) {
    Date curDate = new Date();

    if (StringTools.isEmpty(groupInfo.getGroupId())) {
      // ==================== 建群 ====================

      // ① 建群数量上限（系统设置，Redis 没配就用默认 5）
      Long count = groupInfoMapper.selectCount(
        new LambdaQueryWrapper<GroupInfo>()
          .eq(GroupInfo::getGroupOwnerId, groupInfo.getGroupOwnerId()));
      SysSettingDto sysSettingDto = redisComponet.getSysSetting();
      if (count >= sysSettingDto.getMaxGroupCount()) {
        throw new BusinessException("最多只能创建" + sysSettingDto.getMaxGroupCount() + "个群聊");
      }
      // ② 建群必须传头像
      if (avatarFile == null) {
        throw new BusinessException(ResponseCodeEnum.CODE_600);
      }

      // ③ 生成群ID + 落库（status 不传，走数据库默认值 1）
      groupInfo.setCreateTime(curDate);
      groupInfo.setGroupId(StringTools.getGroupId());
      groupInfoMapper.insert(groupInfo);

      // ④ user_contact：群主 → 群（contact_type=1 群，status=1 好友）
      UserContact userContact = new UserContact();
      userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
      userContact.setContactType(UserContactTypeEnum.GROUP.getType());
      userContact.setContactId(groupInfo.getGroupId());
      userContact.setUserId(groupInfo.getGroupOwnerId());
      userContact.setCreateTime(curDate);
      userContact.setLastUpdateTime(curDate);
      userContactMapper.insert(userContact);

      // ⑤ chat_session：群会话（群ID 的 MD5 就是 sessionId）
      String sessionId = StringTools.getChatSessionId4Group(groupInfo.getGroupId());
      ChatSession chatSession = new ChatSession();
      chatSession.setSessionId(sessionId);
      chatSession.setLastMessage(MessageTypeEnum.GROUP_CREATE.getInitMessage());
      chatSession.setLastReceiveTime(curDate.getTime());
      chatSessionMapper.insert(chatSession);

      // ⑥ chat_session_user：群主的会话视角
      ChatSessionUser chatSessionUser = new ChatSessionUser();
      chatSessionUser.setUserId(groupInfo.getGroupOwnerId());
      chatSessionUser.setContactId(groupInfo.getGroupId());
      chatSessionUser.setContactName(groupInfo.getGroupName());
      chatSessionUser.setSessionId(sessionId);
      chatSessionUserMapper.insert(chatSessionUser);

      // ⑦ Redis 好友列表加群ID（发消息时的"是不是好友"校验查这个）
      redisComponet.addUserContact(groupInfo.getGroupOwnerId(), groupInfo.getGroupId());
      // ⑧ 群主在线的话，把连接登记进该群的 ChannelGroup（群消息推送用）
      channelContextUtils.addUser2Group(groupInfo.getGroupOwnerId(), groupInfo.getGroupId());

      // ⑨ chat_message：一条 GROUP_CREATE(3) 系统消息
      ChatMessage chatMessage = new ChatMessage();
      chatMessage.setSessionId(sessionId);
      chatMessage.setMessageType(MessageTypeEnum.GROUP_CREATE.getType());
      chatMessage.setMessageContent(MessageTypeEnum.GROUP_CREATE.getInitMessage());
      chatMessage.setSendUserId(null);
      chatMessage.setSendUserNickName(null);
      chatMessage.setSendTime(curDate.getTime());
      chatMessage.setContactId(groupInfo.getGroupId());
      chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
      chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());
      chatMessageMapper.insert(chatMessage);

      // ⑩ 推 WS：extendData 带会话视角，前端收到直接插一条会话
      chatSessionUser.setLastMessage(MessageTypeEnum.GROUP_CREATE.getInitMessage());
      chatSessionUser.setLastReceiveTime(curDate.getTime());
      chatSessionUser.setMemberCount(1);
      chatSessionUser.setContactType(UserContactTypeEnum.GROUP.getType());

      MessageSendDto<ChatSessionUser> messageSend = CopyTools.copyToMessageSendDto(chatMessage);
      messageSend.setExtendData(chatSessionUser);
      messageSend.setLastMessage(chatSessionUser.getLastMessage());
      messageHandler.sendMessage(messageSend);

    } else {
      // ==================== 改群 ====================
      GroupInfo dbInfo = groupInfoMapper.selectById(groupInfo.getGroupId());
      // 群不存在 / 不是群主 → 600
      if (dbInfo == null || !dbInfo.getGroupOwnerId().equals(groupInfo.getGroupOwnerId())) {
        throw new BusinessException(ResponseCodeEnum.CODE_600);
      }
      groupInfoMapper.updateById(groupInfo);

      // 群名变了：同步所有成员的会话名 + 通知在线成员刷新
      // （对应源项目 chatSessionUserService.updateRedundanceInfo）
      if (!dbInfo.getGroupName().equals(groupInfo.getGroupName())) {
        ChatSessionUser updateSessionUser = new ChatSessionUser();
        updateSessionUser.setContactName(groupInfo.getGroupName());
        chatSessionUserMapper.update(updateSessionUser,
          new LambdaQueryWrapper<ChatSessionUser>()
            .eq(ChatSessionUser::getContactId, groupInfo.getGroupId()));

        MessageSendDto<String> nameUpdate = new MessageSendDto<>();
        nameUpdate.setMessageType(MessageTypeEnum.CONTACT_NAME_UPDATE.getType()); // 10
        nameUpdate.setContactId(groupInfo.getGroupId());   // G 开头 → 群推送
        nameUpdate.setContactType(UserContactTypeEnum.GROUP.getType());
        nameUpdate.setExtendData(groupInfo.getGroupName()); // 新群名
        messageHandler.sendMessage(nameUpdate);
      }
    }

    // ==================== 头像落盘（建群/改群都要）====================
    if (avatarFile == null) {
      return;
    }
    File avatarFolder = new File(appConfig.getProjectFolder()
      + FileConstants.FILE_FOLDER_FILE + FileConstants.FILE_FOLDER_AVATAR_NAME);
    if (!avatarFolder.exists()) {
      boolean mkdirs = avatarFolder.mkdirs();
      if (!mkdirs) {
        log.error("创建文件夹失败：{}", avatarFolder.getAbsolutePath());
        throw new BusinessException("文件上传失败");
      }
    }
    String filePath = avatarFolder.getPath() + "/" + groupInfo.getGroupId() + FileConstants.IMAGE_SUFFIX;
    try {
      avatarFile.transferTo(new File(filePath));
      if (avatarCover != null) {
        avatarCover.transferTo(new File(filePath + FileConstants.COVER_IMAGE_SUFFIX));
      }
    } catch (Exception e) {
      log.error("群头像上传失败", e);
      throw new BusinessException("头像上传失败");
    }
  }

  @Override
  public List<GroupInfo> loadMyGroup(String userId) {
    return groupInfoMapper.selectList(
      new LambdaQueryWrapper<GroupInfo>()
        .eq(GroupInfo::getGroupOwnerId, userId)
        .orderByDesc(GroupInfo::getCreateTime));
  }

  @Override
  public GroupInfo getGroupInfo(String userId, String groupId) {
    GroupInfo groupInfo = checkGroupAndGet(userId, groupId);
    groupInfo.setMemberCount(countMember(groupId));
    return groupInfo;
  }

  @Override
  public GroupInfoVO getGroupInfo4Chat(String userId, String groupId) {
    GroupInfo groupInfo = checkGroupAndGet(userId, groupId);

    // 群成员：user_contact 里 contact_id=群 且 status=1，按入群时间正序
    List<UserContact> userContactList = userContactMapper.selectList(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getContactId, groupId)
        .eq(UserContact::getStatus, UserContactStatusEnum.FRIEND.getStatus())
        .orderByAsc(UserContact::getCreateTime));

    // 批量补成员昵称/性别（源项目是 inner join user_info，这里两次查询 + Map 回填）
    if (!userContactList.isEmpty()) {
      List<String> userIds = userContactList.stream().map(UserContact::getUserId).toList();
      List<UserInfo> userInfoList = userInfoMapper.selectByIds(userIds);
      Map<String, UserInfo> userInfoMap = userInfoList.stream()
        .collect(Collectors.toMap(UserInfo::getUserId, u -> u));
      for (UserContact uc : userContactList) {
        UserInfo u = userInfoMap.get(uc.getUserId());
        if (u != null) {
          uc.setContactName(u.getNickName());
          uc.setSex(u.getSex());
        }
      }
    }

    GroupInfoVO vo = new GroupInfoVO();
    vo.setGroupInfo(groupInfo);
    vo.setUserContactList(userContactList);
    return vo;
  }

  /**
   * 群详情公共校验（源项目 Controller 里的 getGroupDetailCommon）
   * 1. 我必须在群里且状态正常
   * 2. 群本身必须是"正常"状态
   */
  private GroupInfo checkGroupAndGet(String userId, String groupId) {
    UserContact userContact = userContactMapper.selectOne(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getUserId, userId)
        .eq(UserContact::getContactId, groupId));
    if (userContact == null || !UserContactStatusEnum.FRIEND.getStatus().equals(userContact.getStatus())) {
      throw new BusinessException("你不在群聊或者群聊不存在或已经解散");
    }
    GroupInfo groupInfo = groupInfoMapper.selectById(groupId);
    if (groupInfo == null || !GroupStatusEnum.NORMAL.getStatus().equals(groupInfo.getStatus())) {
      throw new BusinessException("群聊不存在或已经解散");
    }
    return groupInfo;
  }

  /** 群成员数（源项目口径：user_contact 里 contact_id=群 的记录总数，不限状态） */
  private Integer countMember(String groupId) {
    Long count = userContactMapper.selectCount(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getContactId, groupId));
    return count == null ? 0 : count.intValue();
  }



  /**
   * 退群 / 被踢
   * 和源项目的差异：源项目把 Redis 清理 + ChannelGroup 移除放在
   * ChannelContextUtils.sendMsg2Group 里按消息类型做，这里保持一致
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void leaveGroup(String userId, String groupId, MessageTypeEnum messageTypeEnum) {
    GroupInfo groupInfo = groupInfoMapper.selectById(groupId);
    if (groupInfo == null) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }
    // 群主不能退群，只能解散（源项目同样处理）
    if (userId.equals(groupInfo.getGroupOwnerId())) {
      throw new BusinessException("群主不能退出群聊，只能解散群聊");
    }

    // 物理删除我这一行；影响 0 行说明我本来就不在群里（重复点退群 / 被踢过）
    int count = userContactMapper.delete(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getUserId, userId)
        .eq(UserContact::getContactId, groupId));
    if (count == 0) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }

    UserInfo userInfo = userInfoMapper.selectById(userId);
    String nickName = userInfo == null ? "" : userInfo.getNickName();

    String sessionId = StringTools.getChatSessionId4Group(groupId);
    long curTime = System.currentTimeMillis();
    // 11:"%s退出了群聊"  12:"%s被管理员移出了群聊"
    String messageContent = String.format(messageTypeEnum.getInitMessage(), nickName);

    // ① 会话主表：最后一条消息换成系统提示
    ChatSession chatSession = new ChatSession();
    chatSession.setLastMessage(messageContent);
    chatSession.setLastReceiveTime(curTime);
    chatSessionMapper.update(chatSession,
      new LambdaQueryWrapper<ChatSession>()
        .eq(ChatSession::getSessionId, sessionId));

    // ② 消息表：落一条系统消息（sendUserId 为空 = 系统消息）
    ChatMessage chatMessage = new ChatMessage();
    chatMessage.setSessionId(sessionId);
    chatMessage.setSendTime(curTime);
    chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
    chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());
    chatMessage.setMessageType(messageTypeEnum.getType());
    chatMessage.setContactId(groupId);
    chatMessage.setMessageContent(messageContent);
    chatMessageMapper.insert(chatMessage);

    // ③ 剩余人数（status=1 才算在群）
    Long memberCount = userContactMapper.selectCount(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getContactId, groupId)
        .eq(UserContact::getStatus, UserContactStatusEnum.FRIEND.getStatus()));

    // ④ 广播；extendData 带退群人 userId —— 前端靠它判断"是不是我自己退的"
    MessageSendDto<String> messageSend = CopyTools.copyToMessageSendDto(chatMessage);
    messageSend.setExtendData(userId);
    messageSend.setMemberCount(memberCount == null ? 0 : memberCount.intValue());
    messageHandler.sendMessage(messageSend);
  }

  /** 解散群（仅群主）。软删：group_info → 0，全员 user_contact → 2 */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void dissolutionGroup(String userId, String groupId) {
    GroupInfo dbInfo = groupInfoMapper.selectById(groupId);
    // 源项目这里只判了 groupId 是否为 null，dbInfo 为 null 会 NPE；这里补上
    if (dbInfo == null || !dbInfo.getGroupOwnerId().equals(userId)) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }

    // ① 群 → 解散
    GroupInfo updateInfo = new GroupInfo();
    updateInfo.setStatus(GroupStatusEnum.DISSOLUTION.getStatus());
    groupInfoMapper.update(updateInfo,
      new LambdaQueryWrapper<GroupInfo>()
        .eq(GroupInfo::getGroupId, groupId));

    // ② 先查全员（下面清 Redis 要用），再把 status 统一改成 2
    List<UserContact> memberList = userContactMapper.selectList(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getContactId, groupId)
        .eq(UserContact::getContactType, UserContactTypeEnum.GROUP.getType()));

    UserContact updateContact = new UserContact();
    updateContact.setStatus(UserContactStatusEnum.DEL.getStatus());
    userContactMapper.update(updateContact,
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getContactId, groupId));

    // ③ 清每个人 Redis 缓存里的群ID，否则他们还能继续往这个群发消息
    for (UserContact uc : memberList) {
      redisComponet.removeUserContact(uc.getUserId(), groupId);
    }

    String sessionId = StringTools.getChatSessionId4Group(groupId);
    long curTime = System.currentTimeMillis();
    String messageContent = MessageTypeEnum.DISSOLUTION_GROUP.getInitMessage();

    // ④ 会话主表
    ChatSession chatSession = new ChatSession();
    chatSession.setLastMessage(messageContent);
    chatSession.setLastReceiveTime(curTime);
    chatSessionMapper.update(chatSession,
      new LambdaQueryWrapper<ChatSession>()
        .eq(ChatSession::getSessionId, sessionId));

    // ⑤ 消息表
    ChatMessage chatMessage = new ChatMessage();
    chatMessage.setSessionId(sessionId);
    chatMessage.setSendTime(curTime);
    chatMessage.setContactType(UserContactTypeEnum.GROUP.getType());
    chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());
    chatMessage.setMessageType(MessageTypeEnum.DISSOLUTION_GROUP.getType());
    chatMessage.setContactId(groupId);
    chatMessage.setMessageContent(messageContent);
    chatMessageMapper.insert(chatMessage);

    // ⑥ 广播；ChannelContextUtils 收到 8 号消息后会关掉整个 ChannelGroup
    MessageSendDto<Void> messageSend = CopyTools.copyToMessageSendDto(chatMessage);
    messageHandler.sendMessage(messageSend);
  }

  /** 群主拉人 / 踢人 */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void addOrRemoveGroupUser(TokenUserInfoDto tokenDto, String groupId,
                                   String selectContacts, Integer opType) {
    GroupInfo groupInfo = groupInfoMapper.selectById(groupId);
    if (groupInfo == null || !groupInfo.getGroupOwnerId().equals(tokenDto.getUserId())) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);   // 只有群主能操作
    }

    for (String contactId : selectContacts.split(",")) {
      if (Integer.valueOf(0).equals(opType)) {
        // 踢人：复用退群，消息换成 12
        leaveGroup(contactId, groupId, MessageTypeEnum.REMOVE_GROUP);
      } else {
        // 拉人：复用入群（群分支下一批补，先调用）
        AddContactDto addDto = new AddContactDto();
        addDto.setApplyUserId(contactId);
        addDto.setReceiveUserId(groupInfo.getGroupOwnerId());
        addDto.setContactId(groupId);
        addDto.setContactType(UserContactTypeEnum.GROUP.getType());
        userContactService.addContact(addDto);
      }
    }
  }

}
