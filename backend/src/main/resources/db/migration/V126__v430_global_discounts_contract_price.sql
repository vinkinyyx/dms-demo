-- v4.3.0 R6/R7：产品全局折扣、客户全局折扣；合同价清单

-- 产品全局折扣（按时间段，同产品时间段不可重叠，应用层校验）
CREATE TABLE IF NOT EXISTS product_global_discounts (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    product_id     BIGINT NOT NULL REFERENCES products(id),
    discount_rate  NUMERIC(6,4) NOT NULL CHECK (discount_rate >= 0 AND discount_rate < 1),
    valid_from     DATE,
    valid_to       DATE,
    status         VARCHAR(16) NOT NULL DEFAULT 'active',
    remark         VARCHAR(500),
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    created_by     BIGINT,
    updated_by     BIGINT,
    version        INT DEFAULT 0,
    deleted_at     TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_pgd_product ON product_global_discounts(product_id);
CREATE INDEX IF NOT EXISTS idx_pgd_tenant ON product_global_discounts(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_pgd_range ON product_global_discounts(tenant_id, product_id, valid_from, valid_to)
  WHERE deleted_at IS NULL AND status = 'active';

-- 客户全局折扣
CREATE TABLE IF NOT EXISTS dealer_global_discounts (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    dealer_id      BIGINT NOT NULL REFERENCES dealers(id),
    discount_rate  NUMERIC(6,4) NOT NULL CHECK (discount_rate >= 0 AND discount_rate < 1),
    valid_from     DATE,
    valid_to       DATE,
    status         VARCHAR(16) NOT NULL DEFAULT 'active',
    remark         VARCHAR(500),
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    created_by     BIGINT,
    updated_by     BIGINT,
    version        INT DEFAULT 0,
    deleted_at     TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_dgd_dealer ON dealer_global_discounts(dealer_id);
CREATE INDEX IF NOT EXISTS idx_dgd_tenant ON dealer_global_discounts(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_dgd_range ON dealer_global_discounts(tenant_id, dealer_id, valid_from, valid_to)
  WHERE deleted_at IS NULL AND status = 'active';

-- 合同价清单（取价优先级：合同价 > 客户基础价 > 全局价）
CREATE TABLE IF NOT EXISTS contract_prices (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    contract_id       BIGINT NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    dealer_id         BIGINT,
    product_id        BIGINT NOT NULL REFERENCES products(id),
    price_incl_tax    NUMERIC(18,2),
    price_excl_tax    NUMERIC(18,4),
    tax_rate          NUMERIC(5,4) NOT NULL DEFAULT 0.13,
    valid_from        DATE,
    valid_to          DATE,
    status            VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    created_by        BIGINT,
    updated_by        BIGINT,
    version           INT DEFAULT 0,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_contract_prices_contract ON contract_prices(contract_id);
CREATE INDEX IF NOT EXISTS idx_contract_prices_product ON contract_prices(product_id, dealer_id);
CREATE INDEX IF NOT EXISTS idx_contract_prices_effective
  ON contract_prices(tenant_id, product_id, dealer_id, valid_from, valid_to)
  WHERE deleted_at IS NULL AND status = 'active';
