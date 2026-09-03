-- =====================================================================
-- V144: 外部经销商报文协同——物料映射演示种子补种
--   V143 首次在测试环境执行时，物料映射种子误用 PROD-000001..003 编码，
--   与真实演示产品编码（PRD-*）不匹配，导致 open_partner_materials 0 行。
--   本迁移用真实产品编码 PRD-J001/J002/M001 补种，INSERT 带 NOT EXISTS 幂等保护，
--   V143 已修正为相同编码的全新环境执行本迁移不会重复插入。
-- =====================================================================

INSERT INTO open_partner_materials (tenant_id, app_id, dealer_code, external_code, external_name,
                                    product_id, product_code, status, created_at, updated_at)
SELECT t.id, a.id, 'EXT-D1', v.ext_code, v.ext_name, p.id, p.code, 'active', now(), now()
FROM tenants t
JOIN open_app a ON a.tenant_id = t.id AND a.app_key = 'dms-ext-dealer-d1'
CROSS JOIN (VALUES
    ('EXT-MAT-001', '外部物料-支架001', 'PRD-J001'),
    ('EXT-MAT-002', '外部物料-支架002', 'PRD-J002'),
    ('EXT-MAT-003', '外部物料-器械003', 'PRD-M001')
) AS v(ext_code, ext_name, mfr_code)
JOIN products p ON p.tenant_id = t.id AND p.code = v.mfr_code AND p.deleted_at IS NULL
WHERE t.code = 'default'
  AND NOT EXISTS (SELECT 1 FROM open_partner_materials m
                  WHERE m.app_id = a.id AND m.external_code = v.ext_code AND m.deleted_at IS NULL);
