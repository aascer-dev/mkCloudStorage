# GitHub OAuth2 登录使用说明

## 功能概述

本系统实现了 GitHub OAuth2 登录功能，支持用户通过 GitHub 账号快速登录或注册。该实现复用了现有的 `UsersService.register()` 方法，确保代码的高度复用性。

## 核心特性

1. **代码复用**：OAuth2 注册流程完全复用 `UsersService.register()` 方法
2. **自动关联**：首次登录自动创建用户并关联 GitHub 账号
3. **快速登录**：已关联用户可直接通过 GitHub 登录
4. **安全性**：使用 state 参数防止 CSRF 攻击

## 配置步骤

### 1. 在 GitHub 创建 OAuth App

1. 访问 GitHub Settings → Developer settings → OAuth Apps
2. 点击 "New OAuth App"
3. 填写应用信息：
   - Application name: `MK Cloud Storage`
   - Homepage URL: `http://localhost:8080`
   - Authorization callback URL: `http://localhost:8080/api/auth/oauth2/github/callback`
4. 创建后获取 `Client ID` 和 `Client Secret`

### 2. 配置应用

在 `application-dev.yml` 中配置 GitHub OAuth2 参数：

```yaml
oauth2:
  github:
    client-id: your_github_client_id
    client-secret: your_github_client_secret
    redirect-uri: http://localhost:8080/api/auth/oauth2/github/callback
```

### 3. 添加依赖

已在 `pom.xml` 中添加 WebFlux 依赖（用于 WebClient）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

## API 接口

### 1. 获取 GitHub 授权 URL

**请求：**
```http
GET /api/auth/oauth2/github/authorize?state=random_state
```

**响应：**
```json
{
  "code": 200,
  "message": "获取授权链接成功",
  "data": {
    "authUrl": "https://github.com/login/oauth/authorize?client_id=xxx&redirect_uri=xxx&scope=user:email&state=xxx"
  }
}
```

### 2. GitHub OAuth2 回调（POST）

**请求：**
```http
POST /api/auth/oauth2/github/callback
Content-Type: application/json

{
  "code": "github_authorization_code",
  "state": "random_state",
  "rememberMe": true
}
```

**响应：**
```json
{
  "code": 200,
  "message": "GitHub 登录成功",
  "data": {
    "userId": 1,
    "username": "github_user",
    "nickname": "GitHub User",
    "email": "user@example.com",
    "token": "xxx",
    "tokenName": "Authorization",
    "tokenValue": "xxx",
    "isLogin": true,
    "loginId": 1,
    "tokenTimeout": 604800,
    "sessionTimeout": 604800,
    "tokenActiveTimeout": -2,
    "loginDevice": "default-device",
    "tag": null
  }
}
```

### 3. GitHub OAuth2 回调（GET）

用于浏览器重定向方式：

```http
GET /api/auth/oauth2/github/callback?code=xxx&state=xxx&rememberMe=true
```

## 前端集成示例

### Vue 3 示例

```vue
<template>
  <div>
    <button @click="loginWithGitHub">使用 GitHub 登录</button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'

const loginWithGitHub = async () => {
  try {
    // 1. 获取授权 URL
    const state = generateRandomState()
    const response = await axios.get('/api/auth/oauth2/github/authorize', {
      params: { state }
    })
    
    const authUrl = response.data.data.authUrl
    
    // 2. 保存 state 到 sessionStorage（用于回调验证）
    sessionStorage.setItem('oauth_state', state)
    
    // 3. 跳转到 GitHub 授权页面
    window.location.href = authUrl
  } catch (error) {
    console.error('获取授权链接失败', error)
  }
}

const generateRandomState = () => {
  return Math.random().toString(36).substring(2, 15)
}
</script>
```

### 回调页面处理

```vue
<template>
  <div>正在登录...</div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

onMounted(async () => {
  const urlParams = new URLSearchParams(window.location.search)
  const code = urlParams.get('code')
  const state = urlParams.get('state')
  
  // 验证 state
  const savedState = sessionStorage.getItem('oauth_state')
  if (state !== savedState) {
    console.error('State 验证失败')
    router.push('/login')
    return
  }
  
  try {
    // 调用回调接口
    const response = await axios.post('/api/auth/oauth2/github/callback', {
      code,
      state,
      rememberMe: true
    })
    
    // 保存 token
    const token = response.data.data.tokenValue
    localStorage.setItem('token', token)
    
    // 跳转到首页
    router.push('/')
  } catch (error) {
    console.error('GitHub 登录失败', error)
    router.push('/login')
  } finally {
    sessionStorage.removeItem('oauth_state')
  }
})
</script>
```

