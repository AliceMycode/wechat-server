package com.wechat.www.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户-会话关系（对应表 chat_session_user）
 * 联合主键：user_id + contact_id
 * 记录"我"视角下每个会话的联系人名称，是"我"的会话列表来源
 */
@Data
@TableName("chat_session_user")
public class ChatSessionUser implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 用户ID（联合主键之一） */
  private String userId;

  /** 联系人ID（联合主键之一） */
  private String contactId;

  /** 会话ID（关联 chat_session） */
  private String sessionId;

  /** 联系人名称（对方昵称/群名，展示用） */
  private String contactName;

  /**
   * 以下字段 chat_session_user 表里没有（该表只有上面 4 列），
   * 是联查/WS 推送时临时填的，用 @TableField(exist = false) 排除，不参与 SQL
   */
  @TableField(exist = false)
  private String lastMessage;       // 最后消息（联查 chat_session 填）

  @TableField(exist = false)
  private Long lastReceiveTime;     // 最后消息时间（联查填）

  @TableField(exist = false)
  private Integer contactType;      // 联系人类型（0人/1群，按 contactId 前缀判断）

  @TableField(exist = false)
  private Integer memberCount;      // 群成员数（群聊才用）
}
