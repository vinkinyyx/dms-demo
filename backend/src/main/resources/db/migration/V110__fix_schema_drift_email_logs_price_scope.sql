-- V110: 修复 schema 与实体漂移
-- 1) email_logs.duration_ms：实体为 Long(bigint)，V95 误建为 INT，对齐为 BIGINT
-- 2) product_prices.price_scope：V105 已把存量数据归一化为 'SALE'/'PURCHASE'，
--    但列默认值仍为旧的 'SALES'，导致不显式指定 price_scope 的新写入悄悄落回旧值。
ALTER TABLE email_logs
    ALTER COLUMN duration_ms TYPE BIGINT;

ALTER TABLE product_prices
    ALTER COLUMN price_scope SET DEFAULT 'SALE';
