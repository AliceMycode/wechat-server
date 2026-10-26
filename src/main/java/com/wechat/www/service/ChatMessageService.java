package com.wechat.www.service;

import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.ChatMessage;

/**
 * 聊天消息服务：发消息、存消息、推消息
 */
public interface ChatMessageService {

  /**
   * 保存一条聊天消息（核心方法）
   * @param chatMessage 前端传过来的消息（只有 contactId/messageContent/messageType 等）
   * @param tokenUserInfoDto 当前登录用户（发送人）
   * @return MessageSendDto 推给前端和对方的消息对象
   */
  MessageSendDto<Void> saveMessage(ChatMessage chatMessage, TokenUserInfoDto tokenUserInfoDto);
}
