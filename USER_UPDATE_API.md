# 用户信息更新 API 文档

## 接口概述

提供用户更新个人信息的接口，包括昵称、邮箱、头像URL和当前存储桶ID。

---

## API 接口

### 更新当前用户信息

**接口**: `PUT /api/users/profile`

**描述**: 更新当前登录用户的个人信息

**认证**: 需要登录（Bearer Token）

**请求头**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数**:

| 参数 | 类型 | 必填 | 说明 | 验证规则 |
|------|------|------|------|----------|
| nickname | string | 否 | 昵称 | 最大50字符 |
| email | string | 否 | 邮箱 | 邮箱格式，最大100字符，不能与其他用户重复 |
| avatarUrl | string | 否 | 头像URL | 最大500字符 |
| currentBucketId | number | 否 | 当前默认使用的存储桶ID | - |

**请求示例**:

```json
{
  "nickname": "张三",
  "email": "zhangsan@example.com",
  "avatarUrl": "https://example.com/avatar.jpg",
  "currentBucketId": 123
}
```

**成功响应**:

```json
{
  "code": 200,
  "message": "用户信息更新成功",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "nickname": "张三",
    "email": "zhangsan@example.com",
    "avatarUrl": "https://example.com/avatar.jpg",
    "status": 1,
    "currentBucketId": 123,
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "rememberMe": false
  },
  "timestamp": 1234567890
}
```

**错误响应**:

#### 1. 邮箱已被使用

```json
{
  "code": 1106,
  "message": "邮箱已被使用",
  "data": null,
  "timestamp": 1234567890
}
```

#### 2. 用户不存在

```json
{
  "code": 1001,
  "message": "用户不存在",
  "data": null,
  "timestamp": 1234567890
}
```

#### 3. 未登录

```json
{
  "code": 2005,
  "message": "用户未登录",
  "data": null,
  "timestamp": 1234567890
}
```

#### 4. 参数验证失败

```json
{
  "code": 400,
  "message": "邮箱格式不正确",
  "data": null,
  "timestamp": 1234567890
}
```

---

## 使用示例

### JavaScript / Fetch

```javascript
const updateUserProfile = async (profileData) => {
  const token = localStorage.getItem('token');
  
  const response = await fetch('/api/users/profile', {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(profileData)
  });
  
  const result = await response.json();
  
  if (result.code === 200) {
    console.log('更新成功', result.data);
    // 更新本地存储的用户信息
    localStorage.setItem('userInfo', JSON.stringify(result.data));
  } else {
    console.error('更新失败', result.message);
  }
  
  return result;
};

// 使用示例
updateUserProfile({
  nickname: '新昵称',
  email: 'newemail@example.com'
});
```

### Vue 3 示例

```vue
<template>
  <div class="profile-edit">
    <h2>编辑个人信息</h2>
    
    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <label>昵称</label>
        <input 
          v-model="form.nickname" 
          type="text" 
          placeholder="请输入昵称"
          maxlength="50"
        />
      </div>
      
      <div class="form-group">
        <label>邮箱</label>
        <input 
          v-model="form.email" 
          type="email" 
          placeholder="请输入邮箱"
          maxlength="100"
        />
        <span v-if="emailError" class="error">{{ emailError }}</span>
      </div>
      
      <div class="form-group">
        <label>头像URL</label>
        <input 
          v-model="form.avatarUrl" 
          type="url" 
          placeholder="请输入头像URL"
          maxlength="500"
        />
      </div>
      
      <button type="submit" :disabled="loading">
        {{ loading ? '保存中...' : '保存' }}
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';

const form = reactive({
  nickname: '',
  email: '',
  avatarUrl: '',
  currentBucketId: null
});

const loading = ref(false);
const emailError = ref('');

// 加载当前用户信息
const loadUserInfo = async () => {
  const response = await fetch('/api/auth/userinfo', {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  const result = await response.json();
  if (result.code === 200) {
    form.nickname = result.data.nickname || '';
    form.email = result.data.email || '';
    form.avatarUrl = result.data.avatarUrl || '';
    form.currentBucketId = result.data.currentBucketId;
  }
};

// 提交表单
const handleSubmit = async () => {
  emailError.value = '';
  loading.value = true;
  
  try {
    const response = await fetch('/api/users/profile', {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(form)
    });
    
    const result = await response.json();
    
    if (result.code === 200) {
      alert('更新成功');
      // 更新本地用户信息
      localStorage.setItem('userInfo', JSON.stringify(result.data));
    } else if (result.code === 1106) {
      emailError.value = '该邮箱已被其他用户使用';
    } else {
      alert(result.message);
    }
  } catch (error) {
    console.error('更新失败', error);
    alert('更新失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

// 页面加载时获取用户信息
loadUserInfo();
</script>

<style scoped>
.profile-edit {
  max-width: 500px;
  margin: 0 auto;
  padding: 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}

.form-group input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.error {
  color: red;
  font-size: 14px;
  margin-top: 5px;
  display: block;
}

button {
  width: 100%;
  padding: 12px;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

button:hover:not(:disabled) {
  background: #0056b3;
}
</style>
```

