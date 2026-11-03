package com.wechat.www.vo;

import com.wechat.www.entity.GroupInfo;
import com.wechat.www.entity.UserContact;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 聊天面板右侧群详情：群信息 + 成员列表
 * （字段名和源项目 GroupInfoVO 一致，前端 ChatGroupDetail.vue 直接读）
 */
@Data
public class GroupInfoVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private GroupInfo groupInfo;

  /** 群成员（其实是 user_contact 记录，里面用 contactName/sex 展示） */
  private List<UserContact> userContactList;
}
