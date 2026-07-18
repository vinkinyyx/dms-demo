-- =====================================================================
-- V1: 平台租户 & 用户权限（RBAC）初始化
-- 说明：
--   A. 平台租户：tenants, tenant_modules
--   B. 用户与权限：org_units, users, resources, strategies,
--                  strategy_resources, roles, role_strategies,
--                  user_roles, data_scopes, user_login_logs
-- 兼容：Flyway 8.x + PostgreSQL 14
-- 通用字段：tenant_id / created_at / updated_at / version / deleted_at
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------
-- A. 平台租户
-- ---------------------------------------------------------------------
CREATE TABLE tenants (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(32) UNIQUE NOT NULL,
    name             VARCHAR(200) NOT NULL,
    industry         VARCHAR(32)  NOT NULL,
    timezone         VARCHAR(64)  DEFAULT 'Asia/Shanghai',
    logo_url         TEXT,
    status           VARCHAR(16)  DEFAULT 'active',
    modules_enabled  JSONB        DEFAULT '{}'::jsonb,
    quota            JSONB        DEFAULT '{}'::jsonb,
    attrs            JSONB        DEFAULT '{}'::jsonb,
    contact_name     VARCHAR(64),
    contact_email    VARCHAR(128),
    contact_phone    VARCHAR(32),
    effective_from   DATE,
    effective_to     DATE,
    created_at       TIMESTAMPTZ DEFAULT now(),
    updated_at       TIMESTAMPTZ DEFAULT now(),
    created_by       BIGINT,
    updated_by       BIGINT,
    version          INT DEFAULT 0,
    deleted_at       TIMESTAMPTZ
);
COMMENT ON COLUMN tenants.attrs IS 'attrs.primary_color 用于前端主题注入';

CREATE TABLE tenant_modules (
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    module_code  VARCHAR(32) NOT NULL,
    enabled      BOOLEAN DEFAULT true,
    config       JSONB   DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (tenant_id, module_code)
);

-- ---------------------------------------------------------------------
-- B. 用户与权限
-- ---------------------------------------------------------------------
CREATE TABLE org_units (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    parent_id   BIGINT REFERENCES org_units(id),
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    level       INT          NOT NULL,
    path        VARCHAR(500),
    type        VARCHAR(32),
    status      VARCHAR(16)  DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_org_units_tenant ON org_units(tenant_id);
CREATE INDEX idx_org_units_parent ON org_units(parent_id);

CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID        NOT NULL,
    username               VARCHAR(64) NOT NULL,
    name                   VARCHAR(64) NOT NULL,
    user_type              VARCHAR(16) NOT NULL,   -- vendor / dealer
    password_hash          VARCHAR(255) NOT NULL,
    must_change_password   BOOLEAN     DEFAULT true,
    password_updated_at    TIMESTAMPTZ,
    email                  VARCHAR(128),
    phone                  VARCHAR(32),
    org_id                 BIGINT,
    dealer_id              BIGINT,
    status                 VARCHAR(16) DEFAULT 'active',
    login_fail_count       INT         DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    last_login_ip          VARCHAR(64),
    attrs                  JSONB       DEFAULT '{}'::jsonb,
    wechat_openid          VARCHAR(64),
    wechat_unionid         VARCHAR(64),
    wechat_bound_at        TIMESTAMPTZ,
    sso_service_id         VARCHAR(64),           -- V1 预留字段
    created_at             TIMESTAMPTZ DEFAULT now(),
    updated_at             TIMESTAMPTZ DEFAULT now(),
    created_by             BIGINT,
    updated_by             BIGINT,
    version                INT DEFAULT 0,
    deleted_at             TIMESTAMPTZ,
    UNIQUE (tenant_id, username)
);
CREATE UNIQUE INDEX ux_users_wechat_openid
    ON users(wechat_openid) WHERE wechat_openid IS NOT NULL;
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_org ON users(org_id);
CREATE INDEX idx_users_dealer ON users(dealer_id);

CREATE TABLE resources (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(16)  NOT NULL,   -- menu / api / button
    parent_id   BIGINT REFERENCES resources(id),
    operations  VARCHAR(200)[] DEFAULT ARRAY[]::VARCHAR[],
    path        VARCHAR(500),
    status      VARCHAR(16)  DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_resources_parent ON resources(parent_id);

CREATE TABLE strategies (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(16)  DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_strategies_tenant ON strategies(tenant_id);

CREATE TABLE strategy_resources (
    strategy_id  BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    resource_id  BIGINT NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    operations   VARCHAR(200)[] NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (strategy_id, resource_id)
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(16)  DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, code)
);

CREATE TABLE role_strategies (
    role_id      BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    strategy_id  BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (role_id, strategy_id)
);

CREATE TABLE user_roles (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ DEFAULT now(),
    granted_by  BIGINT,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

CREATE TABLE data_scopes (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID   NOT NULL,
    user_id     BIGINT NOT NULL,
    scope_type  VARCHAR(16) NOT NULL,   -- ALL / ORG / SELF / CUSTOM
    org_ids     BIGINT[] DEFAULT ARRAY[]::BIGINT[],
    dealer_ids  BIGINT[] DEFAULT ARRAY[]::BIGINT[],
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    version     INT DEFAULT 0,
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_data_scopes_user ON data_scopes(tenant_id, user_id);

CREATE TABLE user_login_logs (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID,
    user_id      BIGINT,
    login_type   VARCHAR(16),   -- V1: PASSWORD / WECHAT / REMEMBER
    ip           VARCHAR(64),
    user_agent   TEXT,
    success      BOOLEAN,
    fail_reason  VARCHAR(200),
    at_time      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_login_logs_user ON user_login_logs(user_id, at_time DESC);
CREATE INDEX idx_login_logs_tenant ON user_login_logs(tenant_id, at_time DESC);
