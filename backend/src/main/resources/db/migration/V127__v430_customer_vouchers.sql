-- v4.3.0 R5：客户代金券（厂家统一发放，一单一张，抵扣不摊入单价）

CREATE TABLE IF NOT EXISTS customer_vouchers (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    code          VARCHAR(64) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    dealer_id     BIGINT REFERENCES dealers(id),
    face_value    NUMERIC(18,2) NOT NULL CHECK (face_value > 0),
    min_spend     NUMERIC(18,2) DEFAULT 0,
    scope_type    VARCHAR(16) NOT NULL DEFAULT 'ALL',  -- ALL / PRODUCT / CATEGORY
    scope_refs    JSONB,
    valid_from    TIMESTAMPTZ,
    valid_to      TIMESTAMPTZ,
    status        VARCHAR(16) NOT NULL DEFAULT 'ISSUED', -- ISSUED/USED/EXPIRED/DISABLED/VOID
    batch_no      VARCHAR(64),
    remark        VARCHAR(500),
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    version       INT DEFAULT 0,
    deleted_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_vouchers_code ON customer_vouchers(tenant_id, code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_cv_dealer ON customer_vouchers(dealer_id, status);
CREATE INDEX IF NOT EXISTS idx_cv_tenant ON customer_vouchers(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_cv_valid ON customer_vouchers(tenant_id, valid_from, valid_to) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS customer_voucher_usages (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    voucher_id    BIGINT NOT NULL REFERENCES customer_vouchers(id),
    order_id      BIGINT REFERENCES orders(id),
    order_code    VARCHAR(64),
    used_amount   NUMERIC(18,2) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'USED', -- USED/REFUNDED/REVERSED
    used_at       TIMESTAMPTZ DEFAULT now(),
    created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cvu_voucher ON customer_voucher_usages(voucher_id);
CREATE INDEX IF NOT EXISTS idx_cvu_order ON customer_voucher_usages(order_id);