### React 示例

```jsx
import React, { useState, useEffect } from 'react';

function ProfileEdit() {
  const [form, setForm] = useState({
    nickname: '',
    email: '',
    avatarUrl: '',
    currentBucketId: null
  });
  
  const [loading, setLoading] = useState(false);
  const [emailError, setEmailError] = useState('');

  // 加载用户信息
  useEffect(() => {
    const loadUserInfo = async () => {
      const response = await fetch('/api/auth/userinfo', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      
      const result = await response.json();
      if (result.code === 200) {
        setForm({
          nickname: result.data.nickname || '',
          email: result.data.email || '',
          avatarUrl: result.data.avatarUrl || '',
          currentBucketId: result.data.currentBucketId
        });
      }
    };
    
    loadUserInfo();
  }, []);

  // 处理输入变化
  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
    if (name === 'email') {
      setEmailError('');
    }
  };

  // 提交表单
  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setEmailError('');
    
    try {
      const response = await fetch('/api/users/profile', {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(form)
      });
      
      const result = await response.json();
      
      if (result.code === 200) {
        alert('更新成功');
        localStorage.setItem('userInfo', JSON.stringify(result.data));
      } else if (result.code === 1106) {
        setEmailError('该邮箱已被其他用户使用');
      } else {
        alert(result.message);
      }
    } catch (error) {
      console.error('更新失败', error);
      alert('更新失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="profile-edit">
      <h2>编辑个人信息</h2>
      
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>昵称</label>
          <input
            type="text"
            name="nickname"
            value={form.nickname}
            onChange={handleChange}
            placeholder="请输入昵称"
            maxLength={50}
          />
        </div>
        
        <div className="form-group">
          <label>邮箱</label>
          <input
            type="email"
            name="email"
            value={form.email}
            onChange={handleChange}
            placeholder="请输入邮箱"
            maxLength={100}
          />
          {emailError && <span className="error">{emailError}</span>}
        </div>
        
        <div className="form-group">
          <label>头像URL</label>
          <input
            type="url"
            name="avatarUrl"
            value={form.avatarUrl}
            onChange={handleChange}
            placeholder="请输入头像URL"
            maxLength={500}
          />
        </div>
        
        <button type="submit" disabled={loading}>
          {loading ? '保存中...' : '保存'}
        </button>
      </form>
    </div>
  );
}

export default ProfileEdit;
```

---

## 注意事项

### 1. 邮箱唯一性

- 邮箱必须在系统中唯一
- 如果新邮箱已被其他用户使用，会返回错误码 `1106`
- 如果邮箱没有变化，不会进行唯一性检查

### 2. 可选字段

- 所有字段都是可选的
- 只传递需要更新的字段即可
- 未传递的字段不会被修改

### 3. 验证规则

- **昵称**: 最大50字符
- **邮箱**: 必须符合邮箱格式，最大100字符
- **头像URL**: 最大500字符
- **存储桶ID**: 数字类型

### 4. 权限要求

- 用户只能更新自己的信息
- 需要登录状态（Bearer Token）

### 5. 返回数据

- 更新成功后返回完整的用户信息
- 包含最新的 token（如果需要）

---

## 错误码参考

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 200 | 成功 | - |
| 400 | 参数验证失败 | 检查参数格式 |
| 1001 | 用户不存在 | 用户可能已被删除 |
| 1106 | 邮箱已被使用 | 提示用户使用其他邮箱 |
| 2005 | 用户未登录 | 跳转到登录页面 |

---

## 测试用例

### 1. 更新昵称

```bash
curl -X PUT http://localhost:8080/api/users/profile \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "新昵称"
  }'
```

### 2. 更新邮箱

```bash
curl -X PUT http://localhost:8080/api/users/profile \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newemail@example.com"
  }'
```

### 3. 同时更新多个字段

```bash
curl -X PUT http://localhost:8080/api/users/profile \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "张三",
    "email": "zhangsan@example.com",
    "avatarUrl": "https://example.com/avatar.jpg"
  }'
```

---

## 与头像上传接口的区别

### 头像上传接口

- **接口**: `POST /api/users/{id}/avatar`
- **功能**: 上传图片文件到 MinIO
- **参数**: `multipart/form-data` 文件上传
- **权限**: 需要 `user:updateAvatar` 权限

### 用户信息更新接口

- **接口**: `PUT /api/users/profile`
- **功能**: 更新用户信息（包括头像URL）
- **参数**: JSON 格式
- **权限**: 只需要登录

**建议使用场景**：
- 如果需要上传新头像文件 → 使用头像上传接口
- 如果只是更新头像URL（如使用第三方头像） → 使用用户信息更新接口
- 如果需要同时更新多个字段 → 使用用户信息更新接口

---

**文档版本**: 1.0  
**最后更新**: 2026-02-08
