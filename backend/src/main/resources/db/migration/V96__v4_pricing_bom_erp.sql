-- V96: DMS v4.0.0 - tenant inventory switch, pricing, BOM version, order discounts, ERP outbound callbacks

ALTER TABLE products ADD COLUMN IF NOT EXISTS product_line_id BIGINT REFERENCES product_lines(id);
CREATE INDEX IF NOT EXISTS idx_products_product_line ON products(product_line_id);

ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS sales_price_excl_tax NUMERIC(18,4);
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,4) NOT NULL DEFAULT 0.13;
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS valid_from TIMESTAMPTZ;
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS valid_to TIMESTAMPTZ;
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS price_scope VARCHAR(16) NOT NULL DEFAULT 'SALES';
ALTER TABLE product_prices DROP CONSTRAINT IF EXISTS product_prices_uniq;
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_prices_scope_partner
  ON product_prices(tenant_id, product_id, partner_type, partner_id, price_scope)
  WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_product_prices_effective
  ON product_prices(tenant_id, product_id, partner_type, partner_id, price_scope, valid_from, valid_to)
  WHERE deleted_at IS NULL AND status = 'active';

ALTER TABLE product_bundles ADD COLUMN IF NOT EXISTS bom_version VARCHAR(32) NOT NULL DEFAULT '1.0';
ALTER TABLE product_bundles ADD COLUMN IF NOT EXISTS version_status VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE product_bundles ADD COLUMN IF NOT EXISTS version_locked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE product_bundles DROP CONSTRAINT IF EXISTS product_bundles_uniq;
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_bundles_version
  ON product_bundles(tenant_id, product_id, code, bom_version)
  WHERE deleted_at IS NULL;
UPDATE products SET is_bundle = true WHERE id IN (SELECT product_id FROM product_bundles WHERE deleted_at IS NULL) AND COALESCE(is_bundle,false)=false;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_red BOOLEAN DEFAULT false;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ref_order_id BIGINT REFERENCES orders(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ref_sales_out_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS return_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS source_sales_out_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS amount_excl_tax NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS header_discount_type VARCHAR(16);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS header_discount_value NUMERIC(18,4);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS erp_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS erp_error TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS erp_pushed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS product_code VARCHAR(64);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS product_name VARCHAR(200);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS product_spec VARCHAR(200);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS standard_price_incl_tax NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS line_discount_type VARCHAR(16);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS line_discount_value NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS promo_discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS header_discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS final_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS amount_excl_tax NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS closed_qty NUMERIC(14,4) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS close_reason VARCHAR(500);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS bom_parent_product_id BIGINT;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS bom_version VARCHAR(32);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS bom_group_no VARCHAR(64);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS component_qty NUMERIC(14,4) NOT NULL DEFAULT 1;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS price_snapshot JSONB;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS extra JSONB;

ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS source_order_id BIGINT REFERENCES orders(id);
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS source_po_id BIGINT;
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS warehouse_id BIGINT;
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS erp_outbound_no VARCHAR(128);
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS callback_payload JSONB;
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMPTZ;
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS auto_created BOOLEAN DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_outs_idempotency ON sales_outs(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sales_outs_source_order ON sales_outs(source_order_id);

ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS source_order_line_id BIGINT REFERENCES order_lines(id);
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS bom_parent_product_id BIGINT;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS bom_version VARCHAR(32);
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS bom_group_no VARCHAR(64);
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS component_qty NUMERIC(14,4) NOT NULL DEFAULT 1;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS return_locked_qty NUMERIC(14,4) NOT NULL DEFAULT 0;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS returned_qty NUMERIC(14,4) NOT NULL DEFAULT 0;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS amount_excl_tax NUMERIC(18,2);
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(18,2);
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS final_amount NUMERIC(18,2);

CREATE TABLE IF NOT EXISTS erp_outbound_callbacks (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128),
    direction VARCHAR(16) NOT NULL,
    source_order_id BIGINT,
    source_order_code VARCHAR(64),
    sales_out_id BIGINT REFERENCES sales_outs(id),
    erp_outbound_no VARCHAR(128),
    process_status VARCHAR(24) NOT NULL DEFAULT 'PROCESSED',
    raw_payload JSONB NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    processed_at TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_erp_outbound_callback_idem ON erp_outbound_callbacks(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_erp_outbound_callback_order ON erp_outbound_callbacks(source_order_id);
