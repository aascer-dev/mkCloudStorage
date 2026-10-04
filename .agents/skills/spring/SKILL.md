---
name: spring
description: 开发或重构 MKCS 的 Spring Boot Web、Controller、Service、Mapper 集成、事务、异常和配置装配；不用于 Agent 或 CLI 功能。
metadata:
  short-description: MKCS Spring Boot 分层
---

# MKCS Spring Boot 分层

## 模块与分层

- `mkcs-common` 放统一结果、异常、工具和公共属性；`mkcs-model` 放 Entity、DTO、VO；`mkcs-server` 放 Controller、Service、Mapper、配置与资源。不能倒置依赖或把 Web/持久化实现放入 model。
- Controller 处理 HTTP、校验和响应；Service 处理授权、业务规则、事务和外部协调；Mapper 仅访问 MySQL。Controller 不直接访问 Mapper，Entity 不直接作为 API 响应。
- 复用已有 `Result`、`BusinessException`、全局异常处理、MyBatis-Plus Mapper 和 XML，不并行引入 JPA 或另一套响应/异常体系。

## Web 与错误处理

- REST 端点以 `/api` 为前缀，参数在 DTO 上校验。认证和权限遵循 `security-auth`，接口契约遵循 `api-contract`。
- 预期业务失败抛出统一业务异常；全局处理器映射为稳定结果。不要在 Controller 广泛 `catch (Exception)`、返回 `null` 或泄漏外部异常。
- 文件接口使用流式请求/响应。上传、下载、对象一致性与分片规则由 `minio-storage` 定义。

## 注入、配置与事务

- 优先构造器注入（可用 Lombok `@RequiredArgsConstructor`），避免字段注入和循环依赖。新 Bean 要有单一职责并避免重复装配。
- 多个同域外部配置使用 `@ConfigurationProperties`；密钥和环境差异遵循 `configuration`。
- 事务位于 Service 公共方法。将数据库原子更新置于事务中，但不要把长时间 MinIO I/O、邮件或消息消费包进数据库事务；为跨资源失败定义补偿。

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FilesMapper filesMapper;
    private final MinIOUtil minIOUtil;

    @Transactional(rollbackFor = Exception.class)
    public void updateMetadata(Long fileId) {
        // Keep the database-only atomic state transition here.
    }
}
```

- 不使用字段注入；依赖通过构造器注入并尽可能为 `final`。不得用 `@Lazy`、`ApplicationContext#getBean()` 或扩大扫描范围掩盖循环依赖。
- 不为当前唯一实现的简单 Service 强制创建 `Service` + `ServiceImpl` 双层；但保持现有项目中已建立且被调用的接口模式，不进行无关重构。
- 不滥用 `@Component`、`@Async`、`@Transactional`、AOP 或事件。关键异步任务必须使用明确线程池、超时、取消和错误策略。

## 完成检查

- [ ] Controller 未直接操作 Mapper，未包含复杂业务或宽泛 `catch (Exception)`。
- [ ] Service 的授权、事务与跨资源补偿边界明确。
- [ ] Mapper 只承担数据访问，Entity 未作为外部 API 契约。
- [ ] Bean 无循环依赖，配置和密钥来自现有配置契约。
- [ ] 统一异常、鉴权与相关测试已按实际运行结果验证。

## 基础设施

- Sa-Token、Redis、RabbitMQ、MinIO/S3、WebClient 和 SpringDoc 使用已有配置类与依赖，不为局部功能替换核心组件。
- RabbitMQ 消费者应定义可恢复失败、幂等和死信路径；对象存储和邮件调用需清晰映射超时/失败。

## 验证

按改动覆盖 Bean 装配、Controller 校验、事务回滚、统一异常、权限和涉及的外部边界。没有运行实际依赖时，不能声称集成链路已验证。
