package com.wechat.www.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wechat.www.entity.ChatSessionUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 用户-会话关系表 Mapper */
@Mapper
public interface ChatSessionUserMapper extends BaseMapper<ChatSessionUser> {

  /**
   * 补建会话（消息到达时兜底）
   * 用 insert ignore：联合主键 user_id + contact_id，
   * 群聊里两个人同时发消息可能同时发现某个成员缺会话，避免主键冲突把发送事务打挂
   */
  @Insert("insert ignore into chat_session_user(user_id, contact_id, session_id, contact_name) "
    + "values(#{userId}, #{contactId}, #{sessionId}, #{contactName})")
  int insertIgnore(ChatSessionUser chatSessionUser);
}
