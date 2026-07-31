-- V24: 审计日志表 (V5 已创建 audit_logs 分区表)
-- V5 中 audit_logs 已有字段: id, tenant_id, user_id, action, resource_type, resource_id, before, after, ip, user_agent, at_time
-- 本次只为 audit_logs 添加全局请求相关的字段（如尚不存在）
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS request_path VARCHAR(500),
    ADD COLUMN IF NOT EXISTS http_method VARCHAR(10),
    ADD COLUMN IF NOT EXISTS query_params TEXT,
    ADD COLUMN IF NOT EXISTS body_params TEXT,
    ADD COLUMN IF NOT EXISTS response_status INT DEFAULT 200,
    ADD COLUMN IF NOT EXISTS duration_ms INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS client_ip VARCHAR(50) DEFAULT '';

-- 索引：按租户+时间查询
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_at_time ON audit_logs(tenant_id, at_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_at_time ON audit_logs(user_id, at_time DESC);

COMMENT ON TABLE audit_logs IS '全局请求审计日志 - V5基础表，V24扩展请求相关字段';
