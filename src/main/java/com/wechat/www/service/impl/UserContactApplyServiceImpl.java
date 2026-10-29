package com.wechat.www.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wechat.www.constant.CommonConstants;
import com.wechat.www.dto.AddContactDto;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.UserContact;
import com.wechat.www.entity.UserContactApply;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.*;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.mapper.UserContactApplyMapper;
import com.wechat.www.mapper.UserContactMapper;
import com.wechat.www.mapper.UserInfoMapper;
import com.wechat.www.netty.ChannelContextUtils;
import com.wechat.www.netty.MessageHandler;
import com.wechat.www.service.UserContactApplyService;
import com.wechat.www.service.UserContactService;
import com.wechat.www.utils.StringTools;
import com.wechat.www.vo.PaginationResultVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

  @Resource
  private UserContactService userContactService;


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

    // ===== 第5步：joinType=0(直接加入) → 不写申请记录，直接建好友关系 =====
    // 对照源项目 applyAdd：调 addContact 后直接返回，对方申请列表里不会出现这条申请
    if (JoinTypeEnum.JOIN.getType().equals(joinType)) {
      AddContactDto addDto = new AddContactDto();
      addDto.setApplyUserId(applyUserId);
      addDto.setReceiveUserId(receiveUserId);
      addDto.setContactId(contactId);
      addDto.setContactType(typeEnum.getType());
      addDto.setApplyInfo(applyInfo);
      userContactService.addContact(addDto);
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


  /** 申请列表每页条数（源项目 PageSize.SIZE15） */
  private static final int APPLY_PAGE_SIZE = 15;

  @Override
  public PaginationResultVO<UserContactApply> loadApply(String receiveUserId, Integer pageNo) {
    // 1. 页码兜底：前端第一页不传 pageNo → 默认 1（源项目 Integer 允许 null，这里补齐）
    int curPageNo = pageNo == null ? 1 : pageNo;

    // 2. MP 分页查询：只查"发给我的"申请，按最后申请时间倒序
    //    selectPage 会自动执行 count + limit 两条 SQL，count 存在 Page 对象里
    Page<UserContactApply> page = new Page<>(curPageNo, APPLY_PAGE_SIZE);
    Page<UserContactApply> result = userContactApplyMapper.selectPage(page,
      new LambdaQueryWrapper<UserContactApply>()
        .eq(UserContactApply::getReceiveUserId, receiveUserId)
        .orderByDesc(UserContactApply::getLastApplyTime));

    // 3. 批量补申请人昵称（对照源项目 LEFT JOIN user_info 的 contactName）
    //    前端申请列表显示的是"申请人"的昵称，所以按 apply_user_id 查
    List<UserContactApply> list = result.getRecords();
    if (!list.isEmpty()) {
      List<String> applyUserIds = list.stream()
        .map(UserContactApply::getApplyUserId).distinct().toList();
      List<UserInfo> applyUsers = userInfoMapper.selectByIds(applyUserIds);
      Map<String, UserInfo> userMap = applyUsers.stream()
        .collect(Collectors.toMap(UserInfo::getUserId, u -> u));
      for (UserContactApply apply : list) {
        UserInfo u = userMap.get(apply.getApplyUserId());
        if (u != null) {
          // contactName 是 @TableField(exist=false) 的展示字段，不进 SQL
          apply.setContactName(u.getNickName());
        }
      }
    }
    // statusName 不用管：实体的派生 getter 会按 status 现算"待处理/已同意/..."

    // 4. 组装分页返回（构造器自动算总页数）
    return new PaginationResultVO<>(curPageNo, APPLY_PAGE_SIZE, result.getTotal(), list);
  }


  @Override
  @Transactional(rollbackFor = Exception.class)
  public void dealWithApply(String userId, Integer applyId, Integer status) {
    // ===== ① 结果状态校验：必须是 1/2/3，且不能是 0（不能把申请"处理"回待处理）=====
    UserContactApplyStatusEnum statusEnum = UserContactApplyStatusEnum.getByStatus(status);
    if (statusEnum == null || statusEnum == UserContactApplyStatusEnum.INIT) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }

    // ===== ② 权限校验：申请不存在 or 不是发给我的 → 600 =====
    // applyId 是这张表的单主键，selectById 直接用
    UserContactApply applyInfo = userContactApplyMapper.selectById(applyId);
    if (applyInfo == null || !userId.equals(applyInfo.getReceiveUserId())) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }

    // ===== ③ 条件更新：只有"待处理"能被处理 =====
    // WHERE apply_id=? AND status=0 —— 用 SQL 影响行数当并发锁：
    // 双击/两个端同时点，只有第一个成功，第二个匹配不到 status=0 的行 → 600
    UserContactApply updateInfo = new UserContactApply();
    updateInfo.setStatus(statusEnum.getStatus());
    updateInfo.setLastApplyTime(System.currentTimeMillis());
    int count = userContactApplyMapper.update(updateInfo,
      new LambdaQueryWrapper<UserContactApply>()
        .eq(UserContactApply::getApplyId, applyId)
        .eq(UserContactApply::getStatus, UserContactApplyStatusEnum.INIT.getStatus()));
    if (count == 0) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);  // 已处理过（重复请求）
    }

    // ===== ④ 同意 → 复用 1-a 的 addContact 建好友关系 =====
    if (UserContactApplyStatusEnum.PASS.getStatus().equals(status)) {
      AddContactDto addDto = new AddContactDto();
      addDto.setApplyUserId(applyInfo.getApplyUserId());
      addDto.setReceiveUserId(applyInfo.getReceiveUserId()); // 好友场景 receiveUserId==contactId
      addDto.setContactId(applyInfo.getContactId());
      addDto.setContactType(applyInfo.getContactType());
      addDto.setApplyInfo(applyInfo.getApplyInfo());          // 会话列表"最后一条消息"用申请附言
      userContactService.addContact(addDto);
      return;
    }

    // ===== ⑤ 拉黑：给申请人写一条"被拉黑"的关系记录 =====
    // 方向：userId=申请人、contactId=接收人。申请人视角状态=6(首次被拉黑)，
    // 他之后再点"添加好友"会被 applyAdd 第3步的拉黑校验拦下（6 也在拦截范围）
    if (statusEnum == UserContactApplyStatusEnum.BLACKLIST) {
      Date curDate = new Date();
      UserContact userContact = new UserContact();
      userContact.setUserId(applyInfo.getApplyUserId());
      userContact.setContactId(applyInfo.getContactId());
      userContact.setContactType(applyInfo.getContactType());
      userContact.setCreateTime(curDate);
      userContact.setStatus(UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus()); // 6
      userContact.setLastUpdateTime(curDate);
      // 源项目 insertOrUpdate（联合主键 on duplicate key）；这张表没有 @TableId，
      // MP 用不了现成 upsert，且不能复用 saveOrUpdateContact（它会把状态刷回好友1）→ 先查后写
      UserContact dbContact = userContactMapper.selectOne(
        new LambdaQueryWrapper<UserContact>()
          .eq(UserContact::getUserId, userContact.getUserId())
          .eq(UserContact::getContactId, userContact.getContactId()));
      if (dbContact != null) {
        userContactMapper.update(userContact,
          new LambdaQueryWrapper<UserContact>()
            .eq(UserContact::getUserId, userContact.getUserId())
            .eq(UserContact::getContactId, userContact.getContactId()));
      } else {
        userContactMapper.insert(userContact);
      }
    }
    // ===== ⑥ 拒绝(REJECT)：什么都不做——状态已在③改成"已拒绝"，申请列表里能看到 =====
  }

}
