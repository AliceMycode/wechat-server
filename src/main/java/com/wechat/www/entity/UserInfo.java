package com.wechat.www.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wechat.www.constant.CommonConstants;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户信息（对应数据库表 user_info）
 *
 * @TableName("user_info") 告诉 MyBatis-Plus 这个类对应哪张表
 * @Data 是 lombok：自动生成 getter/setter/toString，不用手写
 */
@Data
@TableName("user_info")
public class UserInfo implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * 用户ID（主键，字符串，后端生成 U+11位数字，不是数据库自增）
   * @TableId 标记主键；IdType.INPUT 表示主键由我们自己填，不用数据库自增
   */
  @TableId(type = IdType.INPUT)
  private String userId;

  /** 邮箱（数据库有唯一索引） */
  private String email;

  /** 昵称 */
  private String nickName;

  /** 加入方式 0:直接加入 1:同意后加好友 */
  private Integer joinType;

  /** 性别 0:女 1:男 */
  private Integer sex;

  /**
   * 密码（存的是 MD5 哈希，不是明文）
   * @JsonIgnore：序列化返回前端时忽略这个字段，绝不把密码发出去
   */
  @JsonIgnore
  private String password;

  /** 个性签名 */
  private String personalSignature;

  /** 状态 0:禁用 1:启用 */
  private Integer status;

  /**
   * 创建时间
   * @JsonFormat：返回前端时按 "yyyy-MM-dd HH:mm:ss" 格式转字符串（GMT+8）
   * @DateTimeFormat：接收前端字符串时按这个格式解析成 Date
   */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date createTime;

  /** 最后登录时间 */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date lastLoginTime;

  /** 省份 */
  private String areaName;

  /** 城市 */
  private String areaCode;

  /** 最后离开时间（毫秒时间戳，用于判断在线状态） */
  private Long lastOffTime;

  /**
   * 在线状态：注意这不是数据库字段，也不存值，而是实时计算出来的
   * 规则：最后登录时间晚于最后离开时间 → 在线(1)，否则离线(0)
   *
   * 我们不声明 onlineType 字段，只手写这个 getter：
   * - Jackson 看到 getOnlineType() 会自动把它序列化进返回 JSON
   * - MyBatis-Plus 没有对应字段，不会去找数据库列
   */
  public Integer getOnlineType() {
    if (lastLoginTime != null && lastLoginTime.getTime() > lastOffTime) {
      return CommonConstants.ONE;
    }
    return CommonConstants.ZERO;
  }
}
