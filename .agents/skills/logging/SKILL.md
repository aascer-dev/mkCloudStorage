---
name: logging
description: 新增或修改 MKCS 的 Java、上传、认证、OAuth、邮件和对象存储日志；用于日志级别、关联字段和脱敏设计。
metadata:
  short-description: MKCS 日志与脱敏
---

# MKCS 日志与脱敏

## 基本规则

- 使用 SLF4J/Lombok `@Slf4j` 与参数化日志，禁止 `System.out.println`、字符串拼接和重复记录同一异常。
- `INFO` 记录重要生命周期结果，`DEBUG` 记录可诊断的过程摘要，`WARN` 记录可恢复/可预期的异常情况，`ERROR` 在处理边界记录未处理失败和异常对象。不要用日志替代异常或业务响应。
- 每条日志描述操作、稳定关联 ID、结果和必要的安全摘要。优先用户内部 ID、文件 ID、上传任务 ID、消息 ID、请求 ID、字节数、耗时和哈希短摘要。

```java
@Slf4j
@Service
public class UploadService {

    public void complete(Long userId, String uploadId) {
        log.info("Chunk upload completed, userId={}, uploadId={}", userId, uploadId);
    }

    public void fail(Long userId, String uploadId, Exception exception) {
        log.error("Chunk upload failed, userId={}, uploadId={}", userId, uploadId, exception);
    }
}
```

- 需要记录日志的 Java 类必须优先用 Lombok `@Slf4j`；禁止手写 `LoggerFactory.getLogger`，除非 Lombok 在该类不可用且在类注释说明原因。
- 禁止 `System.out.println`、`System.err.println`、字符串拼接和 `String.format` 日志。CLI 未来如有面向用户的交互输出可以写标准输出，但不得承载诊断日志。
- 同一异常只在 HTTP、消息或任务的最终处理边界完整记录一次；中间层补上下文后抛出，避免重复堆栈。

## 脱敏与禁止项

绝不记录密码、验证码、JWT、Sa-Token、Cookie、Authorization、OAuth code/state/token、client secret、SMTP/Redis/MinIO/MySQL 凭据、完整邮箱、完整对象键、本地路径、文件正文或完整 SQL 参数。

- 上传记录文件 ID、大小、MIME 类型和内容 hash 摘要，不记录原文件名/路径或字节内容。
- OAuth 记录 provider、流程阶段和内部关联 ID，不记录回调 query、token 或 state。
- 邮件记录模板类型、消息 ID 和发送状态，不记录验证码、邮件正文或 SMTP 对话。
- 对象存储记录操作、内部对象引用、字节数和错误类别，不记录 access key、secret 或预签名 URL。

## 异常与审计

- 在最接近 HTTP/消息/任务边界的位置记录一次完整异常；下层要么转译为业务异常，要么向上抛出，避免日志风暴。
- 安全审计记录登录成功/失败、权限拒绝、密码重置和文件访问结论，但不记录认证凭据或可重放数据。
- 大对象仅记录长度、摘要或 hash。生产日志不能依赖 DEBUG 开关来允许敏感内容。

## 验证

检查失败、重试和成功路径，搜索新日志中是否泄漏上述字段，并确认异常仍能映射为统一 API 或消费失败结果。
