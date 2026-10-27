package com.wechat.www.annotation;

import java.lang.annotation.*;

/**
 * 全局拦截注解（对照源项目 GlobalInterceptor）
 * 贴在 Controller 方法上，进入方法前由 GlobalOperationAspect 自动校验登录态
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GlobalInterceptor {
  /** 校验登录，默认 true */
  boolean checkLogin() default true;
  /** 校验管理员，默认 false（管理后台用，现在用不上） */
  boolean checkAdmin() default false;
}
