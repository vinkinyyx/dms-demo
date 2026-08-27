-- v4.3.0：订单计价模式、代金券、EA 单价、价格来源、行 0 金额等

-- orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_id BIGINT REFERENCES customer_vouchers(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promo_messages JSONB;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pricing_snapshot JSONB;

-- order_lines
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS base_price_incl_tax NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS price_source VARCHAR(16); -- CONTRACT/DEALER/GLOBAL
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS product_discount_rate NUMERIC(6,4) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS product_discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS promo_type VARCHAR(24);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS promotion_id BIGINT;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS promo_hit_id BIGINT;
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS unit_price_incl_tax NUMERIC(18,4) NOT NULL DEFAULT 0; -- EA 成交价
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS line_zero BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_orders_voucher ON orders(voucher_id);
CREATE INDEX IF NOT EXISTS idx_order_lines_promo ON order_lines(promotion_id);
