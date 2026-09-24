package com.wechat.www.controller;

import com.wechat.www.constant.RedisKeyConstants;
import com.wechat.www.redis.RedisUtils;
import com.wechat.www.vo.ResponseVO;
import com.wf.captcha.ArithmeticCaptcha;   // easy-captcha 库的算术验证码类（pom 里已引入）
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 账号相关接口（第一个：图形验证码）
 * 完整地址 = context-path(/api) + 类上的(/account) + 方法上的(/checkCode)
 *           = /api/account/checkCode
 */
@RestController            // 声明这是接口类，返回值自动转 JSON
@RequestMapping("/account") // 类下所有接口都以 /account 开头
public class AccountController extends ABaseController {

  @Autowired
  private RedisUtils redisUtils;   // 注入 Redis 工具类

  /**
   * 生成图片验证码
   * 流程：生成算术题图片 → 答案存 Redis（10 分钟过期）→ 把图片+key 返回前端
   * 前端显示图片让用户输答案；登录/注册时前端把 key+答案传回，后端比对是否一致
   */
  @GetMapping("/checkCode")
  public ResponseVO checkCode() {
    // 1. 生成一张 100x42 的算术验证码图片（如 "3+5=?"）
    ArithmeticCaptcha captcha = new ArithmeticCaptcha(100, 42);
    String code = captcha.text();            // 取出正确答案（如 "8"）

    // 2. 生成随机 key，把答案存 Redis，过期 600 秒（10 分钟）
    //    key = wechat:checkcode:<uuid>，value = 答案
    String checkCodeKey = UUID.randomUUID().toString();
    redisUtils.setex(RedisKeyConstants.REDIS_KEY_CHECK_CODE + checkCodeKey, code, 60 * 10);

    // 3. 把图片转成 base64 字符串（前端拿到后直接当 <img> 显示）
    String checkCodeBase64 = captcha.toBase64();

    // 4. 组装返回数据：图片 + key
    Map<String, String> result = new HashMap<>();
    result.put("checkCode", checkCodeBase64);    // 图片内容（base64）
    result.put("checkCodeKey", checkCodeKey);    // 随机 key（下次登录/注册必须带回来）

    // 5. 包成统一响应体返回
    return getSuccessResponseVO(result);
  }
}
