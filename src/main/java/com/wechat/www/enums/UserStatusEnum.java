package com.wechat.www.enums;

import lombok.Getter;

/**
 * 用户状态枚举（对应 user_info.status）
 * 注册时默认 ENABLE(1)；后台可以把账号改成 DISABLE(0) 禁用
 */
@Getter
public enum UserStatusEnum {
  DISABLE(0, "禁用"),
  ENABLE(1, "启用");

  private final Integer status;
  private final String desc;

  UserStatusEnum(Integer status, String desc) {
    this.status = status;
    this.desc = desc;
  }

  // 根据数字状态查枚举（后台改状态时用），查不到返回 null
  public static UserStatusEnum getByStatus(Integer status) {
    for (UserStatusEnum item : values()) {
      if (item.status.equals(status)) {
        return item;
      }
    }
    return null;
  }
}
