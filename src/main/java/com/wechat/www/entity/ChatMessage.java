package com.wechat.www.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 聊天消息（对应表 chat_message）
 * 每条消息落一行：文字、图片、视频、文件、系统提示（加好友/入群等）都在这里
 */
@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 消息自增ID（数据库自增主键） */
  @TableId(type = IdType.AUTO)
  private Long messageId;

  /** 会话ID（关联 chat_session，不建物理外键） */
  private String sessionId;

  /** 消息类型（对应 MessageTypeEnum：CHAT=2、MEDIA_CHAT=5、ADD_FRIEND=1...） */
  private Integer messageType;

  /** 消息内容（文字内容，或文件的存储路径） */
  private String messageContent;

  /** 发送人ID（系统消息可能为 null，如"xxx加入了群聊"） */
  private String sendUserId;

  /** 发送人昵称（冗余字段，避免展示时再联查） */
  private String sendUserNickName;

  /** 发送时间（毫秒时间戳） */
  private Long sendTime;

  /** 接收联系人ID（单聊=对方ID，群聊=群ID） */
  private String contactId;

  /** 联系人类型 0:单聊 1:群聊 */
  private Integer contactType;

  /** 文件大小（字节），非文件消息为 null */
  private Long fileSize;

  /** 文件名（原始文件名） */
  private String fileName;

  /** 文件类型（对应 MediaFileTypeEnum：0图片 1视频 2文件） */
  private Integer fileType;

  /** 状态 0:正在发送 1:已发送（对应 MessageStatusEnum） */
  private Integer status;
}
