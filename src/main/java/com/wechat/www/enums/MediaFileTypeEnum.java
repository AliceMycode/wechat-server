package com.wechat.www.enums;

import lombok.Getter;

/**
 * 媒体文件类型（上传文件时区分）
 */
@Getter
public enum MediaFileTypeEnum {
  IMAGE(0, "图片"),
  VIDEO(1, "视频"),
  FILE(2, "文件");

  private final Integer type;
  private final String desc;

  MediaFileTypeEnum(Integer type, String desc) {
    this.type = type;
    this.desc = desc;
  }

  public static MediaFileTypeEnum getByType(Integer type) {
    for (MediaFileTypeEnum item : values()) {
      if (item.type.equals(type)) {
        return item;
      }
    }
    return null;
  }
}
