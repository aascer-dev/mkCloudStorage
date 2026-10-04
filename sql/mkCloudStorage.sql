/*
 Navicat Premium Dump SQL

 Source Server         : Mysql8
 Source Server Type    : MySQL
 Source Server Version : 80407 (8.4.7)
 Source Host           : 127.0.0.1:3306
 Source Schema         : mkCloudStorage

 Target Server Type    : MySQL
 Target Server Version : 80407 (8.4.7)
 File Encoding         : 65001

 Date: 16/09/2026 21:05:24
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for file_contents
-- ----------------------------
DROP TABLE IF EXISTS `file_contents`;
CREATE TABLE `file_contents`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `content_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件内容 sha256 hash（64位十六进制）',
  `size` bigint UNSIGNED NOT NULL COMMENT '文件大小（字节）',
  `storage_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实际存储路径（对象存储 key 或本地路径）',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MIME 类型',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0=已删除/失效, 1=正常可用',
  `reference_count` int UNSIGNED NOT NULL DEFAULT 1 COMMENT '被引用的文件元数据记录数，用于安全删除判断',
  `random_offset` bigint UNSIGNED NULL DEFAULT NULL COMMENT '秒传随机位置校验的起始字节位置',
  `random_length` int UNSIGNED NULL DEFAULT NULL COMMENT '秒传随机位置校验的字节长度',
  `random_position_hash` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '秒传随机位置数据块的MD5 hash',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_content_hash`(`content_hash` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_reference_count`(`reference_count` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件内容去重表 - 相同内容只存一份' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_favorites
-- ----------------------------
DROP TABLE IF EXISTS `file_favorites`;
CREATE TABLE `file_favorites`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '收藏的用户ID',
  `file_id` bigint UNSIGNED NOT NULL COMMENT '被收藏的文件或文件夹ID',
  `notes` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '收藏备注/标签（可选，用户自定义）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0=已取消收藏, 1=正常收藏',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_file`(`user_id` ASC, `file_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_file_id`(`file_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_favorites_file` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_favorites_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件/文件夹收藏表 - 用户星标/收藏功能' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_permissions
-- ----------------------------
DROP TABLE IF EXISTS `file_permissions`;
CREATE TABLE `file_permissions`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` bigint UNSIGNED NOT NULL COMMENT '关联的文件/文件夹ID (files.id)',
  `subject_type` tinyint UNSIGNED NOT NULL COMMENT '主体类型：1=用户, 2=角色, 3=部门/团队, 4=分享链接, 5=公开(所有人), 6=企业内所有成员, 7=匿名访问',
  `subject_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '主体ID (用户ID/角色ID/部门ID/shares.id)，公开类型时可为NULL',
  `perm_mask` int UNSIGNED NOT NULL DEFAULT 1 COMMENT '权限位掩码 (位运算)',
  `is_inherited` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否继承父级权限: 0=显式权限(打破继承), 1=从父级继承',
  `inherited_from` bigint UNSIGNED NULL DEFAULT NULL COMMENT '继承来源的文件ID (优化查询)',
  `expired_at` datetime NULL DEFAULT NULL COMMENT '权限过期时间 (NULL=永久有效)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0=已撤销/失效, 1=有效',
  `granted_by` bigint UNSIGNED NOT NULL COMMENT '授予权限的用户ID (审计)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID (通常等于granted_by)',
  `updated_by` bigint UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_file_subject`(`file_id` ASC, `subject_type` ASC, `subject_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_file_id`(`file_id` ASC) USING BTREE,
  INDEX `idx_subject`(`subject_type` ASC, `subject_id` ASC) USING BTREE,
  INDEX `idx_granted_by`(`granted_by` ASC) USING BTREE,
  INDEX `idx_expired_at`(`expired_at` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_inherited`(`file_id` ASC, `is_inherited` ASC) USING BTREE,
  CONSTRAINT `fk_fp_file` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_fp_granted_by` FOREIGN KEY (`granted_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件/文件夹权限控制表 (ACL) - 支持继承、分享、角色、部门等' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for files
-- ----------------------------
DROP TABLE IF EXISTS `files`;
CREATE TABLE `files`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bucket_id` bigint UNSIGNED NOT NULL COMMENT '所属存储桶ID',
  `owner_id` bigint UNSIGNED NOT NULL COMMENT '上传者/拥有者用户ID',
  `content_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '关联的文件内容实体ID（文件夹时为 NULL）',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名或文件夹名（用户可见）',
  `parent_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '父文件夹ID，根目录为 NULL',
  `is_folder` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否文件夹：0=文件, 1=文件夹',
  `size` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小（字节，文件夹为 0 或子项总和视需求）',
  `path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '完整路径（冗余字段，便于查询和显示，如 /folder1/sub/file.txt）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0=已删除（软删除）, 1=正常, 2=回收站（可选）',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  `last_accessed_time` datetime NULL DEFAULT NULL COMMENT '最后一次访问/打开/预览/下载的时间，用于“最近使用”排序',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_bucket_parent_filename`(`bucket_id` ASC, `parent_id` ASC, `filename` ASC) USING BTREE,
  INDEX `idx_bucket_id`(`bucket_id` ASC) USING BTREE,
  INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE,
  INDEX `idx_content_id`(`content_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_owner_last_accessed`(`owner_id` ASC, `status` ASC, `last_accessed_time` DESC) USING BTREE,
  CONSTRAINT `fk_files_bucket` FOREIGN KEY (`bucket_id`) REFERENCES `storage_buckets` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_files_content` FOREIGN KEY (`content_id`) REFERENCES `file_contents` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_files_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_files_parent` FOREIGN KEY (`parent_id`) REFERENCES `files` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for oauth_identities
-- ----------------------------
DROP TABLE IF EXISTS `oauth_identities`;
CREATE TABLE `oauth_identities`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '关联本地用户ID',
  `provider` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '平台标识：github, google 等',
  `identifier` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '第三方平台的唯一 ID，如 GitHub 数字 ID',
  `credential` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '可选，存储 AccessToken 或额外信息',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_identifier`(`provider` ASC, `identifier` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_oauth_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 2034903248723271683 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '第三方身份关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for permissions
-- ----------------------------
DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '唯一权限名，如 file:delete, sys:user:ban',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '权限描述',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限点定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for role_permissions
-- ----------------------------
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_id` bigint UNSIGNED NOT NULL COMMENT '权限ID',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_permission`(`role_id` ASC, `permission_id` ASC) USING BTREE,
  INDEX `idx_permission_id`(`permission_id` ASC) USING BTREE,
  CONSTRAINT `fk_role_permissions_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_role_permissions_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 58 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色-权限关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '唯一角色名，如 ROLE_ADMIN, ROLE_USER',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色描述',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色定义表' ROW_FORMAT = Dynamic;

-- 最小 RBAC 种子。注册流程按角色名读取 ROLE_USER，不依赖固定主键值。
INSERT INTO `roles` (`name`, `description`) VALUES
('ROLE_USER', '普通用户');

-- ----------------------------
-- Table structure for shares
-- ----------------------------
DROP TABLE IF EXISTS `shares`;
CREATE TABLE `shares`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `share_type` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '分享类型：1=链接分享, 2=协作邀请（指定用户）, 3=团队共享',
  `sharer_id` bigint UNSIGNED NOT NULL COMMENT '分享者用户ID',
  `receiver_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '接收者用户ID（协作邀请时填写，链接分享可为空）',
  `file_id` bigint UNSIGNED NOT NULL COMMENT '被分享的文件/文件夹ID（files表id）',
  `permission` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '权限：1=只读（预览/下载）, 2=可下载+转存, 3=可编辑（协作）',
  `share_link` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分享链接（链接分享时生成唯一码，如 /s/abc123）',
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '提取码（可选）',
  `expired_at` datetime NULL DEFAULT NULL COMMENT '链接过期时间（NULL=永久）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0=已取消/失效, 1=有效, 2=已过期',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_share_link`(`share_link` ASC) USING BTREE,
  INDEX `idx_receiver_id`(`receiver_id` ASC) USING BTREE,
  INDEX `idx_sharer_id`(`sharer_id` ASC) USING BTREE,
  INDEX `idx_file_id`(`file_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_shares_file` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_shares_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_shares_sharer` FOREIGN KEY (`sharer_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件/文件夹分享记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for storage_buckets
-- ----------------------------
DROP TABLE IF EXISTS `storage_buckets`;
CREATE TABLE `storage_buckets`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `owner_id` bigint UNSIGNED NOT NULL COMMENT '拥有者用户ID（创建者/付费主体）',
  `bucket_type` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '桶类型：0=个人私有, 1=团队/共享',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '桶名称（用户可见）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '桶描述，可选',
  `total_storage` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '总配额（字节）',
  `used_storage` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '已使用量（字节）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0=禁用, 1=正常, 2=只读（扩展用）,3=创建中',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_owner_type_name`(`owner_id` ASC, `bucket_type` ASC, `name` ASC) USING BTREE,
  INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE,
  INDEX `idx_bucket_type`(`bucket_type` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2034903248198983683 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '存储桶 - 容量与归属的核心表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for upload_task_chunks
-- ----------------------------
DROP TABLE IF EXISTS `upload_task_chunks`;
CREATE TABLE `upload_task_chunks`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint UNSIGNED NOT NULL COMMENT '所属上传任务ID',
  `chunk_index` int UNSIGNED NOT NULL COMMENT '分片序号（从0开始）',
  `chunk_size` bigint UNSIGNED NOT NULL COMMENT '本分片大小',
  `uploaded_size` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '已上传字节（支持部分上传）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0=未开始, 1=进行中, 2=完成, 3=失败',
  `etag` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '对象存储返回的 ETag（用于合并验证）',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_chunk`(`task_id` ASC, `chunk_index` ASC) USING BTREE,
  CONSTRAINT `fk_task_chunks_task` FOREIGN KEY (`task_id`) REFERENCES `upload_tasks` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '上传任务分片明细表（断点续传核心）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for upload_tasks
-- ----------------------------
DROP TABLE IF EXISTS `upload_tasks`;
CREATE TABLE `upload_tasks`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '上传用户ID',
  `bucket_id` bigint UNSIGNED NOT NULL COMMENT '目标存储桶ID',
  `parent_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '目标父文件夹ID（NULL=根目录）',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名（原始名）',
  `total_size` bigint UNSIGNED NOT NULL COMMENT '文件总大小（字节）',
  `uploaded_size` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '已上传大小（字节）',
  `chunk_size` int UNSIGNED NULL DEFAULT NULL COMMENT '分片大小（字节），NULL=未分片或小文件',
  `total_chunks` int UNSIGNED NULL DEFAULT NULL COMMENT '总分片数',
  `uploaded_chunks` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '已上传分片数',
  `task_type` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '任务类型：1=上传, 2=下载（扩展用）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0=等待中, 1=进行中, 2=暂停, 3=完成, 4=失败, 5=取消',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因（可选）',
  `file_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件整体 sha256（用于秒传/去重判断）',
  `temp_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '临时存储路径（分片临时目录或对象存储临时key前缀）',
  `minio_upload_id` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MinIO Multipart Upload ID',
  `object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '服务端生成的最终对象键',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件MIME类型',
  `final_file_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '完成后关联的 files.id（成功后填写）',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '任务过期时间（未完成可自动清理）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id_status`(`user_id` ASC, `status` ASC, `updated_at` DESC) USING BTREE,
  INDEX `idx_bucket_id`(`bucket_id` ASC) USING BTREE,
  INDEX `idx_final_file_id`(`final_file_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_minio_upload_id`(`minio_upload_id` ASC) USING BTREE,
  INDEX `idx_upload_tasks_status_expire`(`status` ASC, `expire_time` ASC) USING BTREE,
  CONSTRAINT `fk_upload_tasks_bucket` FOREIGN KEY (`bucket_id`) REFERENCES `storage_buckets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_upload_tasks_file` FOREIGN KEY (`final_file_id`) REFERENCES `files` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_upload_tasks_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '上传/传输任务表 - 支持断点续传和任务管理' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_roles
-- ----------------------------
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles`  (
  `id` bigint NOT NULL COMMENT '自增id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`user_id`, `role_id`, `id`) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE,
  CONSTRAINT `fk_user_roles_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_roles_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `current_bucket_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '当前默认使用的存储桶ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '唯一内部系统登录名/标识',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '显示昵称，允许重复',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '加密存储，OAuth 用户可为空',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '可选，不唯一',
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0: 禁用, 1: 正常',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint NULL DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  INDEX `fk_users_current_bucket`(`current_bucket_id` ASC) USING BTREE,
  CONSTRAINT `fk_users_current_bucket` FOREIGN KEY (`current_bucket_id`) REFERENCES `storage_buckets` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 2034903247100076035 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户核心表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
