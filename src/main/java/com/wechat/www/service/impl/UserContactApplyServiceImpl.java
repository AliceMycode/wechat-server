package com.wechat.www.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.www.constant.CommonConstants;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.UserContact;
import com.wechat.www.entity.UserContactApply;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.JoinTypeEnum;
import com.wechat.www.enums.MessageTypeEnum;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.enums.UserContactApplyStatusEnum;
import com.wechat.www.enums.UserContactStatusEnum;
import com.wechat.www.enums.UserContactTypeEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.mapper.UserContactApplyMapper;
import com.wechat.www.mapper.UserContactMapper;
import com.wechat.www.mapper.UserInfoMapper;
import com.wechat.www.netty.ChannelContextUtils;
import com.wechat.www.netty.MessageHandler;
import com.wechat.www.service.UserContactApplyService;
import com.wechat.www.utils.StringTools;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 好友申请业务实现（对照源项目 UserContactApplyServiceImpl.applyAdd）
 */
@Service
public class UserContactApplyServiceImpl implements UserContactApplyService {

  @Resource
  private UserContactMapper userContactMapper;

  @Resource
  private UserInfoMapper userInfoMapper;

  @Resource
  private UserContactApplyMapper userContactApplyMapper;

  @Resource
  private ChannelContextUtils channelContextUtils;

  @Resource
  private MessageHandler messageHandler;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Integer applyAdd(TokenUserInfoDto tokenDto, String contactId, String contactType, String applyInfo) {
    // ===== 第1步：contactType 字符串转枚举（前端传 "user"/"group"）=====
    UserContactTypeEnum typeEnum = UserContactTypeEnum.getByName(contactType);
    if (typeEnum == null) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }
    String applyUserId = tokenDto.getUserId();

    // ===== 第2步：附言为空 → 默认 "我是{我的昵称}" =====
    applyInfo = StringTools.isEmpty(applyInfo)
      ? String.format(CommonConstants.APPLY_INFO_TEMPLATE, tokenDto.getNickName())
      : applyInfo;

    long curDate = System.currentTimeMillis();
    Integer joinType = null;          // 对方的加入方式：0直接加入 1需要审核
    String receiveUserId = contactId; // 申请接收人（加群时要改成群主，现在不做群）

    // ===== 第3步：查"我→对方"的记录，判断对方是不是把我拉黑了 =====
    // 原理：对方拉黑我时，我这条记录的 status 会被改成 5(被拉黑)/6(首次被拉黑)
    UserContact myContact = userContactMapper.selectOne(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getUserId, applyUserId)
        .eq(UserContact::getContactId, contactId));
    if (myContact != null && (
      UserContactStatusEnum.BLACKLIST_BE.getStatus().equals(myContact.getStatus()) ||
        UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus().equals(myContact.getStatus()))) {
      throw new BusinessException("对方已经把你拉黑，无法添加");
    }

    // ===== 第4步：只做好友场景，查对方用户信息，拿他的 joinType =====
    UserInfo userInfo = userInfoMapper.selectById(contactId);
    if (userInfo == null) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);  // 对方不存在
    }
    joinType = userInfo.getJoinType();

    // ===== 第5步：joinType=0 直接加入 → 不需要申请，直接建好友关系 =====
    // TODO：下一个闭环（同意申请）实现 userContactService.addContact(...)
    // 现在测试号 join_type 都是 null/1(需要审核)，不会进这个分支
    if (JoinTypeEnum.JOIN.getType().equals(joinType)) {
      return joinType;
    }

    // ===== 第6步：写/更新申请记录（唯一索引兜底，重复申请只更新时间）=====
    UserContactApply dbApply = userContactApplyMapper.selectOne(
      new LambdaQueryWrapper<UserContactApply>()
        .eq(UserContactApply::getApplyUserId, applyUserId)
        .eq(UserContactApply::getReceiveUserId, receiveUserId)
        .eq(UserContactApply::getContactId, contactId));
    if (dbApply == null) {
      // 新申请：插入一条，状态=待处理
      UserContactApply apply = new UserContactApply();
      apply.setApplyUserId(applyUserId);
      apply.setReceiveUserId(receiveUserId);
      apply.setContactType(typeEnum.getType());
      apply.setContactId(contactId);
      apply.setLastApplyTime(curDate);
      apply.setStatus(UserContactApplyStatusEnum.INIT.getStatus());
      apply.setApplyInfo(applyInfo);
      userContactApplyMapper.insert(apply);
    } else {
      // 老申请（对方拒绝过/还没处理）：打回待处理，更新时间和附言
      dbApply.setStatus(UserContactApplyStatusEnum.INIT.getStatus());
      dbApply.setLastApplyTime(curDate);
      dbApply.setApplyInfo(applyInfo);
      userContactApplyMapper.updateById(dbApply);
    }

    // ===== 第7步：WS 推送通知对方 =====
    // 只有"新申请"或"上次已处理完"才推；已经是待处理就不重复打扰
    if (dbApply == null || !UserContactApplyStatusEnum.INIT.getStatus().equals(dbApply.getStatus())) {
      MessageSendDto<Object> notify = new MessageSendDto<>();
      notify.setMessageType(MessageTypeEnum.CONTACT_APPLY.getType());  // 4
      notify.setMessageContent(applyInfo);
      notify.setContactId(receiveUserId);
      // channelContextUtils.sendMsg(notify, receiveUserId);
      messageHandler.sendMessage(notify);
    }
    return joinType;
  }
}
