package com.wechat.www.redis;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;


import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类：把 RedisTemplate 的常用操作封装成好记的方法
 * 本项目大量场景依赖它：验证码、登录 token、用户在线状态、WS 连接信息
 * 现在先写够验证码用的 4 个方法，后面用到再加
 */
@Component
public class RedisUtils {

  @Resource
  private RedisTemplate<String, Object> redisTemplate;   // 注入我们自己定义的 RedisTemplate（见 RedisConfig）

  /** 删除一个或多个 key（可变参数：传几个删几个），注册/登录用完验证码后要删掉它 */
  @SuppressWarnings("unchecked")
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
  /** 给已存在的 key 重新设置过期时间（秒），List 每次操作后续约用 */
  public boolean expire(String key, long time) {
    try {
      if (time > 0) {
        redisTemplate.expire(key, time, TimeUnit.SECONDS);
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /** 取出整个 List 队列（0 到 -1 表示全部），登录时一次性加载联系人用 */
  public List<Object> getQueueList(String key) {
    return redisTemplate.opsForList().range(key, 0, -1);
  }

  /** 从左边往 List 压入一个元素，并设置过期时间 */
  public boolean lpush(String key, Object value, long time) {
    try {
      redisTemplate.opsForList().leftPush(key, value);
      if (time > 0) {
        expire(key, time);
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /** 从 List 中删除指定元素（count=1 表示只删第一个匹配的） */
  public long remove(String key, Object value) {
    try {
      return redisTemplate.opsForList().remove(key, 1, value);
    } catch (Exception e) {
      e.printStackTrace();
      return 0;
    }
  }

  /** 从左边批量压入多个元素，并设置过期时间 */
  public boolean lpushAll(String key, List<Object> values, long time) {
    try {
      redisTemplate.opsForList().leftPushAll(key, values);
      if (time > 0) {
        expire(key, time);
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

}
