package com.wechat.www.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 联系人关系（对应表 user_contact）
 * 联合主键：user_id + contact_id（一个人对一个联系人一条记录）
 * 联合主键没法用 @TableId 标单个字段，所以这里不标，
 * 后面按联合主键查询时用 QueryWrapper 条件构造
 */
@Data
@TableName("user_contact")
public class UserContact implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 用户ID（联合主键之一） */
  private String userId;

  /** 联系人ID或群组ID（联合主键之一） */
  private String contactId;

  /** 联系人类型 0:好友 1:群组 */
  private Integer contactType;

  /** 创建时间 */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date createTime;

  /** 状态 0:非好友 1:好友 2:已删除 3:被删除 4:拉黑 5:被拉黑 */
  private Integer status;

  /** 最后更新时间（数据库 ON UPDATE CURRENT_TIMESTAMP 会自动更新） */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date lastUpdateTime;

  /**
   * 联系人昵称：不是数据库字段，联查用户表时临时填，用于前端展示
   * @TableField(exist = false) 告诉 MP 数据库没这列，别参与 SQL
   */
  @TableField(exist = false)
  private String contactName;

  /** 联系人性别：同样不是数据库字段，联查时填 */
  @TableField(exist = false)
  private Integer sex;
}
