package com.wechat.www.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.wechat.www.enums.ResponseCodeEnum;
import com.wechat.www.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * JSON 工具类：对象 ↔ JSON 字符串
 * （对应源项目 JsonUtils；源项目用 fastjson 1.x，我们换成项目已引入的 fastjson2）
 */
@Slf4j
public class JsonUtils {

  // 序列化特性：连 null 字段也输出（fastjson2 用 JSONWriter.Feature.WriteNulls）
  private static final JSONWriter.Feature[] FEATURES = {JSONWriter.Feature.WriteNulls};

  // 对象 → JSON 字符串
  public static String convertObj2Json(Object obj) {
    return JSON.toJSONString(obj, FEATURES);
  }

  // JSON 字符串 → 对象
  public static <T> T convertJson2Obj(String json, Class<T> classz) {
    try {
      return JSONObject.parseObject(json, classz);
    } catch (Exception e) {
      log.error("convertJson2Obj异常，json:{}", json);
      throw new BusinessException(ResponseCodeEnum.CODE_601);
    }
  }

  // JSON 数组字符串 → List
  public static <T> List<T> convertJsonArray2List(String json, Class<T> classz) {
    try {
      return JSONArray.parseArray(json, classz);
    } catch (Exception e) {
      log.error("convertJsonArray2List,json:{}", json, e);
      throw new BusinessException(ResponseCodeEnum.CODE_601);
    }
  }
}
