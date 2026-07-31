-- V33: 供应商等级字典 + 供应商 level 列 + 30 条演示供应商数据
-- Author: DMS Fix Bot @ 2026-07-26
-- Target: 修复 Bug 1 (supplier_level 字典空) / Bug 2 (保存后无 level 列) / Bug 3 (无测试数据)

-- 1. 补齐 suppliers 表 level 列 (幂等)
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS level VARCHAR(32);
COMMENT ON COLUMN suppliers.level IS '供应商等级, 对应 dict_items.code (type=supplier_level)';
CREATE INDEX IF NOT EXISTS idx_suppliers_tenant_level ON suppliers(tenant_id, level);

-- 2. dict_types.supplier_level (每个租户各一份)
INSERT INTO dict_types(tenant_id, code, name, description)
SELECT t.id, 'supplier_level', '供应商等级', '供应商分级管理: L1/L2/L3/L4/STRATEGIC'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM dict_types dt WHERE dt.tenant_id = t.id AND dt.code = 'supplier_level'
);

-- 3. dict_items 五档
WITH sl AS (
    SELECT id, tenant_id FROM dict_types WHERE code = 'supplier_level'
)
INSERT INTO dict_items(type_id, code, name, seq, status)
SELECT sl.id, v.code, v.name, v.seq, 'active'
FROM sl
CROSS JOIN (
    VALUES
        ('L1','一级供应商', 1),
        ('L2','二级供应商', 2),
        ('L3','三级供应商', 3),
        ('L4','四级供应商', 4),
        ('STRATEGIC','战略供应商', 5)
) AS v(code, name, seq)
WHERE NOT EXISTS (
    SELECT 1 FROM dict_items di WHERE di.type_id = sl.id AND di.code = v.code
);

-- 4. 30 条测试供应商 (default 租户)
WITH t AS (
    SELECT id AS tid FROM tenants WHERE code = 'default'
)
INSERT INTO suppliers(tenant_id, code, name, contact_person, contact_phone, address, bank_account, tax_no, remark, status, level)
SELECT t.tid,
       'SUP-' || LPAD(n::text, 4, '0'),
       (ARRAY['北京','上海','广州','深圳','杭州','成都','武汉','西安','南京','天津'])[((n-1)%10)+1]
           || (ARRAY['同仁堂','华润','国药','九州通','老百姓','益丰','大参林','太安堂','海王','康美'])[((n-1)%10)+1]
           || '医药供应链有限公司 #' || n,
       (ARRAY['张伟','李娜','王芳','刘洋','陈静','杨磊','赵敏','黄涛','周敏','吴刚'])[((n-1)%10)+1],
       '138' || LPAD((10000000+n*137)::text, 8, '0'),
       (ARRAY['北京市朝阳区建国路','上海市浦东新区世纪大道','广州市天河区珠江新城','深圳市南山区科技园','杭州市西湖区文三路'])[((n-1)%5)+1]
           || ' ' || (100+n)::text || '号',
       '622848' || LPAD((1234567800+n*13)::text, 10, '0'),
       '91' || LPAD((n*7)::text, 15, '0') || 'X',
       CASE WHEN n % 5 = 0 THEN '战略合作伙伴' ELSE '常规合作' END,
       CASE WHEN n % 7 = 0 THEN 'inactive' ELSE 'active' END,
       CASE
           WHEN n % 10 = 0 THEN 'STRATEGIC'
           WHEN n % 4 = 0  THEN 'L1'
           WHEN n % 3 = 0  THEN 'L2'
           WHEN n % 2 = 0  THEN 'L3'
           ELSE 'L4'
       END
FROM t
CROSS JOIN generate_series(1, 30) AS n
WHERE NOT EXISTS (
    SELECT 1 FROM suppliers s WHERE s.tenant_id = t.tid AND s.code = 'SUP-' || LPAD(n::text, 4, '0')
);
