-- =====================================================================
-- V29: 产品包装层级 (product_package_levels)
-- 说明：
--   支持运输包装 - 纸箱 - 彩盒 - 产品唯一码 的父子层级
--   level: 1=运输包装, 2=纸箱, 3=彩盒, 4=单品
--   quantity: 该层级包含的下一层级数量（如每箱装 N 个彩盒）
--   uom: 计量单位（box/carton/piece/pallet）
--   支持扫码追溯：扫描父码自动带出子码
-- =====================================================================

CREATE TABLE product_package_levels (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    product_id      BIGINT       NOT NULL REFERENCES products(id),
    parent_id       BIGINT       REFERENCES product_package_levels(id),
    level           INT          NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    quantity        INT          NOT NULL DEFAULT 1,
    uom             VARCHAR(32)  DEFAULT 'piece',
    barcode_format  VARCHAR(64),
    gtin            VARCHAR(32),
    sn_rule         VARCHAR(64),
    description     TEXT,
    sort_order      INT          DEFAULT 0,
    status          VARCHAR(16)  DEFAULT 'active',
    created_at      TIMESTAMPTZ  DEFAULT now(),
    updated_at      TIMESTAMPTZ  DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,
    version         INT          DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    UNIQUE (tenant_id, product_id, code)
);

CREATE INDEX idx_ppl_product ON product_package_levels(product_id);
CREATE INDEX idx_ppl_parent ON product_package_levels(parent_id);
CREATE INDEX idx_ppl_tenant ON product_package_levels(tenant_id, status);
CREATE INDEX idx_ppl_level ON product_package_levels(product_id, level);

-- 为产品表增加包装层级数量统计字段（可选）
ALTER TABLE products ADD COLUMN IF NOT EXISTS package_levels_count INT DEFAULT 0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS base_unit VARCHAR(32);

-- 注释
COMMENT ON TABLE product_package_levels IS '产品包装层级，支持多层父子关系用于扫码追溯';
COMMENT ON COLUMN product_package_levels.level IS '层级：1=运输包装, 2=纸箱, 3=彩盒, 4=单品';
COMMENT ON COLUMN product_package_levels.quantity IS '该层级包含下一层级的数量';
COMMENT ON COLUMN product_package_levels.parent_id IS '父级包装层级，根节点(运输包装)为NULL';
COMMENT ON COLUMN product_package_levels.gtin IS '全球贸易项目代码';
COMMENT ON COLUMN product_package_levels.sn_rule IS '序列号生成规则';
