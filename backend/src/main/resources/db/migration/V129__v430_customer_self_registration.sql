-- v4.3.0 R9：客户自助注册审核；CUSTOMER 账号

-- users.user_type 放宽以容纳 CUSTOMER（无 CHECK 约束，仅注释）
COMMENT ON COLUMN users.user_type IS 'vendor/dealer/customer';

-- 客户注册表：公开注册提交，审核通过后自动创建 dealer + user
CREATE TABLE IF NOT EXISTS customer_registrations (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,              -- 厂家租户
    register_name   VARCHAR(64) NOT NULL,
    phone           VARCHAR(32) NOT NULL,
    email           VARCHAR(128),
    password_hash   VARCHAR(255),
    company_name    VARCHAR(200) NOT NULL,
    usc_no          VARCHAR(32),
    legal_person    VARCHAR(64),
    contact_name    VARCHAR(100),
    contact_phone   VARCHAR(32),
    reg_address     VARCHAR(500),
    addresses       JSONB,
    attachments     JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED
    reject_reason   VARCHAR(500),
    reviewer_id     BIGINT,
    reviewed_at     TIMESTAMPTZ,
    created_user_id BIGINT,
    created_dealer_id BIGINT,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    version         INT DEFAULT 0,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_cust_reg_status ON customer_registrations(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_cust_reg_phone ON customer_registrations(phone);

-- dealers 增加注册来源标记
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS register_source VARCHAR(16) DEFAULT 'MANUAL'; -- MANUAL/SELF
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS registration_id BIGINT REFERENCES customer_registrations(id);
