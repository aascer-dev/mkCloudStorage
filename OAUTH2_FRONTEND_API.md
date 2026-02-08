# OAuth2 增强功能 - 前端 API 文档

## 概述

本文档描述了 OAuth2 登录注册增强功能的完整流程和 API 接口，帮助前端开发者快速集成。

### 核心特性

- ✅ **强制邮箱绑定** - 新用户必须绑定邮箱才能完成注册
- ✅ **GitHub 邮箱直接确认** - GitHub 提供的已验证邮箱可直接使用
- ✅ **自定义邮箱需验证** - 用户输入的其他邮箱需要验证码验证
- ✅ **用户名自选** - 用户可以自己选择用户名
- ✅ **账号合并** - 邮箱冲突时可以合并到现有账号
- ✅ **CSRF 防护** - 使用 state 参数防止攻击

---

## 完整流程图

```
用户点击 GitHub 登录
    ↓
1. 获取授权 URL (带 state)
    ↓
2. 跳转到 GitHub 授权页面
    ↓
3. GitHub 回调 (code + state)
    ↓
4. 处理回调
    ├─ 现有用户 → 登录成功 ✓
    └─ 新用户 → 需要注册
        ↓
5. 邮箱绑定页面
    ├─ GitHub 提供邮箱
    │   ├─ 用户确认 → 直接验证 ✓
    │   └─ 用户选择其他邮箱 → 发送验证码
    └─ GitHub 未提供邮箱
        └─ 用户输入邮箱 → 发送验证码
            ↓
        输入验证码 → 验证邮箱 ✓
    ↓
6. 检查邮箱冲突
    ├─ 无冲突 → 继续
    └─ 有冲突 → 提示合并
        ├─ 用户确认 → 跳转账号恢复
        └─ 用户拒绝 → 使用其他邮箱
    ↓
7. 用户名选择页面
    ├─ 显示建议用户名
    └─ 用户输入/修改用户名
    ↓
8. 完成注册 → 登录成功 ✓
```

---

## API 接口详情

### 基础 URL

```
http://your-domain/api/auth
```

---

## 1. 获取 GitHub 授权 URL

**接口:** `GET /oauth2/github/authorize`

**描述:** 获取 GitHub OAuth2 授权链接，用于跳转到 GitHub 登录页面

**请求参数:** 无

**响应示例:**

```json
{
  "code": 200,
  "message": "获取授权链接成功",
  "data": {
    "authUrl": "https://github.com/login/oauth/authorize?client_id=xxx&redirect_uri=xxx&scope=user:email&state=uuid-here"
  },
  "timestamp": 1234567890
}
```

**前端处理:**

```javascript
// 1. 调用接口获取授权 URL
const response = await fetch('/api/auth/oauth2/github/authorize');
const result = await response.json();

// 2. 跳转到 GitHub 授权页面
window.location.href = result.data.authUrl;
```

---

## 2. GitHub 回调处理

**接口:** `GET /oauth2/github/callback`

**描述:** GitHub 授权后的回调处理（浏览器自动重定向）

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | string | 是 | GitHub 返回的授权码 |
| state | string | 是 | 防 CSRF 的 state 参数 |
| rememberMe | boolean | 否 | 是否记住登录（默认 false） |

**响应:** 自动重定向到前端页面，URL 参数包含：

### 情况 1: 登录成功（现有用户）

```
/oauth2-callback?success=true&token=xxx&userId=123&username=johndoe
```

### 情况 2: 需要注册（新用户）

```
/oauth2-callback?requiresRegistration=true&tempUserId=uuid&suggestedEmail=user@example.com&suggestedUsername=johndoe&hasGitHubEmail=true
```

**前端处理:**

```javascript
// 在回调页面解析 URL 参数
const params = new URLSearchParams(window.location.search);

if (params.get('success') === 'true') {
  // 登录成功
  const token = params.get('token');
  const userId = params.get('userId');
  const username = params.get('username');
  
  // 保存 token 并跳转到主页
  localStorage.setItem('token', token);
  window.location.href = '/home';
  
} else if (params.get('requiresRegistration') === 'true') {
  // 需要注册
  const tempUserId = params.get('tempUserId');
  const suggestedEmail = params.get('suggestedEmail');
  const suggestedUsername = params.get('suggestedUsername');
  const hasGitHubEmail = params.get('hasGitHubEmail') === 'true';
  
  // 跳转到邮箱绑定页面
  window.location.href = `/oauth2/email-binding?tempUserId=${tempUserId}&suggestedEmail=${suggestedEmail}&hasGitHubEmail=${hasGitHubEmail}`;
  
} else {
  // 登录失败
  const error = params.get('error');
  alert('登录失败: ' + error);
}
```

