-- V138: v4.4.1 开票订单寄售库存精准拣选。
-- order_lines 增加 consignment_stock_id：开票下单时用户在寄售库存弹窗中勾选的具体台账行，
-- 提交预占(INVOICE_LOCK)/审批实扣(INVOICE_DEDUCT)/驳回释放(INVOICE_RELEASE) 优先按台账行 id 精准定位，
-- 为空时回退 v4.4.0 的 产品+批号+序列号 维度匹配（补货单/普通销售单/历史数据不受影响）。
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS consignment_stock_id BIGINT;

COMMENT ON COLUMN order_lines.consignment_stock_id IS 'v4.4.1 开票订单拣选的寄售库存台账行 id（consignment_stock.id），仅 INVOICE 订单使用';

CREATE INDEX IF NOT EXISTS idx_order_lines_consignment_stock ON order_lines(consignment_stock_id) WHERE consignment_stock_id IS NOT NULL;
