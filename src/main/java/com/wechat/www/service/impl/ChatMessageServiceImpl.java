package com.wechat.www.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.www.constant.CommonConstants;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.SysSettingDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.ChatMessage;
import com.wechat.www.entity.ChatSession;
import com.wechat.www.enums.*;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.mapper.ChatMessageMapper;
import com.wechat.www.mapper.ChatSessionMapper;
import com.wechat.www.netty.ChannelContextUtils;
import com.wechat.www.netty.MessageHandler;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.service.ChatMessageService;
import com.wechat.www.utils.CopyTools;
import com.wechat.www.utils.StringTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

  @Resource
  private ChatMessageMapper chatMessageMapper;

  @Resource
  private ChatSessionMapper chatSessionMapper;

  @Resource
  private RedisComponet redisComponet;

  @Resource
  private ChannelContextUtils channelContextUtils;

  @Resource
  private MessageHandler messageHandler;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public MessageSendDto<Void> saveMessage(ChatMessage chatMessage, TokenUserInfoDto tokenUserInfoDto) {
    log.info("保存消息+++：{}", chatMessage);
    // ===== ① 校验消息类型 =====
    // 前端能发的只有普通聊天(2)和媒体消息(5)，其它类型（加好友/建群等系统消息）不是这里发
    MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(chatMessage.getMessageType());
    if (!(messageTypeEnum == MessageTypeEnum.CHAT || messageTypeEnum == MessageTypeEnum.MEDIA_CHAT)) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }

    // ===== ② 好友校验（机器人回复自己时跳过）=====
    String sendUserId = tokenUserInfoDto.getUserId();
    String contactId = chatMessage.getContactId();
    if (!CommonConstants.ROBOT_UID.equals(sendUserId)) {
      List<String> contactList = redisComponet.getUserContactList(sendUserId);
      System.out.println("=== 好友校验: sendUserId=" + sendUserId + ", contactId=[" + contactId + "], list=" + contactList);  // ← 加这行
      if (!contactList.contains(contactId)) {
        // 根据对方ID前缀判断是"好友"还是"群"，报不同错误码
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(contactId);
        if (contactTypeEnum == UserContactTypeEnum.USER) {
          throw new BusinessException(ResponseCodeEnum.CODE_902);
        } else {
          throw new BusinessException(ResponseCodeEnum.CODE_903);
        }
      }
    }

    // ===== ③ 算 sessionId =====
    UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(contactId);
    String sessionId;
    if (contactTypeEnum == UserContactTypeEnum.USER) {
      // 单聊：两人ID排序后MD5，保证双向是同一个sessionId
      sessionId = StringTools.getChatSessionId4User(new String[]{sendUserId, contactId});
    } else {
      // 群聊：群ID的MD5
      sessionId = StringTools.getChatSessionId4Group(contactId);
    }

    // ===== ④ 更新会话表（不存在则插入）=====
    Long curTime = System.currentTimeMillis();
    String messageContent = StringTools.cleanHtmlTag(chatMessage.getMessageContent()); // 防XSS

    Long sessionCount = chatSessionMapper.selectCount(
      new LambdaQueryWrapper<ChatSession>()
        .eq(ChatSession::getSessionId, sessionId));
    ChatSession chatSession = new ChatSession();
    chatSession.setLastMessage(messageContent);
    chatSession.setLastReceiveTime(curTime);
    if (sessionCount > 0) {
      // 会话存在：更新最后一条消息和时间
      chatSessionMapper.update(chatSession,
        new LambdaQueryWrapper<ChatSession>()
          .eq(ChatSession::getSessionId, sessionId));
    } else {
      // 会话不存在（异常情况兜底）：新建
      chatSession.setSessionId(sessionId);
      chatSessionMapper.insert(chatSession);
    }

    // ===== ⑤ 插入消息表 =====
    chatMessage.setSessionId(sessionId);
    chatMessage.setSendUserId(sendUserId);
    chatMessage.setSendUserNickName(tokenUserInfoDto.getNickName());
    chatMessage.setSendTime(curTime);
    assert contactTypeEnum != null;
    chatMessage.setContactType(contactTypeEnum.getType());
    chatMessage.setMessageContent(messageContent);
    // 文字消息直接标记"已发送"；媒体消息先标记"发送中"，等文件传完再改成已发送
    chatMessage.setStatus(messageTypeEnum == MessageTypeEnum.MEDIA_CHAT
      ? MessageStatusEnum.SENDING.getStatus() : MessageStatusEnum.SENDED.getStatus());
    chatMessageMapper.insert(chatMessage);

    // ===== ⑥ 组装推送对象并推给接收方 =====
    MessageSendDto<Void> messageSend = CopyTools.copyToMessageSendDto(chatMessage);

    // 收件人是机器人 → 机器人自动回复（递归调自己，用机器人身份走一遍流程）
    if (CommonConstants.ROBOT_UID.equals(contactId)) {
      SysSettingDto sysSettingDto = redisComponet.getSysSetting();
      TokenUserInfoDto robot = new TokenUserInfoDto();
      robot.setUserId(sysSettingDto.getRobotUid());
      robot.setNickName(sysSettingDto.getRobotNickName());
      ChatMessage robotChatMessage = new ChatMessage();
      robotChatMessage.setContactId(sendUserId);  // 回复给刚才发消息的人
      robotChatMessage.setMessageContent("我只是一个机器人无法识别你的消息");
      robotChatMessage.setMessageType(MessageTypeEnum.CHAT.getType());
      saveMessage(robotChatMessage, robot);  // 递归：会走②③④⑤⑥，机器人身份跳过好友校验
    } else {
      // 普通用户：推给对方
      // channelContextUtils.sendMsg(messageSend, contactId);
      messageHandler.sendMessage(messageSend);
    }

    return messageSend;  // 返回给发送方，前端把自己发的消息渲染到界面
  }
}
