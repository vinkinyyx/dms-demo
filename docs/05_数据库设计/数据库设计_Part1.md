# 数据库设计（PostgreSQL 14+）Part 1

> **文档版本：V3.0**（原 V1.0 + V8 迁移增量 = 66 张表 + 分区表）
> **⚠️ V3.0 更新说明**：本文档记录 V1-V7 迁移的 69 张基础表；V8 新增的 `purchase_orders`/`purchase_order_lines`/`form_configs` 详见 [数据库设计_Part2.md](数据库设计_Part2.md#附录--v8-迁移新增v30) 附录。

> PostgreSQL 14+；表数 66 张业务表（含分区）
> 通用字段：所有业务表含 tenant_id UUID NOT NULL / created_at / updated_at / created_by / updated_by / version / deleted_at
> 命名：小写下划线；主键 id

> **V1 决策变更（D-24~D-41）提示**：
> - `users` 新增微信登录字段：`wechat_openid` / `wechat_unionid` / `wechat_bound_at`，并加唯一部分索引。
> - `promotions.promo_type` V1 仅启用 `MOQ` / `FULL_REDUCTION`，`GIFT` / `BUNDLE` 保留枚举位供 V2 扩展。
> - `sso_service_id` 等预留字段保留但 V1 不使用；`user_login_logs.login_type` 去除 SSO，仅保留 PASSWORD / WECHAT / REMEMBER。
> - `notifications.channel` V1 仅使用 INAPP / WECHAT_BOT / FEISHU_BOT；EMAIL / SMS 已从 V1 移除。
> - `tenants.attrs` 新增约定：`primary_color` 用于前端主题注入。


## 表分组索引

| 分组 | 表数 | 主要表 |
|---|---|---|
| A 平台租户 | 2 | tenants, tenant_modules |
| B 用户与权限 | 10 | users, roles, strategies, resources, org_units, data_scopes, user_login_logs, user_roles, role_strategies, strategy_resources |
| C 主数据 | 10 | products, product_categories, price_lists, hospitals, regions, dealers, dealer_addresses, warehouses, workflows, workflow_nodes |
| D 合同 | 6 | contract_applications, contract_diff, contracts, contract_attachments, contract_signatures, contract_templates |
| E 授权 | 2 | authorizations, temp_authorizations |
| F 订单 | 4 | orders, order_lines, order_promotion_hits, order_status_history |
| G 收货/库存/销售 | 15 | receipts,receipt_lines,inventory,inventory_transactions,stock_moves,stock_move_lines,loans,loan_lines,inventory_adjustments,adjustment_lines,stocktakes,stocktake_lines,sales_outs,sales_out_lines,sales_out_facts |
| H 分销/退换货 | 4 | distribution_shipments,distribution_lines,rma_authorizations,rma_orders |
| I 发票 | 2 | purchase_invoices, sales_invoices |
| J 促销 | 3 | promotions, promotion_rules, promotion_status_logs |
| K 报表画像 | 3 | rebate_previews, rebate_settlements, dealer_kpi_snapshots |
| L 通用/审批/系统 | 8 | audit_logs, dict_types, dict_items, system_settings, async_jobs, notifications, approval_tasks, approval_history |
| **合计** | **69** | |

## A. 平台租户

```sql
CREATE TABLE tenants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(32) UNIQUE NOT NULL,
  name VARCHAR(200) NOT NULL,
  industry VARCHAR(32) NOT NULL,
  timezone VARCHAR(64) DEFAULT 'Asia/Shanghai',
  logo_url TEXT,
  status VARCHAR(16) DEFAULT 'active',
  modules_enabled JSONB DEFAULT '{}',
  quota JSONB DEFAULT '{}',
  attrs JSONB DEFAULT '{}',
  -- attrs JSONB 中约定：
  --   primary_color: 租户主色（如 #2C4B8E），前端启动时读取注入 CSS variable
  --   modules_enabled: 已在独立字段，此处不再重复
  contact_name VARCHAR(64),
  contact_email VARCHAR(128),
  contact_phone VARCHAR(32),
  effective_from DATE,
  effective_to DATE,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE tenant_modules (
  tenant_id UUID REFERENCES tenants(id),
  module_code VARCHAR(32) NOT NULL,
  enabled BOOLEAN DEFAULT true,
  config JSONB DEFAULT '{}',
  PRIMARY KEY (tenant_id, module_code)
);
```

## B. 用户与权限

```sql
CREATE TABLE org_units (
  id BIGSERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL,
  parent_id BIGINT REFERENCES org_units(id),
  code VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  level INT NOT NULL, path VARCHAR(500),
  type VARCHAR(32), status VARCHAR(16) DEFAULT 'active',
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (tenant_id, code)
);

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL,
  username VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  user_type VARCHAR(16) NOT NULL,           -- vendor/dealer
  password_hash VARCHAR(255) NOT NULL,
  must_change_password BOOLEAN DEFAULT true,
  password_updated_at TIMESTAMPTZ,
  email VARCHAR(128), phone VARCHAR(32),
  org_id BIGINT, dealer_id BIGINT,
  status VARCHAR(16) DEFAULT 'active',
  login_fail_count INT DEFAULT 0,
  locked_until TIMESTAMPTZ,
  last_login_at TIMESTAMPTZ, last_login_ip VARCHAR(64),
  attrs JSONB DEFAULT '{}',
  wechat_openid VARCHAR(64),                -- 微信扫码登录绑定
  wechat_unionid VARCHAR(64),               -- 微信 UnionID（多应用）
  wechat_bound_at TIMESTAMPTZ,              -- 绑定时间
  sso_service_id VARCHAR(64),               -- V1 保留字段，暂不启用（预留 SSO 对接）
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  UNIQUE (tenant_id, username)
);
CREATE UNIQUE INDEX ux_users_wechat_openid ON users(wechat_openid) WHERE wechat_openid IS NOT NULL;

CREATE TABLE resources (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(128) NOT NULL, name VARCHAR(200) NOT NULL,
  type VARCHAR(16) NOT NULL, parent_id BIGINT REFERENCES resources(id),
  operations VARCHAR(200)[] DEFAULT ARRAY[]::VARCHAR[],
  path VARCHAR(500), status VARCHAR(16) DEFAULT 'active',
  UNIQUE (tenant_id, code)
);

CREATE TABLE strategies (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  name VARCHAR(200) NOT NULL, description TEXT,
  status VARCHAR(16) DEFAULT 'active'
);

CREATE TABLE strategy_resources (
  strategy_id BIGINT REFERENCES strategies(id),
  resource_id BIGINT REFERENCES resources(id),
  operations VARCHAR(200)[] NOT NULL,
  PRIMARY KEY (strategy_id, resource_id)
);

CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  description TEXT, status VARCHAR(16) DEFAULT 'active',
  UNIQUE (tenant_id, code)
);

CREATE TABLE role_strategies (
  role_id BIGINT REFERENCES roles(id),
  strategy_id BIGINT REFERENCES strategies(id),
  PRIMARY KEY (role_id, strategy_id)
);

CREATE TABLE user_roles (
  user_id BIGINT REFERENCES users(id),
  role_id BIGINT REFERENCES roles(id),
  granted_at TIMESTAMPTZ DEFAULT now(),
  granted_by BIGINT,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE data_scopes (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  scope_type VARCHAR(16) NOT NULL,
  org_ids BIGINT[] DEFAULT ARRAY[]::BIGINT[],
  dealer_ids BIGINT[] DEFAULT ARRAY[]::BIGINT[]
);

CREATE TABLE user_login_logs (
  id BIGSERIAL PRIMARY KEY,
  tenant_id UUID, user_id BIGINT,
  login_type VARCHAR(16), ip VARCHAR(64),   -- V1 仅使用 PASSWORD / WECHAT / REMEMBER；SSO 已从 V1 移除
  user_agent TEXT, success BOOLEAN,
  fail_reason VARCHAR(200),
  at_time TIMESTAMPTZ DEFAULT now()
);
```

## C. 主数据

```sql
CREATE TABLE regions (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(32) NOT NULL, name VARCHAR(100) NOT NULL,
  parent_id BIGINT REFERENCES regions(id),
  level INT NOT NULL, status VARCHAR(16) DEFAULT 'active',
  UNIQUE (tenant_id, code)
);

CREATE TABLE product_categories (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  parent_id BIGINT REFERENCES product_categories(id),
  level INT NOT NULL, status VARCHAR(16) DEFAULT 'active',
  UNIQUE (tenant_id, code)
);

CREATE TABLE products (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) NOT NULL,
  name_cn VARCHAR(200) NOT NULL, name_en VARCHAR(200),
  category_id BIGINT REFERENCES product_categories(id),
  spec VARCHAR(100), unit VARCHAR(32),
  current_price NUMERIC(14,2),
  tax_rate NUMERIC(5,4) DEFAULT 0.13,
  udi_required BOOLEAN DEFAULT true,
  warn_months INT DEFAULT 3,
  safety_qty NUMERIC(14,4) DEFAULT 0,
  min_order_qty NUMERIC(14,4),
  status VARCHAR(16) DEFAULT 'active',
  attrs JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (tenant_id, code)
);

CREATE TABLE price_lists (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  product_id BIGINT, dealer_id BIGINT,
  price NUMERIC(14,2) NOT NULL, currency VARCHAR(8) DEFAULT 'CNY',
  valid_from DATE NOT NULL, valid_to DATE,
  source VARCHAR(16) DEFAULT 'ERP',
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_price_active ON price_lists(tenant_id, product_id, dealer_id, valid_from DESC);

CREATE TABLE hospitals (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  type VARCHAR(32), level VARCHAR(32),
  region_id BIGINT REFERENCES regions(id),
  address VARCHAR(500), contact VARCHAR(100), phone VARCHAR(32),
  attrs JSONB DEFAULT '{}', status VARCHAR(16) DEFAULT 'active',
  UNIQUE (tenant_id, code)
);

CREATE TABLE dealers (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(32) NOT NULL, name VARCHAR(200) NOT NULL,
  level VARCHAR(16), parent_dealer_id BIGINT REFERENCES dealers(id),
  legal_person VARCHAR(64), usc_no VARCHAR(32),
  reg_address VARCHAR(500), reg_capital NUMERIC(18,2),
  founded_at DATE, business_scope VARCHAR(500),
  gsp_status VARCHAR(16), gsp_expire DATE,
  gmp_status VARCHAR(16), gmp_expire DATE,
  region_id BIGINT,
  contact_name VARCHAR(100), contact_phone VARCHAR(32), contact_email VARCHAR(128),
  sales_owner_user_id BIGINT,
  status VARCHAR(16) DEFAULT 'active',
  attrs JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (tenant_id, code)
);

CREATE TABLE dealer_addresses (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT REFERENCES dealers(id),
  is_default BOOLEAN DEFAULT false,
  contact_name VARCHAR(100), phone VARCHAR(32),
  province VARCHAR(64), city VARCHAR(64), district VARCHAR(64),
  address VARCHAR(500), postal_code VARCHAR(16)
);

CREATE TABLE warehouses (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT NOT NULL REFERENCES dealers(id),
  code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  type VARCHAR(16) NOT NULL,
  hospital_id BIGINT REFERENCES hospitals(id),
  address VARCHAR(500),
  status VARCHAR(16) DEFAULT 'active',
  UNIQUE (tenant_id, dealer_id, code)
);
CREATE UNIQUE INDEX ux_main_wh ON warehouses(tenant_id, dealer_id)
  WHERE type='main' AND status='active';

CREATE TABLE workflows (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
  version INT DEFAULT 1, status VARCHAR(16) DEFAULT 'active',
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (tenant_id, code, version)
);

CREATE TABLE workflow_nodes (
  id BIGSERIAL PRIMARY KEY,
  workflow_id BIGINT REFERENCES workflows(id),
  code VARCHAR(64) NOT NULL, name VARCHAR(200),
  node_type VARCHAR(16), assignee_strategy VARCHAR(32),
  assignee_config JSONB, visible_fields TEXT[],
  timeout_hours INT, seq INT
);
```

## D. 合同

```sql
CREATE TABLE contract_applications (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE NOT NULL,
  application_type VARCHAR(16) NOT NULL,
  contract_category VARCHAR(32) NOT NULL,
  ref_contract_id BIGINT,
  dealer_id BIGINT REFERENCES dealers(id),
  dealer_snapshot JSONB,
  authorization_scope JSONB,
  indicators JSONB,
  valid_from DATE, valid_to DATE,
  status VARCHAR(16) DEFAULT 'draft',
  created_by BIGINT,
  created_at TIMESTAMPTZ DEFAULT now(),
  submitted_at TIMESTAMPTZ, effective_at TIMESTAMPTZ,
  remark TEXT,
  version INT DEFAULT 0, deleted_at TIMESTAMPTZ
);

CREATE TABLE contract_diff (
  id BIGSERIAL PRIMARY KEY,
  application_id BIGINT REFERENCES contract_applications(id),
  field_group VARCHAR(64), field_key VARCHAR(128),
  before_value TEXT, after_value TEXT
);

CREATE TABLE contracts (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE NOT NULL,
  application_id BIGINT REFERENCES contract_applications(id),
  dealer_id BIGINT REFERENCES dealers(id),
  category VARCHAR(32),
  valid_from DATE, valid_to DATE,
  status VARCHAR(16) DEFAULT 'effective',
  pdf_url TEXT, ca_serial_no VARCHAR(128),
  dealer_signed_at TIMESTAMPTZ, vendor_signed_at TIMESTAMPTZ,
  archived_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE contract_attachments (
  id BIGSERIAL PRIMARY KEY,
  ref_type VARCHAR(16), ref_id BIGINT,
  category VARCHAR(64), file_id BIGINT, file_url TEXT,
  file_name VARCHAR(255), size_bytes BIGINT,
  uploaded_by BIGINT, uploaded_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE contract_signatures (
  id BIGSERIAL PRIMARY KEY,
  contract_id BIGINT REFERENCES contracts(id),
  signer_type VARCHAR(16), signer_user_id BIGINT,
  ca_serial_no VARCHAR(128), sms_code_ref VARCHAR(64),
  signed_at TIMESTAMPTZ DEFAULT now(), status VARCHAR(16)
);

CREATE TABLE contract_templates (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) NOT NULL, name VARCHAR(200),
  category VARCHAR(32), content_url TEXT,
  variables TEXT[], version INT DEFAULT 1,
  status VARCHAR(16) DEFAULT 'active',
  UNIQUE (tenant_id, code, version)
);
```

## E. 授权

```sql
CREATE TABLE authorizations (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT REFERENCES dealers(id),
  contract_id BIGINT REFERENCES contracts(id),
  auth_type VARCHAR(32) NOT NULL,
  product_line_id BIGINT, product_id BIGINT,
  terminal_id BIGINT, region_id BIGINT,
  valid_from DATE NOT NULL, valid_to DATE NOT NULL,
  status VARCHAR(16) DEFAULT 'active',
  source VARCHAR(16) DEFAULT 'contract',
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_auth_lookup ON authorizations(tenant_id, dealer_id, auth_type, status, valid_from, valid_to);

CREATE TABLE temp_authorizations (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  dealer_id BIGINT, auth_type VARCHAR(32),
  scope JSONB, valid_from DATE, valid_to DATE,
  reason TEXT, status VARCHAR(16) DEFAULT 'pending',
  applicant_id BIGINT, approved_by BIGINT, approved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

## F. 订单

```sql
CREATE TABLE orders (
  id BIGSERIAL PRIMARY KEY, tenant_id UUID NOT NULL,
  code VARCHAR(64) UNIQUE NOT NULL,
  order_type VARCHAR(16) NOT NULL,
  dealer_id BIGINT NOT NULL REFERENCES dealers(id),
  ship_address_id BIGINT REFERENCES dealer_addresses(id),
  ship_snapshot JSONB,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  parent_order_id BIGINT REFERENCES orders(id),
  amount_incl_tax NUMERIC(18,2) DEFAULT 0,
  discount_amount NUMERIC(18,2) DEFAULT 0,
  final_amount NUMERIC(18,2) DEFAULT 0,
  remark TEXT, expected_date DATE,
  submitted_at TIMESTAMPTZ, approved_at TIMESTAMPTZ,
  shipped_at TIMESTAMPTZ, received_at TIMESTAMPTZ, closed_at TIMESTAMPTZ,
  erp_order_no VARCHAR(64),
  created_by BIGINT,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  version INT DEFAULT 0, deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_orders_dealer ON orders(dealer_id);
CREATE INDEX idx_orders_status ON orders(tenant_id, status);

CREATE TABLE order_lines (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL,
  qty NUMERIC(14,4) NOT NULL,
  unit_price NUMERIC(14,2) NOT NULL,
  tax_rate NUMERIC(5,4),
  sub_total NUMERIC(18,2) NOT NULL,
  is_gift BOOLEAN DEFAULT false, seq INT
);

CREATE TABLE order_promotion_hits (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT REFERENCES orders(id),
  promotion_id BIGINT NOT NULL,
  rule_type VARCHAR(16),
  discount NUMERIC(18,2),
  gift_lines JSONB, detail JSONB
);

CREATE TABLE order_status_history (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL,
  from_status VARCHAR(32), to_status VARCHAR(32),
  action VARCHAR(32), operator_id BIGINT, comment TEXT,
  at_time TIMESTAMPTZ DEFAULT now()
);
```

—— 待续见 `数据库设计_Part2.md`