---

## 3. 发送邮箱验证码

**接口:** `POST /oauth2/send-verification-code`

**描述:** 为自定义邮箱发送 6 位数验证码

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID（从回调获取） |
| email | string | 是 | 要验证的邮箱地址 |

**请求示例:**

```javascript
const response = await fetch('/api/auth/oauth2/send-verification-code', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    email: 'user@example.com'
  })
});
```

**响应示例:**

```json
{
  "code": 200,
  "message": "验证码已发送",
  "data": null,
  "timestamp": 1234567890
}
```

**错误响应:**

```json
{
  "code": 2104,
  "message": "邮箱格式不正确",
  "data": null,
  "timestamp": 1234567890
}
```

---

## 4. 验证邮箱

**接口:** `POST /oauth2/verify-email`

**描述:** 验证邮箱（支持 GitHub 邮箱直接确认或自定义邮箱验证码验证）

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| email | string | 是 | 邮箱地址 |
| code | string | 否 | 验证码（自定义邮箱必填） |
| isGitHubEmail | boolean | 否 | 是否为 GitHub 邮箱（默认 false） |

### 情况 1: 确认 GitHub 邮箱（无需验证码）

**请求示例:**

```javascript
const response = await fetch('/api/auth/oauth2/verify-email', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    email: 'user@example.com',
    isGitHubEmail: 'true'
  })
});
```

### 情况 2: 验证自定义邮箱（需要验证码）

**请求示例:**

```javascript
const response = await fetch('/api/auth/oauth2/verify-email', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    email: 'user@example.com',
    code: '123456'
  })
});
```

**成功响应:**

```json
{
  "code": 200,
  "message": "邮箱验证成功",
  "data": null,
  "timestamp": 1234567890
}
```

**错误响应:**

```json
{
  "code": 2107,
  "message": "验证码已过期",
  "data": null,
  "timestamp": 1234567890
}
```

或

```json
{
  "code": 2108,
  "message": "验证码不正确",
  "data": null,
  "timestamp": 1234567890
}
```

---

## 5. 完成注册

**接口:** `POST /oauth2/complete-registration`

**描述:** 完成 OAuth2 用户注册（用户名选择后）

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| username | string | 是 | 用户选择的用户名 |
| rememberMe | boolean | 否 | 是否记住登录（默认 false） |

**请求示例:**

```javascript
const response = await fetch('/api/auth/oauth2/complete-registration', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    username: 'johndoe',
    rememberMe: 'true'
  })
});
```

**成功响应:**

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

**错误响应:**

```json
{
  "code": 1105,
  "message": "用户名已存在",
  "data": null,
  "timestamp": 1234567890
}
```

或（邮箱冲突）

```json
{
  "code": 1106,
  "message": "邮箱已被使用",
  "data": null,
  "timestamp": 1234567890
}
```

---

## 6. 发起账号合并

**接口:** `POST /oauth2/initiate-merge`

**描述:** 检测到邮箱冲突时，发起账号合并流程

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| email | string | 是 | 冲突的邮箱地址 |

**请求示例:**

```javascript
const response = await fetch('/api/auth/oauth2/initiate-merge', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    email: 'user@example.com'
  })
});
```

**响应示例:**

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

**前端处理:**

```javascript
if (result.code === 200 && result.data.requiresMerge) {
  // 显示确认对话框
  const confirmed = confirm(
    `该邮箱已被账号 "${result.data.existingUsername}" 使用。\n` +
    `这是您的现有账号吗？\n\n` +
    `点击"确定"将跳转到账号恢复页面验证身份后合并账号。`
  );
  
  if (confirmed) {
    // 跳转到账号恢复页面，验证成功后再调用 complete-merge
    window.location.href = `/account-recovery?email=${email}&returnUrl=/oauth2/merge-callback?tempUserId=${tempUserId}&existingUserId=${result.data.existingUserId}`;
  } else {
    // 用户选择使用其他邮箱
    alert('请使用其他邮箱地址');
  }
}
```

