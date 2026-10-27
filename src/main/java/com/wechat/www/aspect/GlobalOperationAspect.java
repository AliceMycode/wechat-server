package com.wechat.www.aspect;

import com.wechat.www.annotation.GlobalInterceptor;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.redis.RedisComponet;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 全局操作切面
 * 所有贴了 @GlobalInterceptor 的方法，执行前先走这里：校验 token → Redis 查登录态
 */
@Component
@Aspect
@Slf4j
public class GlobalOperationAspect {

  @Resource
  private RedisComponet redisComponet;

  @Before("@annotation(com.wechat.www.annotation.GlobalInterceptor)")
  public void interceptorDo(JoinPoint point) {
    try {
      Method method = ((MethodSignature) point.getSignature()).getMethod();
      GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
      if (interceptor == null) return;
      // 校验登录（checkLogin 默认 true；checkAdmin 以后管理后台用）
      if (interceptor.checkLogin() || interceptor.checkAdmin()) {
        checkLogin(interceptor);
      }
    } catch (BusinessException e) {
      log.error("全局拦截器异常", e);
      throw e;
    } catch (Throwable e) {
      log.error("全局拦截器异常", e);
      throw new BusinessException(ResponseCodeEnum.CODE_500);
    }
  }

  private void checkLogin(GlobalInterceptor interceptor) {
    HttpServletRequest request =
      ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
    String token = request.getHeader("token");
    TokenUserInfoDto tokenDto = redisComponet.getTokenUserInfoDto(token);
    if (tokenDto == null) {
      throw new BusinessException(ResponseCodeEnum.CODE_901);
    }
    // 当前登录用户不是管理员却去访问需要管理员权限的接口返回 404
    if (interceptor.checkAdmin() && !tokenDto.getAdmin()) {
      throw new BusinessException(ResponseCodeEnum.CODE_404);
    }
  }
}
