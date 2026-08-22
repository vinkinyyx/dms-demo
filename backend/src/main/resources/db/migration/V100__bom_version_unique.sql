-- v4.0.0-bugfix.2: BOM 支持同一母件多版本，原 (tenant, product, code) 唯一约束会阻断“新建版本”
ALTER TABLE product_bundles DROP CONSTRAINT IF EXISTS product_bundles_tenant_id_product_id_code_key;

-- 清理历史重复版本：保留 active，否则保留每组最新更新记录，其余软删除
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, product_id, bom_version
               ORDER BY CASE WHEN version_status = 'active' THEN 0 ELSE 1 END,
                        updated_at DESC NULLS LAST,
                        id DESC
           ) AS rn
    FROM product_bundles
    WHERE deleted_at IS NULL
)
UPDATE product_bundles pb
SET deleted_at = now(), updated_at = now()
FROM ranked r
WHERE pb.id = r.id AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_bundles_version
    ON product_bundles(tenant_id, product_id, bom_version)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_product_bundles_product_code
    ON product_bundles(tenant_id, product_id, code)
    WHERE deleted_at IS NULL;
