package com.wechat.www.constant;

/**
 * 通用基础常量：数字、长度、机器人、通用 Key、时间、文案模板
 */
public class CommonConstants {

  // ============ 基础数字 / 字符串 ============
  public static final String ZERO_STR = "0";
  public static final Integer ZERO = 0;
  public static final Integer ONE = 1;

  // ============ 长度 ============
  public static final Integer LENGTH_10 = 10;
  public static final Integer LENGTH_11 = 11;
  public static final Integer LENGTH_20 = 20;
  public static final Integer LENGTH_30 = 30;

  // ============ 机器人 ============
  public static final String ROBOT_UID = "Urobot";   // 机器人账号ID

  // ============ 通用 Key ============
  public static final String SESSION_KEY = "session_key";
  public static final String CHECK_CODE_KEY = "check_code_key";

  // ============ 时间 ============
  public static final Long MILLISECOND_3DAYS_AGO = 3 * 24 * 60 * 60 * 1000L;  // 3 天的毫秒数

  // ============ 文案模板 ============
  public static final String APPLY_INFO_TEMPLATE = "我是%s";
  public static final String OUT_GROUP_TEMPLATE_SELF = "%s退出了群聊";
  public static final String OUT_GROUP_TEMPLATE = "%s被管理员移出了群聊";
}
