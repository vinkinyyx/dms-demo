-- Ensure v3.11 documented test accounts exist with the shared documented password.
-- bcrypt hash for Dms@123456
DO $$
DECLARE
    default_tenant uuid := '11111111-1111-1111-1111-111111111111';
    dealer_d00003_id bigint;
BEGIN
    SELECT id INTO dealer_d00003_id
    FROM dealers
    WHERE tenant_id = default_tenant
      AND (code = 'D00003' OR name LIKE '%D00003%')
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    CREATE TEMP TABLE v80_docs_accounts (
        username varchar(64),
        name varchar(64),
        role_code varchar(64),
        user_type varchar(16),
        dealer_bind boolean
    ) ON COMMIT DROP;

    INSERT INTO v80_docs_accounts VALUES
        ('sys_admin', '林管理员', 'SYS_ADMIN', 'vendor', false),
        ('sales_mgr', '赵销售经理', 'SALES_MGR', 'vendor', false),
        ('sales', '孙销售员', 'SALES', 'vendor', false),
        ('cs', '周客服', 'CS', 'vendor', false),
        ('biz', '吴商务', 'BIZ', 'vendor', false),
        ('fin', '郑财务', 'FIN', 'vendor', false),
        ('contract', '王合同专员', 'CONTRACT_SPEC', 'vendor', false),
        ('dealer_admin', '李经销商', 'DEALER_ADMIN', 'dealer', true);

    INSERT INTO users (
        tenant_id, username, name, user_type, password_hash, role, dealer_id,
        must_change_password, password_updated_at, email, status, login_fail_count,
        created_at, updated_at, version
    )
    SELECT default_tenant, a.username, a.name, a.user_type,
           '$2b$10$APCkQX5BAYln6P4G.LH8T.J4jcCKaPqMYHgXiIU1XMp6m9OZ9C/iS',
           a.role_code, CASE WHEN a.dealer_bind THEN dealer_d00003_id ELSE NULL END,
           false, now(), a.username || '@dms.test', 'active', 0, now(), now(), 0
    FROM v80_docs_accounts a
    WHERE NOT EXISTS (
        SELECT 1 FROM users u
        WHERE u.tenant_id = default_tenant AND u.username = a.username AND u.deleted_at IS NULL
    );

    UPDATE users u
       SET name = a.name,
           user_type = a.user_type,
           password_hash = '$2b$10$APCkQX5BAYln6P4G.LH8T.J4jcCKaPqMYHgXiIU1XMp6m9OZ9C/iS',
           role = a.role_code,
           dealer_id = CASE WHEN a.dealer_bind THEN dealer_d00003_id ELSE u.dealer_id END,
           must_change_password = false,
           password_updated_at = now(),
           status = 'active',
           login_fail_count = 0,
           locked_until = NULL,
           deleted_at = NULL,
           updated_at = now()
    FROM v80_docs_accounts a
    WHERE u.tenant_id = default_tenant AND u.username = a.username;

    INSERT INTO user_roles (user_id, role_id, granted_at)
    SELECT u.id, r.id, now()
    FROM users u
    JOIN v80_docs_accounts a ON a.username = u.username
    JOIN roles r ON r.tenant_id = u.tenant_id AND r.code = a.role_code AND r.deleted_at IS NULL
    WHERE u.tenant_id = default_tenant
      AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);
END $$;
