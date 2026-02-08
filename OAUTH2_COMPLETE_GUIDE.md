# OAuth2 完整使用说明

## 📋 目录

- [概述](#概述)
- [功能特性](#功能特性)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 接口文档](#api-接口文档)
- [业务流程](#业务流程)
- [前端集成指南](#前端集成指南)
- [数据库设计](#数据库设计)
- [安全机制](#安全机制)
- [错误处理](#错误处理)
- [常见问题](#常见问题)
- [扩展开发](#扩展开发)

---

## 概述

本系统实现了完整的 OAuth2 第三方登录功能，目前支持 GitHub 登录，并可轻松扩展到其他平台（Google、微信、微博等）。

### 核心特点

- ✅ **完整的注册流程** - 新用户首次登录需完成邮箱绑定和用户名选择
- ✅ **快速登录** - 已关联用户直接登录，无需重复注册
- ✅ **邮箱验证** - 支持 GitHub 邮箱直接确认和自定义邮箱验证码验证
- ✅ **账号合并** - 邮箱冲突时可合并到现有账号
- ✅ **CSRF 防护** - 使用 state 参数防止跨站请求伪造攻击
- ✅ **代码复用** - 完全复用现有的用户注册逻辑
- ✅ **头像同步** - 自动同步 GitHub 头像
- ✅ **安全可靠** - 密码加密、Token 管理、数据过期机制

---

## 功能特性

### 1. 用户登录流程


#### 现有用户登录
```
用户点击 GitHub 登录 → 授权 → 自动登录 ✓
```

#### 新用户注册
```
用户点击 GitHub 登录 → 授权 → 邮箱绑定 → 用户名选择 → 完成注册并登录 ✓
```

### 2. 邮箱绑定机制

- **GitHub 提供邮箱**：用户可直接确认使用，无需验证码
- **自定义邮箱**：需要发送验证码进行验证
- **邮箱冲突处理**：检测到邮箱已存在时，引导用户进行账号合并

### 3. 账号合并功能

当检测到邮箱已被其他账号使用时：
1. 提示用户该邮箱已注册
2. 引导用户通过账号恢复流程验证身份
3. 验证成功后将 OAuth 身份关联到现有账号
4. 自动登录到合并后的账号

---

## 系统架构

### 技术栈

- **后端框架**: Spring Boot 3.x
- **认证框架**: Sa-Token
- **HTTP 客户端**: WebFlux WebClient
- **缓存**: Redis（存储临时数据、验证码、state）
- **数据库**: MySQL（存储用户和 OAuth 关联）
- **邮件服务**: Spring Mail

### 模块结构

```
mkcs-model/          # 数据模型层
├── dto/
│   ├── OAuth2CallbackRequest.java      # OAuth2 回调请求
│   └── OAuth2UserInfo.java             # OAuth2 用户信息
├── entity/
│   ├── Users.java                      # 用户实体
│   └── OauthIdentities.java           # OAuth 关联实体
└── vo/
    └── LoginResponse.java              # 登录响应

mkcs-server/         # 服务层
├── controller/
│   └── AuthController.java             # 认证控制器（含 OAuth2 端点）
├── service/
│   ├── OAuth2Service.java              # OAuth2 服务接口
│   ├── OAuth2ServiceImpl.java          # OAuth2 服务实现
│   ├── OauthIdentitiesService.java     # OAuth 关联服务
│   └── UsersService.java               # 用户服务
└── config/
    └── WebClientConfiguration.java     # WebClient 配置
```

---

## 快速开始

### 前置条件

1. Java 17+
2. MySQL 8.0+
3. Redis 6.0+
4. Maven 3.6+

### 步骤 1：创建 GitHub OAuth App


1. 访问 [GitHub Developer Settings](https://github.com/settings/developers)
2. 点击 **OAuth Apps** → **New OAuth App**
3. 填写应用信息：
   ```
   Application name: MK Cloud Storage
   Homepage URL: http://localhost:8080
   Authorization callback URL: http://localhost:8080/api/auth/oauth2/github/callback
   ```
4. 创建后获取 **Client ID** 和 **Client Secret**

### 步骤 2：配置应用

编辑 `mkcs-server/src/main/resources/application-dev.yml`：

```yaml
oauth2:
  github:
    client-id: 你的_GitHub_Client_ID
    client-secret: 你的_GitHub_Client_Secret
    redirect-uri: http://localhost:8080/api/auth/oauth2/github/callback
  frontend-callback-url: /oauth2-demo.html  # 前端回调页面
```

### 步骤 3：启动应用

```bash
# 构建项目
mvn clean install

# 启动服务
cd mkcs-server
mvn spring-boot:run
```

### 步骤 4：测试

访问示例页面：`http://localhost:8080/oauth2-demo.html`

---

## 配置说明

### OAuth2 配置项

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `oauth2.github.client-id` | GitHub OAuth App 的 Client ID | `Ov23liw6JgXE6Hsyi8h2` |
| `oauth2.github.client-secret` | GitHub OAuth App 的 Client Secret | `061ce4865e27c6b77e28480ac05b36cd8c23d687` |
| `oauth2.github.redirect-uri` | GitHub 授权回调地址 | `http://localhost:8080/api/auth/oauth2/github/callback` |
| `oauth2.frontend-callback-url` | 前端回调页面 URL | `/oauth2-demo.html` 或 `http://localhost:5173/oauth/callback` |

### Redis 配置

OAuth2 使用 Redis 存储以下临时数据：

| Key 格式 | 说明 | 过期时间 |
|----------|------|----------|
| `oauth2:state:{state}` | CSRF 防护的 state 参数 | 10 分钟 |
| `oauth2:temp:{tempUserId}` | 临时 OAuth 用户数据 | 30 分钟 |
| `oauth2:verify:{tempUserId}` | 邮箱验证码 | 10 分钟 |

---

## API 接口文档

### 基础 URL

```
http://your-domain/api/auth
```

### 1. 获取 GitHub 授权 URL

**接口**: `GET /oauth2/github/authorize`

**描述**: 获取 GitHub OAuth2 授权链接

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| state | string | 否 | CSRF 防护参数（不传则自动生成） |

**响应示例**:

```json
{
  "code": 200,
  "message": "获取授权链接成功",
  "data": {
    "authUrl": "https://github.com/login/oauth/authorize?client_id=xxx&redirect_uri=xxx&scope=user:email&state=uuid"
  },
  "timestamp": 1234567890
}
```


**使用示例**:

```javascript
// 前端调用
const response = await fetch('/api/auth/oauth2/github/authorize');
const result = await response.json();
window.location.href = result.data.authUrl;
```

---

### 2. GitHub 回调处理（POST）

**接口**: `POST /oauth2/github/callback`

**描述**: 处理 GitHub 授权回调（JSON 方式）

**请求参数**:

```json
{
  "code": "GitHub返回的授权码",
  "state": "CSRF防护参数",
  "rememberMe": true
}
```

**响应示例 - 登录成功（现有用户）**:

```json
{
  "code": 200,
  "message": "GitHub 登录成功",
  "data": {
    "id": 123,
    "username": "johndoe",
    "nickname": "John Doe",
    "email": "user@example.com",
    "avatarUrl": "https://avatars.githubusercontent.com/u/123",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "rememberMe": true
  },
  "timestamp": 1234567890
}
```

**响应示例 - 需要注册（新用户）**:

```json
{
  "code": 200,
  "message": "需要完成注册",
  "data": {
    "requiresRegistration": true,
    "tempUserId": "uuid-here",
    "suggestedEmail": "user@example.com",
    "suggestedUsername": "johndoe",
    "hasGitHubEmail": true
  },
  "timestamp": 1234567890
}
```

---

### 3. GitHub 回调处理（GET）

**接口**: `GET /oauth2/github/callback`

**描述**: 处理 GitHub 授权回调（浏览器重定向方式）

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | string | 是 | GitHub 返回的授权码 |
| state | string | 否 | CSRF 防护参数 |
| rememberMe | boolean | 否 | 是否记住登录（默认 false） |

**响应**: 自动重定向到前端页面

**重定向 URL 示例 - 登录成功**:
```
/oauth2-demo.html?success=true&token=xxx&userId=123&username=johndoe
```

**重定向 URL 示例 - 需要注册**:
```
/oauth2-demo.html?requiresRegistration=true&tempUserId=uuid&suggestedEmail=user@example.com&suggestedUsername=johndoe&hasGitHubEmail=true
```

**重定向 URL 示例 - 登录失败**:
```
/oauth2-demo.html?success=false&error=错误信息
```

---

### 4. 发送邮箱验证码

**接口**: `POST /oauth2/send-verification-code`

**描述**: 为自定义邮箱发送 6 位数验证码

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| email | string | 是 | 要验证的邮箱地址 |

**请求示例**:

```javascript
const response = await fetch('/api/auth/oauth2/send-verification-code', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    email: 'user@example.com'
  })
});
```

**响应示例**:

```json
{
  "code": 200,
  "message": "验证码已发送",
  "data": null,
  "timestamp": 1234567890
}
```


---

### 5. 验证邮箱

**接口**: `POST /oauth2/verify-email`

**描述**: 验证邮箱（支持 GitHub 邮箱直接确认或自定义邮箱验证码验证）

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| email | string | 是 | 邮箱地址 |
| code | string | 否 | 验证码（自定义邮箱必填） |
| isGitHubEmail | boolean | 否 | 是否为 GitHub 邮箱（默认 false） |

**情况 1: 确认 GitHub 邮箱（无需验证码）**

```javascript
const response = await fetch('/api/auth/oauth2/verify-email', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    email: 'user@example.com',
    isGitHubEmail: 'true'
  })
});
```

**情况 2: 验证自定义邮箱（需要验证码）**

```javascript
const response = await fetch('/api/auth/oauth2/verify-email', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    email: 'user@example.com',
    code: '123456'
  })
});
```

**成功响应**:

```json
{
  "code": 200,
  "message": "邮箱验证成功",
  "data": null,
  "timestamp": 1234567890
}
```

---

### 6. 完成注册

**接口**: `POST /oauth2/complete-registration`

**描述**: 完成 OAuth2 用户注册（用户名选择后）

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| username | string | 是 | 用户选择的用户名 |
| rememberMe | boolean | 否 | 是否记住登录（默认 false） |

**请求示例**:

```javascript
const response = await fetch('/api/auth/oauth2/complete-registration', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    username: 'johndoe',
    rememberMe: 'true'
  })
});
```

**成功响应**:

```json
{
  "code": 200,
  "message": "注册并登录成功",
  "data": {
    "id": 123,
    "username": "johndoe",
    "nickname": "John Doe",
    "email": "user@example.com",
    "avatarUrl": "https://avatars.githubusercontent.com/u/123",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "rememberMe": true
  },
  "timestamp": 1234567890
}
```

---

### 7. 发起账号合并

**接口**: `POST /oauth2/initiate-merge`

**描述**: 检测到邮箱冲突时，发起账号合并流程

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| email | string | 是 | 冲突的邮箱地址 |

**响应示例**:

```json
{
  "code": 200,
  "message": "需要账号合并",
  "data": {
    "requiresMerge": true,
    "existingUserId": 456,
    "existingUsername": "existinguser",
    "message": "检测到该邮箱已注册，请通过账号恢复流程验证身份后合并账号"
  },
  "timestamp": 1234567890
}
```


---

### 8. 完成账号合并

**接口**: `POST /oauth2/complete-merge`

**描述**: 在用户通过账号恢复验证后，完成账号合并

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| existingUserId | number | 是 | 现有用户 ID |
| rememberMe | boolean | 否 | 是否记住登录（默认 false） |

**成功响应**:

```json
{
  "code": 200,
  "message": "账号合并成功",
  "data": {
    "id": 456,
    "username": "existinguser",
    "nickname": "Existing User",
    "email": "user@example.com",
    "avatarUrl": "https://avatars.githubusercontent.com/u/456",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "rememberMe": true
  },
  "timestamp": 1234567890
}
```

---

## 业务流程

### 完整流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                     用户点击 GitHub 登录                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. 前端调用 GET /oauth2/github/authorize 获取授权 URL           │
│     - 后端生成 state 参数并存储到 Redis（10分钟过期）             │
│     - 返回 GitHub 授权链接                                        │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. 前端跳转到 GitHub 授权页面                                    │
│     - 用户在 GitHub 上授权应用                                    │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. GitHub 回调到后端 GET /oauth2/github/callback               │
│     - 携带 code 和 state 参数                                    │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. 后端处理回调                                                  │
│     - 验证 state 参数（CSRF 防护）                                │
│     - 使用 code 换取 access_token                                │
│     - 使用 access_token 获取 GitHub 用户信息                      │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
                    是否已关联？
                    ┌───┴───┐
                    │       │
                   是       否
                    │       │
                    ▼       ▼
        ┌──────────────┐  ┌──────────────────────────────────┐
        │  直接登录     │  │  需要注册                         │
        │  - 更新头像   │  │  - 生成 tempUserId                │
        │  - 返回 token │  │  - 存储临时数据到 Redis（30分钟） │
        └──────────────┘  │  - 重定向到邮箱绑定页面            │
                          └────────────┬─────────────────────┘
                                       │
                                       ▼
                          ┌────────────────────────────┐
                          │  5. 邮箱绑定页面            │
                          │  - GitHub 提供邮箱？        │
                          └────────┬───────────────────┘
                                   │
                          ┌────────┴────────┐
                          │                 │
                         是                否
                          │                 │
                          ▼                 ▼
            ┌──────────────────┐  ┌──────────────────┐
            │ 用户确认使用？    │  │ 用户输入邮箱      │
            └────┬─────────────┘  └────┬─────────────┘
                 │                      │
            ┌────┴────┐                 ▼
            │         │        ┌──────────────────┐
           是        否        │ 发送验证码         │
            │         │        │ POST /send-code   │
            ▼         │        └────┬─────────────┘
  ┌──────────────┐   │             │
  │ 直接验证      │   │             ▼
  │ POST /verify │   │    ┌──────────────────┐
  │ isGitHub=true│   │    │ 输入验证码         │
  └──────┬───────┘   │    │ POST /verify      │
         │           │    │ code=123456       │
         └───────────┴────┴────┬─────────────┘
                                │
                                ▼
                    ┌────────────────────────┐
                    │  6. 检查邮箱冲突        │
                    └────────┬───────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                  无冲突            有冲突
                    │                 │
                    ▼                 ▼
        ┌──────────────────┐  ┌──────────────────┐
        │ 7. 用户名选择页面 │  │ 提示账号合并      │
        │ - 显示建议用户名  │  │ POST /initiate   │
        │ - 用户输入/修改   │  │ - 引导账号恢复    │
        └────────┬─────────┘  └────┬─────────────┘
                 │                  │
                 ▼                  ▼
    ┌──────────────────────┐  ┌──────────────────┐
    │ 8. 完成注册           │  │ 完成合并          │
    │ POST /complete-reg   │  │ POST /complete-  │
    │ - 创建用户            │  │ merge            │
    │ - 创建 OAuth 关联     │  │ - 关联 OAuth     │
    │ - 自动登录            │  │ - 自动登录        │
    └──────────┬───────────┘  └────┬─────────────┘
               │                    │
               └────────┬───────────┘
                        │
                        ▼
            ┌────────────────────────┐
            │  登录成功，跳转到主页   │
            └────────────────────────┘
```


### 关键流程说明

#### 1. State 参数验证（CSRF 防护）

```java
// 生成 state
String state = UUID.randomUUID().toString();
redisTemplate.opsForValue().set("oauth2:state:" + state, System.currentTimeMillis(), 10, TimeUnit.MINUTES);

// 验证 state
String key = "oauth2:state:" + state;
Long createdAt = (Long) redisTemplate.opsForValue().get(key);
if (createdAt == null) {
    throw new RuntimeException("Invalid or expired state");
}
redisTemplate.delete(key); // 验证后立即删除，防止重放攻击
```

#### 2. 临时数据存储

```java
// 存储临时 OAuth 数据
String tempUserId = UUID.randomUUID().toString();
Map<String, String> data = new HashMap<>();
data.put("provider", "github");
data.put("identifier", userInfo.getIdentifier());
data.put("username", userInfo.getUsername());
data.put("email", userInfo.getEmail());
data.put("avatarUrl", userInfo.getAvatarUrl());
data.put("accessToken", userInfo.getAccessToken());

redisTemplate.opsForHash().putAll("oauth2:temp:" + tempUserId, data);
redisTemplate.expire("oauth2:temp:" + tempUserId, 30, TimeUnit.MINUTES);
```

#### 3. 邮箱验证码

```java
// 生成并发送验证码
String code = String.format("%06d", new Random().nextInt(999999));
Map<String, String> verifyData = new HashMap<>();
verifyData.put("code", code);
verifyData.put("email", email);

redisTemplate.opsForHash().putAll("oauth2:verify:" + tempUserId, verifyData);
redisTemplate.expire("oauth2:verify:" + tempUserId, 10, TimeUnit.MINUTES);

emailService.sendVerificationCode(email, code, "OAuth2注册");
```

#### 4. 用户注册（代码复用）

```java
// 构建注册请求
RegisterRequest registerRequest = new RegisterRequest();
registerRequest.setUsername(username);
registerRequest.setPassword(passwordEncoder.encode(generateSecurePassword()));
registerRequest.setEmail(email);
registerRequest.setNickname(oauthData.get("nickname"));

// 复用现有注册方法
Result<LoginResponse> registerResult = usersService.register(registerRequest);

// 创建 OAuth 关联
oauthIdentitiesService.createOrUpdate(
    user.getId(), 
    "github", 
    oauthData.get("identifier"), 
    oauthData.get("accessToken")
);
```

---

## 前端集成指南

### Vue 3 完整示例

```vue
<template>
  <div class="oauth-login">
    <!-- 登录按钮 -->
    <button @click="loginWithGitHub" class="github-btn">
      <img src="/github-icon.svg" alt="GitHub" />
      使用 GitHub 登录
    </button>

    <!-- 邮箱绑定页面 -->
    <div v-if="showEmailBinding" class="email-binding">
      <h2>绑定邮箱</h2>
      
      <!-- GitHub 邮箱确认 -->
      <div v-if="hasGitHubEmail" class="github-email">
        <p>GitHub 已验证邮箱：{{ suggestedEmail }}</p>
        <button @click="confirmGitHubEmail">确认使用此邮箱</button>
        <button @click="useCustomEmail">使用其他邮箱</button>
      </div>

      <!-- 自定义邮箱输入 -->
      <div v-else class="custom-email">
        <input v-model="customEmail" type="email" placeholder="请输入邮箱" />
        <button @click="sendVerificationCode" :disabled="countdown > 0">
          {{ countdown > 0 ? `${countdown}秒后重新发送` : '发送验证码' }}
        </button>
        
        <input v-model="verificationCode" type="text" placeholder="请输入验证码" />
        <button @click="verifyEmail">验证邮箱</button>
      </div>
    </div>

    <!-- 用户名选择页面 -->
    <div v-if="showUsernameSelection" class="username-selection">
      <h2>选择用户名</h2>
      <input v-model="username" type="text" placeholder="请输入用户名" />
      <p class="hint" :class="usernameHintClass">{{ usernameHint }}</p>
      <button @click="completeRegistration">完成注册</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

// 状态管理
const showEmailBinding = ref(false);
const showUsernameSelection = ref(false);
const tempUserId = ref('');
const suggestedEmail = ref('');
const hasGitHubEmail = ref(false);
const customEmail = ref('');
const verificationCode = ref('');
const username = ref('');
const countdown = ref(0);
const usernameHint = ref('');
const usernameHintClass = ref('');

// 1. GitHub 登录
const loginWithGitHub = async () => {
  try {
    const response = await fetch('/api/auth/oauth2/github/authorize');
    const result = await response.json();
    
    if (result.code === 200) {
      // 跳转到 GitHub 授权页面
      window.location.href = result.data.authUrl;
    }
  } catch (error) {
    console.error('获取授权链接失败', error);
  }
};

// 2. 处理回调（在回调页面中调用）
const handleCallback = () => {
  const params = new URLSearchParams(window.location.search);
  
  if (params.get('success') === 'true') {
    // 登录成功
    const token = params.get('token');
    localStorage.setItem('token', token);
    router.push('/home');
    
  } else if (params.get('requiresRegistration') === 'true') {
    // 需要注册
    tempUserId.value = params.get('tempUserId');
    suggestedEmail.value = params.get('suggestedEmail');
    hasGitHubEmail.value = params.get('hasGitHubEmail') === 'true';
    showEmailBinding.value = true;
  }
};

// 3. 确认 GitHub 邮箱
const confirmGitHubEmail = async () => {
  try {
    const response = await fetch('/api/auth/oauth2/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        tempUserId: tempUserId.value,
        email: suggestedEmail.value,
        isGitHubEmail: 'true'
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      showEmailBinding.value = false;
      showUsernameSelection.value = true;
    }
  } catch (error) {
    console.error('邮箱验证失败', error);
  }
};

// 4. 使用自定义邮箱
const useCustomEmail = () => {
  hasGitHubEmail.value = false;
};

// 5. 发送验证码
const sendVerificationCode = async () => {
  try {
    const response = await fetch('/api/auth/oauth2/send-verification-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        tempUserId: tempUserId.value,
        email: customEmail.value
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      // 开始倒计时
      countdown.value = 60;
      const timer = setInterval(() => {
        countdown.value--;
        if (countdown.value === 0) {
          clearInterval(timer);
        }
      }, 1000);
    } else {
      alert(result.message);
    }
  } catch (error) {
    console.error('发送验证码失败', error);
  }
};

// 6. 验证邮箱
const verifyEmail = async () => {
  try {
    const response = await fetch('/api/auth/oauth2/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        tempUserId: tempUserId.value,
        email: customEmail.value,
        code: verificationCode.value
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      showEmailBinding.value = false;
      showUsernameSelection.value = true;
    } else {
      alert(result.message);
    }
  } catch (error) {
    console.error('邮箱验证失败', error);
  }
};

// 7. 用户名实时验证
watch(username, (newValue) => {
  const regex = /^[a-zA-Z0-9_-]{3,32}$/;
  if (!regex.test(newValue)) {
    usernameHint.value = '用户名长度3-32字符，只能包含字母、数字、下划线和连字符';
    usernameHintClass.value = 'error';
  } else {
    usernameHint.value = '用户名格式正确';
    usernameHintClass.value = 'success';
  }
});

// 8. 完成注册
const completeRegistration = async () => {
  try {
    const response = await fetch('/api/auth/oauth2/complete-registration', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        tempUserId: tempUserId.value,
        username: username.value,
        rememberMe: 'true'
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      localStorage.setItem('token', result.data.token);
      alert('注册成功！');
      router.push('/home');
    } else {
      alert(result.message);
    }
  } catch (error) {
    console.error('注册失败', error);
  }
};
</script>

<style scoped>
.github-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: #24292e;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.hint.error {
  color: red;
}

.hint.success {
  color: green;
}
</style>
```


### React 完整示例

```jsx
import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

function OAuth2Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  
  const [showEmailBinding, setShowEmailBinding] = useState(false);
  const [showUsernameSelection, setShowUsernameSelection] = useState(false);
  const [tempUserId, setTempUserId] = useState('');
  const [suggestedEmail, setSuggestedEmail] = useState('');
  const [hasGitHubEmail, setHasGitHubEmail] = useState(false);
  const [customEmail, setCustomEmail] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [username, setUsername] = useState('');
  const [countdown, setCountdown] = useState(0);

  // 处理回调
  useEffect(() => {
    if (searchParams.get('success') === 'true') {
      const token = searchParams.get('token');
      localStorage.setItem('token', token);
      navigate('/home');
    } else if (searchParams.get('requiresRegistration') === 'true') {
      setTempUserId(searchParams.get('tempUserId'));
      setSuggestedEmail(searchParams.get('suggestedEmail'));
      setHasGitHubEmail(searchParams.get('hasGitHubEmail') === 'true');
      setShowEmailBinding(true);
    }
  }, [searchParams, navigate]);

  // GitHub 登录
  const loginWithGitHub = async () => {
    const response = await fetch('/api/auth/oauth2/github/authorize');
    const result = await response.json();
    if (result.code === 200) {
      window.location.href = result.data.authUrl;
    }
  };

  // 确认 GitHub 邮箱
  const confirmGitHubEmail = async () => {
    const response = await fetch('/api/auth/oauth2/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        tempUserId,
        email: suggestedEmail,
        isGitHubEmail: 'true'
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      setShowEmailBinding(false);
      setShowUsernameSelection(true);
    }
  };

  // 发送验证码
  const sendVerificationCode = async () => {
    const response = await fetch('/api/auth/oauth2/send-verification-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ tempUserId, email: customEmail })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      setCountdown(60);
      const timer = setInterval(() => {
        setCountdown(prev => {
          if (prev <= 1) {
            clearInterval(timer);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
  };

  // 验证邮箱
  const verifyEmail = async () => {
    const response = await fetch('/api/auth/oauth2/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        tempUserId,
        email: customEmail,
        code: verificationCode
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      setShowEmailBinding(false);
      setShowUsernameSelection(true);
    }
  };

  // 完成注册
  const completeRegistration = async () => {
    const response = await fetch('/api/auth/oauth2/complete-registration', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        tempUserId,
        username,
        rememberMe: 'true'
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      localStorage.setItem('token', result.data.token);
      navigate('/home');
    }
  };

  return (
    <div className="oauth-login">
      {!showEmailBinding && !showUsernameSelection && (
        <button onClick={loginWithGitHub} className="github-btn">
          使用 GitHub 登录
        </button>
      )}

      {showEmailBinding && (
        <div className="email-binding">
          <h2>绑定邮箱</h2>
          {hasGitHubEmail ? (
            <div>
              <p>GitHub 已验证邮箱：{suggestedEmail}</p>
              <button onClick={confirmGitHubEmail}>确认使用此邮箱</button>
              <button onClick={() => setHasGitHubEmail(false)}>使用其他邮箱</button>
            </div>
          ) : (
            <div>
              <input
                type="email"
                value={customEmail}
                onChange={(e) => setCustomEmail(e.target.value)}
                placeholder="请输入邮箱"
              />
              <button onClick={sendVerificationCode} disabled={countdown > 0}>
                {countdown > 0 ? `${countdown}秒后重新发送` : '发送验证码'}
              </button>
              <input
                type="text"
                value={verificationCode}
                onChange={(e) => setVerificationCode(e.target.value)}
                placeholder="请输入验证码"
              />
              <button onClick={verifyEmail}>验证邮箱</button>
            </div>
          )}
        </div>
      )}

      {showUsernameSelection && (
        <div className="username-selection">
          <h2>选择用户名</h2>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="请输入用户名"
          />
          <button onClick={completeRegistration}>完成注册</button>
        </div>
      )}
    </div>
  );
}

export default OAuth2Login;
```

---

## 数据库设计

### oauth_identities 表

```sql
CREATE TABLE `oauth_identities` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '关联本地用户ID',
  `provider` VARCHAR(50) NOT NULL COMMENT '平台标识：github, google 等',
  `identifier` VARCHAR(255) NOT NULL COMMENT '第三方平台的唯一 ID',
  `credential` TEXT COMMENT '可选，存储 AccessToken 或额外信息',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_identifier` (`provider`, `identifier`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_oauth_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方身份关联表';
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 关联的本地用户 ID |
| provider | VARCHAR(50) | 平台标识（github、google 等） |
| identifier | VARCHAR(255) | 第三方平台的唯一 ID（如 GitHub 用户 ID） |
| credential | TEXT | 存储 AccessToken 或其他凭证信息 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 索引说明

- **主键索引**: `id`
- **唯一索引**: `(provider, identifier)` - 确保同一平台的同一用户只能关联一次
- **普通索引**: `user_id` - 加速根据用户查询 OAuth 关联

---

## 安全机制

### 1. CSRF 防护

使用 state 参数防止跨站请求伪造攻击：

```java
// 生成随机 state
String state = UUID.randomUUID().toString();

// 存储到 Redis（10分钟过期）
redisTemplate.opsForValue().set("oauth2:state:" + state, System.currentTimeMillis(), 10, TimeUnit.MINUTES);

// 验证时检查并删除
String key = "oauth2:state:" + state;
Long createdAt = (Long) redisTemplate.opsForValue().get(key);
if (createdAt == null) {
    throw new RuntimeException("Invalid or expired state");
}
redisTemplate.delete(key); // 验证后立即删除
```

### 2. 数据过期机制

| 数据类型 | 过期时间 | 说明 |
|----------|----------|------|
| State 参数 | 10 分钟 | 防止 CSRF 攻击 |
| 临时 OAuth 数据 | 30 分钟 | 用户注册流程的临时数据 |
| 邮箱验证码 | 10 分钟 | 邮箱验证码有效期 |

### 3. 密码安全

OAuth 用户的密码为随机生成的 UUID，经过 BCrypt 加密：

```java
String defaultPassword = UUID.randomUUID().toString() + UUID.randomUUID().toString();
String encryptedPassword = passwordEncoder.encode(defaultPassword);
```

### 4. Token 管理

使用 Sa-Token 进行 Token 管理：

```java
// 登录时设置 Token 有效期
boolean remember = Boolean.TRUE.equals(rememberMe);
StpUtil.login(user.getId(), remember ? 14 * 24 * 60 * 60 : 6 * 60 * 60);

// 记住我：14 天
// 不记住：6 小时
```

### 5. 邮箱验证

- GitHub 提供的邮箱已经过 GitHub 验证，可直接使用
- 自定义邮箱需要通过验证码验证
- 验证码为 6 位随机数字，10 分钟内有效


---

## 错误处理

### 错误码参考

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 2101 | OAuth2 state 参数无效 | 提示用户重新登录 |
| 2102 | OAuth2 临时数据已过期 | 提示用户重新登录（超过30分钟） |
| 2103 | 该 OAuth 账号已绑定到其他用户 | 提示用户联系管理员 |
| 2104 | 邮箱格式不正确 | 提示用户输入正确的邮箱格式 |
| 2105 | 邮箱未验证 | 提示用户先验证邮箱 |
| 2106 | GitHub 未提供邮箱 | 提示用户手动输入邮箱 |
| 2107 | 验证码已过期 | 提示用户重新发送验证码 |
| 2108 | 验证码不正确 | 提示用户检查验证码 |
| 2109 | 验证码发送失败 | 提示用户稍后重试 |
| 1105 | 用户名已存在 | 提示用户选择其他用户名 |
| 1106 | 邮箱已被使用 | 提示用户是否为现有账号（触发合并流程） |
| 1001 | 用户不存在 | 系统错误，联系管理员 |
| 1002 | 用户已被禁用 | 提示用户账号已被禁用 |

### 错误处理示例

```javascript
// 统一错误处理函数
const handleError = (result) => {
  const errorMessages = {
    2101: '登录已过期，请重新登录',
    2102: '操作超时，请重新登录',
    2103: '该 GitHub 账号已绑定到其他用户',
    2104: '邮箱格式不正确',
    2105: '请先验证邮箱',
    2106: 'GitHub 未提供邮箱，请手动输入',
    2107: '验证码已过期，请重新发送',
    2108: '验证码不正确',
    2109: '验证码发送失败，请稍后重试',
    1105: '用户名已存在，请选择其他用户名',
    1106: '该邮箱已被使用',
    1001: '用户不存在',
    1002: '账号已被禁用'
  };

  const message = errorMessages[result.code] || result.message || '操作失败';
  
  // 特殊处理邮箱冲突
  if (result.code === 1106) {
    return {
      type: 'email_conflict',
      message: message
    };
  }
  
  return {
    type: 'error',
    message: message
  };
};

// 使用示例
const response = await fetch('/api/auth/oauth2/verify-email', {
  method: 'POST',
  body: formData
});

const result = await response.json();

if (result.code !== 200) {
  const error = handleError(result);
  
  if (error.type === 'email_conflict') {
    // 处理邮箱冲突，引导用户合并账号
    showMergeDialog();
  } else {
    // 显示错误消息
    alert(error.message);
  }
}
```

---

## 常见问题

### Q1: GitHub 未提供邮箱怎么办？

**A**: 如果 GitHub 用户未公开邮箱，系统会要求用户手动输入邮箱并通过验证码验证。

### Q2: 用户名冲突怎么处理？

**A**: 系统会提示用户名已存在，用户需要选择其他用户名。前端可以在用户输入时实时检查用户名可用性。

### Q3: 邮箱冲突如何处理？

**A**: 系统会检测邮箱是否已被其他账号使用，如果是，会引导用户进行账号合并流程：
1. 提示用户该邮箱已注册
2. 引导用户通过账号恢复流程验证身份
3. 验证成功后将 OAuth 身份关联到现有账号

### Q4: OAuth 用户如何修改密码？

**A**: OAuth 用户的初始密码是随机生成的，用户可以通过"忘记密码"功能重置密码。

### Q5: 一个用户可以关联多个 OAuth 账号吗？

**A**: 可以。一个用户可以关联多个不同平台的 OAuth 账号（如同时关联 GitHub 和 Google）。

### Q6: 如何取消 OAuth 关联？

**A**: 需要在用户设置中添加"解除绑定"功能，调用 `OauthIdentitiesService` 删除关联记录。

### Q7: 验证码收不到怎么办？

**A**: 
1. 检查邮箱地址是否正确
2. 检查垃圾邮件文件夹
3. 等待 60 秒后重新发送
4. 检查邮件服务配置是否正确

### Q8: 临时数据过期了怎么办？

**A**: 临时数据（tempUserId）有 30 分钟有效期，如果过期需要重新开始 OAuth 登录流程。

### Q9: 如何在生产环境部署？

**A**: 
1. 修改 GitHub OAuth App 的回调地址为生产域名
2. 更新 `application.yml` 中的 `redirect-uri` 和 `frontend-callback-url`
3. 确保 HTTPS 已启用
4. 配置正确的邮件服务

### Q10: 如何添加其他 OAuth 平台（如 Google）？

**A**: 参考下面的"扩展开发"章节。

---

## 扩展开发

### 添加 Google OAuth2 登录

#### 1. 创建 Google OAuth2 应用

1. 访问 [Google Cloud Console](https://console.cloud.google.com/)
2. 创建项目并启用 Google+ API
3. 创建 OAuth 2.0 客户端 ID
4. 获取 Client ID 和 Client Secret

#### 2. 添加配置

```yaml
oauth2:
  google:
    client-id: your_google_client_id
    client-secret: your_google_client_secret
    redirect-uri: http://localhost:8080/api/auth/oauth2/google/callback
```

#### 3. 扩展 OAuth2Service

```java
public interface OAuth2Service {
    // 现有 GitHub 方法
    String getGitHubAuthorizationUrl(String state);
    Result<?> handleGitHubCallback(String code, String state, Boolean rememberMe);
    
    // 新增 Google 方法
    String getGoogleAuthorizationUrl(String state);
    Result<?> handleGoogleCallback(String code, String state, Boolean rememberMe);
    OAuth2UserInfo getGoogleUserInfo(String code);
}
```

#### 4. 实现 Google OAuth2

```java
@Service
public class OAuth2ServiceImpl implements OAuth2Service {
    
    @Value("${oauth2.google.client-id:}")
    private String googleClientId;
    
    @Value("${oauth2.google.client-secret:}")
    private String googleClientSecret;
    
    @Value("${oauth2.google.redirect-uri:}")
    private String googleRedirectUri;
    
    private static final String GOOGLE_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_API_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    
    @Override
    public String getGoogleAuthorizationUrl(String state) {
        if (state == null || state.trim().isEmpty()) {
            state = generateAndStoreState();
        }
        
        return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=openid email profile&state=%s",
                GOOGLE_AUTHORIZE_URL, googleClientId, googleRedirectUri, state);
    }
    
    @Override
    public Result<?> handleGoogleCallback(String code, String state, Boolean rememberMe) {
        // 验证 state
        if (state != null && !validateAndRemoveState(state)) {
            return Result.error(ResultCode.OAUTH_STATE_INVALID);
        }
        
        // 获取用户信息
        OAuth2UserInfo userInfo = getGoogleUserInfo(code);
        
        // 查询是否已关联
        OauthIdentities oauthIdentity = oauthIdentitiesService.getByProviderAndIdentifier("google", userInfo.getIdentifier());
        
        if (oauthIdentity != null) {
            // 已关联，直接登录
            Users user = usersService.getById(oauthIdentity.getUserId());
            // ... 登录逻辑
        } else {
            // 未关联，需要注册
            String tempUserId = storeTempOAuthData(userInfo);
            // ... 返回注册信息
        }
    }
    
    @Override
    public OAuth2UserInfo getGoogleUserInfo(String code) {
        // 1. 换取 access_token
        String accessToken = exchangeGoogleCodeForToken(code);
        
        // 2. 获取用户信息
        WebClient webClient = webClientBuilder.build();
        String userJson = webClient.get()
                .uri(GOOGLE_USER_API_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        
        JsonNode userNode = objectMapper.readTree(userJson);
        
        OAuth2UserInfo userInfo = new OAuth2UserInfo();
        userInfo.setIdentifier(userNode.get("id").asText());
        userInfo.setUsername(userNode.get("email").asText().split("@")[0]);
        userInfo.setNickname(userNode.get("name").asText());
        userInfo.setEmail(userNode.get("email").asText());
        userInfo.setAvatarUrl(userNode.get("picture").asText());
        userInfo.setAccessToken(accessToken);
        
        return userInfo;
    }
    
    private String exchangeGoogleCodeForToken(String code) {
        // 实现 Google Token 交换逻辑
        // ...
    }
}
```

#### 5. 添加控制器端点

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @GetMapping("/oauth2/google/authorize")
    public Result<Map<String, String>> getGoogleAuthUrl(@RequestParam(required = false) String state) {
        String authUrl = oauth2Service.getGoogleAuthorizationUrl(state);
        Map<String, String> data = new HashMap<>();
        data.put("authUrl", authUrl);
        return Result.success("获取授权链接成功", data);
    }
    
    @PostMapping("/oauth2/google/callback")
    public Result<?> handleGoogleCallback(@Valid @RequestBody OAuth2CallbackRequest request) {
        return oauth2Service.handleGoogleCallback(request.getCode(), request.getState(), request.getRememberMe());
    }
    
    @GetMapping("/oauth2/google/callback")
    public void handleGoogleCallbackGet(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, defaultValue = "false") Boolean rememberMe,
            HttpServletResponse response) throws IOException {
        // 实现重定向逻辑
        // ...
    }
}
```

### 添加微信 OAuth2 登录

微信 OAuth2 的实现类似，主要区别：

1. **授权 URL**: `https://open.weixin.qq.com/connect/qrconnect`
2. **Token URL**: `https://api.weixin.qq.com/sns/oauth2/access_token`
3. **用户信息 URL**: `https://api.weixin.qq.com/sns/userinfo`
4. **特殊处理**: 微信需要 `appid` 和 `appsecret`，返回的用户信息格式不同

---

## 性能优化

### 1. Redis 连接池配置

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms
```

### 2. WebClient 连接池

```java
@Configuration
public class WebClientConfiguration {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn -> 
                    conn.addHandlerLast(new ReadTimeoutHandler(5))
                        .addHandlerLast(new WriteTimeoutHandler(5)));
        
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
```

### 3. 异步邮件发送

```java
@Async
public void sendVerificationCodeAsync(String email, String code, String type) {
    emailService.sendVerificationCode(email, code, type);
}
```

---

## 监控和日志

### 关键日志点

```java
// 1. OAuth 授权开始
log.info("生成并存储 OAuth2 state: {}", state);

// 2. 获取用户信息成功
log.info("获取 GitHub 用户信息成功: githubId={}, username={}", userInfo.getIdentifier(), userInfo.getUsername());

// 3. 登录成功
log.info("GitHub OAuth2 登录成功: userId={}, githubId={}", user.getId(), userInfo.getIdentifier());

// 4. 注册流程
log.info("GitHub OAuth2 新用户，需要完成注册: tempUserId={}, githubId={}", tempUserId, userInfo.getIdentifier());

// 5. 邮箱验证
log.info("发送邮箱验证码成功: tempUserId={}, email={}", tempUserId, email);
log.info("邮箱验证成功: tempUserId={}, email={}", tempUserId, email);

// 6. 完成注册
log.info("OAuth2 注册完成: userId={}, username={}, email={}", user.getId(), username, email);

// 7. 账号合并
log.info("账号合并成功: userId={}, provider={}, identifier={}", existingUserId, provider, identifier);

// 8. 错误日志
log.error("Invalid state parameter, possible CSRF attack");
log.error("获取 GitHub 用户信息失败", e);
```

### 监控指标

建议监控以下指标：

1. **OAuth 登录成功率**: 成功登录数 / 总登录尝试数
2. **注册完成率**: 完成注册数 / 开始注册数
3. **邮箱验证成功率**: 验证成功数 / 发送验证码数
4. **平均注册时长**: 从开始到完成注册的平均时间
5. **错误率**: 各类错误的发生频率

---

## 总结

本 OAuth2 系统实现了完整的第三方登录功能，具有以下特点：

✅ **功能完整** - 支持新用户注册、现有用户登录、账号合并
✅ **安全可靠** - CSRF 防护、数据加密、Token 管理
✅ **用户友好** - 流程清晰、错误提示明确
✅ **易于扩展** - 可轻松添加其他 OAuth 平台
✅ **代码复用** - 充分复用现有的用户注册逻辑

如有问题或需要帮助，请参考本文档或联系开发团队。

---

**文档版本**: 1.0  
**最后更新**: 2026-02-08  
**维护者**: 开发团队
