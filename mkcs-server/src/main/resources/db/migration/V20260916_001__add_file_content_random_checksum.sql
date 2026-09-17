-- Adds the random-position checksum required before a duplicate upload can be accepted.
-- The information_schema checks make reruns safe on the MySQL 8.4 deployment image.
-- Rollback: columns are nullable and do not require a data rollback. Remove them only after
-- all application instances that read them have been rolled back.

SET @schema_name = DATABASE();

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'file_contents'
      AND column_name = 'random_offset'
);
SET @statement = IF(@column_exists = 0,
    'ALTER TABLE file_contents ADD COLUMN random_offset BIGINT UNSIGNED NULL COMMENT ''秒传随机位置校验的起始字节位置'' AFTER reference_count',
    'SELECT 1');
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'file_contents'
      AND column_name = 'random_length'
);
SET @statement = IF(@column_exists = 0,
    'ALTER TABLE file_contents ADD COLUMN random_length INT UNSIGNED NULL COMMENT ''秒传随机位置校验的字节长度'' AFTER random_offset',
    'SELECT 1');
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'file_contents'
      AND column_name = 'random_position_hash'
);
SET @statement = IF(@column_exists = 0,
    'ALTER TABLE file_contents ADD COLUMN random_position_hash CHAR(32) NULL COMMENT ''秒传随机位置数据块的MD5 hash'' AFTER random_length',
    'SELECT 1');
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
