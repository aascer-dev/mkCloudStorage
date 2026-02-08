-- 添加用户更新头像权限

-- 查询当前最大的权限ID（可选，用于确认）
-- SELECT MAX(id) FROM permissions;

-- 插入 user:updateAvatar 权限
-- 使用 ID 20（假设 19 已被占用）
-- 如果执行失败，请查询数据库中的最大ID并使用下一个可用ID
INSERT INTO permissions (id, name, description, created_at, updated_at) VALUES
(20, 'user:updateAvatar', '更新用户头像', NOW(), NOW());

-- 为超级管理员角色（ROLE_SUPER_ADMIN, id=1）添加该权限
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at) VALUES
(1, 20, NOW(), NOW());

-- 为系统管理员角色（ROLE_ADMIN, id=2）添加该权限
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at) VALUES
(2, 20, NOW(), NOW());

-- 为普通用户角色（ROLE_USER, id=3）添加该权限
-- 普通用户应该能够更新自己的头像
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at) VALUES
(3, 20, NOW(), NOW());
