package com.wechat.www.controller;

import com.wechat.www.annotation.GlobalInterceptor;
import com.wechat.www.config.AppConfig;
import com.wechat.www.constant.FileConstants;
import com.wechat.www.dto.MessageSendDto;
import com.wechat.www.dto.SaveMessageDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.dto.UploadFileDto;
import com.wechat.www.entity.ChatMessage;
import com.wechat.www.enums.MessageTypeEnum;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.service.ChatMessageService;
import com.wechat.www.utils.StringTools;
import com.wechat.www.vo.ResponseVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 聊天接口：发消息 + 媒体文件上传/下载
 */
@Validated   // ← 开启方法级参数校验，@NotEmpty/@NotNull 才会生效
@RestController
@RequestMapping("/chat")
public class ChatController extends BaseController {

  @Resource
  private ChatMessageService chatMessageService;

  @Resource
  private AppConfig appConfig;

  /**
   * 发送消息
   * 前端 POST form-urlencoded，header 带 token（BaseController 里已取好登录态）
   * 用 SaveMessageDto 接收表单字段：contactId 对方ID、messageContent 内容、messageType 类型（2文字 5媒体）
   */
  @PostMapping("/sendMessage")
  @GlobalInterceptor
  public ResponseVO<MessageSendDto<Void>> sendMessage(HttpServletRequest request,
                                                      @Valid SaveMessageDto dto) {
    // 消息类型校验
    MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(dto.getMessageType());
    if (null == messageTypeEnum ||
      !ArrayUtils.contains(new Integer[]{MessageTypeEnum.CHAT.getType(), MessageTypeEnum.MEDIA_CHAT.getType()}, dto.getMessageType())) {
      throw new BusinessException(ResponseCodeEnum.CODE_600);
    }

    // 从请求头 token 解析出当前登录用户
    TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);

    // 组装消息对象
    ChatMessage chatMessage = new ChatMessage();
    chatMessage.setContactId(dto.getContactId());
    chatMessage.setMessageContent(dto.getMessageContent());
    chatMessage.setMessageType(dto.getMessageType());
    chatMessage.setFileSize(dto.getFileSize());
    chatMessage.setFileName(dto.getFileName());
    chatMessage.setFileType(dto.getFileType());

    // 调核心业务，返回推送对象
    MessageSendDto<Void> messageSendDto = chatMessageService.saveMessage(chatMessage, tokenUserInfoDto);
    return getSuccessResponseVO(messageSendDto);
  }

  /**
   * 上传媒体消息的文件（前端第二步调用，第一步 sendMessage 已经拿到 messageId）
   * 3 个业务参数 → 用 UploadFileDto 接（messageId / file / cover）
   */
  @PostMapping("/uploadFile")
  @GlobalInterceptor
  public ResponseVO<Void> uploadFile(HttpServletRequest request, @Valid UploadFileDto dto) {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);
    chatMessageService.saveMessageFile(
      tokenDto.getUserId(), dto);
    return getSuccessResponseVO(null);
  }

  /**
   * 下载/预览文件
   * fileId 是数字 → 聊天文件（messageId）
   * fileId 不是数字 → 头像（用户ID）
   * @param showCover true=取缩略图（_cover.png）
   */
  @PostMapping("/downloadFile")
  @GlobalInterceptor
  public void downloadFile(HttpServletRequest request, HttpServletResponse response,
                           @NotEmpty String fileId,
                           @NotNull Boolean showCover) throws Exception {
    TokenUserInfoDto tokenDto = getTokenUserInfo(request);

    File file;
    if (!StringTools.isNumber(fileId)) {
      // ---- 头像分支：/file/avatar/{userId}.png ----
      String avatarPath = appConfig.getProjectFolder() + FileConstants.FILE_FOLDER_FILE
        + FileConstants.FILE_FOLDER_AVATAR_NAME + fileId + FileConstants.IMAGE_SUFFIX;
      if (Boolean.TRUE.equals(showCover)) {
        avatarPath = avatarPath + FileConstants.COVER_IMAGE_SUFFIX;
      }
      file = new File(avatarPath);
      if (!file.exists()) {
        throw new BusinessException(ResponseCodeEnum.CODE_602);
      }
    } else {
      // ---- 聊天文件分支：交给 service 做权限校验和定位 ----
      file = chatMessageService.downloadFile(tokenDto, Long.parseLong(fileId), showCover);
    }

    // 以二进制流写回。前端靠响应头 content-type 是不是 application/json 判断"下载失败"，
    // 所以任何异常都必须走 GlobalExceptionHandler 返回 JSON
    response.setContentType("application/x-msdownload; charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment;");
    response.setContentLengthLong(file.length());
    try (InputStream in = new FileInputStream(file);
         OutputStream out = response.getOutputStream()) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = in.read(buffer)) != -1) {
        out.write(buffer, 0, len);
      }
      out.flush();
    }
  }
}
