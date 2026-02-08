# OAuth2 重定向流程说明

## 问题背景

GitHub OAuth2 授权后会直接回调到后端 API 地址，如果后端返回 JSON 响应，浏览器会显示 JSON 数据而不是跳转到前端页面。

## 解决方案

我们采用**后端重定向方案**：后端处理完 OAuth2 回调后，自动重定向到前端页面并传递 token 参数。

## 完整流程

```
1. 用户点击 "GitHub 登录"
   ↓
2. 前端获取授权 URL
   GET /api/auth/oauth2/github/authorize
   ↓
3. 跳转到 GitHub 授权页面
   https://github.com/login/oauth/authorize?client_id=xxx&...
   ↓
4. 用户授权
   ↓
5. GitHub 重定向到后端回调地址（带 code）
   GET /api/auth/oauth2/github/callback?code=xxx&state=xxx
   ↓
6. 后端处理回调
   - 使用 code 换取 access_token
   - 获取 GitHub 用户信息
   - 创建/登录用户
   - 生成 token
   ↓
7. 后端重定向到前端页面（带 token）
   302 Redirect → /oauth2-demo.html?token=xxx&userId=xxx&username=xxx&success=true
   ↓
8. 前端页面处理回调参数
   - 保存 token 到 localStorage
   - 显示登录成功信息
   - 跳转到主页
```

## 代码实现

### 1. 后端控制器（AuthController.java）

```java
@GetMapping("/oauth2/github/callback")
public void handleGitHubCallbackGet(
        @RequestParam String code,
        @RequestParam(required = false) String state,
        @RequestParam(required = false, defaultValue = "false") Boolean rememberMe,
        HttpServletResponse response) throws IOException {
    
    try {
        // 处理 GitHub 回调
        Result<LoginResponse> result = oauth2Service.handleGitHubCallback(code, rememberMe);
        
        if (result.getCode() == 200 && result.getData() != null) {
            LoginResponse loginResponse = result.getData();
            
            // 构建重定向 URL，将 token 和用户信息传递给前端
            String redirectUrl = String.format(
                "%s?token=%s&userId=%d&username=%s&success=true",
                frontendCallbackUrl,
                loginResponse.getTokenValue(),
                loginResponse.getId(),
                loginResponse.getUsername()
            );
            
            // 重定向到前端
            response.sendRedirect(redirectUrl);
        } else {
            // 登录失败，重定向到错误页面
            String errorUrl = String.format(
                "%s?success=false&error=%s",
                frontendCallbackUrl,
                URLEncoder.encode(result.getMessage(), "UTF-8")
            );
            response.sendRedirect(errorUrl);
        }
    } catch (Exception e) {
        // 异常处理
        String errorUrl = String.format(
            "%s?success=false&error=%s",
            frontendCallbackUrl,
            URLEncoder.encode("登录失败: " + e.getMessage(), "UTF-8")
        );
        response.sendRedirect(errorUrl);
    }
}
```

### 2. 配置文件（application-dev.yml）

```yaml
oauth2:
  github:
    client-id: your_github_client_id
    client-secret: your_github_client_secret
    redirect-uri: http://localhost:8080/api/auth/oauth2/github/callback
  # 前端回调页面 URL
  frontend-callback-url: /oauth2-demo.html
  # 如果有前端项目，可以改为：
  # frontend-callback-url: http://localhost:5173/oauth/callback
```

### 3. 前端页面（oauth2-demo.html）

```javascript
// 处理 OAuth2 回调
async function handleCallback() {
    const urlParams = new URLSearchParams(window.location.search);
    const success = urlParams.get('success');
    const token = urlParams.get('token');
    const userId = urlParams.get('userId');
    const username = urlParams.get('username');
    const error = urlParams.get('error');

    // 如果没有回调参数，说明不是回调页面
    if (!success && !token && !error) {
        return;
    }

    // 清除 URL 参数（保持页面干净）
    window.history.replaceState({}, document.title, window.location.pathname);

    if (success === 'true' && token) {
        // 登录成功
        localStorage.setItem('token', token);
        localStorage.setItem('userId', userId);
        localStorage.setItem('username', username);
        
        // 显示成功信息
        showStatus('GitHub 登录成功！正在跳转...', 'success');
        
        // 跳转到主页
        setTimeout(() => {
            window.location.href = '/index.html';
        }, 1500);
    } else if (success === 'false' || error) {
        // 登录失败
        showStatus('GitHub 登录失败: ' + decodeURIComponent(error), 'error');
    }
}

// 页面加载时检查是否是回调
window.addEventListener('DOMContentLoaded', handleCallback);
```

## 配置选项

### 开发环境

#### 选项 1：使用后端静态页面（当前配置）

```yaml
oauth2:
  frontend-callback-url: /oauth2-demo.html
```

- ✅ 简单，无需额外配置
- ✅ 适合快速测试
- ❌ 前后端不分离

#### 选项 2：使用前端开发服务器

```yaml
oauth2:
  frontend-callback-url: http://localhost:5173/oauth/callback
```

- ✅ 前后端分离
- ✅ 适合实际开发
- ⚠️ 需要配置 CORS

### 生产环境

```yaml
oauth2:
  frontend-callback-url: https://your-domain.com/oauth/callback
```

同时需要更新 GitHub OAuth App 的回调地址：
```
https://your-domain.com/api/auth/oauth2/github/callback
```

## 前端项目集成

### Vue 3 示例

#### 1. 创建回调页面（src/views/OAuthCallback.vue）

