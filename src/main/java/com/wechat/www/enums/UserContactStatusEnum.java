package com.wechat.www.enums;

import lombok.Getter;

/**
 * 联系人关系状态枚举（对应 user_contact.status）
 * 前 6 个状态值和建表注释一致，前端/后端逻辑都依赖，不能改
 */
@Getter
public enum UserContactStatusEnum {
  NOT_FRIEND(0, "非好友"),
  FRIEND(1, "好友"),
  DEL(2, "已删除好友"),
  DEL_BE(3, "被好友删除"),
  BLACKLIST(4, "已拉黑好友"),
  BLACKLIST_BE(5, "被好友拉黑"),
  BLACKLIST_BE_FIRST(6, "首次被好友拉黑");

  private final Integer status;
  private final String desc;

  UserContactStatusEnum(Integer status, String desc) {
    this.status = status;
    this.desc = desc;
  }

  // 根据数字查枚举
  public static UserContactStatusEnum getByStatus(Integer status) {
    for (UserContactStatusEnum item : values()) {
      if (item.status.equals(status)) {
        return item;
      }
    }
    return null;
  }
}
