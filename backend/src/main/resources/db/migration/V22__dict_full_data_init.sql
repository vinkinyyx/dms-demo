-- V22: 补全业务字典数据
-- dict_types 字段: id, tenant_id, code, name, description, created_at, updated_at
-- dict_items 字段: id, type_id, code, name, seq, status, attrs, created_at, updated_at

-- 1) 支付方式
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES (NULL, 'payment_method', '支付方式', '订单支付方式')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'CASH', '现金', 10, 'active' FROM dict_types
WHERE code = 'payment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'TRANSFER', '银行转账', 20, 'active' FROM dict_types
WHERE code = 'payment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'CHECK', '支票', 30, 'active' FROM dict_types
WHERE code = 'payment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'ACCEPTANCE', '承兑汇票', 40, 'active' FROM dict_types
WHERE code = 'payment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'LC', '信用证', 50, 'active' FROM dict_types
WHERE code = 'payment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 2) 发票类型
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES (NULL, 'invoice_type', '发票类型', '发票类型枚举')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'SPECIAL', '增值税专用发票', 10, 'active' FROM dict_types
WHERE code = 'invoice_type' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'NORMAL', '增值税普通发票', 20, 'active' FROM dict_types
WHERE code = 'invoice_type' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'ELECTRONIC', '电子发票', 30, 'active' FROM dict_types
WHERE code = 'invoice_type' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'RECEIPT', '收据', 40, 'active' FROM dict_types
WHERE code = 'invoice_type' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 3) 客户等级
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES (NULL, 'customer_level', '客户等级', '经销商/客户等级')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'A', 'A级', 10, 'active' FROM dict_types
WHERE code = 'customer_level' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'B', 'B级', 20, 'active' FROM dict_types
WHERE code = 'customer_level' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'C', 'C级', 30, 'active' FROM dict_types
WHERE code = 'customer_level' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'D', 'D级', 40, 'active' FROM dict_types
WHERE code = 'customer_level' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 4) 发货方式
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES (NULL, 'shipment_method', '发货方式', '物流配送方式')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'EXPRESS', '快递', 10, 'active' FROM dict_types
WHERE code = 'shipment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'LOGISTICS', '物流', 20, 'active' FROM dict_types
WHERE code = 'shipment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'SELF_PICKUP', '自提', 30, 'active' FROM dict_types
WHERE code = 'shipment_method' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- 5) 产品类型
INSERT INTO dict_types (tenant_id, code, name, description)
VALUES (NULL, 'product_type', '产品类型', '产品分类类型')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'finished', '成品', 10, 'active' FROM dict_types
WHERE code = 'product_type' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'semi_finished', '半成品', 20, 'active' FROM dict_types
WHERE code = 'product_type' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq, status)
SELECT id, 'raw_material', '原材料', 30, 'active' FROM dict_types
WHERE code = 'product_type' AND tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;
