package com.wechat.www.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常：由业务代码主动抛出，交由 GlobalExceptionHandler 统一转换成 R.fail 返回
 *
 * @author qiuzhixu
 */
@Getter
public class BusinessException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;


}
