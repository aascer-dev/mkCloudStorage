# 数据库迁移指南 - 添加审计字段

本目录包含为所有表添加 `created_by` 和 `updated_by` 审计字段的 SQL 脚本。

## 执行顺序

### 方式一：一次性执行（适合开发环境）
```sql
-- 执行完整脚本
source add_audit_fields.sql;
```

### 方式二：分步执行（推荐用于生产环境）
```sql
-- 第一步：核心表
source 01_add_audit_fields_step1.sql;

-- 第二步：业务表
source 02_add_audit_fields_step2.sql;

-- 第三步：权限表
source 03_add_audit_fields_step3.sql;

-- 第四步：更新现有数据
source 04_update_existing_data.sql;
```

## 执行前准备

1. **备份数据库**
   ```bash
   mysqldump -u username -p database_name > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **检查表结构**
   ```sql
   SHOW TABLES;
   DESCRIBE users;  -- 检查现有字段
   ```

## 执行后验证

1. **检查字段是否添加成功**
   ```sql
   DESCRIBE users;
   DESCRIBE files;
   -- 应该看到 created_by 和 updated_by 字段
   ```

2. **检查数据是否正确更新**
   ```sql
   SELECT id, created_time, update_time, created_by, updated_by 
   FROM users LIMIT 5;
   ```

3. **验证自动填充功能**
   - 启动应用
   - 创建或更新一条记录
   - 检查 created_by 和 updated_by 是否自动填充

## 字段说明

- `created_by`: 创建人ID，BIGINT 类型，记录创建该记录的用户ID
- `updated_by`: 更新人ID，BIGINT 类型，记录最后更新该记录的用户ID
- 默认值 -1 表示系统用户（未登录或系统操作）

## 涉及的表

1. **核心表**
   - users（用户表）
   - files（文件表）
   - file_contents（文件内容表）

2. **业务表**
   - file_favorites（文件收藏表）
   - storage_buckets（存储桶表）
   - shares（分享表）
   - upload_tasks（上传任务表）
   - upload_task_chunks（上传分片表）

3. **权限表**
   - roles（角色表）
   - permissions（权限表）
   - role_permissions（角色权限关联表）
   - user_roles（用户角色关联表）
   - oauth_identities（OAuth身份表）

## 注意事项

1. 执行前务必备份数据库
2. 建议在维护窗口期间执行
3. 分步执行可以降低风险
4. 执行后需要重启应用以使 MyBatis-Plus 自动填充生效
5. 如果表中数据量很大，UPDATE 操作可能需要较长时间