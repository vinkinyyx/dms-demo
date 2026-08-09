-- V74: 邮件发送日志
CREATE TABLE IF NOT EXISTS email_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID,
    from_address VARCHAR(200),
    to_address VARCHAR(256) NOT NULL,
    subject VARCHAR(256),
    text TEXT,
    status VARCHAR(16) NOT NULL,
    biz_type VARCHAR(64),
    biz_id VARCHAR(64),
    recipient_user_id BIGINT,
    error_message TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_email_logs_tenant_created ON email_logs(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_logs_status ON email_logs(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_logs_biz ON email_logs(biz_type, biz_id);