package com.wechat.www.enums;

import lombok.Getter;

/**
 * 好友申请状态（对应 user_contact_apply.status）
 */
@Getter
public enum UserContactApplyStatusEnum {
  INIT(0, "待处理"),
  PASS(1, "已同意"),
  REJECT(2, "已拒绝"),
  BLACKLIST(3, "已拉黑");

  private final Integer status;
  private final String desc;

  UserContactApplyStatusEnum(Integer status, String desc) {
    this.status = status;
    this.desc = desc;
  }

  public static UserContactApplyStatusEnum getByStatus(Integer status) {
    for (UserContactApplyStatusEnum item : values()) {
      if (item.status.equals(status)) {
        return item;
      }
    }
    return null;
  }
}
