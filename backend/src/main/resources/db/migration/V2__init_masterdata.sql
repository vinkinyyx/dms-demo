-- =====================================================================
-- V2: 主数据初始化
-- 说明：
--   C. 主数据：regions, product_categories, products, price_lists,
--              hospitals, dealers, dealer_addresses, warehouses,
--              workflows, workflow_nodes
--   - warehouses 主仓库唯一部分索引 ux_main_wh
-- 兼容：Flyway 8.x + PostgreSQL 14
-- =====================================================================

CREATE TABLE regions (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    parent_id   BIGINT REFERENCES regions(id),
    level       INT          NOT NULL,
    status      VARCHAR(16)  DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_regions_parent ON regions(parent_id);

CREATE TABLE product_categories (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    parent_id   BIGINT REFERENCES product_categories(id),
    level       INT          NOT NULL,
    status      VARCHAR(16)  DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_prod_cat_parent ON product_categories(parent_id);

CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    code           VARCHAR(64)  NOT NULL,
    name_cn        VARCHAR(200) NOT NULL,
    name_en        VARCHAR(200),
    category_id    BIGINT REFERENCES product_categories(id),
    spec           VARCHAR(100),
    unit           VARCHAR(32),
    current_price  NUMERIC(14,2),
    tax_rate       NUMERIC(5,4) DEFAULT 0.13,
    udi_required   BOOLEAN DEFAULT true,
    warn_months    INT DEFAULT 3,
    safety_qty     NUMERIC(14,4) DEFAULT 0,
    min_order_qty  NUMERIC(14,4),
    status         VARCHAR(16) DEFAULT 'active',
    attrs          JSONB DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    created_by     BIGINT,
    updated_by     BIGINT,
    version        INT DEFAULT 0,
    deleted_at     TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_tenant ON products(tenant_id, status);

CREATE TABLE price_lists (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    product_id  BIGINT,
    dealer_id   BIGINT,
    price       NUMERIC(14,2) NOT NULL,
    currency    VARCHAR(8) DEFAULT 'CNY',
    valid_from  DATE NOT NULL,
    valid_to    DATE,
    source      VARCHAR(16) DEFAULT 'ERP',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_price_active ON price_lists(tenant_id, product_id, dealer_id, valid_from DESC);

CREATE TABLE hospitals (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(32),
    level       VARCHAR(32),
    region_id   BIGINT REFERENCES regions(id),
    address     VARCHAR(500),
    contact     VARCHAR(100),
    phone       VARCHAR(32),
    attrs       JSONB DEFAULT '{}'::jsonb,
    status      VARCHAR(16) DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_hospitals_region ON hospitals(region_id);

CREATE TABLE dealers (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    code                  VARCHAR(32)  NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    level                 VARCHAR(16),
    parent_dealer_id      BIGINT REFERENCES dealers(id),
    legal_person          VARCHAR(64),
    usc_no                VARCHAR(32),
    reg_address           VARCHAR(500),
    reg_capital           NUMERIC(18,2),
    founded_at            DATE,
    business_scope        VARCHAR(500),
    gsp_status            VARCHAR(16),
    gsp_expire            DATE,
    gmp_status            VARCHAR(16),
    gmp_expire            DATE,
    region_id             BIGINT,
    contact_name          VARCHAR(100),
    contact_phone         VARCHAR(32),
    contact_email         VARCHAR(128),
    sales_owner_user_id   BIGINT,
    status                VARCHAR(16) DEFAULT 'active',
    attrs                 JSONB DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ DEFAULT now(),
    updated_at            TIMESTAMPTZ DEFAULT now(),
    created_by            BIGINT,
    updated_by            BIGINT,
    version               INT DEFAULT 0,
    deleted_at            TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_dealers_parent ON dealers(parent_dealer_id);
CREATE INDEX idx_dealers_region ON dealers(region_id);
CREATE INDEX idx_dealers_status ON dealers(tenant_id, status);

CREATE TABLE dealer_addresses (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    dealer_id     BIGINT REFERENCES dealers(id),
    is_default    BOOLEAN DEFAULT false,
    contact_name  VARCHAR(100),
    phone         VARCHAR(32),
    province      VARCHAR(64),
    city          VARCHAR(64),
    district      VARCHAR(64),
    address       VARCHAR(500),
    postal_code   VARCHAR(16),
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    version       INT DEFAULT 0,
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_dealer_addr_dealer ON dealer_addresses(dealer_id);

CREATE TABLE warehouses (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    dealer_id    BIGINT       NOT NULL REFERENCES dealers(id),
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    type         VARCHAR(16)  NOT NULL,   -- main / sub / hospital
    hospital_id  BIGINT REFERENCES hospitals(id),
    address      VARCHAR(500),
    status       VARCHAR(16) DEFAULT 'active',
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    created_by   BIGINT,
    updated_by   BIGINT,
    version      INT DEFAULT 0,
    deleted_at   TIMESTAMPTZ,
    UNIQUE (tenant_id, dealer_id, code)
);
CREATE UNIQUE INDEX ux_main_wh ON warehouses(tenant_id, dealer_id)
    WHERE type = 'main' AND status = 'active';
CREATE INDEX idx_warehouses_dealer ON warehouses(dealer_id);

CREATE TABLE workflows (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    version     INT DEFAULT 1,
    status      VARCHAR(16) DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    deleted_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, code, version)
);

CREATE TABLE workflow_nodes (
    id                 BIGSERIAL PRIMARY KEY,
    workflow_id        BIGINT REFERENCES workflows(id) ON DELETE CASCADE,
    code               VARCHAR(64) NOT NULL,
    name               VARCHAR(200),
    node_type          VARCHAR(16),
    assignee_strategy  VARCHAR(32),
    assignee_config    JSONB,
    visible_fields     TEXT[],
    timeout_hours      INT,
    seq                INT,
    created_at         TIMESTAMPTZ DEFAULT now(),
    updated_at         TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_wf_nodes_wf ON workflow_nodes(workflow_id, seq);
