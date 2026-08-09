-- V77: 合同模块重构（方案A：合并申请/合同为单一实体 + 模板驱动）
-- 说明：老合同数据均为测试数据，直接 DROP 重建；新增合同模板、审批轮次表。

-- 1. 清理旧表（顺序：先子表后父表）
DROP TABLE IF EXISTS contract_signatures CASCADE;
DROP TABLE IF EXISTS contract_diff CASCADE;
DROP TABLE IF EXISTS contract_attachments CASCADE;
DROP TABLE IF EXISTS contracts CASCADE;
DROP TABLE IF EXISTS contract_applications CASCADE;
DROP TABLE IF EXISTS contract_templates CASCADE;

-- 2. 合同模板（法务配置端）
CREATE TABLE contract_templates (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    code              VARCHAR(64) NOT NULL,
    name              VARCHAR(200),
    category          VARCHAR(32),
    original_file_id  BIGINT,
    fields            JSONB NOT NULL DEFAULT '[]'::jsonb,
    numbering_rules   JSONB,
    version           INT NOT NULL DEFAULT 1,
    status            VARCHAR(16) NOT NULL DEFAULT 'draft',  -- draft / published / disabled
    published_at      TIMESTAMPTZ,
    created_by        BIGINT,
    updated_by        BIGINT,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    UNIQUE (tenant_id, code, version)
);
CREATE INDEX idx_contract_tpl_tenant ON contract_templates(tenant_id, status);
CREATE INDEX idx_contract_tpl_category ON contract_templates(tenant_id, category, status);

-- 3. 合同主表（贯穿全生命周期）
CREATE TABLE contracts (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    code              VARCHAR(64) UNIQUE NOT NULL,
    name              VARCHAR(200),
    category          VARCHAR(32),
    application_type  VARCHAR(16) NOT NULL DEFAULT 'NEW', -- NEW/MODIFY/RENEW/TERMINATE
    ref_contract_id   BIGINT REFERENCES contracts(id),
    template_id       BIGINT,
    template_version  INT,
    dealer_id         BIGINT REFERENCES dealers(id),
    vendor_party      VARCHAR(160),
    dealer_party      VARCHAR(160),
    sign_city         VARCHAR(80),
    valid_from        DATE,
    valid_to          DATE,
    target_amount     NUMERIC(14,2),
    signed_amount     NUMERIC(14,2),
    payment_terms     VARCHAR(160),
    settlement_cycle  VARCHAR(64),
    owner_name        VARCHAR(64),
    owner_phone       VARCHAR(32),
    form_data         JSONB NOT NULL DEFAULT '{}'::jsonb,
    status            VARCHAR(16) NOT NULL DEFAULT 'draft',
    source_file_id    BIGINT,
    submitted_at      TIMESTAMPTZ,
    effective_at      TIMESTAMPTZ,
    terminated_at     TIMESTAMPTZ,
    created_by        BIGINT,
    updated_by        BIGINT,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    version           INT DEFAULT 0,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_contracts_tenant_status ON contracts(tenant_id, status);
CREATE INDEX idx_contracts_dealer ON contracts(dealer_id);
CREATE INDEX idx_contracts_valid ON contracts(tenant_id, valid_from, valid_to);
CREATE INDEX idx_contracts_ref ON contracts(ref_contract_id);

-- 4. 合同附件
CREATE TABLE contract_attachments (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID,
    contract_id   BIGINT REFERENCES contracts(id) ON DELETE CASCADE,
    category      VARCHAR(64),   -- annex / final
    file_id       BIGINT,
    file_url      TEXT,
    file_name     VARCHAR(255),
    size_bytes    BIGINT,
    uploaded_by   BIGINT,
    uploaded_at   TIMESTAMPTZ DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_contract_att_contract ON contract_attachments(contract_id);

-- 5. 合同审批轮次/留痕
CREATE TABLE contract_revisions (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID,
    contract_id   BIGINT REFERENCES contracts(id) ON DELETE CASCADE,
    round         INT NOT NULL DEFAULT 1,
    action        VARCHAR(16),   -- submit/approve/reject/return/withdraw/terminate
    operator_id   BIGINT,
    operator_name VARCHAR(128),
    comment       TEXT,
    snapshot      JSONB,
    created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_contract_rev_contract ON contract_revisions(contract_id);

-- 6. 权限资源：合同模板（新菜单）；合同入口沿用/补全 contract:view 等
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, c.code, c.name, c.rtype, ARRAY['view']::varchar[], c.path, 'active', now(), now()
FROM tenants t
CROSS JOIN (VALUES
    ('contract:view',            '合同工作台', 'menu', '/contracts'),
    ('contract_template:manage', '合同模板',   'menu', '/contracts/templates')
) AS c(code, name, rtype, path)
WHERE t.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM resources x WHERE x.tenant_id = t.id AND x.code = c.code AND x.deleted_at IS NULL);

-- 合同模板管理按钮权限
DO $$
DECLARE t_id UUID; bcode TEXT;
BEGIN
    FOR t_id IN SELECT id FROM tenants WHERE deleted_at IS NULL LOOP
        FOREACH bcode IN ARRAY ARRAY[
            'contract_template:search','contract_template:create','contract_template:edit',
            'contract_template:publish','contract_template:delete'
        ] LOOP
            IF NOT EXISTS (SELECT 1 FROM resources WHERE tenant_id=t_id AND code=bcode AND deleted_at IS NULL) THEN
                INSERT INTO resources (tenant_id, code, name, type, operations, status, created_at, updated_at)
                VALUES (t_id, bcode, bcode, 'button', ARRAY['read','write']::varchar[], 'active', now(), now());
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- 7. 将合同模板菜单权限授予已有合同查看权限的策略（保证原有角色可进入）
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT sr_base.strategy_id, r_tpl.id, ARRAY['view']::varchar[], now()
FROM resources r_tpl
JOIN resources r_seed ON r_seed.tenant_id = r_tpl.tenant_id AND r_seed.deleted_at IS NULL
JOIN strategy_resources sr_base ON sr_base.resource_id = r_seed.id
WHERE r_tpl.code = 'contract_template:manage' AND r_tpl.deleted_at IS NULL
  AND r_seed.code = 'contract:view'
  AND NOT EXISTS (SELECT 1 FROM strategy_resources x WHERE x.strategy_id = sr_base.strategy_id AND x.resource_id = r_tpl.id);

-- 8. 清理旧合同相关的 CRUD 平台按钮配置（前端不再走通用模块）
DELETE FROM platform_button_configs WHERE page_key IN ('contract-apps','contracts');