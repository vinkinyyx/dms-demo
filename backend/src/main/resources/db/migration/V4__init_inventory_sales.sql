-- =====================================================================
-- V4: 库存 / 销售 初始化
-- 说明：
--   G. 收货 / 库存 / 销售：receipts, receipt_lines, inventory,
--        inventory_transactions（按月 RANGE 分区）, stock_moves(+lines),
--        loans(+lines), inventory_adjustments(+lines),
--        stocktakes(+lines), sales_outs(+lines), sales_out_facts
--   H. 分销 & 退换货：distribution_shipments(+lines),
--        rma_authorizations, rma_orders
--   I. 发票：purchase_invoices, sales_invoices
-- 分区：inventory_transactions 2026-07~2026-12 六个月分区
-- 兼容：Flyway 8.x + PostgreSQL 14
-- =====================================================================

-- ---------------------------------------------------------------------
-- G-1. 收货
-- ---------------------------------------------------------------------
CREATE TABLE receipts (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    code           VARCHAR(64) UNIQUE NOT NULL,
    receipt_type   VARCHAR(16),
    ref_doc_type   VARCHAR(16),
    ref_doc_id     BIGINT,
    dealer_id      BIGINT REFERENCES dealers(id),
    warehouse_id   BIGINT REFERENCES warehouses(id),
    status         VARCHAR(16) DEFAULT 'PENDING',
    received_at    TIMESTAMPTZ,
    received_by    BIGINT,
    remark         TEXT,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    created_by     BIGINT,
    updated_by     BIGINT,
    version        INT DEFAULT 0,
    deleted_at     TIMESTAMPTZ
);
CREATE INDEX idx_receipts_dealer ON receipts(dealer_id);
CREATE INDEX idx_receipts_status ON receipts(tenant_id, status);

