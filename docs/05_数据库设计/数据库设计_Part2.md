# 数据库设计（PostgreSQL 14+）Part 2

> 承接 `数据库设计_Part1.md`

> **V1 决策变更（D-24~D-41）提示**：
> - `promotions.promo_type` V1 仅启用 `MOQ` / `FULL_REDUCTION`，`GIFT` / `BUNDLE` 保留枚举位供 V2 扩展（API 层拒绝创建）。
> - `notifications.channel` V1 仅使用 `INAPP` / `WECHAT_BOT` / `FEISHU_BOT`；`EMAIL` / `SMS` 已从 V1 移除。
> - `sso_service_id` 等预留字段保留但 V1 不使用；用户登录相关变更详见 Part1。


## G. 收货 / 库存 / 销售（15 张）

```sql
-- 收货
CREATE TABLE receipts (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE NOT NULL,
  receipt_type VARCHAR(16), ref_doc_type VARCHAR(16), ref_doc_id BIGINT,
  dealer_id BIGINT REFERENCES dealers(id),
  warehouse_id BIGINT REFERENCES warehouses(id),
  status VARCHAR(16) DEFAULT 'PENDING',
  received_at TIMESTAMPTZ, received_by BIGINT, remark TEXT,
  created_at TIMESTAMPTZ DEFAULT now(), version INT DEFAULT 0
);
CREATE TABLE receipt_lines (
  id BIGSERIAL PRIMARY KEY,
  receipt_id BIGINT REFERENCES receipts(id) ON DELETE CASCADE,
  product_id BIGINT, batch_no VARCHAR(64), serial_no VARCHAR(64),
  prod_date DATE, exp_date DATE,
  expected_qty NUMERIC(14,4), received_qty NUMERIC(14,4)
);
CREATE UNIQUE INDEX ux_rcpt_serial ON receipt_lines(receipt_id, serial_no) WHERE serial_no IS NOT NULL;

-- 库存
CREATE TABLE inventory (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT REFERENCES dealers(id),
  warehouse_id BIGINT REFERENCES warehouses(id),
  product_id BIGINT REFERENCES products(id),
  batch_no VARCHAR(64), serial_no VARCHAR(64),
  prod_date DATE, exp_date DATE,
  qty NUMERIC(14,4) NOT NULL DEFAULT 0,
  in_source VARCHAR(32),
  UNIQUE (tenant_id, warehouse_id, product_id, batch_no, serial_no)
);
CREATE INDEX idx_inv_lookup ON inventory(tenant_id, dealer_id, product_id, batch_no);
CREATE INDEX idx_inv_expire ON inventory(exp_date);

-- 库存流水（分区表）
CREATE TABLE inventory_transactions (
  id BIGSERIAL, tenant_id UUID NOT NULL,
  dealer_id BIGINT, warehouse_id BIGINT, product_id BIGINT,
  batch_no VARCHAR(64), serial_no VARCHAR(64),
  qty_change NUMERIC(14,4) NOT NULL,
  txn_type VARCHAR(32) NOT NULL,
  ref_doc_type VARCHAR(32), ref_doc_id BIGINT,
  at_time TIMESTAMPTZ DEFAULT now(),
  operator_id BIGINT,
  PRIMARY KEY (id, at_time)
) PARTITION BY RANGE (at_time);

-- 移库
CREATE TABLE stock_moves (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE, dealer_id BIGINT,
  src_warehouse_id BIGINT, dst_warehouse_id BIGINT,
  status VARCHAR(16) DEFAULT 'COMPLETED',
  reason TEXT, operator_id BIGINT,
  at_time TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE stock_move_lines (
  id BIGSERIAL PRIMARY KEY,
  move_id BIGINT REFERENCES stock_moves(id) ON DELETE CASCADE,
  product_id BIGINT, batch_no VARCHAR(64), serial_no VARCHAR(64), qty NUMERIC(14,4)
);

-- 借货
CREATE TABLE loans (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE,
  lender_dealer_id BIGINT, borrower_dealer_id BIGINT,
  status VARCHAR(16) DEFAULT 'PENDING',
  ref_loan_id BIGINT, reason TEXT,
  created_at TIMESTAMPTZ DEFAULT now(), completed_at TIMESTAMPTZ
);
CREATE TABLE loan_lines (
  id BIGSERIAL PRIMARY KEY,
  loan_id BIGINT REFERENCES loans(id) ON DELETE CASCADE,
  product_id BIGINT, batch_no VARCHAR(64), serial_no VARCHAR(64), qty NUMERIC(14,4)
);

-- 库存调整
CREATE TABLE inventory_adjustments (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE, dealer_id BIGINT, warehouse_id BIGINT,
  adj_category VARCHAR(16), adj_type VARCHAR(32),
  status VARCHAR(16) DEFAULT 'DRAFT',
  reason TEXT, operator_id BIGINT, approver_id BIGINT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE adjustment_lines (
  id BIGSERIAL PRIMARY KEY,
  adjustment_id BIGINT REFERENCES inventory_adjustments(id) ON DELETE CASCADE,
  product_id BIGINT, batch_no VARCHAR(64), serial_no VARCHAR(64),
  qty NUMERIC(14,4), reason TEXT
);

-- 盘点
CREATE TABLE stocktakes (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT, period_yyyymm CHAR(6) NOT NULL,
  uploaded_at TIMESTAMPTZ, uploaded_by BIGINT,
  is_late BOOLEAN DEFAULT false, diff_summary JSONB,
  UNIQUE (tenant_id, dealer_id, period_yyyymm)
);
CREATE TABLE stocktake_lines (
  id BIGSERIAL PRIMARY KEY,
  stocktake_id BIGINT REFERENCES stocktakes(id) ON DELETE CASCADE,
  product_id BIGINT, batch_no VARCHAR(64), serial_no VARCHAR(64),
  book_qty NUMERIC(14,4), actual_qty NUMERIC(14,4), diff_qty NUMERIC(14,4)
);

-- 销售出库（报台）
CREATE TABLE sales_outs (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE,
  dealer_id BIGINT, terminal_id BIGINT REFERENCES hospitals(id),
  business_type VARCHAR(16),
  sales_date DATE, surgery_info JSONB,
  is_red BOOLEAN DEFAULT false, ref_sales_out_id BIGINT,
  status VARCHAR(16) DEFAULT 'SUBMITTED',
  amount_incl_tax NUMERIC(18,2),
  created_by BIGINT, created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE sales_out_lines (
  id BIGSERIAL PRIMARY KEY,
  sales_out_id BIGINT REFERENCES sales_outs(id) ON DELETE CASCADE,
  warehouse_id BIGINT, product_id BIGINT,
  batch_no VARCHAR(64), serial_no VARCHAR(64),
  qty NUMERIC(14,4)
);
CREATE UNIQUE INDEX ux_sales_serial ON sales_out_lines(serial_no) WHERE serial_no IS NOT NULL;

CREATE TABLE sales_out_facts (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT, product_id BIGINT, terminal_id BIGINT, region_id BIGINT,
  sales_date DATE, qty NUMERIC(14,4), amount NUMERIC(18,2)
);
CREATE INDEX idx_sof_dealer_date ON sales_out_facts(dealer_id, sales_date);
```

