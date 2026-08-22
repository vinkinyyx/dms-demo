-- V112: 归一化 product_prices.price_scope 历史脏值
-- 根因：早期演示数据 reset 脚本把 price_scope 写成 'SALES'（旧值），
-- 而计价查询按 'SALE' 过滤，导致有价格却解析为“未维护价格”。
-- V105 已归一化过存量数据，但 reset 在迁移之后再次灌入旧种子会复现；此处做幂等兜底。
UPDATE product_prices
SET price_scope = 'SALE', updated_at = now()
WHERE price_scope IN ('SALES', 'GLOBAL', 'DEALER')
  AND (COALESCE(sales_price,0) > 0 OR COALESCE(sales_price_excl_tax,0) > 0);

UPDATE product_prices
SET price_scope = 'PURCHASE', updated_at = now()
WHERE price_scope IN ('PUR', 'SUPPLIER', 'PURCHASE_SCOPE')
  AND (COALESCE(purchase_price,0) > 0 OR COALESCE(purchase_price_excl_tax,0) > 0);
