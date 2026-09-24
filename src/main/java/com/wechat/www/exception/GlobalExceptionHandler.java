package com.wechat.www.exception;

import java.util.Objects;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一转成 R 结构返回，不把堆栈直接暴露给前端
 *
 * @author qiuzhixu
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

}