## 实现原理

### 1. 登录流程

```
用户 → 前端 → 后端 → GitHub → 后端 → 前端 → 用户
  1. 点击登录
  2. 获取授权URL
  3. 跳转到GitHub
  4. 用户授权
  5. GitHub回调
  6. 换取Token
  7. 获取用户信息
  8. 创建/关联用户
  9. 返回登录信息
```

### 2. 代码复用设计

`OAuth2ServiceImpl.registerUserFromOAuth()` 方法通过以下方式复用 `UsersService.register()`：

```java
private Users registerUserFromOAuth(OAuth2UserInfo userInfo) {
    // 1. 构建 RegisterRequest
    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setUsername(generateUniqueUsername(userInfo));
    registerRequest.setPassword(UUID.randomUUID().toString()); // 随机密码
    registerRequest.setEmail(generateUniqueEmail(userInfo));
    registerRequest.setNickname(userInfo.getNickname());
    
    // 2. 调用现有的注册方法（复用所有业务逻辑）
    Result<LoginResponse> registerResult = usersService.register(registerRequest);
    
    // 3. 创建 OAuth 关联
    oauthIdentitiesService.createOrUpdate(user.getId(), "github", 
        userInfo.getIdentifier(), userInfo.getAccessToken());
    
    return user;
}
```

### 3. 数据库设计

使用 `oauth_identities` 表关联 GitHub 账号和本地用户：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 本地用户ID |
| provider | VARCHAR | 平台标识（github） |
| identifier | VARCHAR | GitHub 用户ID |
| credential | TEXT | Access Token |

## 注意事项

1. **邮箱处理**：如果 GitHub 用户未公开邮箱，系统会生成临时邮箱 `github_{id}@oauth.local`
2. **用户名冲突**：如果 GitHub 用户名已存在，会自动添加数字后缀
3. **密码安全**：OAuth 用户的密码为随机生成，无法通过密码登录
4. **Token 存储**：Access Token 存储在 `oauth_identities.credential` 字段，可用于后续 API 调用

## 扩展其他平台

要添加其他 OAuth2 平台（如 Google、微信等），只需：

1. 在 `OAuth2Service` 中添加对应方法
2. 在 `OAuth2ServiceImpl` 中实现具体逻辑
3. 在 `AuthController` 中添加对应端点
4. 在配置文件中添加平台配置

示例：

```java
// OAuth2Service.java
String getGoogleAuthorizationUrl(String state);
Result<LoginResponse> handleGoogleCallback(String code, Boolean rememberMe);

// AuthController.java
@GetMapping("/oauth2/google/authorize")
public Result<Map<String, String>> getGoogleAuthUrl(@RequestParam(required = false) String state) {
    // ...
}
```

## 测试

### 1. 单元测试

```java
@Test
void testGitHubOAuth2Login() {
    // 模拟 GitHub 回调
    String code = "test_code";
    Result<LoginResponse> result = oauth2Service.handleGitHubCallback(code, true);
    
    assertNotNull(result);
    assertEquals(200, result.getCode());
    assertNotNull(result.getData().getToken());
}
```

### 2. 集成测试

使用 Postman 或浏览器测试完整流程：

1. 访问 `/api/auth/oauth2/github/authorize` 获取授权链接
2. 在浏览器中打开授权链接
3. 授权后自动跳转到回调地址
4. 检查是否成功登录并返回 token

## 常见问题

### Q1: 回调地址不匹配

**错误：** `redirect_uri_mismatch`

**解决：** 确保 GitHub OAuth App 配置的回调地址与 `application-dev.yml` 中的 `redirect-uri` 完全一致

### Q2: 获取用户信息失败

**错误：** `401 Unauthorized`

**解决：** 检查 `client-secret` 是否正确配置

### Q3: 邮箱为空

**说明：** GitHub 用户可能未公开邮箱，系统会自动生成临时邮箱

## 总结

本实现通过复用 `UsersService.register()` 方法，实现了高度模块化的 OAuth2 登录功能。核心优势：

- ✅ 代码复用率高
- ✅ 易于扩展其他平台
- ✅ 安全性好
- ✅ 用户体验佳
