package com.wechat.www.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wechat.www.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper
 * @Mapper 注解让 MyBatis 启动时自动扫描并注册该接口（主类未加 @MapperScan，靠的就是它）
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
