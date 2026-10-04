# MK Cloud Storage — 后端服务 (mkcs)

这是 mkCloudStorage 的后端仓库。

基于 Spring Boot 3.5 的多模块云存储后端，提供文件管理、用户认证、权限控制等 RESTful API。

---

## 模块结构

```
mkcs/
├── mkcs-common/        # 公共工具模块（异常、响应封装、加密、MinIO 工具等）
├── mkcs-model/         # 数据模型模块（实体、DTO、VO）
└── mkcs-server/        # 主服务模块（控制器、服务、配置、Mapper）
```

| 模块 | 说明 |
|------|------|
| `mkcs-common` | 统一响应 `Result`、业务异常 `BusinessException`、MinIO 工具、加密工具、RBAC 工具 |
| `mkcs-model` | 数据库实体（12 张表）、请求 DTO、响应 VO |
| `mkcs-server` | Spring Boot 启动类、REST 控制器、业务服务、MyBatis Mapper、配置、安全拦截 |

---

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.5.10 |
| MyBatis-Plus | 3.5.16 |
| Sa-Token | 1.45.0 |
| Druid | 1.2.27 |
| MySQL Connector | 9.1.0 |
| AWS SDK (MinIO) | 2.39.2 |
| SpringDoc OpenAPI | 2.7.0 |
| Redis (Lettuce) | 内嵌于 Spring Boot |
| RabbitMQ | 内嵌于 Spring Boot |

---

## 快速开始

### 前置依赖

- JDK 21
- Maven 3.9+
- MySQL 8.0+
- Redis 7.0+
- MinIO（对象存储）
- RabbitMQ（可选，用于异步邮件）

### 配置

```yaml
# 主要配置位于 application.yml
# 开发环境配置位于 application-dev.yml

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mkcs
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
```

### 数据库初始化

```sql
CREATE DATABASE mkCloudStorage CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mkCloudStorage;
SOURCE sql/mkCloudStorage.sql;
```

应用启动时由 Flyway 自动执行 `mkcs-server/src/main/resources/db/migration/` 中未应用的版本化 SQL。空库会先执行 `V1__initial_schema.sql`；已有库首次接入时记录 V1 基线，再执行后续的幂等增量迁移。执行历史保存在 `flyway_schema_history`，已执行的迁移文件不可修改。`sql/mkCloudStorage.sql` 仅保留为人工查看和恢复用的 schema 快照，不能再作为常规部署入口。

### 编译与运行

```bash
# 编译全部模块
mvn clean install

# 启动服务
mvn spring-boot:run -pl mkcs-server

# 或直接运行 Jar
java -jar mkcs-server/target/mkcs-server-0.0.1-SNAPSHOT.jar
```

服务启动后访问：http://localhost:8080

API 文档（Swagger）：http://localhost:8080/swagger-ui.html

---

## 核心功能

### 用户与认证

| 端点 | 说明 |
|------|------|
| `POST /api/auth/register` | 用户注册（自动创建存储桶） |
| `POST /api/auth/login` | 登录（Sa-Token + Redis） |
| `POST /api/auth/logout` | 登出 |
| `POST /api/auth/oauth2/github` | GitHub OAuth2 登录 |
| `POST /api/auth/verification-code` | 发送邮箱验证码（RabbitMQ → Go → SMTP） |

### 文件管理

| 端点 | 说明 |
|------|------|
| `POST /api/files/upload` | 单文件上传（自动计算 hash，支持秒传） |
| `POST /api/files/check` | 秒传检查（先传 hash，命中则免上传） |
| `POST /api/files/chunk/init` | 初始化分片上传 |
| `POST /api/files/chunk/upload` | 上传单个分片（支持断点续传） |
| `POST /api/files/chunk/complete` | 合并分片 |
| `DELETE /api/files/chunk/cancel` | 取消分片上传 |
| `GET /api/files/download/{fileId}` | 下载文件（MinIO 流式输出） |
| `DELETE /api/files/{fileId}` | 删除文件（软删除） |
| `PUT /api/files/{fileId}/rename` | 重命名 |
| `PUT /api/files/{fileId}/move` | 移动文件 |
| `POST /api/files/folder/create` | 创建文件夹 |
| `POST /api/files/folder/batch-create` | 批量创建文件夹（支持嵌套路径） |
| `GET /api/files/folder/{folderId}/contents` | 获取文件夹内容（分页） |
| `GET /api/files/search` | 搜索文件 |

### 秒传机制

基于 **内容寻址（CAS）** + **引用计数** 的去重方案：

1. 上传时计算文件 SHA256 hash
2. `file_contents` 表全局查询相同 hash
3. 命中则引用计数 +1，**不重复上传**到 MinIO
4. 未命中则上传到 MinIO 共享 `files` 桶，创建新记录

物理存储共享（MinIO `files` 桶），用户隔离通过数据库 `owner_id` 实现。

### 大文件 Multipart 直传

超过 16 MiB 的文件由前端走 `/api/files/multipart/*`：后端只初始化任务、签发每个 Part 的短期 UploadPart URL、查询 MinIO 已上传 Part、完成或中止上传；浏览器直接把二进制写入 MinIO，服务端不再合并临时对象。

部署时应将 `MKCS_MINIO_PUBLIC_ENDPOINT` 配为浏览器可访问且与签名一致的 MinIO 地址。MinIO 必须允许前端 Origin 的 `PUT`、`GET`、`HEAD` 请求，并暴露 `ETag` 响应头；否则浏览器无法取得分片 ETag 来完成上传。未完成 Multipart 上传还应配置 MinIO 生命周期规则自动 Abort，作为应用过期任务清理的兜底。

### 存储桶（逻辑隔离）

每个用户拥有一个逻辑存储桶（`storage_buckets` 表记录），用于组织文件和容量管理。物理文件统一存储在 MinIO 共享 `files` 桶中，不做 MinIO 层面的桶隔离。

### 权限控制（RBAC）

基于 Sa-Token 的角色-权限模型：

- 用户 ↔ 角色（多对多）
- 角色 ↔ 权限（多对多）
- 接口级权限校验：`@SaCheckPermission("file:upload")`

---

## 架构概要

```
 Client (Vue 3)
     │
     ▼
 Spring Boot (mkcs-server)
     ├── Sa-Token 认证/授权
     ├── MyBatis-Plus → MySQL
     ├── Redis (Token / 验证码 / 上传任务)
     ├── MinIO SDK (文件存储)
     └── RabbitMQ → Go 邮件服务 (验证码)
```

---

## 数据库表

| 表 | 说明 |
|----|------|
| `users` | 用户 |
| `roles` | 角色 |
| `permissions` | 权限点 |
| `user_roles` | 用户-角色关联 |
| `role_permissions` | 角色-权限关联 |
| `storage_buckets` | 存储桶（逻辑） |
| `files` | 文件/文件夹元数据 |
| `file_contents` | 文件内容（去重） |
| `upload_tasks` | 上传任务 |
| `upload_task_chunks` | 分片明细 |
| `file_favorites` | 文件收藏 |
| `shares` | 分享记录 |
| `file_permissions` | 文件 ACL |
| `oauth_identities` | OAuth 第三方身份 |

---

## 开发

```bash
# 编译（跳过测试）
mvn clean install -DskipTests

# 仅编译某个模块
mvn compile -pl mkcs-common
mvn compile -pl mkcs-model
mvn compile -pl mkcs-server
```
