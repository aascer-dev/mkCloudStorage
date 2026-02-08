# MinIO Avatar 存储桶配置指南

## 概述

avatar存储桶用于存储用户头像，配置为：
- **公开读取**: 任何人都可以访问和下载头像
- **受限上传**: 只有通过应用程序API且拥有权限的用户才能上传

## 自动创建

应用程序会在首次上传头像时自动创建avatar桶（如果不存在）。

## 手动配置存储桶策略

如果需要手动配置MinIO存储桶策略，请按以下步骤操作：

### 方法1: 使用MinIO Web控制台

1. 访问MinIO控制台：`http://localhost:9000`（或你的MinIO地址）
2. 使用管理员账号登录（默认：minioadmin/minioadmin）
3. 点击左侧菜单的 "Buckets"
4. 找到或创建 `avatar` 桶
5. 点击桶名称进入详情页
6. 选择 "Access Policy" 标签
7. 选择 "Custom" 并粘贴以下策略：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": ["*"]
      },
      "Action": [
        "s3:GetObject"
      ],
      "Resource": [
        "arn:aws:s3:::avatar/*"
      ]
    }
  ]
}
```

8. 点击 "Save" 保存

### 方法2: 使用MinIO Client (mc)

```bash
# 1. 安装MinIO Client
# macOS
brew install minio/stable/mc

# Linux
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/

# Windows
# 下载 https://dl.min.io/client/mc/release/windows-amd64/mc.exe

# 2. 配置MinIO别名
mc alias set myminio http://localhost:9000 minioadmin minioadmin

# 3. 创建avatar桶（如果不存在）
mc mb myminio/avatar

# 4. 设置公开读取策略
mc anonymous set download myminio/avatar
```

### 方法3: 使用AWS CLI

```bash
# 1. 安装AWS CLI
pip install awscli

# 2. 配置AWS CLI连接到MinIO
aws configure --profile minio
# AWS Access Key ID: minioadmin
# AWS Secret Access Key: minioadmin
# Default region name: us-east-1
# Default output format: json

# 3. 创建avatar桶
aws --profile minio --endpoint-url http://localhost:9000 s3 mb s3://avatar

# 4. 创建策略文件 avatar-policy.json
cat > avatar-policy.json << 'EOF'
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
EOF

# 5. 应用策略
aws --profile minio --endpoint-url http://localhost:9000 s3api put-bucket-policy \
  --bucket avatar \
  --policy file://avatar-policy.json
```

## 策略说明

### 公开读取策略详解

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",              // 允许访问
      "Principal": {"AWS": ["*"]},    // 所有人（匿名访问）
      "Action": ["s3:GetObject"],     // 只允许读取对象
      "Resource": ["arn:aws:s3:::avatar/*"]  // 应用于avatar桶中的所有对象
    }
  ]
}
```

### 为什么这样配置？

1. **公开读取**: 
   - 头像需要在网页、移动应用等各种场景中显示
   - 不需要认证即可访问，提高性能和用户体验
   - 只允许`GetObject`操作，不允许列出、删除或修改

2. **受限上传**:
   - 上传操作通过应用程序API控制
   - 需要用户登录并拥有`user:updateAvatar`权限
   - 防止恶意上传和滥用存储空间

## 验证配置

### 测试公开读取

```bash
# 假设已上传一个头像文件
curl http://localhost:9000/avatar/user_1_test.jpg

# 应该能够成功下载文件
```

### 测试受限上传（应该失败）

```bash
# 尝试直接上传（没有认证）
curl -X PUT http://localhost:9000/avatar/test.jpg \
  -H "Content-Type: image/jpeg" \
  --data-binary @test.jpg

# 应该返回 403 Forbidden 或类似错误
```

### 测试通过API上传（应该成功）

```bash
# 通过应用程序API上传（带认证）
curl -X POST http://localhost:8080/api/users/1/avatar \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@test.jpg"

# 应该返回成功响应
```

## 安全建议

1. **文件大小限制**: 在应用层限制文件大小（当前为5MB）
2. **文件类型验证**: 只允许图片格式（JPEG, PNG, GIF, WebP）
3. **文件名随机化**: 使用UUID生成唯一文件名，防止覆盖和猜测
4. **定期清理**: 考虑实现定期清理未使用的头像文件
5. **CDN加速**: 生产环境建议在MinIO前面加CDN加速访问
6. **HTTPS**: 生产环境必须使用HTTPS保护数据传输

## 生产环境配置

### 使用Nginx反向代理

```nginx
# 为avatar桶配置公开访问
location /avatar/ {
    proxy_pass http://minio:9000/avatar/;
    proxy_set_header Host $http_host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    # 缓存配置
    proxy_cache_valid 200 1d;
    proxy_cache_key $uri;
    add_header X-Cache-Status $upstream_cache_status;
}

# API上传接口（需要认证）
location /api/users/ {
    proxy_pass http://app:8080/api/users/;
    # ... 其他配置
}
```

### 使用CDN

1. 配置CDN源站指向MinIO的avatar桶
2. 设置合适的缓存策略（如7天）
3. 启用HTTPS
4. 配置防盗链（可选）

## 故障排查

### 问题1: 无法访问头像

**症状**: 浏览器显示403 Forbidden

**解决方案**:
1. 检查桶策略是否正确配置
2. 确认文件确实存在于avatar桶中
3. 检查MinIO服务是否正常运行

### 问题2: 上传失败

**症状**: API返回"头像上传失败"

**解决方案**:
1. 检查MinIO连接配置
2. 确认MinIO服务可访问
3. 查看应用日志获取详细错误信息
4. 确认avatar桶存在或应用有权限创建桶

### 问题3: 旧头像未删除

**症状**: 上传新头像后，旧头像仍然存在

**解决方案**:
1. 这是正常的，删除失败不会影响新头像上传
2. 检查应用日志查看删除失败原因
3. 可以手动清理或实现定期清理任务

## 相关文档

- [MinIO官方文档](https://min.io/docs/minio/linux/index.html)
- [S3存储桶策略](https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucket-policies.html)
- [UPDATE_AVATAR_API.md](./UPDATE_AVATAR_API.md) - 头像上传API文档