```vue
<template>
  <div class="oauth-callback">
    <div v-if="loading">
      <h2>正在处理登录...</h2>
    </div>
    <div v-else-if="error">
      <h2>登录失败</h2>
      <p>{{ error }}</p>
      <button @click="goToLogin">返回登录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(true)
const error = ref('')

onMounted(() => {
  const urlParams = new URLSearchParams(window.location.search)
  const success = urlParams.get('success')
  const token = urlParams.get('token')
  const userId = urlParams.get('userId')
  const username = urlParams.get('username')
  const errorMsg = urlParams.get('error')

  if (success === 'true' && token) {
    // 登录成功
    localStorage.setItem('token', token)
    localStorage.setItem('userId', userId)
    localStorage.setItem('username', username)
    
    // 跳转到主页
    router.push('/')
  } else {
    // 登录失败
    loading.value = false
    error.value = errorMsg ? decodeURIComponent(errorMsg) : '未知错误'
  }
})

const goToLogin = () => {
  router.push('/login')
}
</script>
```

#### 2. 配置路由（src/router/index.js）

```javascript
const routes = [
  {
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('@/views/OAuthCallback.vue')
  },
  // ... 其他路由
]
```

#### 3. 更新后端配置

```yaml
oauth2:
  frontend-callback-url: http://localhost:5173/oauth/callback
```

### React 示例

#### 1. 创建回调组件（src/pages/OAuthCallback.jsx）

```jsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const OAuthCallback = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const success = urlParams.get('success');
    const token = urlParams.get('token');
    const userId = urlParams.get('userId');
    const username = urlParams.get('username');
    const errorMsg = urlParams.get('error');

    if (success === 'true' && token) {
      // 登录成功
      localStorage.setItem('token', token);
      localStorage.setItem('userId', userId);
      localStorage.setItem('username', username);
      
      // 跳转到主页
      navigate('/');
    } else {
      // 登录失败
      setLoading(false);
      setError(errorMsg ? decodeURIComponent(errorMsg) : '未知错误');
    }
  }, [navigate]);

  if (loading) {
    return <div>正在处理登录...</div>;
  }

  if (error) {
    return (
      <div>
        <h2>登录失败</h2>
        <p>{error}</p>
        <button onClick={() => navigate('/login')}>返回登录</button>
      </div>
    );
  }

  return null;
};

export default OAuthCallback;
```

#### 2. 配置路由（src/App.jsx）

```jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import OAuthCallback from './pages/OAuthCallback';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/oauth/callback" element={<OAuthCallback />} />
        {/* 其他路由 */}
      </Routes>
    </BrowserRouter>
  );
}
```

## URL 参数说明

### 成功回调

```
/oauth2-demo.html?token=xxx&userId=123&username=octocat&success=true
```

| 参数 | 说明 | 示例 |
|------|------|------|
| success | 是否成功 | true |
| token | 访问令牌 | eyJ0eXAiOiJKV1QiLCJhbGc... |
| userId | 用户ID | 123 |
| username | 用户名 | octocat |

### 失败回调

```
/oauth2-demo.html?success=false&error=登录失败
```

| 参数 | 说明 | 示例 |
|------|------|------|
| success | 是否成功 | false |
| error | 错误信息 | 登录失败 |

## 安全性考虑

### 1. Token 传递安全

虽然 token 通过 URL 参数传递，但：
- ✅ 使用 HTTPS 加密传输（生产环境）
- ✅ 前端立即保存到 localStorage 并清除 URL 参数
- ✅ Token 有过期时间限制

### 2. State 参数验证

虽然当前实现简化了 state 验证，但建议：
- 前端生成随机 state 并保存
- 后端验证 state 是否匹配
- 防止 CSRF 攻击

### 3. 生产环境建议

```yaml
# 生产环境配置
oauth2:
  github:
    redirect-uri: https://api.your-domain.com/api/auth/oauth2/github/callback
  frontend-callback-url: https://your-domain.com/oauth/callback
```

## 测试流程

### 1. 启动应用

```bash
mvn spring-boot:run
```

### 2. 访问登录页面

```
http://localhost:8080/oauth2-demo.html
```

### 3. 点击 "使用 GitHub 登录"

### 4. 授权后自动跳转

```
http://localhost:8080/oauth2-demo.html?token=xxx&userId=123&username=octocat&success=true
```

### 5. 查看主页

```
http://localhost:8080/index.html
```

## 常见问题

### Q1: 为什么要用重定向而不是返回 JSON？

**A:** 因为 GitHub 的回调是浏览器重定向，如果返回 JSON，浏览器会显示 JSON 文本而不是跳转到前端页面。

### Q2: Token 通过 URL 传递安全吗？

**A:** 
- 开发环境：可以接受
- 生产环境：建议使用 HTTPS + 立即清除 URL 参数
- 更安全的方案：使用 POST 方式或 Cookie

### Q3: 如何支持多个前端地址？

**A:** 可以通过 URL 参数指定回调地址：

```java
@GetMapping("/oauth2/github/authorize")
public Result<Map<String, String>> getGitHubAuthUrl(
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String callbackUrl) {
    
    // 保存 callbackUrl 到 session 或 Redis
    // 在回调时使用保存的 callbackUrl
}
```

## 总结

通过后端重定向方案：

1. ✅ 解决了 JSON 响应显示问题
2. ✅ 支持前后端分离架构
3. ✅ 提供了完整的登录流程
4. ✅ 易于集成到前端项目

现在用户可以顺利完成 GitHub OAuth2 登录并跳转到主页！
