package com.wechat.www.controller;

import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.vo.ResponseVO;

/**
 * 所有 Controller 的公共父类：放公共方法
 * 现在只有"成功响应"，后面写登录时会再加"解析 token""错误响应"等方法
 */
public class BaseController {
  // 响应状态（status 字段）：成功 / 失败
  protected static final String STATUC_SUCCESS = "success";
  protected static final String STATUC_ERROR = "error";

  /**
   * 成功响应：所有成功接口统一 return getSuccessResponseVO(数据)
   * 泛型 <T>：传入什么类型，data 就是什么类型
   */
  protected <T> ResponseVO<T> getSuccessResponseVO(T t) {
    ResponseVO<T> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_SUCCESS);
    responseVO.setCode(ResponseCodeEnum.CODE_200.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_200.getMsg());
    responseVO.setData(t);
    return responseVO;
  }
}
