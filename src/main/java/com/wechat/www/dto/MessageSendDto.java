package com.wechat.www.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wechat.www.utils.StringTools;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * WebSocket 推送消息的统一格式
 * 服务器→前端所有消息都包成这个 JSON 发过去
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageSendDto<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private Long messageId;           // 消息ID
  private String sessionId;         // 会话ID
  private String sendUserId;        // 发送人ID
  private String sendUserNickName;  // 发送人昵称
  private String contactId;         // 接收人/群ID
  private String contactName;       // 接收人名称
  private String messageContent;    // 消息内容
  private String lastMessage;       // 最后一条消息（会话列表展示用）
  private Integer messageType;      // 消息类型（MessageTypeEnum）
  private Long sendTime;            // 发送时间
  private Integer contactType;      // 联系人类型 0人/1群
  private T extendData;             // 扩展数据（INIT 时塞会话列表等）
  private Integer status;           // 消息状态
  private Long fileSize;
  private String fileName;
  private Integer fileType;
  private Integer memberCount;

  // lastMessage 没填时默认取 messageContent
  public String getLastMessage() {
    return StringTools.isEmpty(lastMessage) ? messageContent : lastMessage;
  }
}