## H. 分销 & 退换货

```sql
CREATE TABLE distribution_shipments (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE,
  from_dealer_id BIGINT, to_dealer_id BIGINT,
  ref_order_id BIGINT,
  status VARCHAR(16) DEFAULT 'PENDING',
  express_no VARCHAR(64),
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE distribution_lines (
  id BIGSERIAL PRIMARY KEY,
  shipment_id BIGINT REFERENCES distribution_shipments(id) ON DELETE CASCADE,
  product_id BIGINT, batch_no VARCHAR(64), serial_no VARCHAR(64), qty NUMERIC(14,4)
);

CREATE TABLE rma_authorizations (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE,
  dealer_id BIGINT, product_line_id BIGINT,
  quota_amount NUMERIC(18,2), quota_used NUMERIC(18,2) DEFAULT 0,
  valid_from DATE, valid_to DATE,
  status VARCHAR(16) DEFAULT 'active',
  reason TEXT, created_by BIGINT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE rma_orders (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE,
  ref_rma_auth_id BIGINT REFERENCES rma_authorizations(id),
  dealer_id BIGINT, rma_type VARCHAR(16),
  amount NUMERIC(18,2),
  status VARCHAR(16) DEFAULT 'DRAFT',
  lines JSONB, reason TEXT, attachments JSONB,
  submitted_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

## I. 发票

```sql
CREATE TABLE purchase_invoices (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  ref_order_id BIGINT REFERENCES orders(id),
  invoice_no VARCHAR(64) NOT NULL,
  invoice_date DATE,
  amount NUMERIC(18,2), tax_amount NUMERIC(18,2), tax_rate NUMERIC(5,4),
  image_url TEXT,
  uploaded_by BIGINT, uploaded_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (tenant_id, invoice_no)
);
CREATE TABLE sales_invoices (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  ref_sales_out_id BIGINT REFERENCES sales_outs(id),
  invoice_no VARCHAR(64) NOT NULL,
  invoice_date DATE, amount NUMERIC(18,2), tax_amount NUMERIC(18,2),
  image_url TEXT, uploaded_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (tenant_id, invoice_no)
);
```

## J. 促销

```sql
CREATE TABLE promotions (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE,
  name VARCHAR(200) NOT NULL,
  promo_type VARCHAR(16) NOT NULL,          -- V1 仅支持 MOQ / FULL_REDUCTION；GIFT / BUNDLE 保留枚举位供 V2 扩展
  priority INT DEFAULT 50,
  valid_from TIMESTAMPTZ, valid_to TIMESTAMPTZ,
  dealer_scope JSONB, product_scope JSONB,
  exclusive BOOLEAN DEFAULT false,
  status VARCHAR(16) DEFAULT 'draft',
  description TEXT,
  created_by BIGINT, approved_by BIGINT, approved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now(),
  version INT DEFAULT 0
);
CREATE INDEX idx_promo_active ON promotions(tenant_id, status, valid_from, valid_to);

