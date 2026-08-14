-- V95: 邮件日志增强字段（重试次数、耗时、模板编码）
ALTER TABLE email_logs ADD COLUMN IF NOT EXISTS retries INT NOT NULL DEFAULT 0;
ALTER TABLE email_logs ADD COLUMN IF NOT EXISTS duration_ms INT;
ALTER TABLE email_logs ADD COLUMN IF NOT EXISTS template_code VARCHAR(64);
