package com.wechat.www.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wechat.www.dto.SysSettingDto;
import com.wechat.www.dto.UserContactSearchResultDto;
import com.wechat.www.entity.*;
import com.wechat.www.enums.*;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.mapper.*;
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

}
