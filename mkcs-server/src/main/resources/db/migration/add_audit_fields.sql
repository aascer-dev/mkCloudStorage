-- 为所有表添加审计字段：created_by 和 updated_by
-- 执行前请备份数据库！

-- 1. users 表
ALTER TABLE users 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 2. files 表
ALTER TABLE files 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 3. file_contents 表
ALTER TABLE file_contents 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 4. file_favorites 表
ALTER TABLE file_favorites 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 5. storage_buckets 表
ALTER TABLE storage_buckets 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 6. shares 表
ALTER TABLE shares 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 7. upload_tasks 表
ALTER TABLE upload_tasks 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 8. upload_task_chunks 表
ALTER TABLE upload_task_chunks 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 9. roles 表
ALTER TABLE roles 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 10. permissions 表
ALTER TABLE permissions 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 11. role_permissions 表
ALTER TABLE role_permissions 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 12. user_roles 表
ALTER TABLE user_roles 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 13. oauth_identities 表
ALTER TABLE oauth_identities 
ADD COLUMN created_by BIGINT COMMENT '创建人ID' AFTER updated_at,
ADD COLUMN updated_by BIGINT COMMENT '更新人ID' AFTER created_by;

-- 为现有数据设置默认值（可选）
-- 将所有现有记录的 created_by 和 updated_by 设置为 -1（系统用户）
UPDATE users SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE files SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE file_contents SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE file_favorites SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE storage_buckets SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE shares SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE upload_tasks SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE upload_task_chunks SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE roles SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE permissions SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE role_permissions SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE user_roles SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE oauth_identities SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;