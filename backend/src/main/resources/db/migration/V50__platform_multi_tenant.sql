-- V50: 平台后台与多厂家多租户基础模型
-- 范围：tenants 扩展、租户-dealer 绑定、平台菜单/UI 配置、产品对码、平台审计、接口 HTTP 日志
-- 说明：本迁移仅新增平台支撑表和必要字段，不删除旧日志表，不破坏现有业务逻辑。

-- 1. tenants 扩展
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS tenant_type VARCHAR(16) NOT NULL DEFAULT 'MANUFACTURER';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS owner_manufacturer_id UUID REFERENCES tenants(id);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deployment_mode VARCHAR(16) NOT NULL DEFAULT 'SHARED';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMPTZ;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS disabled_by BIGINT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS disable_reason VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS enabled_at TIMESTAMPTZ;

UPDATE tenants
SET tenant_type = 'MANUFACTURER',
    deployment_mode = 'SHARED',
    enabled_at = COALESCE(enabled_at, created_at)
WHERE tenant_type IS DISTINCT FROM 'DEALER';

CREATE INDEX IF NOT EXISTS idx_tenants_type_status ON tenants(tenant_type, status);
CREATE INDEX IF NOT EXISTS idx_tenants_owner ON tenants(owner_manufacturer_id, status);

