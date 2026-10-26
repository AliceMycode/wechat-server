package com.wechat.www.dto;

import lombok.Data;

/**
 * 登录请求参数（同 RegisterDto 的绑定方式：POJO 自动接表单字段）
 */
@Data
public class LoginDto {

  private String email;
  private String password;
  private String checkCodeKey;  // 验证码在 Redis 里的 key
  private String checkCode;     // 用户输入的验证码答案
}