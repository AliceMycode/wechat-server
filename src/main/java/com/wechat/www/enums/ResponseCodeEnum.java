package com.wechat.www.enums;

import lombok.Getter;

/**
 * 全局业务状态码枚举：全部接口共用这一套码
 * 注意：900/901 等关键值前端写死了逻辑，不能随意改
 */
@Getter
public enum ResponseCodeEnum {
  CODE_200(200, "请求成功"),
  CODE_404(404, "请求地址不存在"),
  CODE_600(600, "请求参数错误"),
  CODE_601(601, "信息已经存在"),
  CODE_602(602, "文件不存在"),
  CODE_500(500, "服务器返回错误，请联系管理员"),
  CODE_901(901, "登录超时"),
  CODE_902(902, "您不是对方的好友，请先向好友发送朋友验证申请"),
  CODE_903(903, "你已经不在群聊，请重新加入群聊");

  private final Integer code;   // 数字状态码
  private final String msg;     // 默认提示语

  // 枚举构造器：定义每个枚举项时把 code 和 msg 传进去
  ResponseCodeEnum(Integer code, String msg) {
    this.code = code;
    this.msg = msg;
  }

}
