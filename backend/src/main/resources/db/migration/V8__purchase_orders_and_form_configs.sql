-- ============================================================
-- V8: 采购/销售拆分 + 低代码字段配置 + 库存联动增强
-- 覆盖：
--   1. 采购订单 purchase_orders + purchase_order_lines（独立表）
--   2. 低代码字段配置 form_configs（每张单据/主数据的字段元数据）
--   3. 扩展字段：所有主要单据表增加 extra JSONB 列（若不存在）
--   4. 字典项接口对应表已有（dict_types/dict_items）
-- ============================================================

-- ---------------------------------------------------------------
-- 1. 采购订单主表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_orders (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    code                 VARCHAR(64) UNIQUE NOT NULL,
    order_type           VARCHAR(16) DEFAULT 'NORMAL',         -- NORMAL / URGENT / RETURN
    supplier_id          BIGINT,                                -- 供应商（可为空，V1 复用 dealers 表暂存）
    supplier_name        VARCHAR(200),
    warehouse_id         BIGINT,                                -- 目标入库仓库
    amount_incl_tax      NUMERIC(18,2) DEFAULT 0,
    discount_amount      NUMERIC(18,2) DEFAULT 0,
    final_amount         NUMERIC(18,2) DEFAULT 0,
    tax_amount           NUMERIC(18,2) DEFAULT 0,
    expected_date        DATE,
    status               VARCHAR(16) DEFAULT 'DRAFT',
                         -- 状态机: DRAFT -> SUBMITTED -> APPROVED -> RECEIVING -> COMPLETED
                         --                   \-> REJECTED / CANCELLED
    remark               TEXT,
    extra                JSONB DEFAULT '{}'::jsonb,             -- 低代码自定义字段
    submitted_at         TIMESTAMPTZ,
    approved_at          TIMESTAMPTZ,
    approved_by          BIGINT,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ DEFAULT now(),
    updated_at           TIMESTAMPTZ DEFAULT now(),
    created_by           BIGINT,
    updated_by           BIGINT,
    version              INT DEFAULT 0,
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_po_tenant  ON purchase_orders(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_po_supp    ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_po_created ON purchase_orders(tenant_id, created_at DESC);

-- ---------------------------------------------------------------
-- 2. 采购订单明细
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id              BIGSERIAL PRIMARY KEY,
    po_id           BIGINT REFERENCES purchase_orders(id) ON DELETE CASCADE,
    seq             INT DEFAULT 1,
    product_id      BIGINT,
    qty             NUMERIC(14,4) NOT NULL,
    received_qty    NUMERIC(14,4) DEFAULT 0,                    -- 已收货数量
    unit_price      NUMERIC(18,4),
    tax_rate        NUMERIC(5,4) DEFAULT 0.13,
    subtotal        NUMERIC(18,2),
    remark          TEXT,
    extra           JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_pol_po ON purchase_order_lines(po_id);

-- ---------------------------------------------------------------
-- 3. 低代码字段配置表（管理员可配置字段属性）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_configs (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    form_key     VARCHAR(64) NOT NULL,                          -- 表单标识: order / purchase_order / product / dealer ...
    field_key    VARCHAR(64) NOT NULL,                          -- 字段名（可为原生字段或 extra.xxx）
    field_label  VARCHAR(100),                                  -- 中文标签
    field_type   VARCHAR(16) DEFAULT 'text',                    -- text / number / date / select / textarea / picker / boolean
    is_native    BOOLEAN DEFAULT true,                          -- 是否原生表字段（false = extra JSONB 自定义字段）
    required     BOOLEAN DEFAULT false,
    show_in_list BOOLEAN DEFAULT true,
    show_in_form BOOLEAN DEFAULT true,
    show_in_detail BOOLEAN DEFAULT true,
    default_value TEXT,
    options_json TEXT,                                          -- select 类型的选项 JSON
    picker_resource VARCHAR(64),                                -- picker 类型的资源
    placeholder  VARCHAR(200),
    field_group  VARCHAR(64),                                   -- 分组标题
    sort_order   INT DEFAULT 100,
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    UNIQUE (tenant_id, form_key, field_key)
);
CREATE INDEX IF NOT EXISTS idx_fc_form ON form_configs(tenant_id, form_key, sort_order);

-- ---------------------------------------------------------------
-- 4. 为主要业务表增加 extra JSONB 列（若不存在）用于低代码自定义字段
-- ---------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='orders' AND column_name='extra') THEN
        ALTER TABLE orders ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='order_lines' AND column_name='extra') THEN
        ALTER TABLE order_lines ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='products' AND column_name='extra') THEN
        ALTER TABLE products ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='dealers' AND column_name='extra') THEN
        ALTER TABLE dealers ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='warehouses' AND column_name='extra') THEN
        ALTER TABLE warehouses ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='hospitals' AND column_name='extra') THEN
        ALTER TABLE hospitals ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
    END IF;
END $$;

-- ---------------------------------------------------------------
-- 5. 增加字典项：库存状态、仓库类型、产品线（供 admin 演示可维护）
-- ---------------------------------------------------------------
INSERT INTO dict_types (tenant_id, code, name) VALUES
    ((SELECT id FROM tenants WHERE code='default'), 'INVENTORY_STATUS', '库存状态'),
    ((SELECT id FROM tenants WHERE code='default'), 'WAREHOUSE_TYPE',   '仓库类型'),
    ((SELECT id FROM tenants WHERE code='default'), 'PRODUCT_LINE',     '产品线'),
    ((SELECT id FROM tenants WHERE code='default'), 'PO_STATUS',        '采购单状态'),
    ((SELECT id FROM tenants WHERE code='default'), 'SO_STATUS',        '销售单状态')
    ON CONFLICT DO NOTHING;

