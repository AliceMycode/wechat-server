package com.wechat.www.constant;

/**
 * 全局常量类：把散落的"魔法值"集中管理，避免代码里到处是看不懂的字符串
 * 原则：用到什么加什么，不要一次堆满用不上的常量
 */
public class RedisKeyConstants {

  // ============ Redis Key 前缀 ============
  // 所有 Redis key 统一带 "wechat:" 前缀：区分业务、避免和别的项目 key 撞车
  // 验证码的 key 规则：wechat:checkcode:<随机key>，value 存答案，10 分钟过期
  public static final String REDIS_KEY_CHECK_CODE = "wechat:checkcode:";
}
