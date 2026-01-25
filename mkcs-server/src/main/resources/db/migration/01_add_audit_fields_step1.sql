-- 第一步：为核心表添加审计字段
-- 建议分步执行，每执行一个表后检查结果

-- 1. users 表 - 用户核心表
ALTER TABLE users 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 检查结果
-- DESCRIBE users;

-- 2. files 表 - 文件元数据表
ALTER TABLE files 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 检查结果
-- DESCRIBE files;

-- 3. file_contents 表 - 文件内容表
ALTER TABLE file_contents 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 检查结果
-- DESCRIBE file_contents;