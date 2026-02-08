# GitHub OAuth2 登录测试指南

## 🎯 测试目标

验证 GitHub OAuth2 登录功能是否正常工作，包括：
- ✅ 授权流程
- ✅ 用户信息获取
- ✅ 头像保存
- ✅ 重定向跳转
- ✅ Token 传递
- ✅ 主页显示

## 📋 前置条件

### 1. GitHub OAuth App 配置

确保已在 GitHub 创建 OAuth App：
- Application name: `MK Cloud Storage`
- Homepage URL: `http://localhost:8080`
- Authorization callback URL: `http://localhost:8080/api/auth/oauth2/github/callback`

### 2. 后端配置

确认 `application-dev.yml` 配置正确：

```yaml
oauth2:
  github:
    client-id: Ov23liw6JgXE6Hsyi8h2
    client-secret: 061ce4865e27c6b77e28480ac05b36cd8c23d687
    redirect-uri: http://localhost:8080/api/auth/oauth2/github/callback
  frontend-callback-url: /oauth2-demo.html
```

### 3. 数据库

确保数据库表已创建：
- `users` 表（包含 `avatar_url` 字段）
- `oauth_identities` 表

## 🚀 测试步骤

### 步骤 1：启动应用

```bash
cd mkcs-server
mvn spring-boot:run
```

等待应用启动完成，看到：
```
Started MkcsServerApplication in X.XXX seconds
```

### 步骤 2：访问登录页面

打开浏览器访问：
```
http://localhost:8080/oauth2-demo.html
```

应该看到：
- ✅ 页面标题：GitHub OAuth2 登录示例
- ✅ "使用 GitHub 登录" 按钮
- ✅ 使用说明

### 步骤 3：点击 GitHub 登录

点击 "使用 GitHub 登录" 按钮

**预期行为：**
1. 页面显示 "正在获取授权链接..."
2. 自动跳转到 GitHub 授权页面

**GitHub 授权页面应显示：**
- 应用名称：MK Cloud Storage
- 请求的权限：user:email
- "Authorize" 按钮

### 步骤 4：授权应用

点击 "Authorize" 按钮

**预期行为：**
1. GitHub 重定向到后端回调地址
2. 后端处理登录逻辑
3. 自动重定向回 `oauth2-demo.html` 并带上参数

**URL 应该类似：**
```
http://localhost:8080/oauth2-demo.html?token=xxx&userId=123&username=octocat&success=true
```

### 步骤 5：查看登录成功页面

**预期显示：**
- ✅ 绿色提示框："GitHub 登录成功！正在跳转..."
- ✅ 用户信息卡片：
  - 用户ID
  - 用户名
  - Token（部分显示）
- ✅ "进入主页" 按钮
- ✅ "退出登录" 按钮

### 步骤 6：查看数据库

打开数据库客户端，执行查询：

```sql
-- 查看用户信息
SELECT id, username, nickname, email, avatar_url, status
FROM users
WHERE username = 'your_github_username';

-- 查看 OAuth 关联
SELECT id, user_id, provider, identifier
FROM oauth_identities
WHERE provider = 'github';
```

**预期结果：**
- ✅ `users` 表有新记录
- ✅ `avatar_url` 字段有 GitHub 头像 URL
- ✅ `oauth_identities` 表有关联记录

### 步骤 7：进入主页

点击 "进入主页" 按钮

**预期行为：**
1. 跳转到 `http://localhost:8080/index.html`
2. 显示主页内容

**主页应显示：**
- ✅ 页面标题：MK Cloud Storage
- ✅ 右上角显示用户名和用户ID
- ✅ "退出登录" 按钮
- ✅ 欢迎信息："欢迎回来！👋"
- ✅ 功能卡片（文件管理、安全加密等）

### 步骤 8：测试退出登录

点击 "退出登录" 按钮

**预期行为：**
1. 清除 localStorage 中的 token
2. 页面刷新
3. 显示 "请先登录" 提示
4. 显示 "前往登录" 按钮

### 步骤 9：测试再次登录

点击 "前往登录" 按钮，再次通过 GitHub 登录

**预期行为：**
1. 跳转到登录页面
2. 点击 GitHub 登录
3. 因为已授权，GitHub 直接重定向（无需再次授权）
4. 成功登录并跳转到主页

**数据库验证：**
```sql
-- 应该还是同一个用户，不会创建新用户
SELECT COUNT(*) FROM users WHERE username = 'your_github_username';
-- 结果应该是 1

-- OAuth 关联也应该是同一条记录
SELECT COUNT(*) FROM oauth_identities WHERE provider = 'github' AND identifier = 'your_github_id';
-- 结果应该是 1
```

## 🔍 详细验证

### 验证 1：Token 有效性

打开浏览器开发者工具（F12），在 Console 中执行：

```javascript
// 查看保存的 token
console.log('Token:', localStorage.getItem('token'));
console.log('User ID:', localStorage.getItem('userId'));
console.log('Username:', localStorage.getItem('username'));

// 测试 token 是否有效
fetch('/api/auth/check', {
    headers: {
        'Authorization': localStorage.getItem('token')
    }
})
.then(res => res.json())
.then(data => console.log('Token 验证结果:', data));
```

