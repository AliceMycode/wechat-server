package com.wechat.www.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 应用自定义配置：读取 application.yml 里的自定义项
 * （ws.port / project.folder / admin.emails）
 */
@Component
public class AppConfig {
  @Getter
  @Value("${ws.port:0}")
  private Integer wsPort;                 // WebSocket 端口（Netty 启动时读）

  @Value("${project.folder:}")
  private String projectFolder;           // 文件存储根目录

  @Getter
  @Value("${admin.emails:}")
  private String adminEmails;             // 管理员邮箱（逗号分隔）

  // 保证根目录末尾带 "/"，拼路径时不会漏斜杠
  public String getProjectFolder() {
    if (projectFolder != null && !projectFolder.isBlank() && !projectFolder.endsWith("/")) {
      projectFolder = projectFolder + "/";
    }
    return projectFolder;
  }

}
