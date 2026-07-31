/*
 * V36: 销售订单/销售出库表结构对齐采购订单/收货入库
 *
 * 对齐目标（参考 purchase_orders / receipts）：
 *  1. orders 增加发货仓库、税率/税额、审批人和时间、状态机注释
 *  2. order_lines 增加 seq/sub_total/tax_rate 已存在；补 unit_price/tax_rate 非空默认
 *  3. sales_outs 增加 status 扩展注释和 shipped_at/completed_at/approved_by
 *  4. sales_out_lines 统一 expected_qty（订购量）/shipped_qty（累计已发）/qty 语义
 *
 * 此迁移为幂等（IF NOT EXISTS / COALESCE）
 */

-- =======================================================
-- 1. orders 表字段补齐（对应 purchase_orders）
-- =======================================================
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS warehouse_id      BIGINT,
    ADD COLUMN IF NOT EXISTS tax_amount        NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS approved_by       BIGINT,
    ADD COLUMN IF NOT EXISTS completed_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS extra             JSONB DEFAULT '{}'::jsonb;

COMMENT ON COLUMN orders.status IS 'DRAFT / SUBMITTED / APPROVED / SHIPPING / COMPLETED / REJECTED / CANCELLED';

-- =======================================================
-- 2. sales_outs 表字段补齐（对应 receipts；自动建单草稿）
-- =======================================================
ALTER TABLE sales_outs
    ADD COLUMN IF NOT EXISTS warehouse_id     BIGINT,
    ADD COLUMN IF NOT EXISTS remark           TEXT,
    ADD COLUMN IF NOT EXISTS approved_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by      BIGINT,
    ADD COLUMN IF NOT EXISTS shipped_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS extra            JSONB DEFAULT '{}'::jsonb;

-- status 默认改 DRAFT（草稿），对齐 receipts
UPDATE sales_outs SET status = 'DRAFT' WHERE status IS NULL OR status = 'SUBMITTED';

COMMENT ON COLUMN sales_outs.status IS 'DRAFT / APPROVED / PARTIAL_SHIPPED / SHIPPED / COMPLETED / CANCELLED / PARTIAL_CANCELLED';

-- =======================================================
-- 3. sales_out_lines 对齐 receipt_lines 的 expected/shipped/cancelled 语义
--    - expected_qty: 从订单行带过来的应发数
--    - shipped_qty : 累计已确认发货数
--    - qty         : 历史字段，保留含义为"本次/总数"，由代码统一维护
-- =======================================================
ALTER TABLE sales_out_lines
    ADD COLUMN IF NOT EXISTS expected_qty  NUMERIC(14,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipped_qty   NUMERIC(14,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unit_price    NUMERIC(18,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tax_rate      NUMERIC(5,4)  DEFAULT 0.13,
    ADD COLUMN IF NOT EXISTS subtotal      NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS seq           INT DEFAULT 1;

-- 历史数据回填：已存在行用 qty 作为 expected/shipped
UPDATE sales_out_lines SET expected_qty = COALESCE(qty, 0) WHERE expected_qty = 0 AND COALESCE(qty,0) > 0;
UPDATE sales_out_lines SET shipped_qty  = COALESCE(qty, 0) WHERE shipped_qty  = 0 AND COALESCE(qty,0) > 0;

-- 索引补齐
CREATE INDEX IF NOT EXISTS idx_orders_warehouse    ON orders(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_sales_outs_wh       ON sales_outs(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_sol_so              ON sales_out_lines(sales_out_id);
CREATE INDEX IF NOT EXISTS idx_sol_product         ON sales_out_lines(product_id);
