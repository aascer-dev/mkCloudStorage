---
name: configuration
description: 管理 MKCS 的 Spring Boot 配置、.env、Profile、环境变量和密钥；仅在新增、修改或排查配置契约时使用。
metadata:
  short-description: MKCS 配置与密钥边界
---

# MKCS 配置与密钥

## 适用范围

处理配置来源、配置绑定、Profile、`.env` 与敏感值。业务实现交给 `spring`，数据语义交给 `database` / `redis`，日志脱敏交给 `logging`。

## 配置来源

项目使用 Spring Boot 原生 Config Data，不引入 dotenv 依赖或自定义 `PropertySourceLoader`：

- `mkcs-server/src/main/resources/application.yml` 通过 `spring.config.import: optional:file:./.env[.properties]` 导入根目录 `.env`。
- `.env` 使用标准 Java properties 语法（`KEY=value`），即使文件扩展名是 `.env`；不能写 `export KEY=...`、shell 插值或 dotenv 专有语法。
- 启动环境变量和命令行参数可覆盖 `.env`；生产环境应使用部署平台的环境变量或密钥管理，而不是复制本地 `.env`。
- 环境差异放入 `application-<profile>.yml`；共享默认值保留在 `application.yml`。不要为同一值同时维护两套键。

## `.env` 契约

- 根目录必须维护 `.env` 与 `.env.example`。`.env` 已由 `.gitignore` 忽略，`.env.example` 必须提交。
- 两个文件的键集合必须一致；`.env.example` 的密钥值为空或安全占位符。
- 新增、改名、删除环境变量时，同步更新两文件、`application*.yml`、绑定类/占位符、测试与相关文档。
- 敏感值包括数据库、Redis、MinIO、RabbitMQ、SMTP、JWT、加密密钥和 OAuth client secret。它们不能进入源码、提交、文档、异常或日志。

当前外部密钥统一采用 `MKCS_` 前缀，例如 `MKCS_DATASOURCE_PASSWORD`、`MKCS_JWT_SECRET_KEY`、`MKCS_MINIO_SECRET_KEY`、`MKCS_RABBITMQ_PASSWORD`、`MKCS_MAIL_PASSWORD` 和 `MKCS_OAUTH2_GITHUB_CLIENT_SECRET`。新增键使用 `UPPER_SNAKE_CASE`，优先复用现有 `mkcs.*` 配置层级。

```dotenv
# .env.example only: placeholders, never local or production values
MKCS_DATASOURCE_PASSWORD=replace-me
MKCS_REDIS_PASSWORD=replace-me
MKCS_JWT_SECRET_KEY=replace-with-at-least-32-characters
MKCS_MINIO_ENDPOINT=http://127.0.0.1:19000
```

```yaml
# application-dev.yml keeps the stable property name, not the secret value.
mkcs:
  redis:
    password: ${MKCS_REDIS_PASSWORD}
  minio:
    endpoint: ${MKCS_MINIO_ENDPOINT}
```

Bad examples: `password: local-password`, `password: ${MKCS_REDIS_PASSWORD:local-password}`, or an OAuth client secret embedded in YAML.

## Spring 绑定

- 有多个同域配置时，用 `@ConfigurationProperties` 绑定到无业务行为的属性类；单个占位符可以沿用现有 YAML 引用。
- 密钥不能有真实默认值。可选 OAuth 配置可为空；启动必需的密钥应在启动时给出清晰失败信息。
- 新配置前搜索已有键，禁止为同一连接或凭据新增近义变量。
- 不把固定业务规则、枚举或非环境参数机械搬进 `.env`。

## 变更验收

检查 `.env.example` 完整但不含真实值，`.env` 未被 Git 跟踪，并验证配置能从 `.env` 或等价环境变量解析。提交前检查差异，不输出任何配置值。
