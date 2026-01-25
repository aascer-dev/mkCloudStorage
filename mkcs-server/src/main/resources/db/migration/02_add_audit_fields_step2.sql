-- 第二步：为其他业务表添加审计字段

-- 4. file_favorites 表 - 文件收藏表
ALTER TABLE file_favorites 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 5. storage_buckets 表 - 存储桶表
ALTER TABLE storage_buckets 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 6. shares 表 - 分享表
ALTER TABLE shares 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 7. upload_tasks 表 - 上传任务表
ALTER TABLE upload_tasks 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 8. upload_task_chunks 表 - 上传任务分片表
ALTER TABLE upload_task_chunks 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';