-- v4.1.0: 产品价格区分单品价与 BOM 子件价；BOM 母件保留价格头记录。
ALTER TABLE product_prices
  ADD COLUMN IF NOT EXISTS price_context VARCHAR(16) NOT NULL DEFAULT 'STANDALONE',
  ADD COLUMN IF NOT EXISTS bom_parent_product_id BIGINT;

DROP INDEX IF EXISTS uk_product_prices_scope_partner;

-- 1) 为作为 BOM 子件出现的 SKU 复制 BOM_COMPONENT 价格（若尚不存在）
INSERT INTO product_prices (
  tenant_id, product_id, partner_type, partner_id,
  purchase_price, purchase_price_excl_tax, sales_price, sales_price_excl_tax,
  tax_rate, currency, valid_from, valid_to, status, price_scope,
  price_context, bom_parent_product_id, created_at, updated_at
)
SELECT
  pp.tenant_id,
  pp.product_id,
  pp.partner_type,
  pp.partner_id,
  pp.purchase_price,
  pp.purchase_price_excl_tax,
  pp.sales_price,
  pp.sales_price_excl_tax,
  pp.tax_rate,
  pp.currency,
  pp.valid_from,
  pp.valid_to,
  pp.status,
  pp.price_scope,
  'BOM_COMPONENT',
  pb.product_id,
  now(),
  now()
FROM product_prices pp
JOIN product_bundle_lines pbl ON pbl.child_product_id = pp.product_id AND pbl.deleted_at IS NULL
JOIN product_bundles pb ON pb.id = pbl.bundle_id AND pb.deleted_at IS NULL AND pb.version_status = 'active'
WHERE pp.tenant_id = pb.tenant_id
  AND pp.price_scope = 'SALE'
  AND pp.price_context = 'STANDALONE'
  AND pp.status = 'active'
  AND pp.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM product_prices existing
    WHERE existing.tenant_id = pp.tenant_id
      AND existing.product_id = pp.product_id
      AND existing.partner_id IS NOT DISTINCT FROM pp.partner_id
      AND existing.price_scope = 'SALE'
      AND existing.price_context = 'BOM_COMPONENT'
      AND existing.bom_parent_product_id = pb.product_id
      AND existing.deleted_at IS NULL
  );

-- 2) 为每个 (BOM母件, 经销商) 补建 BOM_HEADER 0 元头记录（若不存在）
INSERT INTO product_prices (
  tenant_id, product_id, partner_type, partner_id,
  purchase_price, purchase_price_excl_tax, sales_price, sales_price_excl_tax,
  tax_rate, currency, valid_from, valid_to, status, price_scope,
  price_context, bom_parent_product_id, created_at, updated_at
)
SELECT DISTINCT
  c.tenant_id,
  c.bom_parent_product_id,
  'DEALER',
  c.partner_id,
  0, 0, 0, 0,
  0.13,
  COALESCE(c.currency, 'CNY'),
  CAST(NULL AS TIMESTAMPTZ),
  CAST(NULL AS TIMESTAMPTZ),
  c.status,
  'SALE',
  'BOM_HEADER',
  CAST(NULL AS BIGINT),
  now(),
  now()
FROM product_prices c
WHERE c.price_context = 'BOM_COMPONENT'
  AND c.price_scope = 'SALE'
  AND c.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM product_prices h
    WHERE h.tenant_id = c.tenant_id
      AND h.product_id = c.bom_parent_product_id
      AND h.partner_id IS NOT DISTINCT FROM c.partner_id
      AND h.price_scope = 'SALE'
      AND h.price_context = 'BOM_HEADER'
      AND h.deleted_at IS NULL
  );

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_prices_active_context
  ON product_prices(
    tenant_id,
    product_id,
    partner_type,
    COALESCE(partner_id, 0),
    price_scope,
    price_context,
    COALESCE(bom_parent_product_id, 0)
  )
  WHERE deleted_at IS NULL AND status = 'active';

CREATE INDEX IF NOT EXISTS idx_product_prices_context
  ON product_prices(tenant_id, price_scope, price_context, partner_type, partner_id, bom_parent_product_id)
  WHERE deleted_at IS NULL;