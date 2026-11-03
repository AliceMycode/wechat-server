package com.wechat.www.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 建群/改群入参（头像文件单独接，不放进 DTO）
 * groupId 为空 = 建群，非空 = 改群
 */
@Data
public class SaveGroupDto {

  /** 群ID：建群时前端不传 */
  private String groupId;

  @NotEmpty(message = "群名称不能为空")
  private String groupName;

  /** 群公告（可空） */
  private String groupNotice;

  /** 0:直接加入 1:管理员同意后加入 */
  @NotNull(message = "请选择加入权限")
  private Integer joinType;
}
