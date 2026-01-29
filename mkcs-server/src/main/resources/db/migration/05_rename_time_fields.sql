-- 将所有表的 created_time 和 update_time 字段重命名为 created_at 和 updated_at
-- 执行前请备份数据库！

-- 1. users 表
ALTER TABLE users 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 2. files 表
ALTER TABLE files 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 3. file_contents 表
ALTER TABLE file_contents 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 4. file_favorites 表
ALTER TABLE file_favorites 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 5. storage_buckets 表
ALTER TABLE storage_buckets 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 6. shares 表
ALTER TABLE shares 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 7. upload_tasks 表
ALTER TABLE upload_tasks 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 8. upload_task_chunks 表
ALTER TABLE upload_task_chunks 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 9. roles 表
ALTER TABLE roles 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 10. permissions 表
ALTER TABLE permissions 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 11. role_permissions 表
ALTER TABLE role_permissions 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 12. user_roles 表
ALTER TABLE user_roles 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';

-- 13. oauth_identities 表
ALTER TABLE oauth_identities 
CHANGE COLUMN created_time created_at DATETIME COMMENT '创建时间',
CHANGE COLUMN update_time updated_at DATETIME COMMENT '更新时间';