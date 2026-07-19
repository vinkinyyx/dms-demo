-- V16: v3.4.10 分次发货支持 + 部分收货状态
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS shipped_qty NUMERIC(18,4) DEFAULT 0;
UPDATE sales_out_lines SET shipped_qty = qty WHERE shipped_qty IS NULL OR shipped_qty = 0
  AND sales_out_id IN (SELECT id FROM sales_outs WHERE status = 'COMPLETED');