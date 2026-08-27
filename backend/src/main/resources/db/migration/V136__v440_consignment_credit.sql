-- v4.4.0: 寄售库存（补货/开票）、经销商资信与账期、开票结算终端、样品原因
-- 幂等：列/表存在则跳过（IF NOT EXISTS）。

-- ============ 1. 经销商主数据：寄售开关 + 寄售额度 + 资信账期 ============
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS consignment_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS consignment_limit numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS credit_limit numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS payment_days integer NOT NULL DEFAULT 0;
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS settlement_method varchar(32);
ALTER TABLE dealers ADD COLUMN IF NOT EXISTS credit_grade varchar(16);

-- ============ 2. 寄售库存台账（经销商 + 产品SKU + 批号 + 序列号） ============
CREATE TABLE IF NOT EXISTS consignment_stock (
    id bigserial PRIMARY KEY,
    tenant_id uuid NOT NULL,
    dealer_id bigint NOT NULL,
    product_id bigint NOT NULL,
    product_code varchar(64),
    product_name varchar(255),
    product_spec varchar(255),
    unit varchar(32),
    batch_no varchar(128),
    serial_no varchar(255),
    warehouse_id bigint,
    on_hand_qty integer NOT NULL DEFAULT 0,   -- 实际在经销商处的寄售数量
    locked_qty integer NOT NULL DEFAULT 0,   -- 开票订单提交后预占、审批后转实扣
    std_unit_price numeric(18,4) NOT NULL DEFAULT 0,  -- 标准价快照，用于寄售金额汇总
    source_sales_out_id bigint,              -- 来源补货出库单
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version integer NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_consignment_stock_dealer ON consignment_stock(tenant_id, dealer_id);
CREATE INDEX IF NOT EXISTS idx_consignment_stock_prod ON consignment_stock(tenant_id, dealer_id, product_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_consignment_stock_dim
    ON consignment_stock(tenant_id, dealer_id, product_id, COALESCE(batch_no,''), COALESCE(serial_no,''));

-- ============ 3. 寄售库存流水（补货写入 / 开票预占 / 实扣 / 释放，可追溯） ============
CREATE TABLE IF NOT EXISTS consignment_stock_movements (
    id bigserial PRIMARY KEY,
    tenant_id uuid NOT NULL,
    dealer_id bigint NOT NULL,
    product_id bigint,
    batch_no varchar(128),
    serial_no varchar(255),
    change_type varchar(24) NOT NULL,   -- REPLENISH_IN(补货入库) | INVOICE_LOCK(开票预占) | INVOICE_DEDUCT(开票实扣) | INVOICE_RELEASE(开票释放)
    qty_change integer NOT NULL,        -- 带符号：入库为正，扣减/预占为负，释放为正
    ref_type varchar(24),              -- SALES_OUT | INVOICE_ORDER
    ref_id bigint,
    ref_code varchar(64),
    remark varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint
);
CREATE INDEX IF NOT EXISTS idx_consignment_mv_dealer ON consignment_stock_movements(tenant_id, dealer_id);
CREATE INDEX IF NOT EXISTS idx_consignment_mv_ref ON consignment_stock_movements(tenant_id, ref_type, ref_id);

-- ============ 4. 经销商资信与账期快照表（独立模块，留扩展） ============
CREATE TABLE IF NOT EXISTS dealer_credit_profiles (
    id bigserial PRIMARY KEY,
    tenant_id uuid NOT NULL,
    dealer_id bigint NOT NULL,
    credit_limit numeric(18,2) NOT NULL DEFAULT 0,
    credit_used numeric(18,2) NOT NULL DEFAULT 0,
    payment_days integer NOT NULL DEFAULT 0,
    settlement_method varchar(32),
    credit_grade varchar(16),
    consignment_limit numeric(18,2) NOT NULL DEFAULT 0,
    consignment_used numeric(18,2) NOT NULL DEFAULT 0,
    over_limit_action varchar(16) NOT NULL DEFAULT 'APPROVAL',  -- 超额/超期触发审批（不硬拦截）
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    remark varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version integer NOT NULL DEFAULT 0,
    deleted_at timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dealer_credit_profile ON dealer_credit_profiles(tenant_id, dealer_id) WHERE deleted_at IS NULL;

-- ============ 5. 订单：结算终端（开票订单选医院）+ 样品申请原因 ============
ALTER TABLE orders ADD COLUMN IF NOT EXISTS terminal_hospital_id bigint;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS sample_reason varchar(500);

-- ============ 6. RBAC：资信账期模块资源（按既有资源播种模式） ============
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at, version)
SELECT DISTINCT ten.tenant_id, t.code, t.name, 'menu',
       ARRAY['view','search','create','edit','manage']::varchar[], t.path, 'ENABLED', now(), now(), 0
FROM (SELECT '11111111-1111-1111-1111-111111111111'::uuid AS tenant_id) ten
CROSS JOIN (
    SELECT 'dealer_credit' AS code, '经销商资信与账期' AS name, '/dealer-credit' AS path
) t
WHERE NOT EXISTS (
    SELECT 1 FROM resources r
    WHERE r.tenant_id = ten.tenant_id AND r.code = t.code AND r.deleted_at IS NULL
);