INSERT INTO dict_items (type_id, code, name, seq) VALUES
    ((SELECT id FROM dict_types WHERE code='INVENTORY_STATUS' LIMIT 1), 'AVAILABLE', '可用',   10),
    ((SELECT id FROM dict_types WHERE code='INVENTORY_STATUS' LIMIT 1), 'RESERVED',  '预占',   20),
    ((SELECT id FROM dict_types WHERE code='INVENTORY_STATUS' LIMIT 1), 'QUARANTINE','质检中', 30),
    ((SELECT id FROM dict_types WHERE code='INVENTORY_STATUS' LIMIT 1), 'DEFECTIVE', '不良品', 40),
    ((SELECT id FROM dict_types WHERE code='WAREHOUSE_TYPE' LIMIT 1),   'main',      '主仓库', 10),
    ((SELECT id FROM dict_types WHERE code='WAREHOUSE_TYPE' LIMIT 1),   'sub',       '分仓库', 20),
    ((SELECT id FROM dict_types WHERE code='WAREHOUSE_TYPE' LIMIT 1),   'hospital',  '医院寄售仓', 30),
    ((SELECT id FROM dict_types WHERE code='WAREHOUSE_TYPE' LIMIT 1),   'transit',   '在途仓',   40),
    ((SELECT id FROM dict_types WHERE code='PRODUCT_LINE' LIMIT 1),     'IMPLANT',   '骨科植入物', 10),
    ((SELECT id FROM dict_types WHERE code='PRODUCT_LINE' LIMIT 1),     'REAGENT',   '体外诊断试剂', 20),
    ((SELECT id FROM dict_types WHERE code='PRODUCT_LINE' LIMIT 1),     'CONSUMABLE','医用耗材', 30),
    ((SELECT id FROM dict_types WHERE code='PRODUCT_LINE' LIMIT 1),     'EQUIPMENT', '医疗设备', 40),
    ((SELECT id FROM dict_types WHERE code='PO_STATUS' LIMIT 1),        'DRAFT',     '草稿',     10),
    ((SELECT id FROM dict_types WHERE code='PO_STATUS' LIMIT 1),        'SUBMITTED', '已提交',   20),
    ((SELECT id FROM dict_types WHERE code='PO_STATUS' LIMIT 1),        'APPROVED',  '已审批',   30),
    ((SELECT id FROM dict_types WHERE code='PO_STATUS' LIMIT 1),        'RECEIVING', '收货中',   40),
    ((SELECT id FROM dict_types WHERE code='PO_STATUS' LIMIT 1),        'COMPLETED', '已完成',   50),
    ((SELECT id FROM dict_types WHERE code='PO_STATUS' LIMIT 1),        'REJECTED',  '已驳回',   60),
    ((SELECT id FROM dict_types WHERE code='PO_STATUS' LIMIT 1),        'CANCELLED', '已取消',   70)
    ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------
-- 6. 预置一批 form_configs（订单/采购单/产品/经销商，管理员可后续修改）
-- ---------------------------------------------------------------
DO $$
DECLARE tid UUID := (SELECT id FROM tenants WHERE code='default');
BEGIN

  -- 销售订单
  INSERT INTO form_configs (tenant_id, form_key, field_key, field_label, field_type, is_native, required, show_in_form, show_in_list, field_group, sort_order) VALUES
    (tid, 'order','orderType','订单类型','select',true,true,true,true,'订单信息',10),
    (tid, 'order','dealerId','经销商','picker',true,true,true,true,'订单信息',20),
    (tid, 'order','expectedDate','期望到货日期','date',true,false,true,true,'订单信息',30),
    (tid, 'order','remark','订单备注','textarea',true,false,true,false,'其它',40),
    (tid, 'order','amountInclTax','含税金额','number',true,false,false,true,'',50),
    (tid, 'order','finalAmount','实付金额','number',true,false,false,true,'',60),
    (tid, 'order','status','状态','text',true,false,false,true,'',70)
    ON CONFLICT DO NOTHING;

  -- 采购订单
  INSERT INTO form_configs (tenant_id, form_key, field_key, field_label, field_type, is_native, required, show_in_form, show_in_list, field_group, sort_order) VALUES
    (tid, 'purchase_order','orderType','采购类型','select',true,true,true,true,'采购信息',10),
    (tid, 'purchase_order','supplierId','供应商','picker',true,true,true,true,'采购信息',20),
    (tid, 'purchase_order','warehouseId','入库仓库','picker',true,true,true,true,'采购信息',30),
    (tid, 'purchase_order','expectedDate','期望到货日期','date',true,false,true,true,'采购信息',40),
    (tid, 'purchase_order','remark','采购备注','textarea',true,false,true,false,'其它',50),
    (tid, 'purchase_order','amountInclTax','含税金额','number',true,false,false,true,'',60),
    (tid, 'purchase_order','status','状态','text',true,false,false,true,'',70)
    ON CONFLICT DO NOTHING;

  -- 产品
  INSERT INTO form_configs (tenant_id, form_key, field_key, field_label, field_type, is_native, required, show_in_form, show_in_list, field_group, sort_order) VALUES
    (tid, 'product','code','产品编码','text',true,true,true,true,'基本信息',10),
    (tid, 'product','nameCn','中文名称','text',true,true,true,true,'基本信息',20),
    (tid, 'product','spec','规格型号','text',true,false,true,true,'基本信息',30),
    (tid, 'product','unit','单位','text',true,false,true,true,'基本信息',40),
    (tid, 'product','currentPrice','参考单价','number',true,false,true,true,'价格',50),
    (tid, 'product','taxRate','税率','number',true,false,true,false,'价格',60),
    (tid, 'product','status','状态','select',true,false,true,true,'',70)
    ON CONFLICT DO NOTHING;

END $$;
