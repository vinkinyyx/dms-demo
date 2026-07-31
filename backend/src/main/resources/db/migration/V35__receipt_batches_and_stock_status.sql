-- V35: 收货入库父子单结构 + stock_status 字典 (DMS v3.7.4, R3-R7)
-- Author: DMS Fix Bot @ 2026-07-26
-- 变更:
--   1. 新表 receipt_batches / receipt_batch_lines (子单)
--   2. 清理旧 seed 数据 (V373 场景数据也清理)
--   3. stock_status 字典补 U/Q/B 显示 (内部 code 保留 QUALIFIED/PENDING/DEFECTIVE)

-- 1. 子单表
CREATE TABLE IF NOT EXISTS receipt_batches (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    receipt_id      BIGINT NOT NULL REFERENCES receipts(id) ON DELETE CASCADE,
    code            VARCHAR(80) UNIQUE NOT NULL,       -- 例: RK-20260726-00003-1
    seq             INT NOT NULL,                       -- 父单内序号 1/2/3
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT', -- DRAFT/CONFIRMED/CANCELLED
    remark          TEXT,
    confirmed_at    TIMESTAMPTZ,
    confirmed_by    BIGINT,
    cancelled_at    TIMESTAMPTZ,
    cancelled_by    BIGINT,
    cancel_reason   TEXT,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    created_by      BIGINT
);
CREATE INDEX IF NOT EXISTS idx_rcb_receipt ON receipt_batches(receipt_id);
CREATE INDEX IF NOT EXISTS idx_rcb_tenant  ON receipt_batches(tenant_id, status);

CREATE TABLE IF NOT EXISTS receipt_batch_lines (
    id              BIGSERIAL PRIMARY KEY,
    batch_id        BIGINT NOT NULL REFERENCES receipt_batches(id) ON DELETE CASCADE,
    po_line_id      BIGINT,                             -- 关联采购订单明细行
    po_line_seq     INT,                                -- PO 里的行号
    receipt_line_no INT NOT NULL,                       -- 子单内收货行号
    product_id      BIGINT NOT NULL,
    qty             NUMERIC(14,4) NOT NULL DEFAULT 0,
    batch_no        VARCHAR(64),
    serial_nos      TEXT,                               -- 多行文本, 每行一个序列号
    remark          TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rcbl_batch   ON receipt_batch_lines(batch_id);
CREATE INDEX IF NOT EXISTS idx_rcbl_po_line ON receipt_batch_lines(po_line_id);

-- 2. 清理旧 seed (V373 场景数据 + RC-* seed)
DO $BODY$
DECLARE
    v_tid uuid;
BEGIN
    SELECT id INTO v_tid FROM tenants WHERE code = 'default' LIMIT 1;
    IF v_tid IS NULL THEN RETURN; END IF;

    -- 清理 receipt_execution_lines 里 default 的记录
    DELETE FROM receipt_execution_lines WHERE tenant_id = v_tid;

    -- 清理 receipts + receipt_lines (auto_created 的 seed 或 V373)
    DELETE FROM receipt_lines WHERE receipt_id IN (
        SELECT id FROM receipts WHERE tenant_id = v_tid
    );
    DELETE FROM receipt_batches WHERE tenant_id = v_tid;  -- 幂等
    DELETE FROM receipts WHERE tenant_id = v_tid;

    -- 清理 PO 里 V373 / demo 的记录
    DELETE FROM purchase_order_lines WHERE po_id IN (
        SELECT id FROM purchase_orders WHERE tenant_id = v_tid AND (code LIKE 'PO-V373-%' OR code LIKE 'PO-000%')
    );
    DELETE FROM purchase_orders WHERE tenant_id = v_tid AND (code LIKE 'PO-V373-%' OR code LIKE 'PO-000%');

    -- 清理 inventory / stock_serials 里因 seed 出来的 legacy 数据 (谨慎:仅测试环境)
    DELETE FROM inventory_transactions WHERE tenant_id = v_tid;
    DELETE FROM stock_serials WHERE tenant_id = v_tid;
    DELETE FROM inventory WHERE tenant_id = v_tid;
END $BODY$;

-- 3. stock_status 字典 seed (U 合格 / Q 待检 / B 不合格)
--    内部码值仍为 QUALIFIED/PENDING/DEFECTIVE, label 前缀 U/Q/B 让用户识别
INSERT INTO dict_types(tenant_id, code, name, description)
SELECT t.id, 'stock_status', '库存状态', 'U 合格 / Q 待检 / B 不合格'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM dict_types dt WHERE dt.tenant_id = t.id AND dt.code = 'stock_status');

WITH ss AS (SELECT id, tenant_id FROM dict_types WHERE code = 'stock_status')
INSERT INTO dict_items(type_id, code, name, seq, status)
SELECT ss.id, v.code, v.name, v.seq, 'active'
FROM ss
CROSS JOIN (VALUES 
    ('QUALIFIED','U 合格',1),
    ('PENDING',  'Q 待检',2),
    ('DEFECTIVE','B 不合格',3)
) AS v(code, name, seq)
WHERE NOT EXISTS (
    SELECT 1 FROM dict_items di WHERE di.type_id = ss.id AND di.code = v.code
);

-- 若旧字典项存在 (标签是'合格'), 更新为新标签
UPDATE dict_items SET name = 'U 合格'  WHERE code='QUALIFIED' AND name IN ('合格');
UPDATE dict_items SET name = 'Q 待检'  WHERE code='PENDING'   AND name IN ('待检');
UPDATE dict_items SET name = 'B 不合格' WHERE code='DEFECTIVE' AND name IN ('不合格');

COMMENT ON TABLE receipt_batches IS 'v3.7.4 收货子单(每次收货一张)';
COMMENT ON COLUMN receipt_batches.status IS 'DRAFT / CONFIRMED / CANCELLED';
