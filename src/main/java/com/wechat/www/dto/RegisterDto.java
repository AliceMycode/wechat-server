package com.wechat.www.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册请求参数
 * Controller 方法参数是 POJO 时不加注解，Spring 会自动按"字段名=表单字段名"绑定，
 * 前端传 FormData / form-urlencoded / query 参数都能接住
 */
@Data
public class RegisterDto {

  @NotBlank(message = "邮箱不能为空")
  private String email;

  @NotBlank(message = "昵称不能为空")
  private String nickName;

  @NotBlank(message = "密码不能为空")
  private String password;

  @NotBlank(message = "验证码不能为空")
  private String checkCodeKey;  // 验证码在 Redis 里的 key（checkCode 接口返回的）

  @NotBlank(message = "验证码答案不能为空")
  private String checkCode;     // 用户输入的验证码答案
}