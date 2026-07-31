-- 给销售订单 orders 添加审核字段
ALTER TABLE orders
ADD COLUMN IF NOT EXISTS audit_user_id BIGINT,
ADD COLUMN IF NOT EXISTS audit_user_name VARCHAR(64),
ADD COLUMN IF NOT EXISTS audit_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS audit_remark VARCHAR(512);

COMMENT ON COLUMN orders.audit_user_id IS '审核人ID';
COMMENT ON COLUMN orders.audit_user_name IS '审核人姓名';
COMMENT ON COLUMN orders.audit_time IS '审核时间';
COMMENT ON COLUMN orders.audit_remark IS '审核意见';

-- 给销售出库明细表 sales_out_lines 添加批次字段（支持多行批次拆分）
ALTER TABLE sales_out_lines
ADD COLUMN IF NOT EXISTS batch_num VARCHAR(64),
ADD COLUMN IF NOT EXISTS serial_num VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sales_out_lines_batch ON sales_out_lines(batch_num);

COMMENT ON COLUMN sales_out_lines.batch_num IS '批次号';
COMMENT ON COLUMN sales_out_lines.serial_num IS '序列号';
