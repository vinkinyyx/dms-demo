-- ============================================================
-- V14: 单号前缀标准化 (SO/PO/RSO/RPO/RK/CK)
-- SO  = Sales Order 销售订单
-- PO  = Purchase Order 采购订单
-- RSO = Return Sales Order 销退订单 (orders.is_red=true)
-- RPO = Return Purchase Order 采退订单 (purchase_orders.is_red=true)
-- CK  = Chuku 销售出库
-- RK  = Ruku 采购入库(收货)
-- ============================================================

-- 销售订单 → SO 前缀
UPDATE orders
SET code = CASE
    WHEN is_red = false OR is_red IS NULL THEN 'SO-' || LPAD(id::text, 8, '0')
    ELSE 'RSO-' || LPAD(id::text, 8, '0')
END
WHERE code IS NOT NULL AND code NOT LIKE 'SO-%' AND code NOT LIKE 'RSO-%';

-- 采购订单
UPDATE purchase_orders
SET code = CASE
    WHEN is_red = false OR is_red IS NULL THEN 'PO-' || LPAD(id::text, 8, '0')
    ELSE 'RPO-' || LPAD(id::text, 8, '0')
END
WHERE code IS NOT NULL AND code NOT LIKE 'PO-%' AND code NOT LIKE 'RPO-%';

-- 销售出库 → CK
UPDATE sales_outs
SET code = 'CK-' || LPAD(id::text, 8, '0')
WHERE code IS NOT NULL AND code NOT LIKE 'CK-%';

-- 采购入库 → RK
UPDATE receipts
SET code = 'RK-' || LPAD(id::text, 8, '0')
WHERE code IS NOT NULL AND code NOT LIKE 'RK-%';
