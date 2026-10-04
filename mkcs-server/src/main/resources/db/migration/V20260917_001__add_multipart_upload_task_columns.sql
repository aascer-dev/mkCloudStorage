-- Adds persistent state needed for browser-to-MinIO Multipart Upload.
-- Safe to rerun against an existing MySQL 8 database.
-- Rollback: deploy the previous application first; columns and indexes can then be dropped manually.

SET @schema_name = DATABASE();

SET @column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'upload_tasks' AND column_name = 'minio_upload_id');
SET @statement = IF(@column_exists = 0, 'ALTER TABLE upload_tasks ADD COLUMN minio_upload_id VARCHAR(512) NULL COMMENT ''MinIO Multipart Upload ID'' AFTER temp_path', 'SELECT 1');
PREPARE migration_statement FROM @statement; EXECUTE migration_statement; DEALLOCATE PREPARE migration_statement;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'upload_tasks' AND column_name = 'object_key');
SET @statement = IF(@column_exists = 0, 'ALTER TABLE upload_tasks ADD COLUMN object_key VARCHAR(512) NULL COMMENT ''服务端生成的最终对象键'' AFTER minio_upload_id', 'SELECT 1');
PREPARE migration_statement FROM @statement; EXECUTE migration_statement; DEALLOCATE PREPARE migration_statement;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'upload_tasks' AND column_name = 'mime_type');
SET @statement = IF(@column_exists = 0, 'ALTER TABLE upload_tasks ADD COLUMN mime_type VARCHAR(100) NULL COMMENT ''文件MIME类型'' AFTER object_key', 'SELECT 1');
PREPARE migration_statement FROM @statement; EXECUTE migration_statement; DEALLOCATE PREPARE migration_statement;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'upload_tasks' AND index_name = 'uk_minio_upload_id');
SET @statement = IF(@index_exists = 0, 'ALTER TABLE upload_tasks ADD UNIQUE INDEX uk_minio_upload_id (minio_upload_id)', 'SELECT 1');
PREPARE migration_statement FROM @statement; EXECUTE migration_statement; DEALLOCATE PREPARE migration_statement;

SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'upload_tasks' AND index_name = 'idx_upload_tasks_status_expire');
SET @statement = IF(@index_exists = 0, 'ALTER TABLE upload_tasks ADD INDEX idx_upload_tasks_status_expire (status, expire_time)', 'SELECT 1');
PREPARE migration_statement FROM @statement; EXECUTE migration_statement; DEALLOCATE PREPARE migration_statement;
