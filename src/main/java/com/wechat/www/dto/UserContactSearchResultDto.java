package com.wechat.www.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 搜索联系人返回结果（/contact/search）
 * 前端 UserBaseInfo 组件 + Search.vue 用这些字段判断"加好友/发消息"
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserContactSearchResultDto implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private String userId;              // 用户ID（搜索群时是 null）
  private String contactId;           // 联系人ID（前端跳聊天用它）
  private String contactType;         // "USER" 或 "GROUP"（前端判断显示"用户"还是"群组"）
  private String nickName;            // 昵称/群名
  private Integer sex;                // 性别 0女 1男
  private String areaName;            // 地区
  private String personalSignature;   // 个性签名
  private Long iconLastUpdateTime;    // 头像缓存时间戳（用最后登录时间）
  private Integer status;             // 和我的关系 0非好友 1好友 2删除 3被删 4拉黑 5被拉黑 6首次被拉黑
}
