package com.wechat.www.utils;

import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象拷贝工具：把一个对象的同名属性复制到另一个类的对象
 * （完全对照源项目 CopyTools）
 * 典型用途：把数据库实体 UserInfo 复制成返回给前端的 VO，
 * 避免把密码等敏感字段直接返回
 */
public class CopyTools {

  /**
   * 拷贝列表：把 List<S> 每个元素复制成 classz 类型
   * 泛型 <T, S>：T 目标类型，S 源类型
   */
  public static <T, S> List<T> copyList(List<S> sList, Class<T> classz) {
    List<T> list = new ArrayList<>();
    for (S s : sList) {
      T t;
      try {
        // 用反射创建目标对象
        t = classz.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("对象拷贝失败", e);
      }
      // Spring 提供的属性拷贝：按"同名属性"逐个复制
      BeanUtils.copyProperties(s, t);
      list.add(t);
    }
    return list;
  }

  /**
   * 拷贝单个对象：如 CopyTools.copy(userInfo, UserInfoVO.class)
   */
  public static <T, S> T copy(S s, Class<T> classz) {
    T t;
    try {
      t = classz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException("对象拷贝失败", e);
    }
    BeanUtils.copyProperties(s, t);
    return t;
  }
}
