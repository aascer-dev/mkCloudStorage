# MKCS 项目 Skills

本目录为 MK Cloud Storage 后端服务提供开发路由。Skill 只定义某类改动应遵守的工程约束，不替代代码、[target.md](../../target.md) 或 [task.md](../../task.md)。

## 使用方式

1. 先阅读 `target.md` 与 `task.md`，确认当前目标、已知风险和验收范围。
2. 只加载本次修改直接涉及的 Skill；跨越多个边界时组合加载。
3. 先检查现有实现、配置和工作区，再做最小必要改动。
4. 完成后运行与变更匹配的编译、测试或集成验证，并如实记录未验证项。

## 职责边界与优先级

Skill 说明某类改动必须遵守的工程规则，代码决定具体实现。规则与已有代码冲突时，先确认代码是否为有意偏离；不得静默按规则改代码，也不得无理由让规则迁就代码。

| Skill | 负责 | 不负责 |
| --- | --- | --- |
| `java` | Java 类型、方法、集合、异常、可读性 | Spring 分层、SQL、Redis、配置、日志、Git |
| `spring` | Controller/Service/Mapper、DI、事务、Web 异常 | Java 语法细节、SQL 结构、密钥、Git |
| `database` | MySQL、Mapper SQL、索引与迁移 | Redis、Spring 装配、Git |
| `redis` | Sa-Token、验证码、OAuth state、上传短期状态 | MySQL Schema、对象存储、Spring 分层 |
| `configuration` | `.env`、Profile、环境变量、配置绑定与密钥 | 业务流程、数据建模、Git |
| `logging` | 日志级别、字段、异常关联与脱敏 | 业务错误码、配置来源、Git |
| `git` | 状态、差异、暂存、提交信息与验证记录 | 代码实现和领域规则 |

冲突裁决顺序：用户当前要求 > [target.md](../../target.md) > 本 README > 当前任务相关 Skill > 官方最佳实践。领域规则只在其所有者 Skill 定义，其他 Skill 仅路由，不复制或互相矛盾。

## 强制工作规则

1. 修改前检查现有实现和工作区，只做当前任务所需的最小改动，保留用户已有修改。
2. 复用现有技术栈、组件、命名与结果契约；不得借任务替换 Spring Boot、MyBatis-Plus、Sa-Token、MinIO、Redis 或 RabbitMQ。
3. Skill 中使用“必须”“禁止”“不得”的条目是强制项。需要例外时，在交付说明中写明规则、技术理由和影响，不能静默跳过。
4. 完成前运行直接相关的验证，检查敏感信息、生成物与无关文件；必要时同步 `task.md`。验证失败必须如实说明，不得标记完成。

## 路由

| 任务信号 | Skill |
| --- | --- |
| Java 类型、DTO/VO、异常和通用实现 | `java` |
| Controller、Service、事务、Bean、Web 异常处理 | `spring` |
| MySQL、MyBatis-Plus、Mapper、SQL、迁移 | `database` |
| Redis、Sa-Token 缓存、验证码与上传状态 | `redis` |
| `.env`、Profile、环境变量、配置绑定和密钥 | `configuration` |
| 新增或修改日志、审计字段、脱敏 | `logging` |
| Git 状态、暂存、提交与验证记录 | `git` |
| 实际前端页面的视觉与交互 | `frontend-design` |
| Sa-Token、JWT、RBAC、OAuth2、CORS | `security-auth` |
| MinIO/S3、上传下载、分片、去重、对象清理 | `minio-storage` |
| RabbitMQ、验证码邮件、重试和幂等消费 | `rabbitmq` |
| Maven、单元测试、集成测试、Testcontainers | `testing` |
| REST API、DTO 校验、统一响应、SpringDoc | `api-contract` |

## 项目约束

- 技术栈固定为 Java 21、Spring Boot 3.5、MyBatis-Plus、MySQL、Redis、Sa-Token、MinIO、RabbitMQ 和 SpringDoc；不因局部需求替换核心组件。
- 模块职责：`mkcs-common` 放公共结果、异常和工具；`mkcs-model` 放实体、DTO、VO；`mkcs-server` 放 Web、业务、Mapper、配置和资源。
- 数据库主键沿用 MyBatis-Plus `ASSIGN_ID`；Mapper 使用 MyBatis-Plus 与 XML，不引入 JPA 约定。
- 逻辑存储桶、文件元数据和权限位于 MySQL；对象内容与临时分片位于 MinIO；会话、验证码和短期上传状态位于 Redis。
- `.env` 必须被忽略，`.env.example` 必须提交；任何真实密钥、密码或令牌不得进入源码、配置、日志或提交信息。

## 维护规则

- 每个 Skill 只管理一个领域，跨领域内容只保留路由。
- Skill 必须有 YAML frontmatter，名称与目录名一致，并说明适用范围与排除范围。
- `target.md` 与 `task.md` 记录产品目标和工作进度；Skill 不重复未来计划或功能清单。
- 提交不是自动动作，只有用户明确要求时才执行 Git 提交或推送。
- Skill 的 YAML frontmatter 必须保留 `name` 与有区分度的 `description`；规则只在一个 Skill 中定义，避免重复维护。
