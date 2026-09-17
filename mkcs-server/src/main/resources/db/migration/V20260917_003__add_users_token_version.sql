ALTER TABLE users
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 1 COMMENT '认证版本，用于使旧令牌失效' AFTER status;
