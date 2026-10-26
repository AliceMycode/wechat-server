package com.wechat.www.dto;

import lombok.Data;

/**
 * 发送聊天消息请求参数
 * 命名注意：不要叫 SendMessageDto，和响应用的 MessageSendDto 太容易混淆
 */
@Data
public class SaveMessageDto {

  private String contactId;       // 对方ID（好友userId 或 群ID）
  private String messageContent;  // 消息内容
  private Integer messageType;    // 消息类型 2文字 5媒体（媒体文件相关字段后面再加）
}