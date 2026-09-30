package com.wechat.www.exception;

import lombok.Getter;

import java.io.Serial;


@Getter
public class BusinessException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;


}
