-- v4.3.0 R2：客户多联系人 + 收货地址增强

-- 1) 客户联系人
CREATE TABLE IF NOT EXISTS dealer_contacts (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    dealer_id     BIGINT NOT NULL REFERENCES dealers(id),
    contact_name  VARCHAR(100) NOT NULL,
    phone         VARCHAR(32),
    email         VARCHAR(128),
    position      VARCHAR(64),
    is_default    BOOLEAN NOT NULL DEFAULT FALSE,
    status        VARCHAR(16) NOT NULL DEFAULT 'active',
    remark        VARCHAR(500),
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    version       INT DEFAULT 0,
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_dealer_contacts_dealer ON dealer_contacts(dealer_id);
CREATE INDEX IF NOT EXISTS idx_dealer_contacts_tenant ON dealer_contacts(tenant_id, status);

-- 回填：把 dealers 上的单联系人迁移为默认联系人
INSERT INTO dealer_contacts (tenant_id, dealer_id, contact_name, phone, email, is_default, status, created_at, updated_at)
SELECT d.tenant_id, d.id, NULLIF(d.contact_name,''), d.contact_phone, d.contact_email, TRUE, 'active', now(), now()
FROM dealers d
WHERE d.deleted_at IS NULL
  AND NULLIF(COALESCE(d.contact_name,''),'') IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM dealer_contacts dc WHERE dc.dealer_id = d.id AND dc.deleted_at IS NULL
  );

-- 2) 收货地址增强
ALTER TABLE dealer_addresses ADD COLUMN IF NOT EXISTS address_name VARCHAR(100);
ALTER TABLE dealer_addresses ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE dealer_addresses ADD COLUMN IF NOT EXISTS tags JSONB;
-- 旧数据没有 is_default 时，取每个经销商最早一条为默认
UPDATE dealer_addresses a SET is_default = TRUE
WHERE a.is_default IS FALSE OR a.is_default IS NULL
  AND a.id = (
      SELECT min(id) FROM dealer_addresses a2
      WHERE a2.dealer_id = a.dealer_id AND a2.deleted_at IS NULL
  );
ALTER TABLE dealer_addresses ALTER COLUMN is_default SET DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_dealer_addr_tenant_status ON dealer_addresses(tenant_id, status);
