package com.wechat.www.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存个人信息的请求参数
 * 字段名必须和前端 FormData 的 key 完全一致（UserInfoEdit.vue）
 * 只放前端有权修改的字段，password/status 等根本不进来
 */
@Data
public class SaveUserInfoDto {

  @NotBlank(message = "昵称不能为空")
  @Size(max = 150, message = "昵称不能超过150个字符")
  private String nickName;

  /** 0:女 1:男 */
  private Integer sex;

  /** 加好友方式 0:直接加入 1:需要验证 */
  private Integer joinType;

  /** 地区名，多级用逗号拼接（如 "广东省,深圳市,南山区"） */
  private String areaName;

  /** 地区编码，多级用逗号拼接 */
  private String areaCode;

  @Size(max = 30, message = "个性签名不能超过30个字符")
  private String personalSignature;
}
