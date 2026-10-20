package com.wechat.www.controller;

import com.wechat.www.vo.ResponseVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 版本更新检查（暂时返回无更新，后面再接 app_update 表）
 */
@RestController
@RequestMapping("/update")
public class UpdateController extends BaseController {

  @PostMapping("/checkVersion")
  public ResponseVO<Void> checkVersion(String appVersion) {
    // 没有新版本，返回 null
    return getSuccessResponseVO(null);
  }
}
