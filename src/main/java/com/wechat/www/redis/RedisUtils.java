package com.wechat.www.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;


import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类：把 RedisTemplate 的常用操作封装成好记的方法
 * 本项目大量场景依赖它：验证码、登录 token、用户在线状态、WS 连接信息
 * 现在先写够验证码用的 4 个方法，后面用到再加
 */
@Component("redisUtils")
public class RedisUtils {

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;   // 注入我们自己定义的 RedisTemplate（见 RedisConfig）

  /** 删除一个或多个 key（可变参数：传几个删几个），注册/登录用完验证码后要删掉它 */
  public void delete(String... key) {
    if (key != null && key.length > 0) {
      if (key.length == 1) {
        redisTemplate.delete(key[0]);
      } else {
        redisTemplate.delete((Collection<String>) CollectionUtils.arrayToList(key));
      }
    }
  }

  /** 根据 key 取值（验证码答案、token 用户信息都靠它取） */
  public Object get(String key) {
    return key == null ? null : redisTemplate.opsForValue().get(key);
  }

  /** 存值（不设过期时间，永久生效，慎用） */
  public boolean set(String key, Object value) {
    try {
      redisTemplate.opsForValue().set(key, value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** 存值并设置过期时间（秒）；time<=0 时不设过期。验证码、token 都用这个方法 */
  public boolean setex(String key, Object value, long time) {
    try {
      if (time > 0) {
        redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
      } else {
        set(key, value);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
