package com.wechat.www.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wechat.www.constant.CommonConstants;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统设置：后台可配置，存 Redis；Redis 里没有时用默认值
 * （对应源项目 SysSettingDto）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SysSettingDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer maxGroupCount = 5;          // 每人最多建群数
  private Integer maxGroupMemberCount = 500;  // 每群最多成员数
  private Integer maxImageSize = 2;           // 图片大小上限（MB）
  private Integer maxVideoSize = 5;           // 视频大小上限（MB）
  private Integer maxFileSize = 5;            // 文件大小上限（MB）
  private String robotUid = CommonConstants.ROBOT_UID;  // 机器人账号ID
  private String robotNickName = "EasyChat";            // 机器人昵称
  private String robotWelcome = "欢迎使用EasyChat";      // 机器人欢迎语
}
