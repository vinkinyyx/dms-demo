-- V81: seed missing role-management API resource and grant it to strategies that manage users/roles.
-- The RBAC controller /api/roles/** requires button permissions (role:view/search/...), which are
-- derived by PermissionQueryService from a resource whose path is /api/roles/**.
INSERT INTO resources (tenant_id, code, name, type, path, created_at, updated_at)
SELECT '11111111-1111-1111-1111-111111111111', 'api.role', '角色 API', 'api', '/api/roles/**', now(), now()
WHERE NOT EXISTS (
    SELECT 1 FROM resources WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND code = 'api.role'
);

INSERT INTO strategy_resources (strategy_id, resource_id, operations)
SELECT s.id, r.id, ARRAY['call']
FROM strategies s
JOIN resources r ON r.tenant_id = s.tenant_id AND r.code = 'api.role'
WHERE s.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND EXISTS (
      SELECT 1 FROM strategy_resources sr
      JOIN resources ru ON ru.id = sr.resource_id
      WHERE sr.strategy_id = s.id AND ru.code IN ('api.user', 'menu.role')
  )
  AND NOT EXISTS (
      SELECT 1 FROM strategy_resources sr WHERE sr.strategy_id = s.id AND sr.resource_id = r.id
  );