---
name: redis
description: 设计或修改 MKCS 的 Redis 状态，包括 Sa-Token、验证码、OAuth 状态和分片上传短期状态；不用于 MySQL 或向量检索。
metadata:
  short-description: MKCS Redis 短期状态
---

# MKCS Redis 状态

## 适用范围

Redis 只承载 Sa-Token 会话及可过期的运行时状态：邮箱验证码、OAuth2 state、限流/防刷和分片上传进度。关系元数据、权限真相和文件内容分别属于 MySQL 与 MinIO。

## Key 与 TTL

- Sa-Token 的内部键由框架和现有 `MkcsSaTokenDao` 管理；不得手写、迁移或套用其他项目的键前缀。
- 应用自有键使用 `mkcs:<domain>:<identifier>`，目前域限定为 `verify-code`、`oauth-state`、`upload` 和后续明确的 `rate-limit`。新增域先搜索现有定义。
- 所有非会话键必须有显式 TTL，且 TTL 与业务有效期一致。验证码和 OAuth state 必须一次性消费或删除；完成/取消/过期上传必须清理状态与临时分片。
- key 不放密码、JWT、OAuth code、完整邮箱、文件名或完整路径。需要关联时优先使用内部 ID、哈希或经过编码的稳定标识。

```java
String key = "mkcs:oauth-state:" + stateId;
redisTemplate.opsForValue().set(key, userId.toString(), Duration.ofMinutes(10));

String consumed = redisTemplate.opsForValue().getAndDelete(key);
if (consumed == null) {
    throw new BusinessException(ResultCode.UNAUTHORIZED);
}
```

```text
Good: mkcs:upload:01J...:uploaded-chunks     TTL: 24h
Bad:  codeagent:agent:run-42                 wrong project namespace
Bad:  mkcs:verify-code:user@example.com      exposes personal data in a key
Bad:  SET mkcs:oauth-state:abc value         no expiry
```

## 一致性与并发

- Redis 是验证码、state 与上传协调状态的加速层，不是文件权限或内容元数据的最终来源；重启、过期或缓存丢失时 Service 应按业务语义安全失败或从 MySQL 重建。
- 校验码的读取并删除、上传分片进度更新、限流计数等需要原子语义时，使用 Redis 原子命令、Lua 或现有封装，不能拆成有竞争窗口的多次读写。
- 不缓存可变权限结论而不设计失效策略。缓存值必须定义序列化格式、TTL、删除时机和版本兼容性。
- 禁止 `KEYS`、无界 SCAN 结果和按用户全量枚举作为在线请求路径；清理依赖 TTL 或受限前缀扫描。

```java
// Bad: separate read/delete permits a verification-code replay race.
String code = redisTemplate.opsForValue().get(key);
redisTemplate.delete(key);
```

## 配置与验证

连接配置使用现有 `mkcs.redis.*` 与 `MKCS_REDIS_PASSWORD`，不新增平行 URI/host 配置。变更需验证 TTL、重复消费、过期状态、并发上传或验证码重放，以及敏感值不进入日志。
