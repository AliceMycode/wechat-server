package com.wechat.www.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 返回给前端的用户信息（登录、查询用户信息时用）
 * （对应源项目 UserInfoVO）
 * 不含 password，避免敏感信息外泄
 */
@Data
public class UserInfoVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private String userId;
  private String nickName;
  private Integer sex;
  private Integer joinType;
  private String personalSignature;
  private String areaCode;
  private String areaName;
  private String token;
  private Boolean admin;
  private Integer contactStatus;
}
