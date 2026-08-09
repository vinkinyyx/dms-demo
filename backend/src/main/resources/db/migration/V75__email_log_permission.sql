-- V75: 邮件日志权限
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at)
SELECT t.id, 'email_log:view', '邮件发送日志', 'menu', ARRAY['view']::varchar[], '/email-logs', 'active', now(), now()
FROM tenants t
WHERE NOT EXISTS (
  SELECT 1 FROM resources x WHERE x.tenant_id=t.id AND x.code='email_log:view' AND x.deleted_at IS NULL
);

INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT DISTINCT sr_base.strategy_id, r_new.id, ARRAY['view']::varchar[], now()
FROM resources r_new
JOIN resources r_seed ON r_seed.tenant_id=r_new.tenant_id AND r_seed.code='tenant_ui_config:view' AND r_seed.deleted_at IS NULL
JOIN strategy_resources sr_base ON sr_base.resource_id=r_seed.id
WHERE r_new.code='email_log:view' AND r_new.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM strategy_resources x WHERE x.strategy_id=sr_base.strategy_id AND x.resource_id=r_new.id);