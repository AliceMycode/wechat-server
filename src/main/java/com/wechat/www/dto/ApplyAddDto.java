package com.wechat.www.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ApplyAddDto {

  @NotEmpty(message = "联系人不能为空")
  String contactId;

  @NotEmpty(message = "联系人类型不能为空")
  String contactType;

  @NotEmpty(message = "申请信息不能为空")
  String applyInfo;

}
