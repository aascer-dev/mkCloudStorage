-- 增强秒传安全性：添加随机位置校验字段
-- 执行日期：2026-03-24
-- 目的：为file_contents表添加随机位置校验相关字段，用于秒传时的双重校验

-- 检查是否已添加过字段（如果没有则添加）
ALTER TABLE file_contents
ADD COLUMN IF NOT EXISTS random_offset BIGINT DEFAULT NULL COMMENT '随机位置校验 - 起始字节位置（用于秒传安全验证）',
ADD COLUMN IF NOT EXISTS random_length INT DEFAULT 262144 COMMENT '随机位置校验 - 字节长度（默认256KB=262144字节）',
ADD COLUMN IF NOT EXISTS random_position_hash VARCHAR(255) DEFAULT NULL COMMENT '随机位置校验 - 该位置数据的MD5 hash（用于秒传验证）';

-- 添加索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_random_position_hash ON file_contents(random_position_hash);

-- 添加字段描述信息
-- 注意：以下字段含义说明：
-- random_offset: 文件中用于安全校验的随机位置的起始字节
-- random_length: 从random_offset开始往后读取的字节数（通常为256KB）
-- random_position_hash: 该位置数据块的MD5哈希值
--
-- 校验原理：
-- 1. 文件上传时：生成一个随机位置的参数，读取该位置的数据块并计算hash
-- 2. 秒传时：前端再次上传文件，后端从相同的随机位置读取数据块并计算hash
-- 3. 对比：如果两个hash值相同，说明文件内容确实存在且没有被篡改
-- 4. 优势：比单纯的全文件hash相比，随机位置校验可以进一步降低碰撞概率