---

## 7. 完成账号合并

**接口:** `POST /oauth2/complete-merge`

**描述:** 在用户通过账号恢复验证后，完成账号合并

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempUserId | string | 是 | 临时用户 ID |
| existingUserId | number | 是 | 现有用户 ID |
| rememberMe | boolean | 否 | 是否记住登录（默认 false） |

**请求示例:**

```javascript
const response = await fetch('/api/auth/oauth2/complete-merge', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    tempUserId: 'uuid-here',
    existingUserId: '456',
    rememberMe: 'true'
  })
});
```

**成功响应:**

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

## 错误码参考

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

---

## 前端页面实现建议

### 1. 邮箱绑定页面 (`/oauth2/email-binding`)

**URL 参数:**
- `tempUserId`: 临时用户 ID
- `suggestedEmail`: 建议的邮箱（可能为空）
- `hasGitHubEmail`: 是否有 GitHub 邮箱

**页面逻辑:**

```javascript
// 页面加载时
const params = new URLSearchParams(window.location.search);
const tempUserId = params.get('tempUserId');
const suggestedEmail = params.get('suggestedEmail');
const hasGitHubEmail = params.get('hasGitHubEmail') === 'true';

if (hasGitHubEmail && suggestedEmail) {
  // 情况 1: GitHub 提供了邮箱
  // 显示: "GitHub 已验证邮箱: user@example.com"
  // 按钮: [确认使用此邮箱] [使用其他邮箱]
  
  document.getElementById('github-email-section').style.display = 'block';
  document.getElementById('github-email').textContent = suggestedEmail;
  
  // 确认 GitHub 邮箱
  document.getElementById('confirm-github-email').onclick = async () => {
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
      // 跳转到用户名选择页面
      window.location.href = `/oauth2/username-selection?tempUserId=${tempUserId}`;
    }
  };
  
  // 使用其他邮箱
  document.getElementById('use-other-email').onclick = () => {
    document.getElementById('github-email-section').style.display = 'none';
    document.getElementById('custom-email-section').style.display = 'block';
  };
  
} else {
  // 情况 2: GitHub 未提供邮箱或用户选择其他邮箱
  // 显示邮箱输入框和验证码输入
  document.getElementById('custom-email-section').style.display = 'block';
}

// 发送验证码
document.getElementById('send-code').onclick = async () => {
  const email = document.getElementById('email-input').value;
  
  const response = await fetch('/api/auth/oauth2/send-verification-code', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ tempUserId, email })
  });
  
  const result = await response.json();
  if (result.code === 200) {
    alert('验证码已发送到您的邮箱');
    document.getElementById('code-section').style.display = 'block';
    
    // 60秒倒计时
    let countdown = 60;
    const btn = document.getElementById('send-code');
    btn.disabled = true;
    const timer = setInterval(() => {
      countdown--;
      btn.textContent = `${countdown}秒后重新发送`;
      if (countdown === 0) {
        clearInterval(timer);
        btn.disabled = false;
        btn.textContent = '重新发送';
      }
    }, 1000);
  } else {
    alert(result.message);
  }
};

// 验证邮箱
document.getElementById('verify-email').onclick = async () => {
  const email = document.getElementById('email-input').value;
  const code = document.getElementById('code-input').value;
  
  const response = await fetch('/api/auth/oauth2/verify-email', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ tempUserId, email, code })
  });
  
  const result = await response.json();
  if (result.code === 200) {
    // 验证成功，跳转到用户名选择
    window.location.href = `/oauth2/username-selection?tempUserId=${tempUserId}`;
  } else if (result.code === 1106) {
    // 邮箱冲突，提示合并
    const merge = confirm('该邮箱已被使用，是否为您的现有账号？');
    if (merge) {
      // 发起合并流程
      const mergeResponse = await fetch('/api/auth/oauth2/initiate-merge', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ tempUserId, email })
      });
      const mergeResult = await mergeResponse.json();
      if (mergeResult.code === 200) {
        // 跳转到账号恢复页面
        window.location.href = `/account-recovery?email=${email}&returnUrl=/oauth2/merge-callback?tempUserId=${tempUserId}&existingUserId=${mergeResult.data.existingUserId}`;
      }
    } else {
      alert('请使用其他邮箱地址');
    }
  } else {
    alert(result.message);
  }
};
```

