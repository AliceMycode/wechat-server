package com.wechat.www.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 群信息（对应表 group_info）
 * 单主键 group_id，可以直接用 MP 的 selectById / updateById
 */
@Data
@TableName("group_info")
public class GroupInfo implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 群ID（G + 11位数字） */
  @TableId
  private String groupId;

  /** 群名称 */
  private String groupName;

  /** 群主用户ID */
  private String groupOwnerId;

  /** 创建时间 */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date createTime;

  /** 群公告 */
  private String groupNotice;

  /** 加入方式 0:直接加入 1:管理员同意后加入 */
  private Integer joinType;

  /** 状态 1:正常 0:解散（数据库默认1，插入时不传） */
  private Integer status;

  /** 群成员数：不是表字段，getGroupInfo 时现算 */
  @TableField(exist = false)
  private Integer memberCount;

  /** 群主昵称：不是表字段，目前前端不用，保留与源项目字段一致 */
  @TableField(exist = false)
  private String groupOwnerNickName;
}
