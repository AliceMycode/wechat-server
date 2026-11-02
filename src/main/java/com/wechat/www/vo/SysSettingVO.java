package com.wechat.www.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SysSettingVO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  private Integer maxImageSize = 2;
  private Integer maxVideoSize = 5;
  private Integer maxFileSize = 5;

}
