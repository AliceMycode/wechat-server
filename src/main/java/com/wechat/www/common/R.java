package com.wechat.www.common;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 统一响应体
 *
 * @author qiuzhixu
 */
@Data
public class R<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 成功 */
  public static final int CODE_SUCCESS = 200;
  /** 失败 */
  public static final int CODE_FAIL = 500;

  private int code;
  private String msg;
  private T data;

  public R() {
  }

  public R(int code, String msg, T data) {
    this.code = code;
    this.msg = msg;
    this.data = data;
  }

  public static <T> R<T> ok() {
    return new R<>(CODE_SUCCESS, "success", null);
  }

  public static <T> R<T> ok(T data) {
    return new R<>(CODE_SUCCESS, "success", data);
  }

  public static <T> R<T> fail(String msg) {
    return new R<>(CODE_FAIL, msg, null);
  }

  public static <T> R<T> fail(int code, String msg) {
    return new R<>(code, msg, null);
  }
}
