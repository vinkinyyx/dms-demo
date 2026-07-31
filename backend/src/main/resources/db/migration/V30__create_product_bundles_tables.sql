-- =====================================================================
-- V30: 产品组套主数据 (product_bundles + product_bundle_lines)
-- 说明：
--   组套 = 父 SKU (Bundles) 包含多个子 SKU (BundleLines)
--   line_type: FIXED 固定件(必选), OPTIONAL 可选件
--   拆套规则：allow_split=true 时可单独销售子件
--   定价方式：INHERIT 继承父件价, OVERRIDE 自定义价格, COMPONENT 按子件累加
-- =====================================================================

-- 组套主表
CREATE TABLE product_bundles (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    product_id      BIGINT       NOT NULL REFERENCES products(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    pricing_type    VARCHAR(16)  NOT NULL DEFAULT 'INHERIT',
    bundle_price    NUMERIC(14,2),
    allow_split     BOOLEAN      NOT NULL DEFAULT false,
    split_rule      TEXT,
    version_note    TEXT,
    valid_from      TIMESTAMPTZ,
    valid_to        TIMESTAMPTZ,
    status          VARCHAR(16)  DEFAULT 'active',
    created_at      TIMESTAMPTZ  DEFAULT now(),
    updated_at      TIMESTAMPTZ  DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,
    version         INT          DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    UNIQUE (tenant_id, product_id, code)
);

CREATE INDEX idx_pb_product ON product_bundles(product_id);
CREATE INDEX idx_pb_tenant ON product_bundles(tenant_id, status);

COMMENT ON TABLE product_bundles IS '产品组套主数据：一个父SKU包含多个子SKU';
COMMENT ON COLUMN product_bundles.pricing_type IS '定价方式：INHERIT继承父价/OVERRIDE自定义/COMPONENT按子件累加';
COMMENT ON COLUMN product_bundles.allow_split IS '是否允许拆分销售子件';
COMMENT ON COLUMN product_bundles.split_rule IS '拆套规则描述';

-- 组套明细表
CREATE TABLE product_bundle_lines (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    bundle_id           BIGINT       NOT NULL REFERENCES product_bundles(id) ON DELETE CASCADE,
    child_product_id    BIGINT       NOT NULL REFERENCES products(id),
    line_type           VARCHAR(16)  NOT NULL DEFAULT 'FIXED',
    quantity            NUMERIC(14,4) NOT NULL DEFAULT 1,
    is_required         BOOLEAN      NOT NULL DEFAULT true,
    sort_order          INT          DEFAULT 0,
    description         TEXT,
    status              VARCHAR(16)  DEFAULT 'active',
    created_at          TIMESTAMPTZ  DEFAULT now(),
    updated_at          TIMESTAMPTZ  DEFAULT now(),
    created_by          BIGINT,
    updated_by          BIGINT,
    version             INT          DEFAULT 0,
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_pbl_bundle ON product_bundle_lines(bundle_id);
CREATE INDEX idx_pbl_child ON product_bundle_lines(child_product_id);
CREATE INDEX idx_pbl_tenant ON product_bundle_lines(tenant_id, status);

-- 同一组套下同一子产品唯一
CREATE UNIQUE INDEX uk_pbl_bundle_child ON product_bundle_lines(bundle_id, child_product_id)
WHERE deleted_at IS NULL;

COMMENT ON TABLE product_bundle_lines IS '组套明细：父件包含的子件列表';
COMMENT ON COLUMN product_bundle_lines.line_type IS '子件类型：FIXED固定必选/OPTIONAL可选';
COMMENT ON COLUMN product_bundle_lines.is_required IS '是否必选（冗余字段，供前端快速判断）';
COMMENT ON COLUMN product_bundle_lines.quantity IS '该子件在组套中的数量（支持小数如耗材）';

-- 为产品表增加 is_bundle 标记
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_bundle BOOLEAN DEFAULT false;
ALTER TABLE products ADD COLUMN IF NOT EXISTS bundle_id BIGINT REFERENCES product_bundles(id);
CREATE INDEX IF NOT EXISTS idx_products_bundle ON products(bundle_id);
