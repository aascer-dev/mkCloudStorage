---
name: minio-storage
description: 实现或审查 MKCS 的 MinIO/S3 对象存储、流式上传下载、分片、去重和清理；用于文件内容或对象生命周期改动。
metadata:
  short-description: MKCS 对象存储与上传
---

# MKCS MinIO 对象存储

## 存储边界

MinIO 保存对象内容与临时分片，MySQL 保存文件、内容哈希、逻辑桶、上传任务和权限元数据，Redis 保存短期上传协调状态。使用现有 `MinIOUtil`、`MinIOProperties` 和 S3-compatible 客户端，不新增并行存储 SDK 或绕过现有配置。

## 上传与命名

- 对象键由服务端生成，使用稳定的内部 ID/内容哈希和明确的临时命名空间；不得直接把用户文件名、路径或客户端上传 ID 当对象键。
- 普通上传和分片上传以流方式处理，禁止将整个文件读入内存。限制来自 Spring multipart 配置和业务规则必须一致。
- 每个分片校验索引、大小和哈希；完成前确认分片集合连续且只接受一次完成。取消、失败和过期任务必须清理 Redis 状态与 MinIO 临时对象。
- 内容去重以服务端计算/核验的 SHA-256 为准。秒传还必须执行现有随机位置校验；客户端声明的哈希不是可信事实。

```java
// Good: the service constructs an internal temporary object key.
String objectName = uploadId + "/" + chunkIndex;
minIOUtil.upload(chunk, "chunks", objectName);

// Bad: a client filename or path becomes an object key.
// minIOUtil.upload(chunk, "chunks", originalFilename);
```

```java
// Cancellation clears temporary objects and short-lived coordination state.
for (Integer chunkIndex : uploadedChunks) {
    minIOUtil.deleteObject("chunks", uploadId + "/" + chunkIndex);
}
redisTemplate.delete("mkcs:upload:" + uploadId);
```

## 一致性与下载

- 对象写入与 MySQL 元数据不是单一分布式事务。定义顺序、失败补偿和可重试行为，避免数据库指向不存在对象或孤儿对象长期积累。
- 内容引用计数、去重关联与删除必须处理并发；只在最后一个引用释放后才候选删除物理对象。
- 下载按权限验证后以流式响应输出，设置安全的内容类型/Content-Disposition，防止路径穿越和响应头注入。日志只记录内部 ID、字节数、hash 摘要和状态。

## 验证

至少验证空/超限文件、重复内容、分片乱序/重复/缺失、取消清理、对象写入失败补偿、未授权下载、删除最后引用和随机校验失败。
