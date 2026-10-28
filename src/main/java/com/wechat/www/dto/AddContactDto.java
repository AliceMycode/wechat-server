package com.wechat.www.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 建立联系人关系的入参（对应源项目 addContact 的 5 个散参）
 * 好友/入群共用：单聊时 contactId=对方用户ID；群聊时 contactId=群ID
 */
@Data
public class AddContactDto implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 申请人ID */
  private String applyUserId;

  /** 审批人ID（单聊=对方；群聊=群主，群分支阶段4才用到） */
  private String receiveUserId;

  /** 联系人ID（单聊=对方用户ID；群聊=群ID） */
  private String contactId;

  /** 联系人类型 0=好友 1=群组 */
  private Integer contactType;

  /** 申请附言（同意后作为双方会话的第一条"打招呼消息"内容） */
  private String applyInfo;
}
