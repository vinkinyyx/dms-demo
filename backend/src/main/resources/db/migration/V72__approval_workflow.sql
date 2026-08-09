-- Rename legacy empty approval tables (old schema, superseded by this workflow)
ALTER TABLE IF EXISTS approval_tasks   RENAME TO approval_tasks_legacy;
ALTER TABLE IF EXISTS approval_history RENAME TO approval_history_legacy;
CREATE TABLE IF NOT EXISTS approval_templates (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    template_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    priority INTEGER NOT NULL DEFAULT 100,
    reject_policy VARCHAR(32) NOT NULL DEFAULT 'RETURN_TO_SUBMITTER',
    condition_config JSONB,
    timeout_hours INTEGER,
    remind_interval_hours INTEGER,
    max_remind_count INTEGER DEFAULT 0,
    description VARCHAR(500),
    published_at TIMESTAMPTZ,
    published_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT,
    updated_by BIGINT,
    version INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS approval_template_nodes (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES approval_templates(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    node_order INTEGER NOT NULL,
    name VARCHAR(200) NOT NULL,
    approve_mode VARCHAR(16) NOT NULL DEFAULT 'ANY',
    allow_transfer BOOLEAN NOT NULL DEFAULT TRUE,
    allow_add_sign BOOLEAN NOT NULL DEFAULT TRUE,
    timeout_hours INTEGER,
    remind_interval_hours INTEGER,
    max_remind_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS approval_node_assignees (
    id BIGSERIAL PRIMARY KEY,
    node_id BIGINT NOT NULL REFERENCES approval_template_nodes(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    assignee_type VARCHAR(16) NOT NULL,
    ref_id BIGINT NOT NULL,
    display_name VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS approval_template_ccs (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES approval_templates(id) ON DELETE CASCADE,
    node_id BIGINT REFERENCES approval_template_nodes(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    cc_type VARCHAR(16) NOT NULL,
    ref_id BIGINT NOT NULL,
    display_name VARCHAR(200),
    cc_stage VARCHAR(32) NOT NULL DEFAULT 'AFTER_FINISH',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS approval_instances (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    template_id BIGINT REFERENCES approval_templates(id),
    template_version_no INTEGER,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    business_code VARCHAR(64),
    title VARCHAR(300) NOT NULL,
    submitter_id BIGINT NOT NULL,
    submitter_name VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    current_node_id BIGINT,
    current_node_name VARCHAR(200),
    reject_policy VARCHAR(32) NOT NULL DEFAULT 'RETURN_TO_SUBMITTER',
    template_snapshot JSONB,
    business_snapshot JSONB,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS approval_tasks (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES approval_instances(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    node_id BIGINT,
    node_name VARCHAR(200),
    assignee_id BIGINT NOT NULL,
    assignee_name VARCHAR(64),
    original_assignee_id BIGINT,
    delegated_from_user_id BIGINT,
    task_type VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    parent_task_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    approve_mode VARCHAR(16) NOT NULL DEFAULT 'ANY',
    comment VARCHAR(1000),
    due_at TIMESTAMPTZ,
    reminded_count INTEGER NOT NULL DEFAULT 0,
    last_reminded_at TIMESTAMPTZ,
    handled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS approval_records (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES approval_instances(id) ON DELETE CASCADE,
    task_id BIGINT,
    tenant_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    node_id BIGINT,
    node_name VARCHAR(200),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    comment VARCHAR(1000),
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS approval_cc_records (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES approval_instances(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    stage VARCHAR(32) NOT NULL,
    node_id BIGINT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS approval_delegations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delegator_id BIGINT NOT NULL,
    delegatee_id BIGINT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_templates_tenant_biz_code_version ON approval_templates(tenant_id, business_type, code, version_no);
CREATE INDEX IF NOT EXISTS idx_approval_templates_match ON approval_templates(tenant_id, business_type, status, priority DESC, version_no DESC);
CREATE INDEX IF NOT EXISTS idx_approval_nodes_template ON approval_template_nodes(template_id, node_order);
CREATE INDEX IF NOT EXISTS idx_approval_node_assignees_node ON approval_node_assignees(node_id);
CREATE INDEX IF NOT EXISTS idx_approval_template_ccs_template ON approval_template_ccs(template_id);
CREATE INDEX IF NOT EXISTS idx_approval_instances_biz ON approval_instances(tenant_id, business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_approval_instances_submitter ON approval_instances(tenant_id, submitter_id, status);
CREATE INDEX IF NOT EXISTS idx_approval_tasks_assignee_status ON approval_tasks(tenant_id, assignee_id, status, due_at);
CREATE INDEX IF NOT EXISTS idx_approval_tasks_instance ON approval_tasks(instance_id, status);
CREATE INDEX IF NOT EXISTS idx_approval_records_instance ON approval_records(instance_id, created_at);
CREATE INDEX IF NOT EXISTS idx_approval_cc_records_user ON approval_cc_records(tenant_id, user_id, read_at);
CREATE INDEX IF NOT EXISTS idx_approval_delegations_active ON approval_delegations(tenant_id, delegator_id, status, starts_at, ends_at);
