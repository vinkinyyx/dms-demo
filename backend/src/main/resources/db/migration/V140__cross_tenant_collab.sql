-- ============================================================
-- V100: 厂家关闭进销存（菜单开关持久化）+ 厂家↔经销商跨租户协同基础设施
-- 覆盖：
--   R0: default 厂家租户关闭进销存（inventoryEnabled=false），重建库不复发
--   1. suppliers 增加 manufacturer_tenant_id：标识「平台厂家」供应商（经销商采购单选它才触发跨租户）
--   2. purchase_orders 增加 vendor_order_code：回写厂家销售订单号；
--      orders 增加 customer_po_code：记录来源经销商采购订单号
--   3. 跨租户单据关联台账 cross_tenant_doc_links（溯源 + 幂等）
--   4. 演示：为现有经销商租户自动创建/链接「平台厂家」供应商
-- 说明：本期不做取消/红冲的跨租户联动；包装换算率固定 1:1。
-- ============================================================

-- ---------- R0: 厂家租户关闭进销存模块（经销商租户不受影响） ----------
UPDATE tenants
SET modules_enabled = COALESCE(modules_enabled, '{}'::jsonb)
                      || jsonb_build_object('inventoryEnabled', false, 'purchaseEnabled', false),
    updated_at = now()
WHERE tenant_type = 'MANUFACTURER';

-- ---------- 1. 供应商平台厂家链接 ----------
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS manufacturer_tenant_id UUID REFERENCES tenants(id);
CREATE INDEX IF NOT EXISTS idx_suppliers_mfr ON suppliers(manufacturer_tenant_id) WHERE manufacturer_tenant_id IS NOT NULL;

-- ---------- 2. 单据跨租户来源/回写单号 ----------
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS vendor_order_code VARCHAR(64);   -- 回写：厂家销售订单号
ALTER TABLE orders          ADD COLUMN IF NOT EXISTS customer_po_code  VARCHAR(64);   -- 来源：经销商采购订单号
CREATE INDEX IF NOT EXISTS idx_po_vendor_code ON purchase_orders(vendor_order_code) WHERE vendor_order_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_cust_po  ON orders(customer_po_code) WHERE customer_po_code IS NOT NULL;

-- ---------- 3. 跨租户单据关联台账 ----------
CREATE TABLE IF NOT EXISTS cross_tenant_doc_links (
    id                       BIGSERIAL PRIMARY KEY,
    manufacturer_tenant_id   UUID NOT NULL,
    dealer_tenant_id         UUID NOT NULL,
    link_type                VARCHAR(24) NOT NULL,          -- PO_TO_SALES_ORDER / SALES_OUT_TO_RECEIPT
    po_id                    BIGINT,                        -- 经销商采购订单 id
    po_code                  VARCHAR(64),
    sales_order_id           BIGINT,                        -- 厂家销售订单 id
    sales_order_code         VARCHAR(64),
    sales_out_id             BIGINT,                        -- 厂家销售出库 id
    sales_out_code           VARCHAR(64),
    receipt_id               BIGINT,                        -- 经销商收货入库 id
    receipt_code             VARCHAR(64),
    line_refs                JSONB DEFAULT '[]'::jsonb,     -- 行级溯源：[{poLineId,poSeq,salesOrderLineId,shipExecLineId,productCode,qty,batchNo,serialNo}]
    status                   VARCHAR(16) NOT NULL DEFAULT 'linked',
    created_at               TIMESTAMPTZ DEFAULT now(),
    updated_at               TIMESTAMPTZ DEFAULT now()
);
-- 幂等：同一张厂家出库单只回传一次
CREATE UNIQUE INDEX IF NOT EXISTS ux_ctdl_sales_out ON cross_tenant_doc_links(sales_out_id) WHERE sales_out_id IS NOT NULL;
-- 一张经销商采购单只转一张厂家销售单
CREATE UNIQUE INDEX IF NOT EXISTS ux_ctdl_po ON cross_tenant_doc_links(po_id) WHERE po_id IS NOT NULL AND link_type = 'PO_TO_SALES_ORDER';
CREATE INDEX IF NOT EXISTS idx_ctdl_dealer ON cross_tenant_doc_links(dealer_tenant_id, link_type);

-- ---------- 4. 演示：为经销商租户补齐「平台厂家」供应商 ----------
INSERT INTO suppliers (tenant_id, code, name, contact_person, remark, manufacturer_tenant_id, status, created_at, updated_at)
SELECT dt.id, 'PLATFORM_MFR', '平台厂家（系统供应商）', NULL,
       '跨租户协同：向该供应商采购将自动转换为厂家销售订单',
       dt.owner_manufacturer_id, 'active', now(), now()
FROM tenants dt
WHERE dt.tenant_type = 'DEALER'
  AND dt.owner_manufacturer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM suppliers s
      WHERE s.tenant_id = dt.id AND s.manufacturer_tenant_id = dt.owner_manufacturer_id
        AND s.deleted_at IS NULL
  );
