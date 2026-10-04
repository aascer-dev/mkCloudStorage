-- Ensures existing databases contain the role assigned during user registration.
-- Rerunnable: the unique name constraint makes this an upsert.
-- Rollback: remove ROLE_USER only after all user_roles references have been reassigned or removed.

INSERT INTO roles (name, description)
VALUES ('ROLE_USER', '普通用户')
ON DUPLICATE KEY UPDATE description = VALUES(description);
