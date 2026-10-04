如果**暂时不考虑 OAuth2**，那么方案可以进一步简化。

你的目标变成：

```text
Spring Boot
+
JWT Access Token
+
Redis Refresh Token
+
Sa-Token（可选）
```

而不是：

```text
OAuth2
+
Authorization Server
+
Refresh Token Rotation
+
OIDC
```

这些都可以先不引入。

---

# 推荐的最终架构

```text
浏览器

    │

    ├─ AccessToken（30分钟）
    │
    ▼

Authorization: Bearer xxx

    │
    ▼

JWT验证

    │
    ▼

Controller


────────────────────


浏览器

    │

    ├─ RefreshToken（14天）
    │
    ▼

POST /api/auth/refresh

    │
    ▼

Redis验证

    │
    ▼

签发新的AccessToken
```

---

# 数据库改造

只新增一个字段即可：

```sql
ALTER TABLE users
ADD COLUMN token_version BIGINT NOT NULL DEFAULT 1;
```

作用：

```text
修改密码
强制下线
封号
```

时让所有 JWT 失效。

---

# Redis设计

## Refresh Token

推荐：

```text
mkcs:refresh:{token}
```

Value：

```json
{
  "userId":123,
  "deviceId":"web"
}
```

TTL：

```text
14天
```

---

例如：

```text
mkcs:refresh:f4e2ab3f-xxxx
```

---

# Access Token设计

JWT Payload：

```json
{
  "sub":"123",
  "username":"admin",
  "ver":1,
  "iat":1758090000,
  "exp":1758091800
}
```

建议只放：

```text
userId
username
tokenVersion
```

不要放：

```text
permissions
email
avatar
phone
department
```

这些都应该实时查询。

---

# 登录流程

当前：

```java
StpUtil.login(userId);
```

改成：

```java
tokenService.login(userId);
```

流程：

```text
账号密码验证
↓
查询用户
↓
生成AccessToken
↓
生成RefreshToken
↓
Redis保存RefreshToken
↓
返回
```

返回：

```json
{
  "accessToken":"xxx",
  "refreshToken":"xxx",
  "expiresIn":1800
}
```

---

# 认证过滤器

新增：

```java
JwtAuthenticationFilter
```

流程：

```text
请求进入
↓
Authorization头
↓
解析JWT
↓
验证签名
↓
验证exp
↓
验证tokenVersion
↓
设置当前用户
↓
放行
```

---

# 刷新Token接口

新增：

```http
POST /api/auth/refresh
```

请求：

```json
{
  "refreshToken":"xxxx"
}
```

流程：

```text
Redis查询
↓
不存在
↓
401
```

存在：

```text
删除旧RefreshToken
↓
生成新RefreshToken
↓
生成新AccessToken
↓
写入Redis
↓
返回
```

这就是：

```text
Refresh Token Rotation
```

---

# 退出登录

接口：

```http
POST /api/auth/logout
```

流程：

```text
删除RefreshToken
```

即可。

---

# 修改密码

修改密码成功：

```sql
UPDATE users
SET token_version = token_version + 1
WHERE id = ?
```

效果：

```text
所有旧AccessToken失效
所有旧RefreshToken失效
```

实现时建议：

```text
token_version +1
↓
删除用户全部RefreshToken
```

---

# 多设备登录（后续可做）

第一版甚至可以不做。

Redis：

```text
mkcs:refresh:{token}
```

就够了。

以后需要：

```text
mkcs:user:{userId}:tokens
```

再扩展。

---

# Sa-Token怎么处理？

你现在有两种路线。

## 路线1（推荐）

完全移除认证部分的 Sa-Token：

```text
JWT Access Token
+
Redis Refresh Token
```

自己实现：

```java
@CurrentUser
```

或者：

```java
UserContext.getUserId()
```

获取当前用户。

对于你的 MKCS 这种单体项目，其实足够了。

---

## 路线2

保留 Sa-Token 权限体系：

```text
JWT负责认证
Sa-Token负责鉴权
```

即：

```java
@SaCheckRole
@SaCheckPermission
```

继续使用。

但：

```java
StpUtil.login()
```

不再作为登录核心。

只是权限框架。

---

# 建议的实施顺序

### 第一阶段

新增：

```text
JwtProvider
TokenService
RefreshTokenService
```

先不删除 Sa-Token。

---

### 第二阶段

新增：

```text
JwtAuthenticationFilter
```

让 JWT 接管认证。

---

### 第三阶段

新增：

```text
POST /auth/refresh
POST /auth/logout
```

---

### 第四阶段

增加：

```sql
token_version
```

支持强制失效。

---

### 第五阶段

确认系统稳定后：

```text
删除
StpLogicJwtForSimple
Sa-Token JWT配置
mkcs:login:token:*
mkcs:login:session:*
```

彻底摆脱当前的：

```text
JWT外壳
+
Redis Session
```

模式。

这样最终架构会非常清晰：

```text
Access Token
(30分钟 JWT)

↓

无状态认证


Refresh Token
(14天 Redis)

↓

续期


token_version

↓

强制失效
```

对于你当前的 MKCS 文件存储项目，这是一个比引入完整 OAuth2 更轻量、维护成本更低、也更容易向微服务演进的方案。
