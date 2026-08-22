-- v4.0.0-bugfix.x: 采购价不含税字段；价格记录只增不改（生效/失效）
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS purchase_price_excl_tax NUMERIC(18,4);
UPDATE product_prices SET purchase_price_excl_tax = ROUND((purchase_price / (1 + COALESCE(tax_rate,0.13)))::numeric, 4)
  WHERE purchase_price IS NOT NULL AND purchase_price > 0 AND (purchase_price_excl_tax IS NULL OR purchase_price_excl_tax = 0);
