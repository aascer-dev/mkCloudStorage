# OAuth2 头像自动更新说明

## 功能概述

GitHub OAuth2 登录时，系统会自动获取并保存用户的 GitHub 头像到 `users` 表的 `avatar_url` 字段。

## 实现逻辑

### 1. 新用户注册时保存头像

当用户首次通过 GitHub 登录时：

```java
// OAuth2ServiceImpl.registerUserFromOAuth()

// 1. 调用 register() 创建用户
Result<LoginResponse> registerResult = usersService.register(registerRequest);

// 2. 获取创建的用户
Users user = usersService.getUserByUsername(username);

// 3. 更新用户头像（GitHub 提供的头像 URL）
if (userInfo.getAvatarUrl() != null && !userInfo.getAvatarUrl().trim().isEmpty()) {
    user.setAvatarUrl(userInfo.getAvatarUrl());
    usersService.updateById(user);
    log.info("更新用户头像: userId={}, avatarUrl={}", user.getId(), userInfo.getAvatarUrl());
}
```

### 2. 已有用户登录时更新头像

当已关联的用户再次通过 GitHub 登录时：

```java
// OAuth2ServiceImpl.handleGitHubCallback()

// 1. 获取用户信息
user = usersService.getById(oauthIdentity.getUserId());

// 2. 更新用户头像（如果 GitHub 头像有变化）
if (userInfo.getAvatarUrl() != null && !userInfo.getAvatarUrl().trim().isEmpty()) {
    if (!userInfo.getAvatarUrl().equals(user.getAvatarUrl())) {
        user.setAvatarUrl(userInfo.getAvatarUrl());
        usersService.updateById(user);
        log.info("更新用户头像: userId={}, avatarUrl={}", user.getId(), userInfo.getAvatarUrl());
    }
}
```

## 数据流

```
GitHub API
    ↓
OAuth2UserInfo (avatarUrl)
    ↓
registerUserFromOAuth() / handleGitHubCallback()
    ↓
Users.setAvatarUrl()
    ↓
usersService.updateById()
    ↓
数据库 users.avatar_url
    ↓
LoginResponse (avatarUrl)
    ↓
前端显示
```

## 数据库字段

### users 表

| 字段 | 类型 | 说明 |
|------|------|------|
| avatar_url | VARCHAR(512) | 用户头像 URL |

### 示例数据

```sql
SELECT id, username, nickname, avatar_url 
FROM users 
WHERE id = 1;

-- 结果示例：
-- id: 1
-- username: octocat
-- nickname: The Octocat
-- avatar_url: https://avatars.githubusercontent.com/u/583231?v=4
```

## API 响应

### 登录响应包含头像

```json
{
  "code": 200,
  "message": "GitHub 登录成功",
  "data": {
    "id": 1,
    "username": "octocat",
    "nickname": "The Octocat",
    "email": "octocat@github.com",
    "avatarUrl": "https://avatars.githubusercontent.com/u/583231?v=4",
    "token": "xxx",
    "tokenValue": "xxx",
    ...
  }
}
```

## 前端使用

### 显示用户头像

```html
<!-- Vue 3 -->
<template>
  <div class="user-avatar">
    <img 
      :src="userInfo.avatarUrl || '/default-avatar.png'" 
      :alt="userInfo.nickname"
      @error="handleImageError"
    >
  </div>
</template>

<script setup>
import { ref } from 'vue'

const userInfo = ref({
  avatarUrl: 'https://avatars.githubusercontent.com/u/583231?v=4',
  nickname: 'The Octocat'
})

const handleImageError = (e) => {
  // 头像加载失败时使用默认头像
  e.target.src = '/default-avatar.png'
}
</script>
```

### React 示例

```jsx
const UserAvatar = ({ user }) => {
  const [avatarUrl, setAvatarUrl] = useState(user.avatarUrl || '/default-avatar.png');

  const handleError = () => {
    setAvatarUrl('/default-avatar.png');
  };

  return (
    <img 
      src={avatarUrl} 
      alt={user.nickname}
      onError={handleError}
    />
  );
};
```

## GitHub 头像 URL 格式

GitHub 头像 URL 通常格式为：

```
https://avatars.githubusercontent.com/u/{user_id}?v=4
```

参数说明：
- `user_id`: GitHub 用户的数字 ID
- `v=4`: API 版本号

