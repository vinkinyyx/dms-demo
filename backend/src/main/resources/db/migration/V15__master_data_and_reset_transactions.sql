-- V15: v3.4.9 主数据表结构（数据已手动注入）
ALTER TABLE products ADD COLUMN IF NOT EXISTS unit_type VARCHAR(16) DEFAULT 'EA';
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL, contact_person VARCHAR(64), contact_phone VARCHAR(32),
    address VARCHAR(500), bank_account VARCHAR(64), tax_no VARCHAR(64), remark TEXT,
    status VARCHAR(16) DEFAULT 'active', created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(), deleted_at TIMESTAMP, UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_suppliers_tenant_status ON suppliers(tenant_id, status);
CREATE TABLE IF NOT EXISTS product_prices (
    id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL, product_id BIGINT NOT NULL,
    partner_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL', partner_id BIGINT DEFAULT 0,
    purchase_price NUMERIC(18,4) DEFAULT 0, sales_price NUMERIC(18,4) DEFAULT 0,
    currency VARCHAR(8) DEFAULT 'CNY', effective_date DATE, expire_date DATE, remark TEXT,
    status VARCHAR(16) DEFAULT 'active', created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(), UNIQUE (tenant_id, product_id, partner_type, partner_id)
);
CREATE INDEX IF NOT EXISTS idx_pprice_product ON product_prices(product_id);