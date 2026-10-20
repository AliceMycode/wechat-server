package com.wechat.www.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wechat.www.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/** 会话表 Mapper */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
