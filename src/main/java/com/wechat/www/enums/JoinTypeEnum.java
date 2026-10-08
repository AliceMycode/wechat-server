package com.wechat.www.enums;

import lombok.Getter;

/**
 * 群加入方式（对应 group_info.join_type）
 */
@Getter
public enum JoinTypeEnum {
  JOIN(0, "直接加入"),
  APPLY(1, "需要审核");

  private final Integer type;
  private final String desc;

  JoinTypeEnum(Integer type, String desc) {
    this.type = type;
    this.desc = desc;
  }

  public static JoinTypeEnum getByType(Integer joinType) {
    for (JoinTypeEnum item : values()) {
      if (item.type.equals(joinType)) {
        return item;
      }
    }
    return null;
  }
}
