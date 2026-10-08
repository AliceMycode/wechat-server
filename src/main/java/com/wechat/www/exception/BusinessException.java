package com.wechat.www.exception;

import com.wechat.www.enums.ResponseCodeEnum;
import lombok.Getter;

/**
 * 业务异常：业务代码主动抛出（如"邮箱已存在""验证码错误"）
 * 由全局异常处理器捕获，统一转成 ResponseVO 返回前端
 * （完全对照源项目 BusinessException）
 */
public class BusinessException extends RuntimeException {

  // 对应的响应码枚举（用枚举构造时才有值，否则为 null）
  @Getter
  private ResponseCodeEnum codeEnum;

  // 业务状态码（对应 ResponseCodeEnum）；为 null 时全局异常处理默认按 600
  @Getter
  private Integer code;

  // 错误提示信息（源项目自己定义了 message 字段，并重写 getMessage）
  private String message;

  // 构造器1：提示信息 + 原始异常（包装其他异常、保留异常链时用）
  public BusinessException(String message, Throwable e) {
    super(message, e);
    this.message = message;
  }

  // 构造器2：只带提示信息（最常用）
  public BusinessException(String message) {
    super(message);
    this.message = message;
  }

  // 构造器3：只传原始异常
  public BusinessException(Throwable e) {
    super(e);
  }

  // 构造器4：直接传响应码枚举
  public BusinessException(ResponseCodeEnum codeEnum) {
    super(codeEnum.getMsg());
    this.codeEnum = codeEnum;
    this.code = codeEnum.getCode();
    this.message = codeEnum.getMsg();
  }

  // 构造器5：状态码 + 提示信息
  public BusinessException(Integer code, String message) {
    super(message);
    this.code = code;
    this.message = message;
  }

  // 重写 getMessage：返回自己存的 message
  @Override
  public String getMessage() {
    return message;
  }

  /**
   * 重写 fillInStackTrace：业务异常不收集堆栈信息，提高效率
   * 业务异常是"预期内"的（如邮箱已存在），不需要堆栈，省掉生成堆栈的开销
   */
  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