CREATE TABLE receipt_lines (
    id             BIGSERIAL PRIMARY KEY,
    receipt_id     BIGINT REFERENCES receipts(id) ON DELETE CASCADE,
    product_id     BIGINT,
    batch_no       VARCHAR(64),
    serial_no      VARCHAR(64),
    prod_date      DATE,
    exp_date       DATE,
    expected_qty   NUMERIC(14,4),
    received_qty   NUMERIC(14,4),
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX ux_rcpt_serial ON receipt_lines(receipt_id, serial_no) WHERE serial_no IS NOT NULL;
CREATE INDEX idx_rcpt_lines_receipt ON receipt_lines(receipt_id);

-- ---------------------------------------------------------------------
-- G-2. 库存
-- ---------------------------------------------------------------------
CREATE TABLE inventory (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    dealer_id     BIGINT REFERENCES dealers(id),
    warehouse_id  BIGINT REFERENCES warehouses(id),
    product_id    BIGINT REFERENCES products(id),
    batch_no      VARCHAR(64),
    serial_no     VARCHAR(64),
    prod_date     DATE,
    exp_date      DATE,
    qty           NUMERIC(14,4) NOT NULL DEFAULT 0,
    in_source     VARCHAR(32),
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    version       INT DEFAULT 0,
    UNIQUE (tenant_id, warehouse_id, product_id, batch_no, serial_no)
);
CREATE INDEX idx_inv_lookup ON inventory(tenant_id, dealer_id, product_id, batch_no);
CREATE INDEX idx_inv_expire ON inventory(exp_date);

-- ---------------------------------------------------------------------
-- G-3. 库存流水（按月 RANGE 分区）
-- ---------------------------------------------------------------------
CREATE TABLE inventory_transactions (
    id             BIGSERIAL,
    tenant_id      UUID NOT NULL,
    dealer_id      BIGINT,
    warehouse_id   BIGINT,
    product_id     BIGINT,
    batch_no       VARCHAR(64),
    serial_no      VARCHAR(64),
    qty_change     NUMERIC(14,4) NOT NULL,
    txn_type       VARCHAR(32) NOT NULL,
    ref_doc_type   VARCHAR(32),
    ref_doc_id     BIGINT,
    at_time        TIMESTAMPTZ DEFAULT now(),
    operator_id    BIGINT,
    PRIMARY KEY (id, at_time)
) PARTITION BY RANGE (at_time);

CREATE INDEX idx_inv_txn_tenant_time ON inventory_transactions(tenant_id, at_time DESC);
CREATE INDEX idx_inv_txn_product ON inventory_transactions(product_id, at_time DESC);

-- 2026-07 ~ 2026-12 六个月分区
CREATE TABLE inventory_transactions_2026_07 PARTITION OF inventory_transactions
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');
CREATE TABLE inventory_transactions_2026_08 PARTITION OF inventory_transactions
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');
CREATE TABLE inventory_transactions_2026_09 PARTITION OF inventory_transactions
    FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');
CREATE TABLE inventory_transactions_2026_10 PARTITION OF inventory_transactions
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');
CREATE TABLE inventory_transactions_2026_11 PARTITION OF inventory_transactions
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
CREATE TABLE inventory_transactions_2026_12 PARTITION OF inventory_transactions
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

-- ---------------------------------------------------------------------
-- G-4. 移库
-- ---------------------------------------------------------------------
CREATE TABLE stock_moves (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    code              VARCHAR(64) UNIQUE,
    dealer_id         BIGINT,
    src_warehouse_id  BIGINT,
    dst_warehouse_id  BIGINT,
    status            VARCHAR(16) DEFAULT 'COMPLETED',
    reason            TEXT,
    operator_id       BIGINT,
    at_time           TIMESTAMPTZ DEFAULT now(),
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    version           INT DEFAULT 0,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_stock_moves_dealer ON stock_moves(dealer_id);

CREATE TABLE stock_move_lines (
    id          BIGSERIAL PRIMARY KEY,
    move_id     BIGINT REFERENCES stock_moves(id) ON DELETE CASCADE,
    product_id  BIGINT,
    batch_no    VARCHAR(64),
    serial_no   VARCHAR(64),
    qty         NUMERIC(14,4),
    created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_stock_move_lines_move ON stock_move_lines(move_id);

-- ---------------------------------------------------------------------
-- G-5. 借货
-- ---------------------------------------------------------------------
CREATE TABLE loans (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    code                 VARCHAR(64) UNIQUE,
    lender_dealer_id     BIGINT,
    borrower_dealer_id   BIGINT,
    status               VARCHAR(16) DEFAULT 'PENDING',
    ref_loan_id          BIGINT,
    reason               TEXT,
    created_at           TIMESTAMPTZ DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ DEFAULT now(),
    created_by           BIGINT,
    updated_by           BIGINT,
    version              INT DEFAULT 0,
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_loans_lender ON loans(lender_dealer_id);
CREATE INDEX idx_loans_borrower ON loans(borrower_dealer_id);

CREATE TABLE loan_lines (
    id          BIGSERIAL PRIMARY KEY,
    loan_id     BIGINT REFERENCES loans(id) ON DELETE CASCADE,
    product_id  BIGINT,
    batch_no    VARCHAR(64),
    serial_no   VARCHAR(64),
    qty         NUMERIC(14,4),
    created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_loan_lines_loan ON loan_lines(loan_id);

-- ---------------------------------------------------------------------
-- G-6. 库存调整
-- ---------------------------------------------------------------------
CREATE TABLE inventory_adjustments (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    code          VARCHAR(64) UNIQUE,
    dealer_id     BIGINT,
    warehouse_id  BIGINT,
    adj_category  VARCHAR(16),   -- INCREASE / DECREASE
    adj_type      VARCHAR(32),
    status        VARCHAR(16) DEFAULT 'DRAFT',
    reason        TEXT,
    operator_id   BIGINT,
    approver_id   BIGINT,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    version       INT DEFAULT 0,
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_inv_adj_dealer ON inventory_adjustments(dealer_id);
CREATE INDEX idx_inv_adj_status ON inventory_adjustments(tenant_id, status);

CREATE TABLE adjustment_lines (
    id             BIGSERIAL PRIMARY KEY,
    adjustment_id  BIGINT REFERENCES inventory_adjustments(id) ON DELETE CASCADE,
    product_id     BIGINT,
    batch_no       VARCHAR(64),
    serial_no      VARCHAR(64),
    qty            NUMERIC(14,4),
    reason         TEXT,
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_adj_lines_adj ON adjustment_lines(adjustment_id);

-- ---------------------------------------------------------------------
-- G-7. 盘点
-- ---------------------------------------------------------------------
CREATE TABLE stocktakes (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    dealer_id       BIGINT,
    period_yyyymm   CHAR(6) NOT NULL,
    uploaded_at     TIMESTAMPTZ,
    uploaded_by     BIGINT,
    is_late         BOOLEAN DEFAULT false,
    diff_summary    JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    version         INT DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    UNIQUE (tenant_id, dealer_id, period_yyyymm)
);

CREATE TABLE stocktake_lines (
    id            BIGSERIAL PRIMARY KEY,
    stocktake_id  BIGINT REFERENCES stocktakes(id) ON DELETE CASCADE,
    product_id    BIGINT,
    batch_no      VARCHAR(64),
    serial_no     VARCHAR(64),
    book_qty      NUMERIC(14,4),
    actual_qty    NUMERIC(14,4),
    diff_qty      NUMERIC(14,4),
    created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_stkt_lines_stkt ON stocktake_lines(stocktake_id);

-- ---------------------------------------------------------------------
-- G-8. 销售出库（报台）
-- ---------------------------------------------------------------------
CREATE TABLE sales_outs (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    code              VARCHAR(64) UNIQUE,
    dealer_id         BIGINT,
    terminal_id       BIGINT REFERENCES hospitals(id),
    business_type     VARCHAR(16),
    sales_date        DATE,
    surgery_info      JSONB,
    is_red            BOOLEAN DEFAULT false,
    ref_sales_out_id  BIGINT,
    status            VARCHAR(16) DEFAULT 'SUBMITTED',
    amount_incl_tax   NUMERIC(18,2),
    created_by        BIGINT,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    updated_by        BIGINT,
    version           INT DEFAULT 0,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_sales_outs_dealer ON sales_outs(dealer_id, sales_date DESC);
CREATE INDEX idx_sales_outs_terminal ON sales_outs(terminal_id);

CREATE TABLE sales_out_lines (
    id             BIGSERIAL PRIMARY KEY,
    sales_out_id   BIGINT REFERENCES sales_outs(id) ON DELETE CASCADE,
    warehouse_id   BIGINT,
    product_id     BIGINT,
    batch_no       VARCHAR(64),
    serial_no      VARCHAR(64),
    qty            NUMERIC(14,4),
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX ux_sales_serial ON sales_out_lines(serial_no) WHERE serial_no IS NOT NULL;
CREATE INDEX idx_sales_lines_out ON sales_out_lines(sales_out_id);

CREATE TABLE sales_out_facts (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    dealer_id    BIGINT,
    product_id   BIGINT,
    terminal_id  BIGINT,
    region_id    BIGINT,
    sales_date   DATE,
    qty          NUMERIC(14,4),
    amount       NUMERIC(18,2),
    created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_sof_dealer_date ON sales_out_facts(dealer_id, sales_date);
CREATE INDEX idx_sof_tenant_date ON sales_out_facts(tenant_id, sales_date);

-- ---------------------------------------------------------------------
-- H. 分销 & 退换货
-- ---------------------------------------------------------------------
CREATE TABLE distribution_shipments (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    code            VARCHAR(64) UNIQUE,
    from_dealer_id  BIGINT,
    to_dealer_id    BIGINT,
    ref_order_id    BIGINT,
    status          VARCHAR(16) DEFAULT 'PENDING',
    express_no      VARCHAR(64),
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    version         INT DEFAULT 0,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_dist_from ON distribution_shipments(from_dealer_id);
CREATE INDEX idx_dist_to ON distribution_shipments(to_dealer_id);

CREATE TABLE distribution_lines (
    id           BIGSERIAL PRIMARY KEY,
    shipment_id  BIGINT REFERENCES distribution_shipments(id) ON DELETE CASCADE,
    product_id   BIGINT,
    batch_no     VARCHAR(64),
    serial_no    VARCHAR(64),
    qty          NUMERIC(14,4),
    created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_dist_lines_shp ON distribution_lines(shipment_id);

CREATE TABLE rma_authorizations (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    code            VARCHAR(64) UNIQUE,
    dealer_id       BIGINT,
    product_line_id BIGINT,
    quota_amount    NUMERIC(18,2),
    quota_used      NUMERIC(18,2) DEFAULT 0,
    valid_from      DATE,
    valid_to        DATE,
    status          VARCHAR(16) DEFAULT 'active',
    reason          TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    version         INT DEFAULT 0,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_rma_auth_dealer ON rma_authorizations(dealer_id);

CREATE TABLE rma_orders (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    code              VARCHAR(64) UNIQUE,
    ref_rma_auth_id   BIGINT REFERENCES rma_authorizations(id),
    dealer_id         BIGINT,
    rma_type          VARCHAR(16),
    amount            NUMERIC(18,2),
    status            VARCHAR(16) DEFAULT 'DRAFT',
    lines             JSONB,
    reason            TEXT,
    attachments       JSONB,
    submitted_at      TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    created_by        BIGINT,
    updated_by        BIGINT,
    version           INT DEFAULT 0,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_rma_orders_dealer ON rma_orders(dealer_id, status);

-- ---------------------------------------------------------------------
-- I. 发票
-- ---------------------------------------------------------------------
CREATE TABLE purchase_invoices (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    ref_order_id  BIGINT REFERENCES orders(id),
    invoice_no    VARCHAR(64) NOT NULL,
    invoice_date  DATE,
    amount        NUMERIC(18,2),
    tax_amount    NUMERIC(18,2),
    tax_rate      NUMERIC(5,4),
    image_url     TEXT,
    uploaded_by   BIGINT,
    uploaded_at   TIMESTAMPTZ DEFAULT now(),
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    version       INT DEFAULT 0,
    deleted_at    TIMESTAMPTZ,
    UNIQUE (tenant_id, invoice_no)
);

CREATE TABLE sales_invoices (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    ref_sales_out_id  BIGINT REFERENCES sales_outs(id),
    invoice_no        VARCHAR(64) NOT NULL,
    invoice_date      DATE,
    amount            NUMERIC(18,2),
    tax_amount        NUMERIC(18,2),
    image_url         TEXT,
    uploaded_at       TIMESTAMPTZ DEFAULT now(),
    uploaded_by       BIGINT,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    version           INT DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    UNIQUE (tenant_id, invoice_no)
);
