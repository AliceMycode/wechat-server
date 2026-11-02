package com.wechat.www.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ApplyAddDto {

  @NotEmpty(message = "联系人不能为空")
  String contactId;

  @NotEmpty(message = "联系人类型不能为空")
  String contactType;

  /** 申请附言：允许为空，为空时服务端自动填"我是{昵称}"（与源项目一致） */
  String applyInfo;

}
