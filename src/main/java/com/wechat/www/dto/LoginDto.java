package com.wechat.www.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数（同 RegisterDto 的绑定方式：POJO 自动接表单字段）
 */
@Data
public class LoginDto {

  @NotBlank(message = "邮箱不能为空")
  @Email(message = "邮箱格式不正确")
  private String email;

  @NotBlank(message = "密码不能为空")
  private String password;

  @NotBlank(message = "验证码不能为空")
  private String checkCodeKey;  // 验证码在 Redis 里的 key

  @NotBlank(message = "验证码答案不能为空")
  private String checkCode;     // 用户输入的验证码答案
}
