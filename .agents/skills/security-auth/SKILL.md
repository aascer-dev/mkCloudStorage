---
name: security-auth
description: 实现或审查 MKCS 的 Sa-Token、JWT、RBAC、OAuth2、密码和 CORS；用于认证、授权或跨域改动。
metadata:
  short-description: MKCS 认证与授权
---

# MKCS 认证与授权

## 边界

本 Skill 管理身份、会话、权限和外部登录。接口形状见 `api-contract`，Redis 短期状态见 `redis`，配置密钥见 `configuration`。

## Sa-Token 与 RBAC

- 认证使用现有 Sa-Token 配置和 JWT token style；不得同时引入 Spring Security 会话/JWT 链路。
- 登录、登出、续期和认证失败统一走现有 Sa-Token/全局异常处理，避免 Controller 自行拼装不一致响应。
- 授权基于既有 users、roles、permissions、user_roles、role_permissions 数据模型；在 Service 层对资源所有者、分享和 ACL 做业务校验，不能仅依赖前端隐藏按钮。
- 新接口应明确公开、仅登录或所需权限。文件 ID、分享码和用户 ID 都是不可信输入，必须验证归属和可见性。

## 密码与 OAuth2

- 密码使用现有 BCrypt 配置散列，绝不记录、回显或可逆加密密码。重置密码须消费验证码并使旧会话按既有策略失效。
- GitHub OAuth2 的 `state` 必须难猜、绑定发起上下文、短时过期并一次性消费；回调严格验证 state，再使用允许的前端回调地址。
- client secret、JWT 签名密钥、OAuth code 和 token 只能来自配置，不能出现在 URL、响应、异常或日志中。

## CORS 与接口错误

- CORS 仅允许明确的开发/部署前端 origin、方法和请求头；携带凭据时不能使用 `*` origin。预检请求必须在认证拦截之前可通过。
- 区分未登录（401）与已登录但无权限（403），并映射为项目统一响应。不得把内部权限规则、堆栈或 token 细节返回给客户端。

```java
// Service-layer resource authorization: client IDs are never trusted.
Files file = filesService.getById(fileId);
Assert.notNull(file, ResultCode.FILE_NOT_FOUND);
Assert.isTrue(file.getOwnerId().equals(currentUserId), ResultCode.FORBIDDEN);
```

```java
// Bad: exposes authentication internals to callers.
// return Result.error(401, "Authentication failed: " + exception.getMessage());
```

```text
Development CORS origin: http://localhost:5173
Bad with credentials: Access-Control-Allow-Origin: *
```

## 验证

覆盖未登录、权限不足、资源越权、合法角色、预检、OAuth state 重放/过期及密码错误路径。测试使用伪造凭据，不使用真实密钥或账号。
