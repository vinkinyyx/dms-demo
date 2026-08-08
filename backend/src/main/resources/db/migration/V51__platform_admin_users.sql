-- V51: 平台后台管理员账号（独立于业务 users 表）
-- 第一期仅一个固定后台管理员，不做平台后台 RBAC。
CREATE TABLE IF NOT EXISTS platform_admin_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(128),
    phone VARCHAR(32),
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    password_updated_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    last_login_ip VARCHAR(64),
    login_fail_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    attrs JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_platform_admin_status ON platform_admin_users(status);

COMMENT ON TABLE platform_admin_users IS '平台后台管理员账号，与业务 users 表隔离';

-- 默认平台后台管理员：admin / Sh123456（bcrypt 成本因子 10）
INSERT INTO platform_admin_users (username, name, password_hash, status, must_change_password, password_updated_at)
SELECT 'admin', '平台管理员', '$2a$10$hfbr8i5pCRVv.11B2V.xC.b.1TkKfCL9bILnKwBkJg/OdJmYSP0N.', 'active', false, now()
WHERE NOT EXISTS (SELECT 1 FROM platform_admin_users WHERE username = 'admin');
