# GitHub OAuth2 登录 - 快速开始

## 🎯 功能特点

- ✅ **代码复用**：完全复用 `UsersService.register()` 方法
- ✅ **自动注册**：首次登录自动创建用户并关联 GitHub 账号
- ✅ **快速登录**：已关联用户直接登录
- ✅ **安全可靠**：使用 state 参数防止 CSRF 攻击

## 📦 已创建的文件

### 1. 模型层 (mkcs-model)
- `OAuth2CallbackRequest.java` - OAuth2 回调请求 DTO
- `OAuth2UserInfo.java` - OAuth2 用户信息 DTO

### 2. 服务层 (mkcs-server)
- `OAuth2Service.java` - OAuth2 服务接口
- `OAuth2ServiceImpl.java` - OAuth2 服务实现（核心逻辑）
- `OauthIdentitiesService.java` - OAuth 身份关联服务接口
- `OauthIdentitiesServiceImpl.java` - OAuth 身份关联服务实现

### 3. 控制器层
- `AuthController.java` - 添加了 3 个 OAuth2 端点

### 4. 配置
- `WebClientConfiguration.java` - WebClient 配置
- `application-dev.yml` - 添加了 OAuth2 配置项
- `pom.xml` - 添加了 WebFlux 依赖

### 5. 文档和示例
- `OAUTH2_USAGE.md` - 详细使用文档
- `oauth2-demo.html` - 前端示例页面

## 🚀 快速配置（3 步）

### 步骤 1：创建 GitHub OAuth App

1. 访问 https://github.com/settings/developers
2. 点击 "OAuth Apps" → "New OAuth App"
3. 填写信息：
   ```
   Application name: MK Cloud Storage
   Homepage URL: http://localhost:8080
   Authorization callback URL: http://localhost:8080/api/auth/oauth2/github/callback
   ```
4. 创建后获取 `Client ID` 和 `Client Secret`

### 步骤 2：配置应用

编辑 `mkcs-server/src/main/resources/application-dev.yml`：

```yaml
oauth2:
  github:
    client-id: 你的_GitHub_Client_ID
    client-secret: 你的_GitHub_Client_Secret
    redirect-uri: http://localhost:8080/api/auth/oauth2/github/callback
```

### 步骤 3：启动应用

```bash
mvn clean install
cd mkcs-server
mvn spring-boot:run
```

## 🧪 测试

### 方式 1：使用示例页面

1. 启动应用后访问：http://localhost:8080/oauth2-demo.html
2. 点击 "使用 GitHub 登录"
3. 授权后自动完成登录

### 方式 2：使用 API

#### 1. 获取授权 URL
```bash
curl http://localhost:8080/api/auth/oauth2/github/authorize?state=test123
```

响应：
```json
{
  "code": 200,
  "data": {
    "authUrl": "https://github.com/login/oauth/authorize?client_id=xxx&..."
  }
}
```

#### 2. 在浏览器中打开授权 URL 并授权

#### 3. 处理回调（使用回调中的 code）
```bash
curl -X POST http://localhost:8080/api/auth/oauth2/github/callback \
  -H "Content-Type: application/json" \
  -d '{
    "code": "从回调URL中获取的code",
    "state": "test123",
    "rememberMe": true
  }'
```

响应：
```json
{
  "code": 200,
  "message": "GitHub 登录成功",
  "data": {
    "userId": 1,
    "username": "github_user",
    "token": "xxx",
    "tokenValue": "xxx",
    ...
  }
}
```

## 📋 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/oauth2/github/authorize` | 获取 GitHub 授权 URL |
| POST | `/api/auth/oauth2/github/callback` | 处理 GitHub 回调（JSON） |
| GET | `/api/auth/oauth2/github/callback` | 处理 GitHub 回调（浏览器重定向） |

## 🔍 核心实现

### 代码复用设计

`OAuth2ServiceImpl` 通过构建 `RegisterRequest` 来复用 `UsersService.register()`：

