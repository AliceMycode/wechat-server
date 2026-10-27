package com.wechat.www.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 好友申请（对应表 user_contact_apply）
 * 一张申请记录 = 某人申请加我为好友/群
 * 唯一索引(apply_user_id, receive_user_id, contact_id)：同一人反复申请只更新
 */
@Data
@TableName("user_contact_apply")
public class UserContactApply implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 申请ID（自增主键） */
  @TableId(type = IdType.AUTO)
  private Integer applyId;

  /** 申请人ID（谁发起的申请） */
  private String applyUserId;

  /** 接收人ID（申请发给谁；加群时=群主ID） */
  private String receiveUserId;

  /** 联系人类型 0:好友 1:群组 */
  private Integer contactType;

  /** 联系人或群ID（加好友=对方ID；加群=群ID） */
  private String contactId;

  /** 最后申请时间（毫秒时间戳） */
  private Long lastApplyTime;

  /** 状态 0:待处理 1:已同意 2:已拒绝 3:已拉黑 */
  private Integer status;

  /** 申请附言（"我是xxx"） */
  private String applyInfo;

  // ===== 下面 3 个是"查询时联表补的字段"，数据库里没有这 3 列 =====

  /** 申请人昵称（联查 user_info 补） */
  @TableField(exist = false)
  private String contactName;

  /** 申请人最后登录时间（当前端头像缓存时间戳，联查补） */
  @TableField(exist = false)
  private Long iconLastUpdateTime;

  /** 状态描述（由 status 枚举翻译，给前端展示"已同意/已拒绝"） */
  @TableField(exist = false)
  private String statusName;
}
