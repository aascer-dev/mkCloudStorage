-- 第三步：为权限相关表添加审计字段

-- 9. roles 表 - 角色表
ALTER TABLE roles 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 10. permissions 表 - 权限表
ALTER TABLE permissions 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 11. role_permissions 表 - 角色权限关联表
ALTER TABLE role_permissions 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 12. user_roles 表 - 用户角色关联表
ALTER TABLE user_roles 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';

-- 13. oauth_identities 表 - OAuth身份表
ALTER TABLE oauth_identities 
ADD COLUMN created_by BIGINT COMMENT '创建人ID',
ADD COLUMN updated_by BIGINT COMMENT '更新人ID';