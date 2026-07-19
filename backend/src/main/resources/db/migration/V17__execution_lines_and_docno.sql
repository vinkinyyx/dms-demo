-- V17: v3.4.12 收发货执行明细表 + 单据号序列表
-- 每一次收货/发货、每一个批次/序列号，单独留一条记录

CREATE TABLE IF NOT EXISTS receipt_execution_lines (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    receipt_id BIGINT NOT NULL,
    receipt_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_no VARCHAR(128),
    serial_no VARCHAR(128),
    qty NUMERIC(18,4) NOT NULL,
    seq_no INT NOT NULL DEFAULT 1,
    operator_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rel_receipt ON receipt_execution_lines(receipt_id);

CREATE TABLE IF NOT EXISTS sales_out_execution_lines (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sales_out_id BIGINT NOT NULL,
    sales_out_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_no VARCHAR(128),
    serial_no VARCHAR(128),
    qty NUMERIC(18,4) NOT NULL,
    seq_no INT NOT NULL DEFAULT 1,
    operator_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_soel_so ON sales_out_execution_lines(sales_out_id);

-- 单据号序列表：PREFIX-YYYYMMDD-00001 连续流水
CREATE TABLE IF NOT EXISTS doc_no_sequences (
    tenant_id UUID NOT NULL,
    prefix VARCHAR(16) NOT NULL,
    date_key VARCHAR(8) NOT NULL,
    last_seq INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, prefix, date_key)
);

-- 产品分类补充测试数据（补充 level/status 满足非空约束）
INSERT INTO product_categories (tenant_id, code, name, level, status)
SELECT DISTINCT pc.tenant_id, 'CAT-MON', '监护设备', 1, 'active'
FROM product_categories pc
WHERE NOT EXISTS (SELECT 1 FROM product_categories x WHERE x.tenant_id = pc.tenant_id AND x.name = '监护设备');
INSERT INTO product_categories (tenant_id, code, name, level, status)
SELECT DISTINCT pc.tenant_id, 'CAT-REH', '康复器械', 1, 'active'
FROM product_categories pc
WHERE NOT EXISTS (SELECT 1 FROM product_categories x WHERE x.tenant_id = pc.tenant_id AND x.name = '康复器械');

-- 将无分类的产品补一个默认分类（取该租户最小分类 id）
UPDATE products p SET category_id = (
    SELECT MIN(c.id) FROM product_categories c WHERE c.tenant_id = p.tenant_id
) WHERE p.category_id IS NULL;
