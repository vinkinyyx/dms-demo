-- =====================================================================
-- V3: 合同 / 授权 / 订单初始化
-- 说明：
--   D. 合同：contract_applications, contract_diff, contracts,
--            contract_attachments, contract_signatures, contract_templates
--   E. 授权：authorizations, temp_authorizations
--   F. 订单：orders, order_lines, order_promotion_hits, order_status_history
-- 兼容：Flyway 8.x + PostgreSQL 14
-- =====================================================================

-- ---------------------------------------------------------------------
-- D. 合同
-- ---------------------------------------------------------------------
CREATE TABLE contract_applications (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    code                  VARCHAR(64) UNIQUE NOT NULL,
    application_type      VARCHAR(16) NOT NULL,   -- NEW/MODIFY/RENEW/TERMINATE/BATCH_EXTEND/BATCH_UPDATE
    contract_category     VARCHAR(32) NOT NULL,
    ref_contract_id       BIGINT,
    dealer_id             BIGINT REFERENCES dealers(id),
    dealer_snapshot       JSONB,
    authorization_scope   JSONB,
    indicators            JSONB,
    valid_from            DATE,
    valid_to              DATE,
    status                VARCHAR(16) DEFAULT 'draft',
    created_by            BIGINT,
    created_at            TIMESTAMPTZ DEFAULT now(),
    updated_at            TIMESTAMPTZ DEFAULT now(),
    updated_by            BIGINT,
    submitted_at          TIMESTAMPTZ,
    effective_at          TIMESTAMPTZ,
    remark                TEXT,
    version               INT DEFAULT 0,
    deleted_at            TIMESTAMPTZ
);
CREATE INDEX idx_contract_app_dealer ON contract_applications(dealer_id);
CREATE INDEX idx_contract_app_status ON contract_applications(tenant_id, status);

