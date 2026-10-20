package com.wechat.www.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会话信息（对应表 chat_session）
 * 一个单聊/群聊对应一个会话，记录最后一条消息，用于会话列表排序和展示
 */
@Data
@TableName("chat_session")
public class ChatSession implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * 会话ID（主键，后端生成，非自增）
   * 单聊：两人ID排序拼接后 MD5；群聊：群ID 的 MD5
   */
  @TableId(type = IdType.INPUT)
  private String sessionId;

  /** 最后一条消息内容 */
  private String lastMessage;

  /** 最后一条消息时间（毫秒） */
  private Long lastReceiveTime;
}
