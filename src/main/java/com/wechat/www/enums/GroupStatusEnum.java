package com.wechat.www.enums;

import lombok.Getter;

/**
 * 群状态（对应 group_info.status）
 */
@Getter
public enum GroupStatusEnum {
  NORMAL(1, "正常"),
  DISSOLUTION(0, "解散");

  private final Integer status;
  private final String desc;

  GroupStatusEnum(Integer status, String desc) {
    this.status = status;
    this.desc = desc;
  }

  public static GroupStatusEnum getByStatus(Integer status) {
    for (GroupStatusEnum item : values()) {
      if (item.status.equals(status)) {
        return item;
      }
    }
    return null;
  }
}
