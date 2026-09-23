# wechat-server

微信相关后端服务，基于 **Spring Boot 3.5.10 + JDK 17** 构建。

## 技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| 基础框架 | Spring Boot | 3.5.10 |
| JDK | Java | 17 |
| ORM | MyBatis-Plus | 3.5.17 |
| 数据库 | MySQL | 驱动由父工程管理（mysql-connector-j 9.5.0） |
| 连接池 | Druid | 1.2.28 |
| 缓存 | Redis（Spring Data Redis + Redisson） | Redisson 3.52.0 |
| JSON | fastjson2 | 2.0.65 |
| HTTP 客户端 | OkHttp | 4.12.0 |
| 日志 | Logback | 由父工程管理（1.5.25） |

> 说明：下面未标注版本的依赖由 `spring-boot-starter-parent` 统一管理版本，无需手动指定。

## 依赖说明

### 核心
- **spring-boot-starter**：Spring Boot 核心启动依赖，提供自动装配、基础配置等能力。
- **spring-boot-starter-web**：Web 支持，内嵌 Tomcat + Spring MVC，自动引入 Logback 日志。
- **spring-boot-starter-validation**：参数校验（Jakarta Bean Validation + Hibernate Validator），支持 `@Valid`/`@Validated`。
- **spring-boot-starter-test**：测试支持（JUnit 5、Mockito、AssertJ 等），仅 `test` 作用域。

### 数据访问
- **mybatis-plus-spring-boot3-starter**：MyBatis-Plus 增强 ORM 框架（Spring Boot 3 专用坐标），提供通用 CRUD、分页插件、条件构造器等。
- **mysql-connector-j**：MySQL JDBC 驱动（坐标已由 `mysql:mysql-connector-java` 迁移至 `com.mysql:mysql-connector-j`）。
- **druid-spring-boot-3-starter**：阿里巴巴 Druid 数据库连接池（Spring Boot 3 专用坐标），提供连接池监控、SQL 统计、SQL 防火墙等能力。

### 缓存 / Redis
- **spring-boot-starter-data-redis**：Spring Data Redis 集成，默认使用 Lettuce 客户端。
- **redisson**：Redis 分布式客户端，提供分布式锁、分布式集合、限流等高级能力。

### AOP
- **aspectjweaver**：AspectJ 织入，支持 `@Aspect` 切面编程（日志、权限、事务等横切逻辑）。

### 工具 / 网络
- **okhttp**：轻量 HTTP 客户端，用于调用外部接口。
- **fastjson2**：阿里巴巴 JSON 序列化/反序列化库（fastjson 的继任者，包名 `com.alibaba.fastjson2`）。
- **commons-lang3**：Apache 常用工具类（`StringUtils`、`ObjectUtils` 等）。
- **commons-codec**：编码/解码工具（Base64、MD5、SHA、Hex 等）。
- **commons-io**：IO 操作工具类（文件、流处理）。
- **easy-captcha**：图形验证码生成。
- **netty-all**：高性能异步事件驱动的网络通信框架。

## 日志

- 使用 **Logback**（由 `spring-boot-starter-web` 自动引入，版本由父工程管理）。
- 配置文件：`src/main/resources/logback.xml`。

## 构建

```bash
./mvnw clean package
```

- `skipTests=true`：构建时默认跳过测试。
- 打包插件：`spring-boot-maven-plugin`，可生成可执行 jar。
