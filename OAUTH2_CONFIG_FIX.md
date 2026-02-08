# OAuth2 配置问题修复说明

## 问题原因

你的配置中 `redirect-uri` 设置错误。OAuth2 的回调流程是：

```
用户 → GitHub 授权 → GitHub 回调到后端 → 后端处理 → 重定向到前端
```

### 两个 URL 的区别

1. **`redirect-uri`** (GitHub 回调地址)
   - 这是 GitHub 授权后回调的地址
   - **必须是后端地址**
   - 格式：`http://localhost:8080/api/auth/oauth2/github/callback`
   - GitHub 会将授权码发送到这个地址

2. **`frontend-callback-url`** (前端页面地址)
   - 这是后端处理完成后重定向到的前端页面
   - 可以是前端开发服务器地址
   - 格式：`http://localhost:5173/oauth/callback`
   - 用户最终会看到这个页面

## 正确配置

### application-dev.yml

```yaml
oauth2:
  github:
    client-id: Ov23liw6JgXE6Hsyi8h2
    client-secret: 061ce4865e27c6b77e28480ac05b36cd8c23d687
    # 重要：这是 GitHub 回调到后端的地址
    redirect-uri: http://localhost:8080/api/auth/oauth2/github/callback
  # 前端回调页面 URL（后端处理完成后重定向到前端的页面）
  frontend-callback-url: http://localhost:5173/oauth/callback
```

### GitHub OAuth App 配置

在 GitHub OAuth App 设置中，**Authorization callback URL** 也必须设置为：

```
http://localhost:8080/api/auth/oauth2/github/callback
```

**注意**：GitHub OAuth App 的回调地址必须与 `redirect-uri` 完全一致！

## 完整流程

```
1. 前端调用: GET /api/auth/oauth2/github/authorize
   ↓
2. 后端返回 GitHub 授权 URL
   ↓
3. 前端跳转到 GitHub: https://github.com/login/oauth/authorize?...
   ↓
4. 用户在 GitHub 授权
   ↓
5. GitHub 回调到后端: http://localhost:8080/api/auth/oauth2/github/callback?code=xxx&state=xxx
   ↓
6. 后端处理授权码，获取用户信息
   ↓
7. 后端重定向到前端: http://localhost:5173/oauth/callback?token=xxx&userId=xxx
   ↓
8. 前端接收参数，完成登录
```

## 检查清单

- [ ] `application-dev.yml` 中的 `redirect-uri` 是后端地址
- [ ] `application-dev.yml` 中的 `frontend-callback-url` 是前端地址
- [ ] GitHub OAuth App 的回调地址与 `redirect-uri` 一致
- [ ] 后端服务运行在 `http://localhost:8080`
- [ ] 前端服务运行在 `http://localhost:5173`
- [ ] 重启后端服务使配置生效

## 测试步骤

1. 启动后端服务（端口 8080）
2. 启动前端服务（端口 5173）
3. 访问前端登录页面
4. 点击 "GitHub 登录"
5. 在 GitHub 授权页面点击授权
6. 应该会自动跳转回前端页面并完成登录

## 常见错误

### 错误 1: redirect_uri_mismatch

```
The redirect_uri MUST match the registered callback URL for this application.
```

**原因**：GitHub OAuth App 的回调地址与 `redirect-uri` 不一致

**解决**：
1. 检查 GitHub OAuth App 设置
2. 确保回调地址为：`http://localhost:8080/api/auth/oauth2/github/callback`

### 错误 2: 404 Not Found

**原因**：后端服务未启动或端口不对

**解决**：
1. 确认后端服务运行在 8080 端口
2. 访问 `http://localhost:8080/api/auth/oauth2/github/authorize` 测试

### 错误 3: CORS 错误

**原因**：前端跨域请求被拦截

**解决**：确保后端配置了 CORS，允许 `http://localhost:5173`

## 生产环境配置

生产环境需要修改为实际域名：

```yaml
oauth2:
  github:
    client-id: your_production_client_id
    client-secret: your_production_client_secret
    redirect-uri: https://api.yourdomain.com/api/auth/oauth2/github/callback
  frontend-callback-url: https://yourdomain.com/oauth/callback
```

同时在 GitHub OAuth App 中添加生产环境的回调地址。

---

## 新增功能：OAuth2 解除绑定

### API 接口

#### 1. 获取 OAuth2 绑定列表

