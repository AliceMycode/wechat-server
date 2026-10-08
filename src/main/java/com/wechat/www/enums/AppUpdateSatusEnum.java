package com.wechat.www.enums;

import lombok.Getter;

/**
 * App 版本发布状态（对应 app_update.status）
 */
@Getter
public enum AppUpdateSatusEnum {
  INIT(0, "未发布"),
  GRAYSCALE(1, "灰度发布"),
  ALL(2, "全网发布");

  private final Integer status;
  private final String description;

  AppUpdateSatusEnum(int status, String description) {
    this.status = status;
    this.description = description;
  }

  public static AppUpdateSatusEnum getByStatus(Integer status) {
    for (AppUpdateSatusEnum item : values()) {
      if (item.status.equals(status)) {
        return item;
      }
    }
    return null;
  }
}
