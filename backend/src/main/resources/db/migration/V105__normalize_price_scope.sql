-- Normalize v4.0.0 product price scope after replacing old GLOBAL/DEALER price types.
UPDATE product_prices
SET price_scope = 'SALE'
WHERE price_scope IN ('SALES', 'GLOBAL', 'DEALER')
  AND (COALESCE(sales_price,0) > 0 OR COALESCE(sales_price_excl_tax,0) > 0);

UPDATE product_prices
SET price_scope = 'PURCHASE'
WHERE price_scope IN ('PURCHASE', 'SUPPLIER', 'PUR')
  AND (COALESCE(purchase_price,0) > 0 OR COALESCE(purchase_price_excl_tax,0) > 0);

UPDATE product_prices
SET partner_type = 'DEALER'
WHERE price_scope = 'SALE' AND partner_type IS NULL;

UPDATE product_prices
SET partner_type = 'SUPPLIER'
WHERE price_scope = 'PURCHASE' AND partner_type IS NULL;

UPDATE product_prices
SET partner_id = 0
WHERE price_scope = 'SALE' AND partner_type = 'GLOBAL' AND partner_id IS NULL;

UPDATE product_prices
SET partner_type = 'GLOBAL'
WHERE price_scope = 'SALE' AND partner_id = 0;
