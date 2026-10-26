package com.wechat.www.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送聊天消息请求参数
 * 命名注意：不要叫 SendMessageDto，和响应用的 MessageSendDto 太容易混淆
 * 校验注解生效条件：Controller 方法参数上加 @Valid，否则注解是摆设
 */
@Data
public class SaveMessageDto {

  @NotBlank(message = "联系人不能为空")   // 字符串专用：null 或纯空格都算不通过
  private String contactId;       // 对方ID（好友userId 或 群ID）

  @NotBlank(message = "消息内容不能为空")
  private String messageContent;  // 消息内容

  @NotNull(message = "消息类型不能为空")  // 包装类型专用：只查 null
  private Integer messageType;    // 消息类型 2文字 5媒体（媒体文件相关字段后面再加）
}
