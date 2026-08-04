-- v3.8.7 手术报台：表头附件 + 去除仓库约束
-- 1. 通用文件表（任何业务单据的附件都存这里）
CREATE TABLE IF NOT EXISTS files (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    rel_path VARCHAR(512) NOT NULL,
    content_type VARCHAR(128),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    uploaded_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_files_tenant_biz ON files(tenant_id, biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_files_created_at ON files(created_at DESC);

-- 2. 手术报台：warehouse_id 允许为空；新增附件字段
ALTER TABLE surgery_reports ALTER COLUMN warehouse_id DROP NOT NULL;
ALTER TABLE surgery_reports ADD COLUMN IF NOT EXISTS attachment_file_id BIGINT;
ALTER TABLE surgery_reports ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(255);
ALTER TABLE surgery_reports ADD COLUMN IF NOT EXISTS attachment_url TEXT;
