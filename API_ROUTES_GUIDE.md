# API 路由指南

## 问题说明

错误信息：
```
参数类型不匹配异常: /api/users/oauth-identities
Method parameter 'id': Failed to convert value of type 'java.lang.String' to required type 'java.lang.Long'
For input string: "oauth-identities"
```

**原因**：访问了错误的路径 `/api/users/oauth-identities`，该路径不存在。

Spring 将这个请求匹配到了 `GET /api/users/{id}` 路由，把 "oauth-identities" 当作 id 参数，导致类型转换失败。

---

## 正确的 API 路径

### 用户相关接口（UserController）

**基础路径**: `/api/users`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users` | 创建用户 |
| GET | `/api/users/{id}` | 获取用户详情 |
| PUT | `/api/users/{id}` | 更新用户 |
| POST | `/api/users/{id}/avatar` | 上传用户头像 |
| PUT | `/api/users/profile` | 更新当前用户信息 |

**注意**：`/api/users/{id}` 会匹配所有 `/api/users/xxx` 格式的路径（除了明确定义的路径如 `/profile`）

---

### 认证相关接口（AuthController）

**基础路径**: `/api/auth`

#### 基础认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/logout` | 用户登出 |
| GET | `/api/auth/userinfo` | 获取当前用户信息 |
| GET | `/api/auth/check` | 检查登录状态 |
| POST | `/api/auth/refresh` | 刷新 Token |

#### 用户名/邮箱可用性检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/usernames/{username}/availability` | 检查用户名是否可用 |
| GET | `/api/auth/emails/{email}/availability` | 检查邮箱是否可用 |

#### 验证码

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/verification-code/send` | 发送验证码 |
| POST | `/api/auth/verification-code/verify` | 验证验证码 |
| GET | `/api/auth/verification-code/cooldown` | 检查验证码冷却状态 |

#### OAuth2 登录注册

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/oauth2/github/authorize` | 获取 GitHub 授权 URL |
| POST | `/api/auth/oauth2/github/callback` | GitHub 回调（JSON） |
| GET | `/api/auth/oauth2/github/callback` | GitHub 回调（重定向） |
| POST | `/api/auth/oauth2/send-verification-code` | 发送邮箱验证码 |
| POST | `/api/auth/oauth2/verify-email` | 验证邮箱 |
| POST | `/api/auth/oauth2/complete-registration` | 完成注册 |
| POST | `/api/auth/oauth2/initiate-merge` | 发起账号合并 |
| POST | `/api/auth/oauth2/complete-merge` | 完成账号合并 |

#### OAuth2 绑定管理 ⭐

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/oauth2/bindings` | 获取 OAuth2 绑定列表 |
| DELETE | `/api/auth/oauth2/bindings/{provider}` | 解除 OAuth2 绑定 |

---

## OAuth2 绑定管理 API 详解

### 1. 获取绑定列表

**接口**: `GET /api/auth/oauth2/bindings`

**请求示例**:
```bash
curl -X GET http://localhost:8080/api/auth/oauth2/bindings \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**JavaScript 示例**:
```javascript
const response = await fetch('/api/auth/oauth2/bindings', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
const result = await response.json();
```

**响应示例**:
```json
{
  "code": 200,
  "message": "获取绑定列表成功",
  "data": {
    "userId": 123,
    "hasPassword": true,
    "totalBindings": 1,
    "bindings": [
      {
        "provider": "github",
        "identifier": "12345678",
        "createdAt": "2026-01-20T10:30:00",
        "canUnbind": true
      }
    ]
  }
}
```

---

### 2. 解除绑定

**接口**: `DELETE /api/auth/oauth2/bindings/{provider}`

**路径参数**:
- `provider`: 平台标识（如 `github`）

**请求示例**:
```bash
curl -X DELETE http://localhost:8080/api/auth/oauth2/bindings/github \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**JavaScript 示例**:
```javascript
const response = await fetch('/api/auth/oauth2/bindings/github', {
  method: 'DELETE',
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
const result = await response.json();
```

**响应示例**:
```json
{
  "code": 200,
  "message": "解绑成功",
  "data": null
}
```

---

## 常见错误

### 错误 1: 路径不存在

❌ **错误路径**: `/api/users/oauth-identities`  
✅ **正确路径**: `/api/auth/oauth2/bindings`

### 错误 2: 路径参数类型错误

如果访问 `/api/users/xxx`（xxx 不是数字），会被匹配到 `GET /api/users/{id}`，导致类型转换错误。

**解决方法**：
- 确保访问正确的路径
- 如果需要添加新的用户相关路径，使用更具体的路径名（如 `/profile`）

### 错误 3: 缺少认证 Token

OAuth2 绑定管理接口需要登录，必须在请求头中携带 Token：

```javascript
headers: {
  'Authorization': `Bearer ${token}`
}
```

---

## 路由优先级

Spring MVC 的路由匹配规则：

1. **精确匹配** > **模式匹配**
   - `/api/users/profile` 优先于 `/api/users/{id}`

2. **具体路径** > **通配符路径**
   - `/api/users/profile` 优先于 `/api/users/*`

3. **路径参数** 会匹配任何字符串
   - `/api/users/{id}` 会匹配 `/api/users/123`、`/api/users/abc` 等

**建议**：
- 将具体的路径（如 `/profile`）放在参数路径（如 `/{id}`）之前定义
- 使用更具体的路径名避免冲突

---

## 完整的前端示例

### Vue 3 示例

```vue
<template>
  <div class="oauth-bindings">
    <h2>第三方账号绑定</h2>
    
    <div v-if="loading">加载中...</div>
    
    <div v-else>
      <div v-for="binding in bindings" :key="binding.provider" class="binding-item">
        <span>{{ binding.provider }}</span>
        <button @click="unbind(binding.provider)">解除绑定</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const bindings = ref([]);
const loading = ref(true);

const loadBindings = async () => {
  try {
    const response = await fetch('/api/auth/oauth2/bindings', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
    
    const result = await response.json();
    if (result.code === 200) {
      bindings.value = result.data.bindings;
    }
  } catch (error) {
    console.error('加载失败', error);
  } finally {
    loading.value = false;
  }
};

const unbind = async (provider) => {
  if (!confirm(`确定要解除 ${provider} 绑定吗？`)) {
    return;
  }
  
  try {
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
  } catch (error) {
    console.error('解绑失败', error);
  }
};

onMounted(() => {
  loadBindings();
});
</script>
```

---

## 总结

✅ **正确的 OAuth2 绑定管理路径**：
- 获取列表：`GET /api/auth/oauth2/bindings`
- 解除绑定：`DELETE /api/auth/oauth2/bindings/{provider}`

❌ **错误的路径**：
- `/api/users/oauth-identities`（不存在）

**记住**：所有 OAuth2 相关的接口都在 `/api/auth/oauth2/` 路径下！