**接口**: `GET /api/auth/oauth2/bindings`

**描述**: 获取当前用户的所有 OAuth2 绑定

**请求头**:
```
Authorization: Bearer {token}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "获取绑定列表成功",
  "data": {
    "userId": 123,
    "hasPassword": true,
    "totalBindings": 2,
    "bindings": [
      {
        "provider": "github",
        "identifier": "12345678",
        "createdAt": "2026-01-20T10:30:00",
        "canUnbind": true
      },
      {
        "provider": "google",
        "identifier": "user@gmail.com",
        "createdAt": "2026-01-21T15:20:00",
        "canUnbind": true
      }
    ]
  }
}
```

#### 2. 解除 OAuth2 绑定

**接口**: `DELETE /api/auth/oauth2/bindings/{provider}`

**描述**: 解除指定平台的 OAuth2 绑定

**请求头**:
```
Authorization: Bearer {token}
```

**路径参数**:
- `provider`: 平台标识（如 `github`）

**响应示例**:

```json
{
  "code": 200,
  "message": "解绑成功",
  "data": null
}
```

**错误响应**:

```json
{
  "code": 400,
  "message": "未找到该平台的绑定记录",
  "data": null
}
```

### 解绑规则

由于所有 OAuth2 用户在注册时都会生成一个随机默认密码，因此：

- ✅ **所有用户都可以解绑任何 OAuth 绑定**
- ✅ 解绑后仍可以使用用户名+密码登录
- ℹ️ 如果忘记密码，可以通过"忘记密码"功能重置

**注意**：如果用户从未设置过自定义密码，解绑后需要通过"忘记密码"功能来设置新密码才能登录。

### 前端使用示例

```javascript
// 获取绑定列表
const getBindings = async () => {
  const response = await fetch('/api/auth/oauth2/bindings', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  const result = await response.json();
  return result.data;
};

// 解除绑定
const unbindOAuth = async (provider) => {
  const confirmed = confirm(`确定要解除 ${provider} 绑定吗？`);
  if (!confirmed) return;
  
  const response = await fetch(`/api/auth/oauth2/bindings/${provider}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  
  const result = await response.json();
  if (result.code === 200) {
    alert('解绑成功');
    // 刷新绑定列表
    await getBindings();
  } else {
    alert(result.message);
  }
};
```

### Vue 3 示例

```vue
<template>
  <div class="oauth-bindings">
    <h2>第三方账号绑定</h2>
    
    <div v-if="!bindings.hasPassword" class="warning">
      ℹ️ 您使用的是默认密码，建议通过"修改密码"功能设置自定义密码
    </div>
    
    <div v-for="binding in bindings.bindings" :key="binding.provider" class="binding-item">
      <div class="provider-info">
        <img :src="`/icons/${binding.provider}.svg`" :alt="binding.provider" />
        <span>{{ getProviderName(binding.provider) }}</span>
        <span class="identifier">{{ binding.identifier }}</span>
      </div>
      
      <button 
        @click="unbind(binding.provider)" 
        class="unbind-btn"
      >
        解除绑定
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const bindings = ref({
  hasPassword: false,
  totalBindings: 0,
  bindings: []
});

const getProviderName = (provider) => {
  const names = {
    github: 'GitHub',
    google: 'Google',
    wechat: '微信'
  };
  return names[provider] || provider;
};

const loadBindings = async () => {
  const response = await fetch('/api/auth/oauth2/bindings', {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  const result = await response.json();
  if (result.code === 200) {
    bindings.value = result.data;
  }
};

const unbind = async (provider) => {
  if (!confirm(`确定要解除 ${getProviderName(provider)} 绑定吗？`)) {
    return;
  }
  
  const response = await fetch(`/api/auth/oauth2/bindings/${provider}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  const result = await response.json();
  if (result.code === 200) {
    alert('解绑成功');
    await loadBindings();
  } else {
    alert(result.message);
  }
};

onMounted(() => {
  loadBindings();
});
</script>

<style scoped>
.warning {
  padding: 10px;
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
  margin-bottom: 20px;
}

.binding-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 4px;
  margin-bottom: 10px;
}

.provider-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.unbind-btn {
  padding: 8px 16px;
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.unbind-btn:hover {
  background: #c82333;
}
</style>
```

---

**配置已修复，OAuth2 解绑功能已添加！**
