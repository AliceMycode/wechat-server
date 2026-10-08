package com.wechat.www.exception;

import com.wechat.www.controller.BaseController;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理：拦截所有 Controller 抛出的异常，统一转成 ResponseVO
 * 继承 ABaseController：复用父类 STATUC_ERROR 常量
 *
 * 每种异常单独一个 @ExceptionHandler 方法，Spring 按异常类型自动匹配最接近的方法
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseController {

  /** 1. 404：请求地址不存在 */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseVO<Void> handleNoHandlerFound(NoHandlerFoundException e, HttpServletRequest request) {
    log.warn("请求地址不存在：{}", request.getRequestURL());
    ResponseVO<Void> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_ERROR);
    responseVO.setCode(ResponseCodeEnum.CODE_404.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_404.getMsg());
    return responseVO;
  }

  /** 2. 业务异常：我们自己 throw new BusinessException(...) 抛出的，code 用异常自带的 */
  @ExceptionHandler(BusinessException.class)
  public ResponseVO<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
    log.warn("业务异常：地址{}，信息{}", request.getRequestURL(), e.getMessage());
    ResponseVO<Void> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_ERROR);
    responseVO.setCode(e.getCode() == null ? ResponseCodeEnum.CODE_600.getCode() : e.getCode());
    responseVO.setInfo(e.getMessage());
    return responseVO;
  }

  /** 3. 表单绑定异常：表单参数绑定到对象失败 */
  @ExceptionHandler(BindException.class)
  public ResponseVO<Void> handleBindException(BindException e, HttpServletRequest request) {
    log.warn("参数绑定错误：地址{}", request.getRequestURL());
    ResponseVO<Void> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_ERROR);
    responseVO.setCode(ResponseCodeEnum.CODE_600.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_600.getMsg());
    return responseVO;
  }

  /** 4. 参数类型不匹配：如该传数字却传了字符串 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseVO<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    log.warn("参数类型错误：地址{}", request.getRequestURL());
    ResponseVO<Void> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_ERROR);
    responseVO.setCode(ResponseCodeEnum.CODE_600.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_600.getMsg());
    return responseVO;
  }

  /** 5. 唯一键冲突：如邮箱重复插入，触发数据库唯一索引报错 */
  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseVO<Void> handleDuplicateKey(DuplicateKeyException e, HttpServletRequest request) {
    log.warn("唯一键冲突：地址{}", request.getRequestURL());
    ResponseVO<Void> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_ERROR);
    responseVO.setCode(ResponseCodeEnum.CODE_601.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_601.getMsg());
    return responseVO;
  }

  /** 6. 参数校验异常：@NotEmpty/@Email/@NotNull 等注解不满足 */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseVO<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
    log.warn("参数校验错误：地址{}", request.getRequestURL());
    ResponseVO<Void> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_ERROR);
    responseVO.setCode(ResponseCodeEnum.CODE_600.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_600.getMsg());
    return responseVO;
  }

  /** 7. 兜底：其他未预料的异常，统一 500 */
  @ExceptionHandler(Exception.class)
  public ResponseVO<Void> handleException(Exception e, HttpServletRequest request) {
    log.error("服务器异常：地址{}", request.getRequestURL(), e);
    ResponseVO<Void> responseVO = new ResponseVO<>();
    responseVO.setStatus(STATUC_ERROR);
    responseVO.setCode(ResponseCodeEnum.CODE_500.getCode());
    responseVO.setInfo(ResponseCodeEnum.CODE_500.getMsg());
    return responseVO;
  }
}