ALTER TABLE promotions ADD CONSTRAINT ck_promo_type_v1
  CHECK (promo_type IN ('MOQ','FULL_REDUCTION','GIFT','BUNDLE'));
-- V1 上线时 API 层仅允许创建 MOQ / FULL_REDUCTION，GIFT/BUNDLE 拒绝创建但保留数据

CREATE TABLE promotion_rules (
  id BIGSERIAL PRIMARY KEY,
  promotion_id BIGINT REFERENCES promotions(id) ON DELETE CASCADE,
  seq INT,
  rule_detail JSONB NOT NULL
);

CREATE TABLE promotion_status_logs (
  id BIGSERIAL PRIMARY KEY,
  promotion_id BIGINT REFERENCES promotions(id),
  from_status VARCHAR(16), to_status VARCHAR(16),
  operator_id BIGINT, comment TEXT,
  at_time TIMESTAMPTZ DEFAULT now()
);
```

## K. 报表画像

```sql
CREATE TABLE rebate_previews (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT, period_yyyymm CHAR(6),
  target_amount NUMERIC(18,2), actual_amount NUMERIC(18,2),
  achievement_rate NUMERIC(9,4),
  tier_hit JSONB, gross_rebate NUMERIC(18,2),
  deductions JSONB, net_rebate NUMERIC(18,2),
  snapshot_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (tenant_id, dealer_id, period_yyyymm)
);
CREATE TABLE rebate_settlements (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT, period_yyyymm CHAR(6),
  net_rebate NUMERIC(18,2),
  status VARCHAR(16) DEFAULT 'LOCKED',
  settled_at TIMESTAMPTZ DEFAULT now(), paid_at TIMESTAMPTZ,
  UNIQUE (tenant_id, dealer_id, period_yyyymm)
);
CREATE TABLE dealer_kpi_snapshots (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID,
  dealer_id BIGINT, period_yyyymm CHAR(6),
  stock_report_rate NUMERIC(5,4), sales_report_rate NUMERIC(5,4),
  order_pass_rate NUMERIC(5,4), return_rate NUMERIC(5,4),
  UNIQUE (tenant_id, dealer_id, period_yyyymm)
);
```

## L. 通用 / 审批 / 系统

```sql
CREATE TABLE audit_logs (
  id BIGSERIAL, tenant_id UUID,
  user_id BIGINT, action VARCHAR(32),
  resource_type VARCHAR(64), resource_id VARCHAR(64),
  before JSONB, after JSONB,
  ip VARCHAR(64), user_agent TEXT,
  at_time TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (id, at_time)
) PARTITION BY RANGE (at_time);

