-- =====================================================================
-- V28: 产品线主数据 (product_lines)
-- 说明：
--   支持 BU - 产品线 - 产品分类 - SKU 层级
--   level: 1=BU, 2=产品线, 3=分类
--   与 product_categories 并存（不废弃），category_id 可选关联到 ProductLine
--   启用 status 控制（active/inactive）
--   启用 parent_id 自引用实现树形结构
-- =====================================================================

CREATE TABLE product_lines (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    parent_id       BIGINT REFERENCES product_lines(id),
    level           INT          NOT NULL DEFAULT 1,
    description     TEXT,
    sort_order      INT          DEFAULT 0,
    status          VARCHAR(16)  DEFAULT 'active',
    created_at      TIMESTAMPTZ  DEFAULT now(),
    updated_at      TIMESTAMPTZ  DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,
    version         INT          DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_product_lines_parent ON product_lines(parent_id);
CREATE INDEX idx_product_lines_tenant ON product_lines(tenant_id, status);
CREATE INDEX idx_product_lines_level ON product_lines(tenant_id, level);

-- 为 products 表新增 product_line_id 字段（可选关联，不强制）
ALTER TABLE products ADD COLUMN IF NOT EXISTS product_line_id BIGINT REFERENCES product_lines(id);
CREATE INDEX IF NOT EXISTS idx_products_product_line ON products(product_line_id);

-- 注释
COMMENT ON TABLE product_lines IS '产品线主数据，支持BU/产品线/分类树形层级';
COMMENT ON COLUMN product_lines.level IS '层级：1=BU, 2=产品线, 3=分类';
COMMENT ON COLUMN product_lines.parent_id IS '父级产品线ID，根节点为NULL';