CREATE TABLE contract_diff (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT REFERENCES contract_applications(id) ON DELETE CASCADE,
    field_group     VARCHAR(64),
    field_key       VARCHAR(128),
    before_value    TEXT,
    after_value     TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_contract_diff_app ON contract_diff(application_id);

CREATE TABLE contracts (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    code                VARCHAR(64) UNIQUE NOT NULL,
    application_id      BIGINT REFERENCES contract_applications(id),
    dealer_id           BIGINT REFERENCES dealers(id),
    category            VARCHAR(32),
    valid_from          DATE,
    valid_to            DATE,
    status              VARCHAR(16) DEFAULT 'effective',
    pdf_url             TEXT,
    ca_serial_no        VARCHAR(128),
    dealer_signed_at    TIMESTAMPTZ,
    vendor_signed_at    TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ DEFAULT now(),
    updated_at          TIMESTAMPTZ DEFAULT now(),
    created_by          BIGINT,
    updated_by          BIGINT,
    version             INT DEFAULT 0,
    deleted_at          TIMESTAMPTZ
);
CREATE INDEX idx_contracts_dealer ON contracts(dealer_id);
CREATE INDEX idx_contracts_status ON contracts(tenant_id, status);
CREATE INDEX idx_contracts_valid ON contracts(tenant_id, valid_from, valid_to);

CREATE TABLE contract_attachments (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID,
    ref_type      VARCHAR(16),   -- application / contract
    ref_id        BIGINT,
    category      VARCHAR(64),
    file_id       BIGINT,
    file_url      TEXT,
    file_name     VARCHAR(255),
    size_bytes    BIGINT,
    uploaded_by   BIGINT,
    uploaded_at   TIMESTAMPTZ DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_contract_att_ref ON contract_attachments(ref_type, ref_id);

CREATE TABLE contract_signatures (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID,
    contract_id     BIGINT REFERENCES contracts(id) ON DELETE CASCADE,
    signer_type     VARCHAR(16),   -- vendor / dealer
    signer_user_id  BIGINT,
    ca_serial_no    VARCHAR(128),
    sms_code_ref    VARCHAR(64),
    signed_at       TIMESTAMPTZ DEFAULT now(),
    status          VARCHAR(16)
);
CREATE INDEX idx_contract_sig_contract ON contract_signatures(contract_id);

CREATE TABLE contract_templates (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    code         VARCHAR(64) NOT NULL,
    name         VARCHAR(200),
    category     VARCHAR(32),
    content_url  TEXT,
    variables    TEXT[],
    version      INT DEFAULT 1,
    status       VARCHAR(16) DEFAULT 'active',
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted_at   TIMESTAMPTZ,
    UNIQUE (tenant_id, code, version)
);

-- ---------------------------------------------------------------------
-- E. 授权
-- ---------------------------------------------------------------------
CREATE TABLE authorizations (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    dealer_id        BIGINT REFERENCES dealers(id),
    contract_id      BIGINT REFERENCES contracts(id),
    auth_type        VARCHAR(32) NOT NULL,   -- ORDER/SALES_TO_HOSPITAL/SALES_TO_SUB/RMA/MOVE/LOAN
    product_line_id  BIGINT,
    product_id       BIGINT,
    terminal_id      BIGINT,
    region_id        BIGINT,
    valid_from       DATE NOT NULL,
    valid_to         DATE NOT NULL,
    status           VARCHAR(16) DEFAULT 'active',
    source           VARCHAR(16) DEFAULT 'contract',
    created_at       TIMESTAMPTZ DEFAULT now(),
    updated_at       TIMESTAMPTZ DEFAULT now(),
    created_by       BIGINT,
    updated_by       BIGINT,
    version          INT DEFAULT 0,
    deleted_at       TIMESTAMPTZ
);
CREATE INDEX idx_auth_lookup ON authorizations(tenant_id, dealer_id, auth_type, status, valid_from, valid_to);
CREATE INDEX idx_auth_contract ON authorizations(contract_id);

CREATE TABLE temp_authorizations (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    dealer_id     BIGINT,
    auth_type     VARCHAR(32),
    scope         JSONB,
    valid_from    DATE,
    valid_to      DATE,
    reason        TEXT,
    status        VARCHAR(16) DEFAULT 'pending',
    applicant_id  BIGINT,
    approved_by   BIGINT,
    approved_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    version       INT DEFAULT 0,
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_temp_auth_dealer ON temp_authorizations(dealer_id, status);

-- ---------------------------------------------------------------------
-- F. 订单
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    code               VARCHAR(64) UNIQUE NOT NULL,
    order_type         VARCHAR(16) NOT NULL,   -- PURCHASE/SHORTAGE/CUSTOM
    dealer_id          BIGINT NOT NULL REFERENCES dealers(id),
    ship_address_id    BIGINT REFERENCES dealer_addresses(id),
    ship_snapshot      JSONB,
    status             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    parent_order_id    BIGINT REFERENCES orders(id),
    amount_incl_tax    NUMERIC(18,2) DEFAULT 0,
    discount_amount    NUMERIC(18,2) DEFAULT 0,
    final_amount       NUMERIC(18,2) DEFAULT 0,
    remark             TEXT,
    expected_date      DATE,
    submitted_at       TIMESTAMPTZ,
    approved_at        TIMESTAMPTZ,
    shipped_at         TIMESTAMPTZ,
    received_at        TIMESTAMPTZ,
    closed_at          TIMESTAMPTZ,
    erp_order_no       VARCHAR(64),
    created_by         BIGINT,
    created_at         TIMESTAMPTZ DEFAULT now(),
    updated_at         TIMESTAMPTZ DEFAULT now(),
    updated_by         BIGINT,
    version            INT DEFAULT 0,
    deleted_at         TIMESTAMPTZ
);
CREATE INDEX idx_orders_dealer ON orders(dealer_id);
CREATE INDEX idx_orders_status ON orders(tenant_id, status);
CREATE INDEX idx_orders_submitted ON orders(tenant_id, submitted_at DESC);

CREATE TABLE order_lines (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   BIGINT NOT NULL,
    qty          NUMERIC(14,4) NOT NULL,
    unit_price   NUMERIC(14,2) NOT NULL,
    tax_rate     NUMERIC(5,4),
    sub_total    NUMERIC(18,2) NOT NULL,
    is_gift      BOOLEAN DEFAULT false,
    seq          INT,
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_order_lines_order ON order_lines(order_id);
CREATE INDEX idx_order_lines_product ON order_lines(product_id);

CREATE TABLE order_promotion_hits (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT REFERENCES orders(id) ON DELETE CASCADE,
    promotion_id  BIGINT NOT NULL,
    rule_type     VARCHAR(16),
    discount      NUMERIC(18,2),
    gift_lines    JSONB,
    detail        JSONB,
    created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_order_promo_order ON order_promotion_hits(order_id);

CREATE TABLE order_status_history (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL,
    from_status  VARCHAR(32),
    to_status    VARCHAR(32),
    action       VARCHAR(32),
    operator_id  BIGINT,
    comment      TEXT,
    at_time      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_order_status_order ON order_status_history(order_id, at_time DESC);
