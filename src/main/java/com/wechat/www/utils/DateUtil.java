package com.wechat.www.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 日期工具类：线程安全的 SimpleDateFormat
 *
 * 为什么要这么写：SimpleDateFormat 本身不是线程安全的，多线程共用一个会出错。
 * 这里用 ThreadLocal 给每个线程存一份自己的 SimpleDateFormat，按格式缓存复用
 */
public class DateUtil {

  private static final Object lockObj = new Object();
  // 缓存：格式 → 该格式的 ThreadLocal
  private static final Map<String, ThreadLocal<SimpleDateFormat>> SDF_MAP = new HashMap<>();

  // 获取指定格式的 SimpleDateFormat（双重检查锁，避免重复创建）
  private static SimpleDateFormat getSdf(final String pattern) {
    ThreadLocal<SimpleDateFormat> tl = SDF_MAP.get(pattern);
    if (tl == null) {
      synchronized (lockObj) {
        tl = SDF_MAP.get(pattern);
        if (tl == null) {
          // Lambda 表达式写法
          tl = ThreadLocal.withInitial(() -> new SimpleDateFormat(pattern));
          // 匿名内部类写法
          // tl = ThreadLocal.withInitial(
          //   new Supplier<SimpleDateFormat>() {
          //     @Override
          //     public SimpleDateFormat get() {
          //       return new SimpleDateFormat(pattern);
          //     }
          //   }
          // );
          SDF_MAP.put(pattern, tl);
        }
      }
    }
    return tl.get();
  }

  // 日期 → 字符串
  public static String format(Date date, String pattern) {
    return getSdf(pattern).format(date);
  }

  // 字符串 → 日期，解析失败返回当前时间
  public static Date parse(String dateStr, String pattern) {
    try {
      return getSdf(pattern).parse(dateStr);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return new Date();
  }
}
