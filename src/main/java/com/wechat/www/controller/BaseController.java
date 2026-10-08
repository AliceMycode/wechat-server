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

  /**
   * 业务错误响应：把 BusinessException 转成统一格式
   * code 为 null 时默认按 600（参数错误）
   */
  protected <T> ResponseVO<T> getBusinessErrorResponseVO(BusinessException e, T t) {
    ResponseVO<T> vo = new ResponseVO<>();
    vo.setStatus(STATUC_ERROR);
    if (e.getCode() == null) {
      vo.setCode(ResponseCodeEnum.CODE_600.getCode());
    } else {
      vo.setCode(e.getCode());
    }
    vo.setInfo(e.getMessage());
    vo.setData(t);
    return vo;
  }

  /**
   * 服务器错误响应：兜底的 500
   */
  protected <T> ResponseVO<T> getServerErrorResponseVO(T t) {
    ResponseVO<T> vo = new ResponseVO<>();
    vo.setStatus(STATUC_ERROR);
    vo.setCode(ResponseCodeEnum.CODE_500.getCode());
    vo.setInfo(ResponseCodeEnum.CODE_500.getMsg());
    vo.setData(t);
    return vo;
  }
}
