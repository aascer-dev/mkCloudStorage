---
name: rabbitmq
description: 实现或审查 MKCS 的 RabbitMQ 验证码邮件消息、重试、幂等消费和死信处理；用于异步邮件链路改动。
metadata:
  short-description: MKCS RabbitMQ 邮件消息
---

# MKCS RabbitMQ 邮件消息

## 范围

当前 RabbitMQ 用于验证码邮件异步发送。连接与 listener 基础配置沿用 `RabbitMQConfiguration` 和 `application.yml`；业务状态、验证码 TTL 与消费幂等需与 `redis`、`security-auth` 协作。

## 消息契约

- 消息定义稳定版本化的 DTO，只包含发送所需的非敏感字段，例如消息 ID、邮件类型、接收方引用和模板参数。不能传递密码、JWT、OAuth token 或完整验证码日志副本。
- 生产者在持久化/生成验证码后再发布，明确发布失败行为。消息 ID 用于幂等，消费者必须能安全处理重复投递。
- 由业务配置声明 exchange、queue、routing key 和持久化策略；不要在不同 Service 随意创建近义队列。

## 重试与失败

- 短暂 SMTP/网络故障使用有限次数、退避重试。永久错误和重试耗尽的消息进入死信队列或受控失败处理，不能无限循环。
- 自动确认只在消费真正成功后使用；失败策略必须与现有 listener 设置一致，避免确认后邮件丢失。
- 邮件发送不可逆，至少用消息 ID 去重并记录可检索状态；不得因为消费者重试重复发送验证码邮件。

## 验证

测试序列化兼容性、重复投递、瞬时失败重试、永久失败死信/告警和验证码过期。日志不记录邮件密码、验证码全文或 SMTP 凭据。
