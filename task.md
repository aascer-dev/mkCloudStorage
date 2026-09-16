# 当前任务与进度

最后盘点：2026-09-16。本文以当前工作区的代码和 Git 变更为准，不将未执行的运行测试标记为已验证。

## 已具备的基础能力

- 三模块 Maven 工程已建立：`mkcs-common`、`mkcs-model`、`mkcs-server`。
- 用户注册、账号密码登录、Sa-Token 会话、RBAC、邮箱验证码、用户资料/头像与 GitHub OAuth2 相关实现已在代码中存在。
- 文件元数据、内容去重、逻辑存储桶、上传任务/分片、收藏、分享和文件权限的数据模型、服务层与 API 基础已具备。
- 文件接口覆盖普通上传、秒传检查、分片初始化/上传/完成/取消、文件夹创建、查询、删除、重命名、移动与搜索。

## 本轮正在收口的功能

- [x] 普通上传、分片上传、断点续传和文件管理 API 已加入 `FilesController` 与 `FilesServiceImpl`。
- [x] 内容去重与秒传已接入文件上传流程。
- [x] 随机位置校验字段已加入 `file_contents` 实体。
- [x] 分片上传接口已预留 `randomOffset`、`randomLength`、`randomHash` 参数。
- [x] 数据库脚本入口已收敛至根目录 `sql/`，`mkcs-server/src/main/resources/db/migration/` 的重复历史脚本已移除。
- [x] 密码重置 DTO、服务逻辑和认证端点已添加。
- [x] 存储桶摘要接口与相关 DTO 已添加。
- [x] 分散的历史专题说明文档已从当前工作区删除；项目说明统一保留在 `README.md`、`target.md` 和本文件中。

## 下一步优先级

1. [x] 密码重置端点已返回统一成功响应，限制 `RESET_PASSWORD` 验证码类型，重置后使已有会话失效，并覆盖参数校验、验证码失败和成功路径测试。
2. [x] 分片初始化与分片上传请求已拆分为 `ChunkUploadInitRequest` 和 `ChunkPartUploadRequest`；初始化不再要求上传任务或分片字段，单分片 multipart 元数据使用专用 DTO 并完成接口测试。
3. [x] 秒传随机位置校验端到端验证已完成：新增 `sql/V20260916_001__add_file_content_random_checksum.sql`，从空库和已有本地库重复执行迁移；`FileUploadRandomChecksumIntegrationTest` 已验证首次上传写入校验信息、相同文件秒传并增加引用计数、篡改文件不能秒传。
4. [x] 已完善根目录 `sql/` 的空库与增量迁移策略：`mkCloudStorage.sql` 作为可重建基线，`V20260916_002__seed_default_user_role.sql` 为已有库补齐 `ROLE_USER`；注册改为按角色名查询，不再依赖固定角色 ID。已在临时空库导入基线、重复执行补丁，并验证 RBAC 外键、`ROLE_USER` 与文件去重/随机校验字段。
5. [x] Sa-Token 全局异常与 CORS 已完善：认证过滤器仅保护非认证 `/api/**` 路径，未登录返回 HTTP 401 标准响应，权限/角色不足返回 HTTP 403 且不暴露内部标识；CORS 仅允许配置的 Origin 并放行预检。已移除待办和标准输出日志，关闭 Sa-Token 的配置/框架日志打印。
6. [x] 已为认证、权限、上传、去重、分片与迁移补充可重复自动化测试：认证与 CORS 覆盖未登录、预检和权限错误映射；RBAC 测试改为隔离的 Mapper 单元测试；上传覆盖随机校验去重集成、分片任务缺失/越权/重复/缺片、取消清理和越权下载。迁移已按根目录 SQL 的空库与已有库路径验证，并完成完整 Maven 测试套件。

## 验证状态

- `mvn -B -pl mkcs-server -am -Dtest=FilesControllerChunkRequestTest -Dsurefire.failIfNoSpecifiedTests=false test` 已通过：3 个分片请求 DTO/接口绑定测试成功。
- `mvn -B -pl mkcs-server -am -Dtest=FileUploadRandomChecksumIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` 已通过：1 个 MySQL 与 MinIO 集成测试成功；迁移已在空库和本地已有库重复执行验证。
- `mvn -B test` 已通过：Reactor 共 29 个测试，0 个失败、0 个错误；其中秒传集成测试使用本地 MySQL 与 MinIO，并在结束后清理测试数据和对象。
- 空库与已有库迁移验证已通过：临时空库导入 `sql/mkCloudStorage.sql` 后有 14 张表、4 个去重/随机校验字段、`uk_content_hash`、`ROLE_USER` 和 4 条 RBAC 外键；`V20260916_002__seed_default_user_role.sql` 已在本地开发库重复执行验证。
- `mvn -B -pl mkcs-server -am -Dtest=SecurityWebIntegrationTest,GlobalExceptionHandlerSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test` 已通过：4 个测试覆盖未登录 401、允许/拒绝 Origin 的预检和不泄露权限标识的 403 响应；启动日志未出现完整 Sa-Token 配置字段。
- Maven Wrapper 已补回必需的 `.mvn/wrapper/maven-wrapper.properties`；仍需在可联网或已有 Maven 分发包的环境中验证其首次下载行为。
- Redis、RabbitMQ 与邮件发送链路尚未纳入本轮自动化集成测试；其连接与行为仍需在对应功能变更时单独验证。
- `application.yml` 和 `application-dev.yml` 的敏感配置已改为环境变量引用；之前暴露过的本地凭据和密钥必须轮换。
- 根目录 `sql/` 是当前唯一数据库脚本来源。该目录包含运行数据导出，导入前需核验目标环境并妥善保管其中的敏感字段。
- 根目录的 `test_random_position_checksum.sh`、`test_random_position_checksum.bat` 和 `verify_jwt_token.sh` 是功能校验辅助脚本，不作为冗余文件删除。
