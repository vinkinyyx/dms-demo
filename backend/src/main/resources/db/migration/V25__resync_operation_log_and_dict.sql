-- =====================================================================
-- V25: 重同步之前未应用的迁移 (V20 operation_log + V23 dict_types/product_type)
-- 原因：Flyway 历史显示 V19 之后直接到 V22，说明 V20/V21/V23 在某些
--       服务器从未真正执行。手动建表/补数据并把这些历史填回 Flyway。
-- =====================================================================

-- 1) operation_log 表 (V20)
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

-- 2) orders/purchase_orders 审核字段 (V21, 表名是复数)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'DRAFT';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS audit_user_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS audit_user_name VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS audit_time TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS audit_remark VARCHAR(512);

ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'DRAFT';
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS audit_user_id BIGINT;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS audit_user_name VARCHAR(64);
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS audit_time TIMESTAMP;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS audit_remark VARCHAR(512);

-- 3) product_type 字典 (V23 修正：dict_types.name 不是 type_name)
INSERT INTO dict_types (tenant_id, code, name, description) VALUES
    (NULL, 'payment_method', '支付方式', '订单支付方式枚举'),
    (NULL, 'invoice_type',   '发票类型', '发票类型枚举'),
    (NULL, 'customer_level', '客户等级', '经销商/客户等级'),
    (NULL, 'shipment_method','发货方式', '物流配送方式'),
    (NULL, 'product_type',   '产品类型', '产品分类类型')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq)
SELECT dt.id, 'finished', '成品', 10 FROM dict_types dt WHERE dt.code='product_type' AND dt.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq)
SELECT dt.id, 'semi_finished', '半成品', 20 FROM dict_types dt WHERE dt.code='product_type' AND dt.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq)
SELECT dt.id, 'raw_material', '原材料', 30 FROM dict_types dt WHERE dt.code='product_type' AND dt.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 4) 把 V20/V21/V23 写回 Flyway history（每个 rank 不同）
DO $$
DECLARE
    base_rank BIGINT;
BEGIN
    SELECT COALESCE(MAX(installed_rank), 0) INTO base_rank FROM flyway_schema_history;

    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='20') THEN
        INSERT INTO flyway_schema_history(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
        VALUES (-20, '20', 'create operation log table', 'SQL', 'V20__create_operation_log_table.sql', NULL, 'dms', CURRENT_TIMESTAMP, 100, TRUE);    END IF;

    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='21') THEN
        INSERT INTO flyway_schema_history(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
        VALUES (-21, '21', 'add audit and batch fields', 'SQL', 'V21__add_audit_and_batch_fields.sql', NULL, 'dms', CURRENT_TIMESTAMP, 100, TRUE);    END IF;

    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='23') THEN
        INSERT INTO flyway_schema_history(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
        VALUES (-23, '23', 'fix dict types column names', 'SQL', 'V23__fix_dict_types_column_names.sql', NULL, 'dms', CURRENT_TIMESTAMP, 100, TRUE);
    END IF;
END $$;
