---
name: api-contract
description: 设计或修改 MKCS REST API、DTO 校验、统一响应、异常映射、SpringDoc 和文件上传接口；用于 Controller 或公开接口改动。
metadata:
  short-description: MKCS REST API 契约
---

# MKCS REST API 契约

## 范围

公开 API 以 `/api` 为前缀，统一结果使用 `mkcs-common` 的 `Result`、`BusinessException` 和既有全局异常处理。Controller 只做 HTTP 编排和 DTO 转换，业务、权限和事务留在 Service。

## DTO 与响应

- 请求/响应模型放入 `mkcs-model` 的 DTO/VO，不能直接暴露 Entity、密码散列、密钥、内部存储对象键或 ORM 技术字段。
- 在 DTO 上使用 Jakarta Validation，嵌套对象加 `@Valid`；Controller 使用 `@Validated` / `@Valid` 触发校验。跨字段、归属、权限和状态转换由 Service 验证。
- 成功、参数错误、业务错误、未认证、无权限和存储故障使用稳定的项目结果码与统一结构。异常处理不泄漏栈、SQL、路径、token 或外部服务凭据。

## 文件上传与分页

- 上传接口明确 multipart 字段、最大限制、普通/分片模式、hash、幂等标识和失败响应。初始化与上传分片应使用不同 DTO，不能要求初始化请求提交尚不存在的分片字段。
- 文件下载接口明确授权、Content-Type、Content-Disposition 和流式语义；不能通过客户端路径定位对象。
- 列表接口明确分页参数、默认/最大值、稳定排序和空结果格式。

## OpenAPI 与兼容性

- 使用现有 SpringDoc 配置为公开端点维护清晰摘要、参数、响应码和上传媒体类型；文档不得包含真实凭据或内部实现细节。
- 改动已有字段、状态码、认证方式或回调 URL 前评估客户端兼容性。破坏性变更应版本化或先保留兼容路径。

## 验证

覆盖合法请求、字段校验、未知/非法 ID、未认证、无权访问、异常映射和 OpenAPI 可见性；文件接口还覆盖大小限制与分片状态错误。
