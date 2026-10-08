package com.wechat.www.enums;

import lombok.Getter;

/**
 * 常用日期时间格式
 */
@Getter
public enum DateTimePatternEnum {
  YYYY_MM_DD_HH_MM_SS("yyyy-MM-dd HH:mm:ss"),
  YYYY_MM_DD("yyyy-MM-dd"),
  YYYYMM("yyyyMM");

  private final String pattern;

  DateTimePatternEnum(String pattern) {
    this.pattern = pattern;
  }

}
