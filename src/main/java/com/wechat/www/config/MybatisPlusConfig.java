package com.wechat.www.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册分页插件，否则 Page 查询不会真正分页（会查全表再截断）
 *
 * @author qiuzhixu
 */
@Configuration
public class MybatisPlusConfig {

  /** 单页最大条数，避免 pageSize 被传得过大拖垮数据库 */
  private static final long MAX_PAGE_SIZE = 500L;

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
    pagination.setMaxLimit(MAX_PAGE_SIZE);
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
  }
}
