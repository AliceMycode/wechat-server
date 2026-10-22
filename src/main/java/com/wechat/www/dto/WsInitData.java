package com.wechat.www.dto;

import com.wechat.www.entity.ChatMessage;
import com.wechat.www.entity.ChatSessionUser;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * WebSocket 连接成功后，服务器一次性推给前端的初始化数据
 * 包含：会话列表、离线消息数、好友申请数
 */
@Data
public class WsInitData implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private List<ChatSessionUser> chatSessionList;  // 我的会话列表
  private List<ChatMessage> chatMessageList;      // 离线消息
  private Integer applyCount;                      // 待处理好友申请数
}
