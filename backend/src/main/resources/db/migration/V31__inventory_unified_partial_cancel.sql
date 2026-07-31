/*
 * V31__inventory_unified_partial_cancel.sql
 * v3.7.3 库存管理加强：部分取消、批次选择、退货回滚
 *
 * 设计要点（参考 SAP MM/WMS 最佳实践）：
 * 1. 在收/出库明细行增加 expected_qty(预期) / qty(本次操作量) 字段
 * 2. 增加 cancelled_qty（已取消数量）和 cancelled_from_line_id（取消时关联原行）
 * 3. 支持一个原始单据行被分多次收货/出库/取消
 * 4. 增加 stock_serial 表用于序列号在库绑定（便于销售出库按批次选择序列号）
 */

-- 1. receipt_lines: 增加 cancelled_qty, cancelled_at
ALTER TABLE receipt_lines
    ADD COLUMN IF NOT EXISTS cancelled_qty numeric(14,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancelled_at timestamp with time zone;

-- 2. sales_out_lines: 同样字段
ALTER TABLE sales_out_lines
    ADD COLUMN IF NOT EXISTS cancelled_qty numeric(14,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancelled_at timestamp with time zone;

-- 3. 库存 transactions 增加 source_line_id / reversal_of_id 用于对冲事务
ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS source_line_id bigint,
    ADD COLUMN IF NOT EXISTS reversal_of_id bigint;

CREATE INDEX IF NOT EXISTS idx_inv_tx_source_line ON inventory_transactions(source_line_id);
CREATE INDEX IF NOT EXISTS idx_inv_tx_reversal_of ON inventory_transactions(reversal_of_id);

-- 4. inventory(库存) 增加 serial_no 用于按序列号管理（医疗器械）
ALTER TABLE inventory
    ADD COLUMN IF NOT EXISTS stock_batch_id bigint;

-- 5. stock_serial：在库序列号清单（便于销售出库选择）
CREATE TABLE IF NOT EXISTS stock_serials (
    id                  bigserial PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    warehouse_id        bigint NOT NULL,
    product_id          bigint NOT NULL,
    batch_no            varchar(64) NOT NULL,
    serial_no           varchar(64) NOT NULL,
    stock_status        varchar(16) NOT NULL DEFAULT 'QUALIFIED',
    source_doc_type     varchar(16),
    source_doc_id       bigint,
    source_line_id      bigint,
    received_at         timestamp with time zone DEFAULT now(),
    shipped_at          timestamp with time zone,
    CONSTRAINT uq_stock_serial UNIQUE (tenant_id, batch_no, serial_no, warehouse_id)
);

CREATE INDEX IF NOT EXISTS idx_stock_serials_batch ON stock_serials(tenant_id, warehouse_id, product_id, batch_no, stock_status);
CREATE INDEX IF NOT EXISTS idx_stock_serials_serial ON stock_serials(tenant_id, serial_no);

-- 6. code_prefix 标准化：RK 收货、XS 销售出库（仅当 code_prefixes 表存在时执行；
--    本项目实际使用 V17 的 doc_no_sequences，v3.7.2 起兼容处理，避免 Flyway 迁移失败）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'code_prefixes') THEN
        EXECUTE $sql$
            INSERT INTO code_prefixes (doc_type, prefix, current_seq, padding, description)
            VALUES ('PO_RECEIPT', 'PO-RK', 1, 6, '采购订单自动生成的收货入库单号')
            ON CONFLICT (doc_type) DO NOTHING
        $sql$;
    END IF;
END $$;

-- 7. 增加取消类型的状态机允许值
COMMENT ON COLUMN receipts.status IS 'PENDING / PARTIAL_RECEIVED / RECEIVED / COMPLETED / CANCELLED / PARTIAL_CANCELLED';
COMMENT ON COLUMN sales_outs.status IS 'DRAFT / APPROVED / PARTIAL_SHIPPED / SHIPPED / COMPLETED / CANCELLED / PARTIAL_CANCELLED';