### 调整头像大小

可以通过添加 `s` 参数调整大小：

```
https://avatars.githubusercontent.com/u/583231?v=4&s=200
```

- `s=200`: 头像大小为 200x200 像素
- 支持的尺寸：任意正整数

## 头像更新策略

### 1. 首次登录

- ✅ 自动保存 GitHub 头像

### 2. 再次登录

- ✅ 检查头像是否变化
- ✅ 如果变化则更新
- ✅ 如果相同则跳过

### 3. 手动更新

用户可以通过个人设置页面上传自定义头像，覆盖 GitHub 头像：

```java
// UserController.updateAvatar()
@PostMapping("/avatar")
public Result<String> updateAvatar(@RequestParam("file") MultipartFile file) {
    // 1. 上传文件到 MinIO
    String avatarUrl = minioService.uploadFile(file);
    
    // 2. 更新用户头像
    Users user = usersService.getById(StpUtil.getLoginIdAsLong());
    user.setAvatarUrl(avatarUrl);
    usersService.updateById(user);
    
    return Result.success("头像更新成功", avatarUrl);
}
```

## 默认头像

如果用户没有头像（`avatar_url` 为 NULL），前端应显示默认头像：

### 方案 1：使用占位符服务

```javascript
const defaultAvatar = `https://ui-avatars.com/api/?name=${encodeURIComponent(user.nickname)}&size=200&background=random`;
```

### 方案 2：使用本地默认头像

```javascript
const avatarUrl = user.avatarUrl || '/images/default-avatar.png';
```

### 方案 3：使用 Gravatar

```javascript
import md5 from 'crypto-js/md5';

const gravatarUrl = `https://www.gravatar.com/avatar/${md5(user.email)}?d=identicon&s=200`;
```

## 测试

### 1. 测试新用户注册

```bash
# 1. 通过 GitHub 登录
curl http://localhost:8080/api/auth/oauth2/github/authorize

# 2. 授权后检查响应
# 应该包含 avatarUrl 字段

# 3. 查询数据库
SELECT id, username, avatar_url FROM users WHERE username = 'octocat';
```

### 2. 测试头像更新

```bash
# 1. 在 GitHub 修改头像
# 2. 再次通过 GitHub 登录
# 3. 检查数据库中的 avatar_url 是否更新
```

## 常见问题

### Q1: 为什么头像显示不出来？

**可能原因：**
1. GitHub 头像 URL 需要网络访问
2. 防火墙或代理阻止了 GitHub 域名
3. 头像 URL 已过期

**解决方案：**
- 使用默认头像作为后备
- 考虑将头像下载到本地存储

### Q2: 如何下载 GitHub 头像到本地？

```java
@Service
public class AvatarService {
    
    @Autowired
    private MinIOUtil minioUtil;
    
    public String downloadAndSaveAvatar(String githubAvatarUrl, Long userId) {
        try {
            // 1. 下载 GitHub 头像
            URL url = new URL(githubAvatarUrl);
            InputStream inputStream = url.openStream();
            
            // 2. 上传到 MinIO
            String fileName = "avatars/" + userId + ".jpg";
            String localUrl = minioUtil.uploadFile(inputStream, fileName);
            
            return localUrl;
        } catch (Exception e) {
            log.error("下载头像失败", e);
            return githubAvatarUrl; // 失败时返回原 URL
        }
    }
}
```

### Q3: 头像 URL 太长怎么办？

**方案 1：** 增加数据库字段长度

```sql
ALTER TABLE users MODIFY COLUMN avatar_url VARCHAR(1024);
```

**方案 2：** 使用短链接服务

**方案 3：** 下载到本地存储

## 相关文件

- `OAuth2ServiceImpl.java` - OAuth2 登录逻辑（保存头像）
- `Users.java` - 用户实体（包含 avatarUrl 字段）
- `LoginResponse.java` - 登录响应（包含 avatarUrl 字段）
- `UserConverter.java` - 用户转换器（映射 avatarUrl）

## 总结

通过 OAuth2 登录时：

1. ✅ 新用户注册时自动保存 GitHub 头像
2. ✅ 已有用户登录时自动更新头像（如果变化）
3. ✅ 登录响应中包含头像 URL
4. ✅ 前端可以直接显示头像

这样用户就不需要手动上传头像，提供了更好的用户体验！
