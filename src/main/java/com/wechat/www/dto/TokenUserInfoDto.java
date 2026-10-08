package com.wechat.www.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录态用户信息：登录成功后存进 Redis，也跟着 WebSocket 流转
 * （对应源项目 TokenUserInfoDto）
 * @JsonIgnoreProperties(ignoreUnknown = true)：反序列化遇到未知字段不报错
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenUserInfoDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private String token;     // 登录凭证
  private String userId;    // 用户ID
  private String nickName;  // 昵称
  private Boolean admin;    // 是否管理员（由 admin.emails 配置判断）
}
