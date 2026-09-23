package com.wechat.www.common;

import java.util.Objects;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一转成 R 结构返回，不把堆栈直接暴露给前端
 *
 * @author qiuzhixu
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 业务异常：可预期，记 warn 且不打印堆栈 */
  @ExceptionHandler(BusinessException.class)
  public R<Void> handleBusinessException(BusinessException e) {
    log.warn("业务异常: {}", e.getMessage());
    return R.fail(e.getCode(), e.getMessage());
  }

  /** {@code @RequestBody} 参数校验失败（MethodArgumentNotValidException 是 BindException 的子类，一并命中） */
  @ExceptionHandler(BindException.class)
  public R<Void> handleBindException(BindException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse("参数校验失败");
    log.warn("参数校验失败: {}", msg);
    return R.fail(msg);
  }

  /** 方法参数（{@code @RequestParam} / {@code @PathVariable}）校验失败 */
  @ExceptionHandler(ConstraintViolationException.class)
  public R<Void> handleConstraintViolationException(ConstraintViolationException e) {
    String msg = e.getConstraintViolations().stream()
        .map(ConstraintViolation::getMessage)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse("参数校验失败");
    log.warn("参数校验失败: {}", msg);
    return R.fail(msg);
  }

  /** 兜底：非预期异常，记 error 并打印堆栈，对外只返回通用提示 */
  @ExceptionHandler(Exception.class)
  public R<Void> handleException(Exception e) {
    log.error("系统异常", e);
    return R.fail("系统异常，请稍后重试");
  }
}
