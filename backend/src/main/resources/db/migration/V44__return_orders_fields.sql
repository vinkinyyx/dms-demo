-- v3.8.1 销退/采退改造：新增关联与退货原因字段
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ref_sales_out_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS return_reason TEXT;

ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS return_reason TEXT;

-- 销售出库单增加 source_po_id，用于采退订单(RP)审批生成的出库单(RGI)回链
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS source_po_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_sales_outs_source_po ON sales_outs(source_po_id);

-- v3.8.1 销退/采退改造：销退行记录批次/序列号（用于关联原发货批次）
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS serial_no VARCHAR(64);
