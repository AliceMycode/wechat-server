package com.wechat.www.utils;

import com.wechat.www.constant.CommonConstants;
import com.wechat.www.enums.UserContactTypeEnum;
import com.wechat.www.exception.BusinessException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 字符串/通用工具类：静态方法，直接 StringTools.xxx() 调用
 * （完全对照源项目 StringTools）
 *
 * 注册/登录会用到：isEmpty、getRandomNumber、getRandomString、encodeByMD5、getUserId
 */
public class StringTools {

  /**
   * 校验"多条件更新/删除"时，参数对象里至少有一个非空字段
   * 用反射遍历字段，调用 getter 看值
   * （后面写多条件更新时才用，先放着）
   */
  public static void checkParam(Object param) {
    try {
      Field[] fields = param.getClass().getDeclaredFields();
      boolean notEmpty = false;
      for (Field field : fields) {
        String methodName = "get" + StringTools.upperCaseFirstLetter(field.getName());
        Method method = param.getClass().getMethod(methodName);
        Object object = method.invoke(param);
        if (object instanceof String && !StringTools.isEmpty(object.toString())
          || object != null && !(object instanceof String)) {
          notEmpty = true;
          break;
        }
      }
      if (!notEmpty) {
        throw new BusinessException("多参数更新，删除，必须有非空条件");
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      throw new BusinessException("校验参数是否为空失败");
    }
  }

  // 字段名首字母大写（拼 getter 方法名用）
  public static String upperCaseFirstLetter(String field) {
    if (isEmpty(field)) {
      return field;
    }
    // 如果第二个字母是大写，第一个字母不大写
    if (field.length() > 1 && Character.isUpperCase(field.charAt(1))) {
      return field;
    }
    return field.substring(0, 1).toUpperCase() + field.substring(1);
  }

  // 判断是否纯数字
  public static boolean isNumber(String str) {
    String checkNumber = "^[0-9]+$";
    if (null == str) {
      return false;
    }
    return str.matches(checkNumber);
  }

  /**
   * 判断字符串是否为空：null、空串、"null"、\u0000、全空格 都算空
   * （比简单的 str == null 判断更全，整个项目到处用）
   */
  public static boolean isEmpty(String str) {
    if (null == str || str.isEmpty() || "null".equals(str) || "\u0000".equals(str)) {
      return true;
    } else return str.trim().isEmpty();
  }

  /**
   * 生成随机数字串：count 位
   * （RandomStringUtils.random(count, false, true)：false=不包含字母，true=包含数字）
   */
  // 生成随机数字串（count 位）
  public static String getRandomNumber(Integer count) {
    return RandomStringUtils.secure().next(count, false, true);
  }

  /**
   * 生成随机字母+数字串：count 位（生成 token 用）
   */
  // 生成随机字母+数字串（count 位）
  public static String getRandomString(Integer count) {
    return RandomStringUtils.secure().next(count, true, true);
  }
  /**
   * MD5 加密：把明文转成 32 位十六进制哈希
   * 密码、用户ID、会话ID 都用它；入参为空返回 null
   */
  public static String encodeByMD5(String originString) {
    return StringTools.isEmpty(originString) ? null : DigestUtils.md5Hex(originString);
  }

  // 取文件后缀（含点，如 ".jpg"）
  public static String getFileSuffix(String fileName) {
    return fileName.substring(fileName.lastIndexOf("."));
  }

  /**
   * 路径安全检查：禁止包含 ../ 或 ..\（防止目录穿越攻击）
   * 安全返回 true
   */
  public static boolean pathIsOk(String path) {
    if (StringTools.isEmpty(path)) {
      return true;
    }
    return !path.contains("../") && !path.contains("..\\");
  }

  /**
   * 生成群ID：G + 11 位随机数字（如 G12345678901）
   */
  public static String getGroupId() {
    return UserContactTypeEnum.GROUP.getPrefix() + getRandomNumber(CommonConstants.LENGTH_11);
  }

  /**
   * 生成用户ID：U + 11 位随机数字（如 U12345678901）
   */
  public static String getUserId() {
    return UserContactTypeEnum.USER.getPrefix() + getRandomNumber(CommonConstants.LENGTH_11);
  }

  /**
   * 生成单聊会话ID：先把两个用户ID排序（保证谁在前都一样），
   * 拼接后取 MD5。这样 A找B 和 B找A 得到的是同一个会话ID
   */
  public static String getChatSessionId4User(String[] userIds) {
    Arrays.sort(userIds);
    return encodeByMD5(StringUtils.join(userIds, ""));
  }

  /**
   * 生成群聊会话ID：群ID 取 MD5
   */
  public static String getChatSessionId4Group(String groupId) {
    return encodeByMD5(groupId);
  }

  /**
   * 清理 HTML 标签：把 < 转义，换行转成 <br>
   * 防止用户输入的内容被当成 HTML 执行（XSS 防护）
   */
  public static String cleanHtmlTag(String content) {
    if (isEmpty(content)) {
      return content;
    }
    content = content.replace("<", "&lt;");
    content = content.replace("\r\n", "<br>");
    content = content.replace("\n", "<br>");
    return content;
  }

  // 重置消息内容（目前就是清理 HTML）
  public static String resetMessageContent(String content) {
    return cleanHtmlTag(content);
  }
}