**预期输出：**
```json
{
  "code": 200,
  "message": "用户已登录",
  "data": null
}
```

### 验证 2：用户信息 API

```javascript
fetch('/api/auth/userinfo', {
    headers: {
        'Authorization': localStorage.getItem('token')
    }
})
.then(res => res.json())
.then(data => console.log('用户信息:', data));
```

**预期输出：**
```json
{
  "code": 200,
  "message": "获取用户信息成功",
  "data": {
    "id": 123,
    "username": "octocat",
    "nickname": "The Octocat",
    "email": "octocat@github.com",
    "avatarUrl": "https://avatars.githubusercontent.com/u/583231?v=4",
    "token": "xxx",
    ...
  }
}
```

### 验证 3：头像 URL

复制数据库中的 `avatar_url`，在浏览器中打开：

```
https://avatars.githubusercontent.com/u/583231?v=4
```

**预期：**
- ✅ 显示 GitHub 头像图片

### 验证 4：日志检查

查看应用日志，应该包含：

```
INFO  - 获取 GitHub 用户信息成功: githubId=583231, username=octocat
INFO  - 创建OAuth身份关联: userId=123, provider=github, identifier=583231
INFO  - 更新用户头像: userId=123, avatarUrl=https://avatars.githubusercontent.com/u/583231?v=4
INFO  - GitHub OAuth2 注册并登录成功: userId=123, githubId=583231
INFO  - GitHub OAuth2 登录成功，重定向到前端: /oauth2-demo.html?token=xxx&userId=123&username=octocat&success=true
```

## ❌ 常见问题排查

### 问题 1：点击登录后没有跳转

**可能原因：**
- GitHub Client ID 配置错误
- 网络问题

**排查步骤：**
1. 检查浏览器 Console 是否有错误
2. 检查 `application-dev.yml` 配置
3. 测试网络连接：`curl https://github.com`

### 问题 2：授权后显示 JSON 数据

**可能原因：**
- 后端代码未更新
- 配置的 `frontend-callback-url` 错误

**解决方案：**
1. 确认使用了新的 `handleGitHubCallbackGet` 方法
2. 检查配置：`oauth2.frontend-callback-url: /oauth2-demo.html`

### 问题 3：登录成功但头像为空

**可能原因：**
- GitHub 用户未公开头像
- 代码未更新

**排查步骤：**
1. 查看日志是否有 "更新用户头像" 信息
2. 检查数据库 `avatar_url` 字段
3. 在 GitHub 设置中公开头像

### 问题 4：再次登录创建了新用户

**可能原因：**
- `oauth_identities` 表未正确关联
- GitHub ID 不匹配

**排查步骤：**
```sql
-- 查看 OAuth 关联
SELECT * FROM oauth_identities WHERE provider = 'github';

-- 查看用户数量
SELECT COUNT(*) FROM users;
```

### 问题 5：主页显示 "请先登录"

**可能原因：**
- Token 未保存到 localStorage
- Token 已过期

**排查步骤：**
1. 打开开发者工具，查看 localStorage
2. 检查 token 是否存在
3. 测试 token 有效性（验证 1）

## 📊 测试检查清单

- [ ] 应用成功启动
- [ ] 登录页面正常显示
- [ ] 点击登录跳转到 GitHub
- [ ] GitHub 授权页面显示正确
- [ ] 授权后重定向回前端
- [ ] 显示登录成功信息
- [ ] 用户信息正确显示
- [ ] 数据库有用户记录
- [ ] 头像 URL 已保存
- [ ] OAuth 关联已创建
- [ ] 主页正常显示
- [ ] 用户信息显示在右上角
- [ ] 退出登录功能正常
- [ ] 再次登录不创建新用户
- [ ] Token 验证通过
- [ ] 用户信息 API 正常

## 🎉 测试成功标准

全部通过以上检查清单，即表示 GitHub OAuth2 登录功能测试成功！

## 📝 测试报告模板

```
测试日期：2026-02-07
测试人员：[你的名字]
测试环境：开发环境

测试结果：
✅ 授权流程正常
✅ 用户信息获取成功
✅ 头像保存成功
✅ 重定向跳转正常
✅ Token 传递成功
✅ 主页显示正常
✅ 退出登录正常
✅ 再次登录正常

数据库验证：
- 用户记录：1 条
- OAuth 关联：1 条
- 头像 URL：已保存

结论：GitHub OAuth2 登录功能测试通过 ✅
```

## 🔗 相关文档

- [OAuth2 使用说明](mkcs-server/OAUTH2_USAGE.md)
- [OAuth2 快速开始](GITHUB_OAUTH2_QUICKSTART.md)
- [OAuth2 重定向流程](mkcs-server/OAUTH2_REDIRECT_FLOW.md)
- [OAuth2 头像更新](mkcs-server/OAUTH2_AVATAR_UPDATE.md)

祝测试顺利！🚀
