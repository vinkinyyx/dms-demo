-- =====================================================================
-- V23: 修正 V22/V6 中残留字段名错误，仅按当前表结构追加 product_type 字典项
-- 当前 dict_types 列: id, tenant_id, code, name, description, created_at, updated_at
-- 当前 dict_items 列: id, type_id, code, name, seq, status, attrs, created_at, updated_at
-- =====================================================================

-- 确保 product_type 字典类型存在
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES (NULL, 'product_type', '产品类型', '产品分类类型')
ON CONFLICT (tenant_id, code) DO NOTHING;

-- 添加 product_type 字典项（成品/半成品/原材料）
INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT dt.id, 'finished', '成品', 10, 'active'
FROM dict_types dt
WHERE dt.code = 'product_type' AND dt.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT dt.id, 'semi_finished', '半成品', 20, 'active'
FROM dict_types dt
WHERE dt.code = 'product_type' AND dt.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT dt.id, 'raw_material', '原材料', 30, 'active'
FROM dict_types dt
WHERE dt.code = 'product_type' AND dt.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;
