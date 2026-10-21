# LabGuard 实验室管理系统

基于 Spring Boot 的实验室信息管理系统。

## 技术栈

- Java 17
- Spring Boot 4.1.1（Maven）
- Spring MVC + Thymeleaf
- 数据库：H2（默认，可切换 MySQL）
- 文件上传：本地磁盘存储

## 快速开始

```bash
# 1. 准备环境配置
cp .env.example .env        # Windows: copy .env.example .env

# 2. 运行
mvn spring-boot:run
```

访问 http://localhost:8080 ，H2 控制台位于 http://localhost:8080/h2-console
（JDBC URL 与 `DB_URL` 一致，默认 `jdbc:h2:file:./data/labguard`，用户名 `sa`，密码空）。

## 配置说明

项目通过 `.env` 文件管理环境相关配置，由 `com.labguard.config.EnvConfig`
在应用启动前加载。**优先级：操作系统环境变量 > .env > application.properties**。

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `SERVER_PORT` | 服务端口 | `8080` |
| `DB_URL` | 数据库 JDBC URL | `jdbc:h2:file:./data/labguard` |
| `DB_USERNAME` | 数据库用户名 | `sa` |
| `DB_PASSWORD` | 数据库密码 | 空 |
| `UPLOAD_DIR` | 上传文件存储目录 | `uploads` |
| `MAX_FILE_SIZE` | 单文件大小上限 | `5MB` |

## 目录结构与分工

```
src/main/java/com/labguard/
├── LabGuardApplication.java          启动类（D）
├── config/
│   ├── EnvConfig.java                .env 加载（D）
│   ├── GlobalExceptionHandler.java   全局异常处理（D）
│   └── FileStorageConfig.java        文件存储配置（D）
├── util/
│   └── FileStorageUtil.java          图片存储工具（D）
├── controller/                       控制器（成员C）
├── service/                          业务层（成员A、B）
└── model/                            数据模型（成员B）

src/main/resources/
├── application.properties            基础配置（D）
├── schema.sql                        建表 SQL（成员B，启动时自动执行）
└── templates/                        Thymeleaf 页面（成员C）

src/test/java/com/labguard/
└── BaseIntegrationTest.java          集成测试基类（D）
```

## 基础设施使用说明（成员 A / B / C 必读）

- **上传图片**：Controller 中注入 `FileStorageUtil`，调用 `saveImage(MultipartFile)`
  得到 `/uploads/xxx.png` 形式的访问路径（已自动映射为静态资源）；
  删除时调用 `delete(storedPath)`。类型与大小限制由 `labguard.storage.*` 配置。
- **全局异常**：业务代码直接抛出 `IllegalArgumentException`（400）或
  `ResponseStatusException`（自定义状态码）即可，错误响应统一为 JSON：
  `{timestamp, status, error, message, path}`。
- **数据访问**：`spring-boot-starter-jdbc` 已引入，直接使用 `JdbcTemplate`；
  `schema.sql` 每次启动自动执行（`spring.sql.init.mode=always`），建表语句
  请使用 `CREATE TABLE IF NOT EXISTS`。
- **集成测试**：继承 `BaseIntegrationTest`，自动使用隔离的内存数据库与上传目录。

## 常用命令

```bash
mvn test          # 运行测试
mvn package       # 打包（target/labguard-0.0.1-SNAPSHOT.jar）
java -jar target/labguard-0.0.1-SNAPSHOT.jar
```

## 切换 MySQL

1. `pom.xml` 中移除 `h2` 依赖，添加 `com.mysql:mysql-connector-j`；
2. `.env` 中设置 `DB_URL=jdbc:mysql://localhost:3306/labguard`、`DB_USERNAME`、`DB_PASSWORD`；
3. `schema.sql` 调整为 MySQL 方言（成员 B 负责）。
