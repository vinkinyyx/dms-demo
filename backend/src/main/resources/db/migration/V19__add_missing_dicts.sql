-- =====================================================================
-- V19: 补充标准业务字典数据
-- 新增：支付方式、发票类型、客户等级、发货方式、产品类型
-- =====================================================================

-- 1) 新增字典类型
INSERT INTO dict_types (tenant_id, code, name, description) VALUES
    (NULL, 'payment_method', '支付方式', '订单支付方式枚举'),
    (NULL, 'invoice_type',   '发票类型', '发票类型枚举'),
    (NULL, 'customer_level', '客户等级', '经销商/客户等级'),
    (NULL, 'shipment_method','发货方式', '物流配送方式'),
    (NULL, 'product_type',   '产品类型', '产品分类类型')
ON CONFLICT (tenant_id, code) DO NOTHING;

-- 2) 支付方式
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('CASH',      '现金',       10),
    ('TRANSFER',  '银行转账',   20),
    ('ACCEPTANCE','承兑汇票',   30),
    ('CREDIT',    '赊账/信用',  40),
    ('ONLINE',    '在线支付',   50)
) AS v(code, name, seq)
WHERE t.code = 'payment_method' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 3) 发票类型
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('VAT_SPECIAL',   '增值税专用发票', 10),
    ('VAT_NORMAL',    '增值税普通发票', 20),
    ('ELECTRONIC',    '电子发票',       30),
    ('ROLL',          '卷式发票',       40),
    ('NO_INVOICE',    '不开发票',       50)
) AS v(code, name, seq)
WHERE t.code = 'invoice_type' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 4) 客户等级
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('STRATEGIC', '战略客户', 10),
    ('KEY',       '重点客户', 20),
    ('NORMAL',    '普通客户', 30),
    ('NEW',       '新客户',   40)
) AS v(code, name, seq)
WHERE t.code = 'customer_level' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 5) 发货方式
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('SELF_PICKUP', '自提',         10),
    ('EXPRESS',     '快递',         20),
    ('LOGISTICS',   '物流/货运',    30),
    ('DELIVERY',    '送货上门',     40),
    ('COLD_CHAIN',  '冷链配送',     50)
) AS v(code, name, seq)
WHERE t.code = 'shipment_method' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 6) 产品类型
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('IMPLANT',    '植入物',   10),
    ('INSTRUMENT', '手术器械', 20),
    ('CONSUMABLE', '耗材',     30),
    ('EQUIPMENT',  '设备',     40),
    ('ACCESSORY',  '配件',     50)
) AS v(code, name, seq)
WHERE t.code = 'product_type' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;
