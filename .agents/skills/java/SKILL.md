---
name: java
description: 编写或重构 MKCS 的 Java 类型、DTO/VO、Service 实现、异常和可读性；仅用于 Java 实现层代码。
metadata:
  short-description: MKCS Java 实现规范
---

# MKCS Java 实现规范

## 类型与模块

- Entity、DTO、VO 位于 `mkcs-model`；跨模块的统一结果、异常和工具位于 `mkcs-common`；Web 与业务实现在 `mkcs-server`。
- DTO 表达入参，VO 表达出参，Entity 表达持久化模型。不要复用 Entity 接收外部请求或返回密码散列、存储对象键等内部字段。
- 使用清晰的领域名称和小而集中的方法。避免通用 `Util` 承载业务流程、`Map<String, Object>` 传递核心状态或静态全局可变状态。

## 校验、异常与空值

- 格式、范围和必填由 DTO 校验注解承担；涉及数据库、权限、对象状态或多字段的业务校验放在 Service。
- 用 `BusinessException` 与既有结果码表达可预期业务失败；不要以 `null`、魔法字符串或吞异常表示失败。
- 公开方法明确空值语义，集合优先返回空集合。不能以 Optional 作为 DTO/Entity 字段或方法参数。

## Lombok 与集合

- 对简单数据类可使用现有 Lombok 模式；不要让 `@Data` 无意生成敏感字段的 `toString`、`equals` 或 API 表现。需要时显式选择 `@Getter` / `@Setter`。
- 使用不可变局部值、泛型集合和标准库；对外部输入设置大小上限，避免把文件或大 payload 完整读入内存。

## 方法与控制流

- 方法只做一个可命名的动作；参数过多或布尔参数难以理解时，优先提取请求对象、值对象或小方法，而不是堆叠分支。
- 正常业务分支使用清晰的 early return 和明确的局部变量。避免三层以上嵌套、复杂三元表达式、难以调试的 Stream 链，以及用反射替代普通代码。
- 不用 `Map<String, Object>` 传递核心业务状态；DTO/VO/领域类型应明确字段与空值语义。
- 不吞掉异常。可预期错误映射为既有 `BusinessException`/结果码；需要保留上下文时包装或向上抛出原始异常原因。

```java
// Good: DTO 校验后的业务失败有稳定语义
if (!bucketService.canWrite(userId, bucketId)) {
    throw new BusinessException(ResultCode.FORBIDDEN);
}
```

## 代码审查清单

- [ ] 类型是否放在正确模块，DTO/VO/Entity 是否未混用？
- [ ] 方法和类是否职责单一，是否存在无意义抽象或静态全局状态？
- [ ] 业务失败是否具有稳定错误语义，而不是 `null`、魔法值或空 catch？
- [ ] 外部流、连接与资源是否正确关闭？
- [ ] 是否避免把文件内容或大 payload 整体载入内存？
- [ ] 是否为边界、状态迁移和失败路径增加了相应测试？

## 异步与资源

- 流、连接和响应体用 try-with-resources 或框架管理关闭。异步任务需定义线程池、超时、取消和异常处理，不能使用无界线程创建。
- 密码、令牌、验证码、OAuth code、邮件凭据与对象存储密钥不得进入字段、异常消息或日志。

## 验证

改动后至少编译相关模块，并为状态转换、边界和错误分支补充测试。风格调整不得夹带业务行为变化。
