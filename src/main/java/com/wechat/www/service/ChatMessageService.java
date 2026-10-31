package com.wechat.www.service;

import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.dto.UploadFileDto;
import com.wechat.www.entity.ChatMessage;


import java.io.File;

/**
 * 聊天消息服务：发消息、存消息、推消息、文件存取
 */
public interface ChatMessageService {

  /**
   * 保存一条聊天消息（核心方法）
   * @param chatMessage 前端传过来的消息（只有 contactId/messageContent/messageType 等）
   * @param tokenUserInfoDto 当前登录用户（发送人）
   * @return MessageSendDto 推给前端和对方的消息对象
   */
  MessageSendDto<Void> saveMessage(ChatMessage chatMessage, TokenUserInfoDto tokenUserInfoDto);

  /**
   * 保存媒体消息的文件（图片/视频/普通文件），并推"上传完成"通知
   * @param userId    当前登录用户（必须是这条消息的发送人）
   */
  void saveMessageFile(String userId, UploadFileDto dto);

  /**
   * 取媒体消息对应的本地文件（下载/预览用）
   * @param userInfoDto 当前登录用户（做权限判断）
   * @param messageId   消息ID
   * @param showCover   true=取 _cover.png 缩略图
   */
  File downloadFile(TokenUserInfoDto userInfoDto, Long messageId, Boolean showCover);
}
