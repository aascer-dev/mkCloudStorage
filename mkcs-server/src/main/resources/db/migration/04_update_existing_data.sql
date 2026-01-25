-- 第四步：为现有数据设置默认值
-- 将所有现有记录的 created_by 和 updated_by 设置为 -1（系统用户）

-- 更新核心表
UPDATE users SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE files SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE file_contents SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;

-- 更新业务表
UPDATE file_favorites SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE storage_buckets SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE shares SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE upload_tasks SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE upload_task_chunks SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;

-- 更新权限表
UPDATE roles SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE permissions SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE role_permissions SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE user_roles SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;
UPDATE oauth_identities SET created_by = -1, updated_by = -1 WHERE created_by IS NULL;

-- 验证更新结果
SELECT 'users' as table_name, COUNT(*) as total_records, 
       COUNT(created_by) as created_by_filled, COUNT(updated_by) as updated_by_filled 
FROM users
UNION ALL
SELECT 'files', COUNT(*), COUNT(created_by), COUNT(updated_by) FROM files
UNION ALL
SELECT 'file_contents', COUNT(*), COUNT(created_by), COUNT(updated_by) FROM file_contents;