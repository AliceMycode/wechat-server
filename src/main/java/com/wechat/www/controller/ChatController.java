package com.wechat.www.controller;

import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.SaveMessageDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.ChatMessage;
import com.wechat.www.service.ChatMessageService;
import com.wechat.www.vo.ResponseVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天接口：发消息（文件上传后面做）
 */
@RestController
@RequestMapping("/chat")
public class ChatController extends BaseController {

  @Resource
  private ChatMessageService chatMessageService;

  /**
   * 发送消息
   * 前端 POST form-urlencoded，header 带 token（BaseController 里已取好登录态）
   * 用 SaveMessageDto 接收表单字段：contactId 对方ID、messageContent 内容、messageType 类型（2文字 5媒体）
   */
  @PostMapping("/sendMessage")
  public ResponseVO<MessageSendDto<Void>> sendMessage(HttpServletRequest request, SaveMessageDto dto) {
    // 从请求头 token 解析出当前登录用户
    TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);

    // 组装消息对象（fileSize/fileName/fileType 现在不传，媒体消息功能后面做）
    ChatMessage chatMessage = new ChatMessage();
    chatMessage.setContactId(dto.getContactId());
    chatMessage.setMessageContent(dto.getMessageContent());
    chatMessage.setMessageType(dto.getMessageType());

    // 调核心业务，返回推送对象
    MessageSendDto<Void> messageSendDto = chatMessageService.saveMessage(chatMessage, tokenUserInfoDto);
    return getSuccessResponseVO(messageSendDto);
  }
}
