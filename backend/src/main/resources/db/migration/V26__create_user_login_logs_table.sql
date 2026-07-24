-- V26: 创建用户登录日志表
-- 记录用户登录成功/失败信息，支持管理员审计

CREATE TABLE IF NOT EXISTS user_login_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID,
    user_id BIGINT,
    login_type VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
    ip VARCHAR(50),
    user_agent VARCHAR(512),
    success BOOLEAN NOT NULL DEFAULT true,
    fail_reason VARCHAR(512),
    at_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_user_login_logs_tenant ON user_login_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_login_logs_user ON user_login_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_user_login_logs_time ON user_login_logs(at_time DESC);
CREATE INDEX IF NOT EXISTS idx_user_login_logs_success ON user_login_logs(success);

-- 注释
COMMENT ON TABLE user_login_logs IS '用户登录日志';
COMMENT ON COLUMN user_login_logs.tenant_id IS '租户ID';
COMMENT ON COLUMN user_login_logs.user_id IS '用户ID';
COMMENT ON COLUMN user_login_logs.login_type IS '登录类型: PASSWORD/WECHAT/OTHER';
COMMENT ON COLUMN user_login_logs.ip IS '登录IP';
COMMENT ON COLUMN user_login_logs.user_agent IS '用户代理';
COMMENT ON COLUMN user_login_logs.success IS '是否成功';
COMMENT ON COLUMN user_login_logs.fail_reason IS '失败原因';
COMMENT ON COLUMN user_login_logs.at_time IS '登录时间';