### 2. 用户名选择页面 (`/oauth2/username-selection`)

**URL 参数:**
- `tempUserId`: 临时用户 ID

**页面逻辑:**

```javascript
const params = new URLSearchParams(window.location.search);
const tempUserId = params.get('tempUserId');

// 实时验证用户名
let checkTimeout;
document.getElementById('username-input').oninput = (e) => {
  const username = e.target.value;
  
  // 格式验证
  const regex = /^[a-zA-Z0-9_-]{3,32}$/;
  if (!regex.test(username)) {
    document.getElementById('username-hint').textContent = 
      '用户名长度3-32字符，只能包含字母、数字、下划线和连字符';
    document.getElementById('username-hint').className = 'error';
    return;
  }
  
  // 防抖检查可用性
  clearTimeout(checkTimeout);
  checkTimeout = setTimeout(async () => {
    // 这里可以调用一个检查用户名可用性的接口（如果有的话）
    // 或者直接在提交时检查
    document.getElementById('username-hint').textContent = '用户名格式正确';
    document.getElementById('username-hint').className = 'success';
  }, 500);
};

// 完成注册
document.getElementById('complete-registration').onclick = async () => {
  const username = document.getElementById('username-input').value;
  const rememberMe = document.getElementById('remember-me').checked;
  
  const response = await fetch('/api/auth/oauth2/complete-registration', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ tempUserId, username, rememberMe })
  });
  
  const result = await response.json();
  if (result.code === 200) {
    // 注册成功，保存 token 并跳转
    localStorage.setItem('token', result.data.token);
    alert('注册成功！');
    window.location.href = '/home';
  } else {
    alert(result.message);
  }
};
```

### 3. 账号合并回调页面 (`/oauth2/merge-callback`)

**URL 参数:**
- `tempUserId`: 临时用户 ID
- `existingUserId`: 现有用户 ID

**页面逻辑:**

```javascript
// 用户通过账号恢复验证后，自动调用合并接口
const params = new URLSearchParams(window.location.search);
const tempUserId = params.get('tempUserId');
const existingUserId = params.get('existingUserId');

(async () => {
  const response = await fetch('/api/auth/oauth2/complete-merge', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      tempUserId,
      existingUserId,
      rememberMe: 'true'
    })
  });
  
  const result = await response.json();
  if (result.code === 200) {
    localStorage.setItem('token', result.data.token);
    alert('账号合并成功！');
    window.location.href = '/home';
  } else {
    alert('合并失败: ' + result.message);
    window.location.href = '/login';
  }
})();
```

---

## 注意事项

### 1. 数据过期时间

- **State 参数**: 10 分钟过期
- **临时 OAuth 数据**: 30 分钟过期
- **验证码**: 10 分钟过期

如果用户操作超时，需要提示重新开始登录流程。

### 2. 验证码重发限制

建议前端实现 60 秒倒计时，防止用户频繁请求验证码。

### 3. 用户名格式要求

- 长度: 3-32 字符
- 允许字符: 字母、数字、下划线(_)、连字符(-)
- 正则表达式: `^[a-zA-Z0-9_-]{3,32}$`

### 4. 错误处理

所有接口调用都应该处理错误情况，并给用户友好的提示信息。

### 5. Token 存储

登录成功后，将 token 存储在 localStorage 或 sessionStorage 中，后续请求需要在 Header 中携带：

```javascript
headers: {
  'Authorization': 'Bearer ' + localStorage.getItem('token')
}
```

---

## 完整示例代码

查看项目中的示例页面：
- `/oauth2-demo.html` - OAuth2 登录演示页面
- `/oauth2/email-binding.html` - 邮箱绑定页面
- `/oauth2/username-selection.html` - 用户名选择页面

---

## 联系支持

如有问题，请联系后端开发团队或查看完整的 API 文档。
