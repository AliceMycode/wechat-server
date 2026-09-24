package com.wechat.www.vo;

import lombok.Data;

/**
 * 统一响应体：所有接口都返回这个结构
 * 前端 Request.js 写死了：code=200 成功；code=901 登录超时强制重登；其他 code 按 info 弹提示
 */
@Data   // Lombok：自动生成所有字段的 getter/setter（少写一堆样板代码）
public class ResponseVO<T> {
  private String status;    // 状态标识：success / error（前端主要看 code，这个字段兼容原项目）
  private Integer code;     // 业务状态码：200 成功，其余见 ResponseCodeEnum
  private String info;      // 提示信息：错误时前端弹给用户看的话术
  private T data;           // 真正的数据（泛型 T：每个接口返回的数据类型不同）
}
