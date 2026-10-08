package com.wechat.www.constant;

/**
 * Redis 常量：Key 前缀 + 过期时间
 * （redis 相关的常量统一放这里）
 */
public class RedisKeyConstants {

  // ============ Redis Key 前缀 ============
  // 所有 Redis key 统一带 "wechat:" 前缀：区分业务、避免和别的项目 key 撞车
  // 验证码的 key 规则：wechat:checkcode:<随机key>，value 存答案，10 分钟过期
  public static final String REDIS_KEY_CHECK_CODE = "wechat:checkcode:";             // 图形验证码
  public static final String REDIS_KEY_WS_TOKEN = "wechat:ws:token:";                // token → 用户信息
  public static final String REDIS_KEY_WS_TOKEN_USERID = "wechat:ws:token:userid:";  // 用户ID → token
  public static final String REDIS_KEY_WS_USER_HEART_BEAT = "wechat:ws:user:heartbeat:"; // 心跳（在线判断）
  public static final String REDIS_KEY_WS_ON_LINE_USER = "wechat:ws:online:";        // 在线用户
  public static final String REDIS_KEY_USER_CONTACT = "wechat:ws:user:contact:";     // 联系人ID列表（Redis List）
  public static final String REDIS_KEY_USER_SESSION = "wechat:ws:user:session:";     // 会话ID列表（Redis List）
  public static final String REDIS_KEY_SYS_SETTING = "wechat:syssetting:";           // 系统设置


  // ============ 过期时间（秒） ============
  public static final Integer REDIS_KEY_EXPIRES_ONE_MIN = 60;
  public static final Integer REDIS_KEY_EXPIRES_HEART_BEAT = 6;
  public static final Integer REDIS_KEY_EXPIRES_DAY = REDIS_KEY_EXPIRES_ONE_MIN * 60 * 24;  // 1 天
  public static final Integer REDIS_KEY_TOKEN_EXPIRES = REDIS_KEY_EXPIRES_DAY * 2;           // token 有效期 2 天
}
