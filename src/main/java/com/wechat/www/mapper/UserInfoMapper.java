package com.wechat.www.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wechat.www.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper
 * @Mapper 让 MyBatis 扫描到（主类已加 @MapperScan，这里注解可选，写上更明确）
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
