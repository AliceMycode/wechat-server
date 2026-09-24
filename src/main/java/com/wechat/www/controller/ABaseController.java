package com.wechat.www.controller;

import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.vo.ResponseVO;

/**
 * 所有 Controller 的公共父类：放公共方法
 * 现在只有"成功响应"，后面写登录时会再加"解析 token""错误响应"等方法
 */
public class ABaseController {

  /**
   * 封装"请求成功"的响应：所有成功接口统一 return getSuccessResponseVO(数据)
   * 泛型 <T>：传入什么类型的数据，响应体的 data 就是什么类型
   */
  protected <T> ResponseVO<T> getSuccessResponseVO(T t) {
    ResponseVO<T> responseVO = new ResponseVO<>();
    responseVO.setStatus("success");
    responseVO.setCode(ResponseCodeEnum.CODE_200.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_200.getMsg());
    responseVO.setData(t);
    return responseVO;
  }
}
