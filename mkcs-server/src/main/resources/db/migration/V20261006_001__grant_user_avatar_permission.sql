-- Allow regular users to update their own avatar.
-- Both statements are idempotent so this also repairs existing databases.

INSERT INTO permissions (name, description)
VALUES ('user:updateAvatar', '更新自己的头像')
ON DUPLICATE KEY UPDATE description = '更新自己的头像';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'user:updateAvatar'
WHERE r.name = 'ROLE_USER'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = r.id
        AND existing.permission_id = p.id
  );