-- 2. 经销商租户与厂家 dealer 主数据绑定
CREATE TABLE IF NOT EXISTS tenant_dealer_bindings (
    id BIGSERIAL PRIMARY KEY,
    dealer_tenant_id UUID NOT NULL REFERENCES tenants(id),
    manufacturer_tenant_id UUID NOT NULL REFERENCES tenants(id),
    dealer_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    bound_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    bound_by BIGINT,
    unbound_at TIMESTAMPTZ,
    remark VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tdb_dealer_tenant_active
    ON tenant_dealer_bindings(dealer_tenant_id)
    WHERE deleted_at IS NULL AND status = 'active';
CREATE UNIQUE INDEX IF NOT EXISTS ux_tdb_manufacturer_dealer_active
    ON tenant_dealer_bindings(manufacturer_tenant_id, dealer_id)
    WHERE deleted_at IS NULL AND status = 'active';
CREATE INDEX IF NOT EXISTS idx_tdb_manufacturer_status
    ON tenant_dealer_bindings(manufacturer_tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_tdb_dealer_ref
    ON tenant_dealer_bindings(manufacturer_tenant_id, dealer_id);

COMMENT ON TABLE tenant_dealer_bindings IS '经销商租户与厂家 dealer 主数据绑定关系';
COMMENT ON COLUMN tenant_dealer_bindings.dealer_id IS '厂家租户 dealers.id，不加物理外键以保持租户边界清晰';

-- 3. 平台默认角色模板
CREATE TABLE IF NOT EXISTS role_templates (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    tenant_type VARCHAR(16) NOT NULL,
    data_scope VARCHAR(32) NOT NULL,
    description TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS role_template_resources (
    template_id BIGINT NOT NULL REFERENCES role_templates(id) ON DELETE CASCADE,
    resource_code VARCHAR(128) NOT NULL,
    actions VARCHAR(200)[] NOT NULL DEFAULT ARRAY[]::VARCHAR[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (template_id, resource_code)
);

CREATE TABLE IF NOT EXISTS role_data_policies (
    role_id BIGINT PRIMARY KEY REFERENCES roles(id) ON DELETE CASCADE,
    data_scope VARCHAR(32) NOT NULL,
    position_tree_enabled BOOLEAN NOT NULL DEFAULT false,
    self_created_enabled BOOLEAN NOT NULL DEFAULT false,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. 平台菜单、页面字段、筛选配置
CREATE TABLE IF NOT EXISTS platform_menus (
    id BIGSERIAL PRIMARY KEY,
    menu_key VARCHAR(64) NOT NULL UNIQUE,
    parent_key VARCHAR(64),
    label VARCHAR(100) NOT NULL,
    icon VARCHAR(64),
    route VARCHAR(200),
    permission_code VARCHAR(128),
    tenant_type VARCHAR(16) NOT NULL DEFAULT 'ALL',
    visible BOOLEAN NOT NULL DEFAULT true,
    sort_order INT NOT NULL DEFAULT 100,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS platform_page_configs (
    id BIGSERIAL PRIMARY KEY,
    page_key VARCHAR(100) NOT NULL,
    tenant_type VARCHAR(16) NOT NULL,
    field_key VARCHAR(100) NOT NULL,
    label VARCHAR(100),
    visible BOOLEAN NOT NULL DEFAULT true,
    readonly BOOLEAN NOT NULL DEFAULT false,
    required BOOLEAN NOT NULL DEFAULT false,
    exportable BOOLEAN NOT NULL DEFAULT true,
    sort_order INT NOT NULL DEFAULT 100,
    width INT,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (page_key, tenant_type, field_key)
);

CREATE TABLE IF NOT EXISTS platform_filter_configs (
    id BIGSERIAL PRIMARY KEY,
    page_key VARCHAR(100) NOT NULL,
    tenant_type VARCHAR(16) NOT NULL,
    filter_key VARCHAR(100) NOT NULL,
    label VARCHAR(100) NOT NULL,
    component_type VARCHAR(32) NOT NULL,
    dict_type VARCHAR(100),
    default_value TEXT,
    multiple BOOLEAN NOT NULL DEFAULT false,
    visible BOOLEAN NOT NULL DEFAULT true,
    sort_order INT NOT NULL DEFAULT 100,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (page_key, tenant_type, filter_key)
);

CREATE INDEX IF NOT EXISTS idx_platform_menus_type_sort ON platform_menus(tenant_type, sort_order);
CREATE INDEX IF NOT EXISTS idx_platform_page_configs_lookup ON platform_page_configs(page_key, tenant_type, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_platform_filter_configs_lookup ON platform_filter_configs(page_key, tenant_type, status, sort_order);

-- 5. 产品对码
CREATE TABLE IF NOT EXISTS product_mappings (
    id BIGSERIAL PRIMARY KEY,
    manufacturer_tenant_id UUID NOT NULL,
    dealer_tenant_id UUID NOT NULL,
    manufacturer_product_id BIGINT NOT NULL,
    dealer_product_id BIGINT NOT NULL,
    manufacturer_product_code VARCHAR(64) NOT NULL,
    dealer_product_code VARCHAR(64) NOT NULL,
    package_unit VARCHAR(32),
    conversion_rate NUMERIC(18,6) NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    import_batch_no VARCHAR(64),
    remark VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT,
    updated_by BIGINT,
    version INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pm_manufacturer_product
    ON product_mappings(manufacturer_tenant_id, dealer_tenant_id, manufacturer_product_id)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_pm_dealer_product
    ON product_mappings(manufacturer_tenant_id, dealer_tenant_id, dealer_product_id)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_pm_manufacturer_status ON product_mappings(manufacturer_tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_pm_dealer_tenant_status ON product_mappings(dealer_tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_pm_import_batch ON product_mappings(import_batch_no);

CREATE TABLE IF NOT EXISTS product_mapping_import_batches (
    id BIGSERIAL PRIMARY KEY,
    manufacturer_tenant_id UUID NOT NULL,
    file_name VARCHAR(255),
    object_key TEXT,
    error_object_key TEXT,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_pm_batch_manufacturer ON product_mapping_import_batches(manufacturer_tenant_id, created_at DESC);

COMMENT ON TABLE product_mappings IS '厂家产品与经销商产品一对一编码映射';

-- 6. 平台审计日志
CREATE TABLE IF NOT EXISTS platform_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT,
    admin_username VARCHAR(64),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(64),
    before_json JSONB,
    after_json JSONB,
    ip VARCHAR(64),
    user_agent VARCHAR(512),
    success BOOLEAN,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_platform_audit_action_time ON platform_audit_logs(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_audit_target ON platform_audit_logs(target_type, target_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_audit_admin_time ON platform_audit_logs(admin_user_id, created_at DESC);

-- 7. HTTP 接口日志元数据，原始报文入 MinIO
CREATE TABLE IF NOT EXISTS api_http_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64),
    trace_id VARCHAR(64),
    tenant_id UUID,
    tenant_type VARCHAR(16),
    owner_manufacturer_id UUID,
    user_id BIGINT,
    username VARCHAR(64),
    auth_source VARCHAR(16),
    http_method VARCHAR(8),
    path VARCHAR(255),
    query_string TEXT,
    status_code INT,
    biz_code INT,
    success BOOLEAN,
    slow BOOLEAN NOT NULL DEFAULT false,
    spent_ms BIGINT,
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    error_message TEXT,
    request_object_key TEXT,
    response_object_key TEXT,
    request_size BIGINT,
    response_size BIGINT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_http_logs_started ON api_http_logs(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_http_logs_tenant ON api_http_logs(tenant_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_http_logs_owner ON api_http_logs(owner_manufacturer_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_http_logs_request ON api_http_logs(request_id);
CREATE INDEX IF NOT EXISTS idx_api_http_logs_slow ON api_http_logs(slow, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_http_logs_path ON api_http_logs(path, started_at DESC);

COMMENT ON TABLE api_http_logs IS 'HTTP 接口日志元数据，原始请求/响应报文存储在 MinIO';

-- 8. 默认角色模板种子
INSERT INTO role_templates (code, name, tenant_type, data_scope, description, status)
VALUES
  ('MANUFACTURER_ADMIN', '厂家管理员', 'MANUFACTURER', 'ALL_TENANT', '厂家租户管理员，拥有本租户全部数据权限', 'active'),
  ('MANUFACTURER_SERVICE', '厂家客服', 'MANUFACTURER', 'ALL_TENANT', '厂家客服，拥有本租户全部数据权限', 'active'),
  ('MANUFACTURER_SALES', '厂家销售', 'MANUFACTURER', 'POSITION_TREE', '厂家销售，按岗位树查看负责经销商数据', 'active'),
  ('DEALER_ADMIN', '经销商管理员', 'DEALER', 'ALL_TENANT', '经销商租户管理员，拥有本租户全部数据权限', 'active'),
  ('DEALER_SERVICE', '经销商客服', 'DEALER', 'ALL_TENANT', '经销商客服，拥有本租户全部数据权限', 'active'),
  ('DEALER_SALES', '经销商销售', 'DEALER', 'POSITION_TREE', '经销商销售，按岗位树查看负责客户数据', 'active')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    tenant_type = EXCLUDED.tenant_type,
    data_scope = EXCLUDED.data_scope,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = now();

-- 9. 平台菜单种子（基于现有前台菜单；平台后台可后续维护）
INSERT INTO platform_menus (menu_key, parent_key, label, icon, route, permission_code, tenant_type, visible, sort_order, status)
VALUES
  ('products', NULL, '产品管理', 'Goods', '/module/products', 'product:menu', 'ALL', true, 10, 'active'),
  ('categories', NULL, '产品分类', 'Files', '/module/categories', 'category:menu', 'ALL', true, 20, 'active'),
  ('dealers', NULL, '经销商管理', 'OfficeBuilding', '/module/dealers', 'dealer:menu', 'MANUFACTURER', true, 30, 'active'),
  ('hospitals', NULL, '医院/终端', 'FirstAidKit', '/module/hospitals', 'hospital:menu', 'ALL', true, 40, 'active'),
  ('warehouses', NULL, '仓库管理', 'House', '/module/warehouses', 'warehouse:menu', 'ALL', true, 50, 'active'),
  ('suppliers', NULL, '供应商', 'Shop', '/module/suppliers', 'supplier:menu', 'ALL', true, 60, 'active'),
  ('contracts', NULL, '合同', 'Document', '/module/contracts', 'contract:menu', 'ALL', true, 70, 'active'),
  ('authorizations', NULL, '授权管理', 'Key', '/module/authorizations', 'authorization:menu', 'MANUFACTURER', true, 80, 'active'),
  ('orders', NULL, '销售订单', 'Sell', '/module/orders', 'sales-order:menu', 'ALL', true, 90, 'active'),
  ('sales-returns', NULL, '销退订单', 'RefreshLeft', '/module/sales-returns', 'sales-return:menu', 'ALL', true, 100, 'active'),
  ('purchase-orders', NULL, '采购订单', 'ShoppingCart', '/module/purchase-orders', 'purchase-order:menu', 'ALL', true, 110, 'active'),
  ('purchase-returns', NULL, '采退订单', 'RefreshRight', '/module/purchase-returns', 'purchase-return:menu', 'ALL', true, 120, 'active'),
  ('inventory', NULL, '库存查询', 'Box', '/module/inventory', 'inventory:menu', 'ALL', true, 130, 'active'),
  ('sales-outs', NULL, '销售出库', 'Van', '/module/sales-outs', 'sales-out:menu', 'ALL', true, 140, 'active'),
  ('receipts', NULL, '收货入库', 'TakeawayBox', '/module/receipts', 'receipt:menu', 'ALL', true, 150, 'active'),
  ('stock-moves', NULL, '库存移动', 'Switch', '/module/stock-moves', 'stock-move:menu', 'ALL', true, 160, 'active'),
  ('inventory-adjustments', NULL, '库存调整', 'ScaleToOriginal', '/module/inventory-adjustments', 'inventory-adjustment:menu', 'ALL', true, 170, 'active'),
  ('surgery-reports', NULL, '手术植入报告', 'FirstAidKit', '/module/surgery-reports', 'surgery-report:menu', 'ALL', true, 180, 'active'),
  ('promotions', NULL, '促销规则', 'Present', '/module/promotions', 'promotion:menu', 'MANUFACTURER', true, 190, 'active'),
  ('dashboard', NULL, '数据看板', 'DataLine', '/dashboard', 'dashboard:menu', 'ALL', true, 200, 'active'),
  ('report-order-trace', NULL, '订单追踪', 'TrendCharts', '/module/report-order-trace', 'report-order-trace:menu', 'ALL', true, 210, 'active'),
  ('positions', NULL, '销售岗位', 'OfficeBuilding', '/positions', 'position:menu', 'ALL', true, 220, 'active'),
  ('users', NULL, '账号管理', 'User', '/module/users', 'user:menu', 'ALL', true, 230, 'active'),
  ('roles', NULL, '角色管理', 'Avatar', '/module/roles', 'role:menu', 'ALL', true, 240, 'active'),
  ('product-mappings', NULL, '产品对码', 'Connection', '/product-mappings', 'product-mapping:menu', 'MANUFACTURER', true, 250, 'active')
ON CONFLICT (menu_key) DO UPDATE
SET label = EXCLUDED.label,
    icon = EXCLUDED.icon,
    route = EXCLUDED.route,
    permission_code = EXCLUDED.permission_code,
    tenant_type = EXCLUDED.tenant_type,
    visible = EXCLUDED.visible,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    updated_at = now();
