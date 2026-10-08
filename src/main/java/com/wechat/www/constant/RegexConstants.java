package com.wechat.www.constant;

/**
 * 正则表达式常量
 */
public class RegexConstants {

  // 密码规则：8~18 位，必须同时包含数字和字母
  public static final String REGEX_PASSWORD = "^(?=.*\\d)(?=.*[a-zA-Z])[\\da-zA-Z~!@#$%^&*_]{8,18}$";
}
