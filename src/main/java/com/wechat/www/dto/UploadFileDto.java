package com.wechat.www.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传媒体消息文件的参数
 * 字段名必须和前端 formData.append 的 key 完全一致（messageId / file / cover），
 * 因为 Spring MVC 是按属性名从 multipart 表单里取值的
 */
@Data
public class UploadFileDto {

  /** 第一步发消息时后端返回的消息ID */
  @NotNull(message = "消息ID不能为空")
  private Long messageId;

  /** 文件本体 */
  @NotNull(message = "文件不能为空")
  private MultipartFile file;

  /** 封面/缩略图：图片、视频才有（前端 ffmpeg 生成），可以为空 */
  private MultipartFile cover;
}