```java
private Users registerUserFromOAuth(OAuth2UserInfo userInfo) {
    // 1. 构建注册请求
    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setUsername(generateUniqueUsername(userInfo));
    registerRequest.setPassword(UUID.randomUUID().toString()); // 随机密码
    registerRequest.setEmail(generateUniqueEmail(userInfo));
    registerRequest.setNickname(userInfo.getNickname());
    
    // 2. 复用现有注册方法（包含创建用户、分配角色、创建存储桶等）
    Result<LoginResponse> registerResult = usersService.register(registerRequest);
    
    // 3. 创建 OAuth 关联
    oauthIdentitiesService.createOrUpdate(
        user.getId(), 
        "github", 
        userInfo.getIdentifier(), 
        userInfo.getAccessToken()
    );
    
    return user;
}
```

### 登录流程

```
1. 用户点击 "GitHub 登录"
2. 前端调用 /oauth2/github/authorize 获取授权 URL
3. 跳转到 GitHub 授权页面
4. 用户授权后，GitHub 重定向到回调地址（带 code）
5. 后端使用 code 换取 access_token
6. 使用 access_token 获取 GitHub 用户信息
7. 检查是否已关联：
   - 已关联：直接登录
   - 未关联：调用 register() 创建用户并关联
8. 返回登录信息（token）
```

## 🎨 前端集成

### Vue 3 示例

```vue
<template>
  <button @click="loginWithGitHub">GitHub 登录</button>
</template>

<script setup>
import axios from 'axios'

const loginWithGitHub = async () => {
  const state = Math.random().toString(36).substring(2)
  sessionStorage.setItem('oauth_state', state)
  
  const { data } = await axios.get('/api/auth/oauth2/github/authorize', {
    params: { state }
  })
  
  window.location.href = data.data.authUrl
}
</script>
```

### React 示例

```jsx
const loginWithGitHub = async () => {
  const state = Math.random().toString(36).substring(2);
  sessionStorage.setItem('oauth_state', state);
  
  const response = await fetch(`/api/auth/oauth2/github/authorize?state=${state}`);
  const result = await response.json();
  
  window.location.href = result.data.authUrl;
};

return <button onClick={loginWithGitHub}>GitHub 登录</button>;
```

## 📝 数据库

使用现有的 `oauth_identities` 表：

```sql
CREATE TABLE oauth_identities (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  provider VARCHAR(50) NOT NULL,      -- 'github'
  identifier VARCHAR(255) NOT NULL,   -- GitHub 用户 ID
  credential TEXT,                    -- Access Token
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uk_provider_identifier (provider, identifier)
);
```

## ⚠️ 注意事项

1. **邮箱处理**：GitHub 用户未公开邮箱时，生成临时邮箱 `github_{id}@oauth.local`
2. **用户名冲突**：GitHub 用户名已存在时，自动添加数字后缀
3. **密码安全**：OAuth 用户密码为随机生成，无法通过密码登录
4. **生产环境**：记得修改 `redirect-uri` 为生产域名

## 🔧 扩展其他平台

要添加 Google、微信等平台，只需：

1. 在 `OAuth2Service` 添加方法
2. 在 `OAuth2ServiceImpl` 实现逻辑
3. 在 `AuthController` 添加端点
4. 在配置文件添加配置

示例：

```java
// OAuth2Service.java
String getGoogleAuthorizationUrl(String state);
Result<LoginResponse> handleGoogleCallback(String code, Boolean rememberMe);
```

## 📚 更多文档

- 详细使用文档：`mkcs-server/OAUTH2_USAGE.md`
- API 文档：启动后访问 http://localhost:8080/swagger-ui.html

## ✅ 完成清单

- [x] 创建 OAuth2 相关 DTO
- [x] 实现 OAuth2Service 和 OauthIdentitiesService
- [x] 添加 AuthController 端点
- [x] 配置 WebClient
- [x] 添加配置项
- [x] 创建前端示例页面
- [x] 编写文档

## 🎉 开始使用

现在你可以：

1. 配置 GitHub OAuth App
2. 更新 `application-dev.yml`
3. 启动应用
4. 访问 http://localhost:8080/oauth2-demo.html 测试

祝你使用愉快！🚀
