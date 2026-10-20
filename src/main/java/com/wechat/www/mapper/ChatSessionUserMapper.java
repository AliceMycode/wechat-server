package com.wechat.www.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wechat.www.entity.ChatSessionUser;
import org.apache.ibatis.annotations.Mapper;

/** 用户-会话关系表 Mapper */
@Mapper
public interface ChatSessionUserMapper extends BaseMapper<ChatSessionUser> {
}
