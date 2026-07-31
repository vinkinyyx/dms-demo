-- V39: 销售出库父子单结构（对齐收货入库 receipt_batches 模型）
-- 每次发货一张子单(sales_out_batches)，可保存草稿/独立确认/取消本次；
-- 取消剩余待发则完结父单。
-- 与收货的关键区别：子单行的 batch_no/serial_no 必须来自在库合格库存(QUALIFIED)，由前端选择而非手填。

CREATE TABLE IF NOT EXISTS sales_out_batches (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    sales_out_id    BIGINT NOT NULL REFERENCES sales_outs(id) ON DELETE CASCADE,
    code            VARCHAR(80) UNIQUE NOT NULL,
    seq             INT NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
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
CREATE INDEX IF NOT EXISTS idx_sob_sales_out ON sales_out_batches(sales_out_id);
CREATE INDEX IF NOT EXISTS idx_sob_tenant    ON sales_out_batches(tenant_id, status);

CREATE TABLE IF NOT EXISTS sales_out_batch_lines (
    id                BIGSERIAL PRIMARY KEY,
    batch_id          BIGINT NOT NULL REFERENCES sales_out_batches(id) ON DELETE CASCADE,
    expected_line_id  BIGINT,
    expected_line_seq INT,
    ship_line_no      INT NOT NULL,
    product_id        BIGINT NOT NULL,
    warehouse_id      BIGINT NOT NULL,
    qty               NUMERIC(14,4) NOT NULL DEFAULT 0,
    stock_batch_id    BIGINT,
    batch_no          VARCHAR(64),
    serial_no         VARCHAR(128),
    unit_price        NUMERIC(18,4) DEFAULT 0,
    remark            TEXT,
    created_at        TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sobl_batch   ON sales_out_batch_lines(batch_id);
CREATE INDEX IF NOT EXISTS idx_sobl_exp_line ON sales_out_batch_lines(expected_line_id);

COMMENT ON TABLE sales_out_batches IS '销售出库子单(每次发货一张)';
COMMENT ON COLUMN sales_out_batches.status IS 'DRAFT / CONFIRMED / CANCELLED';