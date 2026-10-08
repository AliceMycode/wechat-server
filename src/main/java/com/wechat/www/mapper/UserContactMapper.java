package com.wechat.www.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wechat.www.entity.UserContact;
import org.apache.ibatis.annotations.Mapper;

/** 联系人关系表 Mapper */
@Mapper
public interface UserContactMapper extends BaseMapper<UserContact> {
}
