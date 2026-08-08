-- v4.2.0 传输接口调用日志增强
-- 传输接口（库存查询/销售订单传输/采购订单传输）需要按业务单号快速检索日志，
-- 现有 api_call_log 仅记录了 biz_code（业务返回码），缺少业务单号和动作标签。
-- biz_key:    业务单号（库存=productCode|warehouseCode，订单=SO-/PO- 前缀的 code）
-- biz_action: 业务动作（如 inventory.query / order.transfer.sales / order.transfer.purchase）

ALTER TABLE api_call_log ADD COLUMN IF NOT EXISTS biz_key    VARCHAR(64);
ALTER TABLE api_call_log ADD COLUMN IF NOT EXISTS biz_action VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_api_call_log_biz_key    ON api_call_log (biz_key);
CREATE INDEX IF NOT EXISTS idx_api_call_log_biz_action ON api_call_log (biz_action, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_call_log_path_time  ON api_call_log (path, started_at DESC);

COMMENT ON COLUMN api_call_log.biz_key    IS '业务单号：SO-/PO- 订单编号、productCode|warehouseCode 等';
COMMENT ON COLUMN api_call_log.biz_action IS '业务动作标签：inventory.query / order.transfer.sales / order.transfer.purchase';
