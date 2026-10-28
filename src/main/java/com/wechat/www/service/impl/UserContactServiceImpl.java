package com.wechat.www.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wechat.www.dto.AddContactDto;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.SysSettingDto;
import com.wechat.www.dto.UserContactSearchResultDto;
import com.wechat.www.entity.*;
import com.wechat.www.enums.*;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.mapper.*;
import com.wechat.www.netty.MessageHandler;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.service.UserContactService;
import com.wechat.www.utils.CopyTools;
import com.wechat.www.utils.StringTools;
import com.wechat.www.vo.PaginationResultVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 联系人业务实现
 * （对应源项目 UserContactServiceImpl，先只实现 addContact4Robot）
 */
@Service("userContactService")
public class UserContactServiceImpl implements UserContactService {

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
  private UserInfoMapper userInfoMapper;

  @Resource
  private UserContactApplyMapper userContactApplyMapper;


  @Resource
  private MessageHandler messageHandler;



  /**
   * 给新用户加机器人好友 + 初始化会话和欢迎消息
   * 涉及 4 张表（user_contact / chat_session / chat_session_user / chat_message），
   * 必须在同一事务里，任何一步失败整体回滚
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void addContact4Robot(String userId) {
    Date curDate = new Date();

    // 1. 从 Redis 拿系统设置（机器人ID/昵称/欢迎语；Redis 没配就用 SysSettingDto 里的默认值）
    SysSettingDto sysSetting = redisComponet.getSysSetting();
    String robotUid = sysSetting.getRobotUid();            // 默认 Urobot
    String robotNickName = sysSetting.getRobotNickName();  // 默认 EasyChat
    // 欢迎语去掉 HTML 标签（防止后台配置里带富文本）
    String welcome = StringTools.cleanHtmlTag(sysSetting.getRobotWelcome());

    // 2. user_contact：给"我"加一条指向机器人的好友记录
    UserContact userContact = new UserContact();
    userContact.setUserId(userId);
    userContact.setContactId(robotUid);
    userContact.setContactType(UserContactTypeEnum.USER.getType());   // 0=好友
    userContact.setCreateTime(curDate);
    userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());  // 1=好友
    userContact.setLastUpdateTime(curDate);
    userContactMapper.insert(userContact);

    // 3. 生成单聊会话ID：两人ID排序拼接后 MD5
    //    （保证 A 找 B 和 B 找 A 算出同一个 sessionId，单聊共用一个会话）
    String sessionId = StringTools.getChatSessionId4User(new String[]{userId, robotUid});

    // 4. chat_session：会话主表，记录最后一条消息
    ChatSession chatSession = new ChatSession();
    chatSession.setSessionId(sessionId);
    chatSession.setLastMessage(welcome);
    chatSession.setLastReceiveTime(curDate.getTime());
    chatSessionMapper.insert(chatSession);

    // 5. chat_session_user："我"视角下这个会话指向机器人（联系人名=EasyChat）
    ChatSessionUser sessionUser = new ChatSessionUser();
    sessionUser.setUserId(userId);
    sessionUser.setContactId(robotUid);
    sessionUser.setContactName(robotNickName);
    sessionUser.setSessionId(sessionId);
    // insertOrUpdate：有就更新、没有就插入（MP 3.5.7+ BaseMapper 自带；
    // 注册场景下一定没有，这里用它做防御性写法）
    // chatSessionUserMapper.insertOrUpdate(sessionUser);

    // 这张表是联合主键(user_id+contact_id)，MP 的 insertOrUpdate 依赖单主键，不能用。
    // 改成：先按 user_id+contact_id 查一下，存在就 update，不存在就 insert。
    Long count = chatSessionUserMapper.selectCount(
      new LambdaQueryWrapper<ChatSessionUser>()
        .eq(ChatSessionUser::getUserId, userId)
        .eq(ChatSessionUser::getContactId, robotUid));
    if (count != null && count > 0) {
      chatSessionUserMapper.update(sessionUser,
        new LambdaQueryWrapper<ChatSessionUser>()
          .eq(ChatSessionUser::getUserId, userId)
          .eq(ChatSessionUser::getContactId, robotUid));
    } else {
      chatSessionUserMapper.insert(sessionUser);
    }

    // 6. chat_message：机器人给"我"发一条欢迎消息
    ChatMessage chatMessage = new ChatMessage();
    chatMessage.setSessionId(sessionId);
    chatMessage.setMessageType(MessageTypeEnum.CHAT.getType());        // 2=普通聊天
    chatMessage.setMessageContent(welcome);
    chatMessage.setSendUserId(robotUid);                               // 发送人=机器人
    chatMessage.setSendUserNickName(robotNickName);
    chatMessage.setSendTime(curDate.getTime());
    chatMessage.setContactId(userId);                                   // 接收人=新用户
    chatMessage.setContactType(UserContactTypeEnum.USER.getType());    // 0=单聊
    chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());       // 1=已发送
    chatMessageMapper.insert(chatMessage);

    // 同步 Redis 好友列表缓存：发消息时后端要校验"是不是好友"，
    // 校验就是从 Redis 这个列表里 contains 一下，不加的话机器人发不出消息
    redisComponet.addUserContact(userId, robotUid);

  }


  @Override
  public List<UserContact> loadContact(String userId, UserContactTypeEnum contactType) {
    // 1. 查 user_contact：当前用户 + 指定类型 + 状态是"有效关系"（好友/被删/被拉黑都算，前端要显示）
    List<UserContact> list = userContactMapper.selectList(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getUserId, userId)
        .eq(UserContact::getContactType, contactType.getType())
        .in(UserContact::getStatus,
          UserContactStatusEnum.FRIEND.getStatus(),       // 1 好友
          UserContactStatusEnum.DEL_BE.getStatus(),       // 3 被好友删除
          UserContactStatusEnum.BLACKLIST_BE.getStatus())// 5 被好友拉黑
        .orderByDesc(UserContact::getLastUpdateTime));    // 按最后互动时间倒序

    // 2. 好友类型：批量查 user_info，把昵称/性别塞进每条记录（群聊等建群功能再做）
    // 枚举常量在 JVM 里是单例，UserContactTypeEnum.USER   // 这个对象在整个 JVM 里只有一份
    // 枚举里每个值（`USER`、`GROUP`）都是 `public static final` 的唯一实例。不管从哪拿到的 `USER`，都是同一个对象引用
    if (contactType == UserContactTypeEnum.USER && !list.isEmpty()) {
      List<String> contactIds = list.stream().map(UserContact::getContactId).toList();
      // selectBatchIds：where user_id in (contactIds)
      List<UserInfo> userInfoList = userInfoMapper.selectByIds(contactIds);
      // 转成 Map<userId, UserInfo>，方便 O(1) 取
      Map<String, UserInfo> userInfoMap = userInfoList.stream()
        .collect(Collectors.toMap(UserInfo::getUserId, u -> u));
      for (UserContact c : list) {
        UserInfo u = userInfoMap.get(c.getContactId());
        if (u != null) {
          c.setContactName(u.getNickName());  // UserContact 里 contactName 是 @TableField(exist=false)，只用于返回前端
          c.setSex(u.getSex());
        }
      }
    }
    return list;
  }

  @Override
  public UserContactSearchResultDto searchContact(String myUserId, String contactId) {
    // ===== 第1步：从 ID 前缀判断搜的是"用户"还是"群"（U=用户，G=群）=====
    UserContactTypeEnum typeEnum = UserContactTypeEnum.getByPrefix(contactId);
    if (typeEnum == null) {
      return null;  // 前缀不对，不是合法ID，前端自己处理"查无此人"
    }
    UserContactSearchResultDto resultDto;
    // ===== 第2步：按类型查库 =====
    if (UserContactTypeEnum.USER == typeEnum) {
      UserInfo userInfo = userInfoMapper.selectById(contactId);
      if (userInfo == null) {
        return null;  // 用户不存在
      }
      // 同名属性自动拷贝（userId/nickName/sex/areaName/personalSignature）
      resultDto = CopyTools.copy(userInfo, UserContactSearchResultDto.class);
    } else {
      // TODO 群搜索：群模块还没做，直接返回 null
      return null;
    }
    // ===== 第3步：补上类型和联系人ID =====
    resultDto.setContactType(typeEnum.name());  // "USER"
    resultDto.setContactId(contactId);
    // ===== 第4步：查"我和TA的关系"，填 status =====
    // 搜的是我自己 → 直接当好友显示
    if (myUserId.equals(contactId)) {
      resultDto.setStatus(UserContactStatusEnum.FRIEND.getStatus());
      return resultDto;
    }
    // 查 user_contact：我→TA 的记录
    UserContact userContact = userContactMapper.selectOne(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getUserId, myUserId)
        .eq(UserContact::getContactId, contactId));
    // 没记录 = 陌生人（status 给 null，和源项目保持一致）
    resultDto.setStatus(userContact == null ? null : userContact.getStatus());
    return resultDto;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void addContact(AddContactDto dto) {
    // 入参解包（好友分支用不到 receiveUserId，群分支阶段4才用）
    String applyUserId = dto.getApplyUserId();
    String contactId = dto.getContactId();
    Integer contactType = dto.getContactType();
    String applyInfo = dto.getApplyInfo();

    // ===== 群分支：阶段3/4建群时再实现 =====
    if (UserContactTypeEnum.GROUP.getType().equals(contactType)) {
      // TODO 群聊：人数上限校验 + 群会话 + ADD_GROUP 系统消息（阶段4补）
      throw new BusinessException(ResponseCodeEnum.CODE_600);  // 防止现在被误调用
    }

    Date curDate = new Date();

    // ===== ① 双向写好友关系（user_contact：A→B、B→A 各一条）=====
    UserContact contact = new UserContact();
    contact.setUserId(applyUserId);          // A 添加 B
    contact.setContactId(contactId);
    contact.setContactType(contactType);
    contact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
    contact.setCreateTime(curDate);
    contact.setLastUpdateTime(curDate);
    saveOrUpdateContact(contact);
    // 反向再来一条：B 添加 A（复用对象，只换方向两个字段）
    contact.setUserId(contactId);
    contact.setContactId(applyUserId);
    saveOrUpdateContact(contact);

    // ===== ② 同步 Redis 好友缓存（发消息时"是不是好友"的校验查这个）=====
    redisComponet.addUserContact(applyUserId, contactId);
    redisComponet.addUserContact(contactId, applyUserId);

    // ===== ③ 会话ID：两人ID排序拼接后MD5，保证双向是同一个会话 =====
    String sessionId = StringTools.getChatSessionId4User(new String[]{applyUserId, contactId});

    // ===== ④ chat_session：会话主表，最后一条消息先存申请附言 =====
    // 同意好友后双方会话列表直接出现对方，最后一条消息=附言（和源项目一致）
    ChatSession chatSession = new ChatSession();
    chatSession.setSessionId(sessionId);
    chatSession.setLastMessage(applyInfo);
    chatSession.setLastReceiveTime(curDate.getTime());
    // chat_session 单主键(@TableId)，可以用 MP 自带的 insertOrUpdate
    chatSessionMapper.insertOrUpdate(chatSession);

    // ===== ⑤ chat_session_user：双向各写一条"我的会话视角" =====
    UserInfo contactUser = userInfoMapper.selectById(contactId);  // 对方（审批人B）
    UserInfo applyUser = userInfoMapper.selectById(applyUserId);  // 申请人A
    ChatSessionUser sessionUser = new ChatSessionUser();
    sessionUser.setUserId(applyUserId);                 // A 视角：联系人=B，显示 B 的昵称
    sessionUser.setContactId(contactId);
    sessionUser.setContactName(contactUser.getNickName());
    sessionUser.setSessionId(sessionId);
    saveOrUpdateSessionUser(sessionUser);
    // 反向：B 视角：联系人=A，显示 A 的昵称（复用对象，换方向字段和显示名）
    sessionUser.setUserId(contactId);
    sessionUser.setContactId(applyUserId);
    sessionUser.setContactName(applyUser.getNickName());
    saveOrUpdateSessionUser(sessionUser);

    // ===== ⑥ 打招呼消息入库：type=1，内容=申请附言，发送人=A =====
    ChatMessage chatMessage = new ChatMessage();
    chatMessage.setSessionId(sessionId);
    chatMessage.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());  // 1=加好友打招呼
    chatMessage.setMessageContent(applyInfo);
    chatMessage.setSendUserId(applyUserId);
    chatMessage.setSendUserNickName(applyUser.getNickName());
    chatMessage.setSendTime(curDate.getTime());
    chatMessage.setContactId(contactId);
    chatMessage.setContactType(UserContactTypeEnum.USER.getType());
    chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());  // 直接已发送
    chatMessageMapper.insert(chatMessage);

    // ===== ⑦ WS 推送两次 =====
    // 对照源项目：从刚入库的 chatMessage 拷贝出推送对象，sessionId/contactType/status(=1) 全带上。
    // status 必须是 1！前端收到推送会拿消息字段直接建本地会话行，本地会话列表查询条件是 status=1，
    // 如果这里 status 是 null，前端建出来的会话行就是"隐形"的（联系人在、会话列表里看不到）
    MessageSendDto<Object> messageSend = CopyTools.copyToMessageSendDto(chatMessage);
    messageSend.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
    messageSend.setMessageContent(applyInfo);
    messageSend.setSendUserId(applyUserId);   // sendUserId 有值 → sendMsg 常规分支覆写 contactId=A
    messageSend.setSendUserNickName(applyUser.getNickName());
    messageSend.setContactId(contactId);      // 路由目标=B
    messageSend.setSendTime(curDate.getTime());
    messageHandler.sendMessage(messageSend);  // 第1推：给接收方 B，正常覆写，B 看到 contactId=A ✅

    // 第2推：给申请人 A 自己。sendUserId 也是 A，常规覆写会让 A 看到"聊天对象是自己"→错乱，
    // 所以打 ADD_FRIEND_SELF(13) 标记 + 塞 B 的 UserInfo，sendMsg 特殊分支负责转回来
    messageSend.setMessageType(MessageTypeEnum.ADD_FRIEND_SELF.getType());
    messageSend.setContactId(applyUserId);    // 路由目标=A
    messageSend.setExtendData(contactUser);   // B 的 UserInfo（userId/nickName）
    messageHandler.sendMessage(messageSend);
  }

  /**
   * user_contact 先查后写：有旧记录（含删除/拉黑状态）就刷回好友，没有就新插
   * （对应源项目 insertOrUpdateBatch；联合主键没有现成 upsert，只能自己写）
   * @param contact 目标关系：userId+contactId 定位，其余字段作为写入值
   */
  private void saveOrUpdateContact(UserContact contact) {
    UserContact dbContact = userContactMapper.selectOne(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getUserId, contact.getUserId())
        .eq(UserContact::getContactId, contact.getContactId()));
    if (dbContact != null) {
      // 旧记录（重新加回已删除/被拉黑的好友）：状态刷回好友
      // 注意不能用 updateById：这张表联合主键、实体没有 @TableId，运行时会挂
      UserContact update = new UserContact();
      update.setStatus(UserContactStatusEnum.FRIEND.getStatus());
      update.setLastUpdateTime(contact.getLastUpdateTime());
      userContactMapper.update(update,
        new LambdaQueryWrapper<UserContact>()
          .eq(UserContact::getUserId, contact.getUserId())
          .eq(UserContact::getContactId, contact.getContactId()));
    } else {
      // 全新好友关系：插入
      userContactMapper.insert(contact);
    }
  }

  /**
   * chat_session_user 先查后写：会话视角有旧记录就更新联系人名/会话ID，没有就新插
   * @param sessionUser 目标视角：userId+contactId 定位，contactName/sessionId 作为写入值
   */
  private void saveOrUpdateSessionUser(ChatSessionUser sessionUser) {
    Long count = chatSessionUserMapper.selectCount(
      new LambdaQueryWrapper<ChatSessionUser>()
        .eq(ChatSessionUser::getUserId, sessionUser.getUserId())
        .eq(ChatSessionUser::getContactId, sessionUser.getContactId()));
    if (count != null && count > 0) {
      chatSessionUserMapper.update(sessionUser,
        new LambdaQueryWrapper<ChatSessionUser>()
          .eq(ChatSessionUser::getUserId, sessionUser.getUserId())
          .eq(ChatSessionUser::getContactId, sessionUser.getContactId()));
    } else {
      chatSessionUserMapper.insert(sessionUser);
    }
  }
}
