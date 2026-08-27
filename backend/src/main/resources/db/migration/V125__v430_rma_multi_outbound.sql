-- v4.3.0 R1：销退单支持关联同一经销商的多张销售出库单

-- 关联的出库单
CREATE TABLE IF NOT EXISTS rma_order_refs (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    rma_id         BIGINT NOT NULL REFERENCES rma_orders(id) ON DELETE CASCADE,
    sales_out_id   BIGINT NOT NULL REFERENCES sales_outs(id),
    sales_out_code VARCHAR(64),
    dealer_id      BIGINT,
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rma_refs_rma ON rma_order_refs(rma_id);
CREATE INDEX IF NOT EXISTS idx_rma_refs_out ON rma_order_refs(sales_out_id);

-- 关系化销退行（可退量按 sales_out_line_id 锁定）
CREATE TABLE IF NOT EXISTS rma_order_lines (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    rma_id            BIGINT NOT NULL REFERENCES rma_orders(id) ON DELETE CASCADE,
    ref_id            BIGINT REFERENCES rma_order_refs(id) ON DELETE CASCADE,
    sales_out_line_id BIGINT REFERENCES sales_out_lines(id),
    product_id        BIGINT NOT NULL REFERENCES products(id),
    product_code      VARCHAR(64),
    product_name      VARCHAR(200),
    product_spec      VARCHAR(200),
    qty               INTEGER NOT NULL CHECK (qty > 0),
    unit_price_incl_tax NUMERIC(18,4) NOT NULL DEFAULT 0,  -- EA 退价快照
    tax_rate          NUMERIC(5,4) NOT NULL DEFAULT 0.13,
    sub_total         NUMERIC(18,2) NOT NULL DEFAULT 0,
    reason            VARCHAR(500),
    batch_no          VARCHAR(128),
    seq               INT DEFAULT 1,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rma_lines_rma ON rma_order_lines(rma_id);
CREATE INDEX IF NOT EXISTS idx_rma_lines_out_line ON rma_order_lines(sales_out_line_id);
CREATE INDEX IF NOT EXISTS idx_rma_lines_product ON rma_order_lines(product_id);

-- rma_orders 增强
ALTER TABLE rma_orders ADD COLUMN IF NOT EXISTS sales_out_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE rma_orders ADD COLUMN IF NOT EXISTS total_qty INTEGER NOT NULL DEFAULT 0;
ALTER TABLE rma_orders ADD COLUMN IF NOT EXISTS price_snapshot JSONB;
