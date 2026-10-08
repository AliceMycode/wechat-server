package com.wechat.www.enums;

import lombok.Getter;

/**
 * 消息发送状态（对应 chat_message.status）
 */
@Getter
public enum MessageStatusEnum {
  SENDING(0, "发送中"),
  SENDED(1, "已发送");

  private final Integer status;
  private final String desc;

  MessageStatusEnum(Integer status, String desc) {
    this.status = status;
    this.desc = desc;
  }

  public static MessageStatusEnum getByStatus(Integer status) {
    for (MessageStatusEnum item : values()) {
      if (item.status.equals(status)) {
        return item;
      }
    }
    return null;
  }
}