CREATE TABLE dict_types (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID,
  code VARCHAR(64) NOT NULL, name VARCHAR(200),
  description TEXT,
  UNIQUE (tenant_id, code)
);

CREATE TABLE dict_items (
  id BIGSERIAL PRIMARY KEY,
  type_id BIGINT REFERENCES dict_types(id),
  code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  seq INT, status VARCHAR(16) DEFAULT 'active',
  attrs JSONB DEFAULT '{}',
  UNIQUE (type_id, code)
);

CREATE TABLE system_settings (
  id BIGSERIAL PRIMARY KEY,
  scope VARCHAR(16) NOT NULL,
  tenant_id UUID,
  key VARCHAR(128) NOT NULL,
  value_json JSONB NOT NULL,
  description TEXT,
  updated_by BIGINT, updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (scope, tenant_id, key)
);

CREATE TABLE async_jobs (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID,
  user_id BIGINT, job_type VARCHAR(32),
  payload JSONB, status VARCHAR(16) DEFAULT 'PENDING',
  progress INT DEFAULT 0, result JSONB, error TEXT,
  started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID,
  user_id BIGINT NOT NULL,
  channel VARCHAR(16),                        -- V1 仅使用 INAPP / WECHAT_BOT / FEISHU_BOT；EMAIL/SMS 已从 V1 移除
  title VARCHAR(200), body TEXT,
  ref_type VARCHAR(32), ref_id VARCHAR(64),
  is_read BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_noti_user_unread ON notifications(user_id, is_read);

CREATE TABLE approval_tasks (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID,
  workflow_id BIGINT, workflow_node_code VARCHAR(64),
  ref_type VARCHAR(32) NOT NULL,
  ref_id BIGINT NOT NULL,
  assignee_id BIGINT NOT NULL,
  status VARCHAR(16) DEFAULT 'PENDING',
  action VARCHAR(16), comment TEXT, deadline TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(), done_at TIMESTAMPTZ
);
CREATE INDEX idx_appr_assignee ON approval_tasks(assignee_id, status);
CREATE INDEX idx_appr_ref ON approval_tasks(ref_type, ref_id);

CREATE TABLE approval_history (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT REFERENCES approval_tasks(id),
  ref_type VARCHAR(32), ref_id BIGINT,
  operator_id BIGINT, action VARCHAR(16), comment TEXT,
  at_time TIMESTAMPTZ DEFAULT now()
);
```

## M. 物化视图（示例）

```sql
-- 月度经销商 KPI 汇总
CREATE MATERIALIZED VIEW mv_dealer_kpi_month AS
SELECT tenant_id, dealer_id,
       to_char(sales_date,'YYYYMM') AS period,
       SUM(qty) AS total_qty, SUM(amount) AS total_amount
FROM sales_out_facts
GROUP BY tenant_id, dealer_id, to_char(sales_date,'YYYYMM');
CREATE UNIQUE INDEX ux_mv_dealer_kpi ON mv_dealer_kpi_month(tenant_id, dealer_id, period);

-- 每日刷新任务：REFRESH MATERIALIZED VIEW CONCURRENTLY mv_dealer_kpi_month;
```

## N. 关键索引 & 约束汇总

- 所有业务表的第一索引应包含 `tenant_id`；
- 高频筛选字段：`orders(dealer_id, status, submitted_at)`；`inventory(product_id, batch_no)`；`authorizations(dealer_id, auth_type, valid_from/to)`；`promotions(status, valid_from, valid_to)`；
- 唯一：`(tenant_id, code)` 或 `(tenant_id, dealer_id, code)` 等业务唯一；
- 分区：`inventory_transactions`, `audit_logs` 按月 RANGE 分区；
- 部分索引：`ux_main_wh` 只对 type='main' AND status='active' 生效；`ux_sales_serial` 只对非空 serial_no 生效。

## O. 数据字典枚举（示例）

| 字典 Code | 值 |
|---|---|
| ORDER_STATUS | DRAFT, PENDING, APPROVED, ERP_APPROVING, ERP_APPROVED, ERP_SHIPPED, RECEIVED, COMPLETED, REJECTED, RETURNED_TO_APPLICANT, CANCELLED |
| ORDER_TYPE | PURCHASE, SHORTAGE, CUSTOM |
| CONTRACT_APP_TYPE | NEW, MODIFY, RENEW, TERMINATE, BATCH_EXTEND, BATCH_UPDATE |
| CONTRACT_STATUS | draft, pending, approving, awaiting_sign, effective, rejected, cancelled, terminated, expired |
| AUTH_TYPE | ORDER, SALES_TO_HOSPITAL, SALES_TO_SUB, RMA, MOVE, LOAN |
| WAREHOUSE_TYPE | main, sub, hospital |
| ADJ_CATEGORY | INCREASE, DECREASE |
| ADJ_TYPE | 换货入库, 厂方换货, 盘盈, 其他入库, 过期报损, 损坏报损, 盘亏, 其他出库 |
| PROMO_TYPE | MOQ, GIFT, FULL_REDUCTION, BUNDLE（V1 只启用 MOQ / FULL_REDUCTION，GIFT/BUNDLE 已规划 V2 上线） |
| PROMO_STATUS | draft, pending, active, paused, expired, rejected |
| USER_TYPE | vendor, dealer |
| USER_STATUS | active, locked, disabled |

—— END ——


---

## 附录 · V8 迁移新增（v3.0）

### 变更概览

| 类型 | 对象 | 说明 |
|---|---|---|
| 新表 | `purchase_orders` | 采购订单主表 |
| 新表 | `purchase_order_lines` | 采购明细，含 `received_qty` 已收货数 |
| 新表 | `form_configs` | 低代码字段配置元数据 |
| 增列 | `orders/order_lines/products/dealers/warehouses/hospitals`.extra | JSONB 自定义扩展字段 |
| 新字典 | INVENTORY_STATUS / WAREHOUSE_TYPE / PRODUCT_LINE / PO_STATUS / SO_STATUS | 5 类新字典 |
| 预置配置 | order / purchase_order / product | 3 个表单的默认字段元数据 |

### 表结构

```sql
-- 采购订单主表
CREATE TABLE purchase_orders (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    code                 VARCHAR(64) UNIQUE NOT NULL,   -- 编号 PO-{timestamp}
    order_type           VARCHAR(16) DEFAULT 'NORMAL',  -- NORMAL / URGENT / RETURN
    supplier_id          BIGINT,                        -- 供应商（V1 复用 dealers）
    supplier_name        VARCHAR(200),
    warehouse_id         BIGINT,                        -- 目标入库仓库
    amount_incl_tax      NUMERIC(18,2) DEFAULT 0,
    discount_amount      NUMERIC(18,2) DEFAULT 0,
    final_amount         NUMERIC(18,2) DEFAULT 0,
    tax_amount           NUMERIC(18,2) DEFAULT 0,
    expected_date        DATE,
    status               VARCHAR(16) DEFAULT 'DRAFT',
                         -- 状态机: DRAFT -> SUBMITTED -> APPROVED -> RECEIVING -> COMPLETED
                         --                       \-> REJECTED / CANCELLED
    remark               TEXT,
    extra                JSONB DEFAULT '{}'::jsonb,     -- 低代码自定义字段
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
CREATE INDEX idx_po_tenant  ON purchase_orders(tenant_id, status);
CREATE INDEX idx_po_supp    ON purchase_orders(supplier_id);
CREATE INDEX idx_po_created ON purchase_orders(tenant_id, created_at DESC);

-- 采购订单明细
CREATE TABLE purchase_order_lines (
    id              BIGSERIAL PRIMARY KEY,
    po_id           BIGINT REFERENCES purchase_orders(id) ON DELETE CASCADE,
    seq             INT DEFAULT 1,
    product_id      BIGINT,
    qty             NUMERIC(14,4) NOT NULL,
    received_qty    NUMERIC(14,4) DEFAULT 0,           -- 已收货数量（收货入库时累加）
    unit_price      NUMERIC(18,4),
    tax_rate        NUMERIC(5,4) DEFAULT 0.13,
    subtotal        NUMERIC(18,2),
    remark          TEXT,
    extra           JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_pol_po ON purchase_order_lines(po_id);

-- 低代码字段配置表
CREATE TABLE form_configs (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    form_key       VARCHAR(64) NOT NULL,               -- 表单标识
    field_key      VARCHAR(64) NOT NULL,               -- 字段名
    field_label    VARCHAR(100),                       -- 中文标签
    field_type     VARCHAR(16) DEFAULT 'text',         -- text/number/date/select/textarea/picker/boolean
    is_native      BOOLEAN DEFAULT true,               -- 原生 vs 自定义（存 extra JSONB）
    required       BOOLEAN DEFAULT false,
    show_in_list   BOOLEAN DEFAULT true,
    show_in_form   BOOLEAN DEFAULT true,
    show_in_detail BOOLEAN DEFAULT true,
    default_value  TEXT,
    options_json   TEXT,                               -- select 类型的选项 JSON
    picker_resource VARCHAR(64),                       -- picker 类型的关联资源
    placeholder    VARCHAR(200),
    field_group    VARCHAR(64),                        -- 分组标题
    sort_order     INT DEFAULT 100,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    UNIQUE (tenant_id, form_key, field_key)
);
CREATE INDEX idx_fc_form ON form_configs(tenant_id, form_key, sort_order);

-- 主要业务表增加扩展字段列（低代码自定义字段存储）
ALTER TABLE orders          ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
ALTER TABLE order_lines     ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
ALTER TABLE products        ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
ALTER TABLE dealers         ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
ALTER TABLE warehouses      ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
ALTER TABLE hospitals       ADD COLUMN extra JSONB DEFAULT '{}'::jsonb;
```

### 关联关系

- `purchase_orders.supplier_id` → `dealers.id`（V1 复用，V2 可分离为独立 suppliers 表）
- `purchase_orders.warehouse_id` → `warehouses.id`
- `purchase_order_lines.po_id` → `purchase_orders.id` (级联删除)
- `purchase_order_lines.product_id` → `products.id`

### 库存联动（触发点）

采购单 `/receive` 调用时会：
1. UPDATE `purchase_order_lines` 累加 `received_qty`
2. UPDATE/INSERT `inventory` （+qty）
3. INSERT `inventory_transactions` (RECEIPT / ref_doc_type='purchase_order')

### 数据库导出快照

阿里云生产环境的完整数据库快照已导出至：
- `docs/05_数据库设计/schema_export/dms_schema.sql`（157 KB · 66 张表 Schema）
- `docs/05_数据库设计/schema_export/dms_data.sql`（2.2 MB · 数据）
- `docs/05_数据库设计/schema_export/dms_full.sql`（2.3 MB · Schema + 数据）

本地还原：
```powershell
docker run -d --name pg-local -e POSTGRES_USER=dms -e POSTGRES_PASSWORD=dms123456 -e POSTGRES_DB=dms -p 15432:5432 postgres:14-alpine
docker exec -i pg-local psql -U dms -d dms < docs\05_数据库设计\schema_export\dms_full.sql
```

