-- 初始化RBAC测试数据

-- 插入角色数据
INSERT INTO roles (id, name, description, created_time, updated_time, deleted) VALUES
(1, 'ROLE_SUPER_ADMIN', '超级管理员', NOW(), NOW(), 0),
(2, 'ROLE_ADMIN', '系统管理员', NOW(), NOW(), 0),
(3, 'ROLE_USER', '普通用户', NOW(), NOW(), 0),
(4, 'ROLE_GUEST', '访客', NOW(), NOW(), 0);

-- 插入权限数据
INSERT INTO permissions (id, name, description, created_time, updated_time, deleted) VALUES
-- 系统管理权限
(1, 'sys:user:create', '创建用户', NOW(), NOW(), 0),
(2, 'sys:user:update', '更新用户', NOW(), NOW(), 0),
(3, 'sys:user:delete', '删除用户', NOW(), NOW(), 0),
(4, 'sys:user:view', '查看用户', NOW(), NOW(), 0),
(5, 'sys:user:list', '用户列表', NOW(), NOW(), 0),
(6, 'sys:role:manage', '角色管理', NOW(), NOW(), 0),
(7, 'sys:permission:manage', '权限管理', NOW(), NOW(), 0),

-- 文件管理权限
(8, 'file:upload', '文件上传', NOW(), NOW(), 0),
(9, 'file:download', '文件下载', NOW(), NOW(), 0),
(10, 'file:delete', '文件删除', NOW(), NOW(), 0),
(11, 'file:share', '文件分享', NOW(), NOW(), 0),
(12, 'file:manage', '文件管理', NOW(), NOW(), 0),
(13, 'file:upload:image', '上传图片', NOW(), NOW(), 0),
(14, 'file:upload:document', '上传文档', NOW(), NOW(), 0),

-- 存储管理权限
(15, 'storage:bucket:create', '创建存储桶', NOW(), NOW(), 0),
(16, 'storage:bucket:delete', '删除存储桶', NOW(), NOW(), 0),
(17, 'storage:quota:manage', '配额管理', NOW(), NOW(), 0),
(18, 'storage:manage', '存储管理', NOW(), NOW(), 0);

-- 角色权限关联
INSERT INTO role_permissions (id, role_id, permission_id, created_time, updated_time, deleted) VALUES
-- 超级管理员拥有所有权限
(1, 1, 1, NOW(), NOW(), 0), (2, 1, 2, NOW(), NOW(), 0), (3, 1, 3, NOW(), NOW(), 0),
(4, 1, 4, NOW(), NOW(), 0), (5, 1, 5, NOW(), NOW(), 0), (6, 1, 6, NOW(), NOW(), 0),
(7, 1, 7, NOW(), NOW(), 0), (8, 1, 8, NOW(), NOW(), 0), (9, 1, 9, NOW(), NOW(), 0),
(10, 1, 10, NOW(), NOW(), 0), (11, 1, 11, NOW(), NOW(), 0), (12, 1, 12, NOW(), NOW(), 0),
(13, 1, 13, NOW(), NOW(), 0), (14, 1, 14, NOW(), NOW(), 0), (15, 1, 15, NOW(), NOW(), 0),
(16, 1, 16, NOW(), NOW(), 0), (17, 1, 17, NOW(), NOW(), 0), (18, 1, 18, NOW(), NOW(), 0),

-- 系统管理员权限
(19, 2, 1, NOW(), NOW(), 0), (20, 2, 2, NOW(), NOW(), 0), (21, 2, 3, NOW(), NOW(), 0),
(22, 2, 4, NOW(), NOW(), 0), (23, 2, 5, NOW(), NOW(), 0), (24, 2, 6, NOW(), NOW(), 0),
(25, 2, 8, NOW(), NOW(), 0), (26, 2, 9, NOW(), NOW(), 0), (27, 2, 10, NOW(), NOW(), 0),
(28, 2, 11, NOW(), NOW(), 0), (29, 2, 12, NOW(), NOW(), 0), (30, 2, 13, NOW(), NOW(), 0),
(31, 2, 14, NOW(), NOW(), 0), (32, 2, 15, NOW(), NOW(), 0), (33, 2, 17, NOW(), NOW(), 0),

-- 普通用户权限
(34, 3, 4, NOW(), NOW(), 0), (35, 3, 5, NOW(), NOW(), 0), (36, 3, 8, NOW(), NOW(), 0),
(37, 3, 9, NOW(), NOW(), 0), (38, 3, 11, NOW(), NOW(), 0), (39, 3, 13, NOW(), NOW(), 0),
(40, 3, 14, NOW(), NOW(), 0),

-- 访客权限
(41, 4, 4, NOW(), NOW(), 0), (42, 4, 5, NOW(), NOW(), 0), (43, 4, 9, NOW(), NOW(), 0);

-- 创建测试用户（如果不存在）
INSERT IGNORE INTO users (id, username, nickname, password, email, status, created_time, updated_time, deleted) VALUES
(1, 'admin', '超级管理员', '$2a$12$encrypted_password_here', 'admin@example.com', 1, NOW(), NOW(), 0),
(2, 'manager', '系统管理员', '$2a$12$encrypted_password_here', 'manager@example.com', 1, NOW(), NOW(), 0),
(3, 'user', '普通用户', '$2a$12$encrypted_password_here', 'user@example.com', 1, NOW(), NOW(), 0),
(4, 'guest', '访客用户', '$2a$12$encrypted_password_here', 'guest@example.com', 1, NOW(), NOW(), 0);

-- 用户角色关联
INSERT INTO user_roles (id, user_id, role_id, created_time, updated_time, deleted) VALUES
(1, 1, 1, NOW(), NOW(), 0), -- admin 用户拥有超级管理员角色
(2, 2, 2, NOW(), NOW(), 0), -- manager 用户拥有系统管理员角色
(3, 3, 3, NOW(), NOW(), 0), -- user 用户拥有普通用户角色
(4, 4, 4, NOW(), NOW(), 0); -- guest 用户拥有访客角色