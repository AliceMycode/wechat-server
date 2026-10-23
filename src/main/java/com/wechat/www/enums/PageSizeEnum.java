package com.wechat.www.enums;

import lombok.Getter;

/**
 * 分页每页条数（分页查询时用）
 */
@Getter
public enum PageSizeEnum {
  SIZE15(15), SIZE20(20), SIZE30(30), SIZE40(40), SIZE50(50);

  private final int size;

  PageSizeEnum(int size) {
    this.size = size;
  }

}
