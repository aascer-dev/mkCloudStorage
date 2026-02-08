# 更新用户头像 API

## 接口信息

- **路径**: `POST /api/users/{id}/avatar`
- **权限**: `user:updateAvatar`
- **认证**: 需要登录 (`@SaCheckLogin`)
- **Content-Type**: `multipart/form-data`

## 功能说明

该接口用于上传用户头像文件到MinIO的`avatar`存储桶。avatar桶配置为：
- **公开读取**: 任何人都可以下载头像
- **受限上传**: 只有拥有`user:updateAvatar`权限的用户才能上传

## 权限说明

该接口需要 `user:updateAvatar` 权限，该权限已分配给以下角色：
- 超级管理员 (ROLE_SUPER_ADMIN)
- 系统管理员 (ROLE_ADMIN)
- 普通用户 (ROLE_USER)

## 访问控制

1. **权限检查**: 用户必须拥有 `user:updateAvatar` 权限
2. **身份验证**: 
   - 普通用户只能更新自己的头像
   - 超级管理员和系统管理员可以更新任何用户的头像

## 文件限制

- **支持的格式**: JPEG, JPG, PNG, GIF, WebP
- **最大文件大小**: 5MB
- **文件参数名**: `file`

## 请求参数

### 路径参数
- `id` (Long): 要更新头像的用户ID

### 表单参数
- `file` (MultipartFile, 必填): 头像图片文件

## 响应示例

### 成功响应 (200)
```json
{
  "code": 200,
  "message": "头像更新成功",
  "data": {
    "id": 1,
    "username": "user123",
    "nickname": "张三",
    "email": "user@example.com",
    "avatarUrl": "http://localhost:9000/avatar/user_1_a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
    "status": 1,
    "createdAt": "2026-02-08T10:00:00",
    "updatedAt": "2026-02-08T11:30:00"
  }
}
```

### 错误响应

#### 无权限 (403)
```json
{
  "code": 403,
  "message": "无权限更新头像",
  "data": null
}
```

#### 只能更新自己的头像 (403)
```json
{
  "code": 403,
  "message": "只能更新自己的头像",
  "data": null
}
```

#### 用户不存在 (404)
```json
{
  "code": 404,
  "message": "用户不存在",
  "data": null
}
```

#### 文件为空 (400)
```json
{
  "code": 400,
  "message": "上传文件不能为空",
  "data": null
}
```

#### 文件过大 (400)
```json
{
  "code": 400,
  "message": "文件大小不能超过5MB",
  "data": null
}
```

#### 文件类型不支持 (400)
```json
{
  "code": 400,
  "message": "只支持上传图片文件（JPEG、PNG、GIF、WebP）",
  "data": null
}
```

#### 上传失败 (500)
```json
{
  "code": 500,
  "message": "头像上传失败",
  "data": null
}
```

## 使用示例

### cURL
```bash
# 更新自己的头像
curl -X POST "http://localhost:8080/api/users/1/avatar" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@/path/to/avatar.jpg"
```

### JavaScript (Fetch)
```javascript
const updateAvatar = async (userId, file) => {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await fetch(`/api/users/${userId}/avatar`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    },
    body: formData
  });
  
  return await response.json();
};

// 使用
const fileInput = document.querySelector('input[type="file"]');
const file = fileInput.files[0];

updateAvatar(1, file)
  .then(result => console.log('头像更新成功:', result))
  .catch(error => console.error('头像更新失败:', error));
```

### HTML 表单
```html
<form action="/api/users/1/avatar" method="POST" enctype="multipart/form-data">
  <input type="file" name="file" accept="image/*" required>
  <button type="submit">上传头像</button>
</form>
```

## 实现细节

### 文件命名规则
上传的文件会被重命名为：`user_{userId}_{UUID}.{extension}`

例如：`user_1_a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg`

### 旧头像处理
- 上传新头像时，会自动删除用户的旧头像文件
- 删除失败不会影响新头像的上传

### MinIO存储桶
- **桶名称**: `avatar`
- **自动创建**: 如果桶不存在，系统会自动创建
- **访问策略**: 公开读取，受限上传

## 数据库变更

执行以下SQL脚本添加权限：
```sql
-- 文件: mkcs-server/src/main/resources/db/migration/07_add_user_update_avatar_permission.sql
```

该脚本会：
1. 添加 `user:updateAvatar` 权限 (ID: 20)
2. 为超级管理员、系统管理员、普通用户角色分配该权限

## 相关文件

- **Controller**: `mkcs-server/src/main/java/cn/zjj/mkcsserver/controller/UserController.java`
- **MinIO工具类**: `mkcs-common/src/main/java/com/zjj/mkcscommon/utils/MinIOUtil.java`
- **Entity**: `mkcs-model/src/main/java/cn/zjj/mkcsmodel/entity/Users.java`
- **SQL**: `mkcs-server/src/main/resources/db/migration/07_add_user_update_avatar_permission.sql`

## 注意事项

1. **文件大小限制**: 确保Spring Boot配置允许上传5MB的文件
   ```yaml
   spring:
     servlet:
       multipart:
         max-file-size: 5MB
         max-request-size: 5MB
   ```

2. **MinIO配置**: 确保MinIO服务正常运行，并配置正确的连接信息
   ```yaml
   mkcs:
     minio:
       endpoint: http://localhost:9000
       access-key: minioadmin
       secret-key: minioadmin
       bucket-name: default
   ```

3. **存储桶策略**: 需要在MinIO中为avatar桶设置公开读取策略
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Principal": {"AWS": ["*"]},
         "Action": ["s3:GetObject"],
         "Resource": ["arn:aws:s3:::avatar/*"]
       }
     ]
   }
   ```
