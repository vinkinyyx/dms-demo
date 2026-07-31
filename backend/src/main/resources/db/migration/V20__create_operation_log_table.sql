-- 操作日志表 - 记录所有单据的用户操作历史
-- 支持记录每个字段修改前后的快照

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL DEFAULT 'default',
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    change_json TEXT,
    remark VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_operation_log_business ON operation_log(business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_operation_log_tenant ON operation_log(tenant_code);
CREATE INDEX IF NOT EXISTS idx_operation_log_created ON operation_log(created_at);

COMMENT ON TABLE operation_log IS '业务操作日志';
COMMENT ON COLUMN operation_log.business_type IS '业务类型: sales_order/purchase_order/...';
COMMENT ON COLUMN operation_log.business_id IS '业务单据ID';
COMMENT ON COLUMN operation_log.operator_id IS '操作人ID';
COMMENT ON COLUMN operation_log.operator_name IS '操作人姓名';
COMMENT ON COLUMN operation_log.action IS '操作类型: CREATE/UPDATE/DELETE/APPROVE/REJECT';
COMMENT ON COLUMN operation_log.change_json IS '修改内容JSON快照 {field: {before: xxx, after: xxx}}';
COMMENT ON COLUMN operation_log.remark IS '备注/审核意见';
