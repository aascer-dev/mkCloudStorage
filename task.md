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
2. 复核分片初始化 DTO：`ChunkUploadRequest` 同时要求 `uploadId`、分片索引、分片哈希和分片大小，但初始化接口并不使用这些字段；拆分初始化与分片上传请求模型，避免客户端提交无关必填字段。
3. 完成秒传随机位置校验的端到端测试：执行数据库迁移，验证首次上传记录校验信息、相同文件秒传、篡改文件不能秒传。现有 `test_random_position_checksum.sh` 和 `.bat` 可作为人工验证起点。
4. 根据根目录 `sql/` 的脚本和目标数据库结构，补足从空库启动所需的建表与版本化迁移策略，并验证 RBAC 与文件去重字段。
5. 完善 Sa-Token 全局异常和 CORS 配置，清理 `SaTokenExceptionHandler` 中的待办，并覆盖未登录、无权限和预检请求。
6. 为认证、权限、上传、去重、分片与迁移增加可重复的自动化测试，然后运行完整 Maven 测试套件。

## 验证状态

- `mvn -pl mkcs-server -am test -DskipTests` 已通过：主代码与测试源码均可编译，测试执行因参数显式跳过。
- Maven Wrapper 已补回必需的 `.mvn/wrapper/maven-wrapper.properties`；仍需在可联网或已有 Maven 分发包的环境中验证其首次下载行为。
- 本次未启动 MySQL、Redis、MinIO、RabbitMQ 或邮件服务，核心流程仍需在完整本地依赖环境中执行集成测试。
- `application.yml` 和 `application-dev.yml` 的敏感配置已改为环境变量引用；之前暴露过的本地凭据和密钥必须轮换。
- 根目录 `sql/` 是当前唯一数据库脚本来源。该目录包含运行数据导出，导入前需核验目标环境并妥善保管其中的敏感字段。
- 根目录的 `test_random_position_checksum.sh`、`test_random_position_checksum.bat` 和 `verify_jwt_token.sh` 是功能校验辅助脚本，不作为冗余文件删除。
