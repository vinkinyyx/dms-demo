-- V84: Ensure the super-admin (SYS_ADMIN) sees every list/row action button.
-- Some button permission codes were never granted to the all-permissions strategy,
-- so the frontend filtered out create/edit/delete buttons. Grant every button code
-- referenced by platform_button_configs to the super-admin strategy (id 1).

-- 1) Create missing button resources for every referenced permission_code.
INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at, version)
SELECT DISTINCT
       '11111111-1111-1111-1111-111111111111'::uuid,
       bc.permission_code,
       COALESCE(bc.label, bc.permission_code),
       'button',
       ARRAY['view','create','edit','delete','search','export','import','submit','approve','reject','cancel','confirm','manage','admin']::varchar[],
       NULL,
       'active',
       now(), now(), 0
FROM platform_button_configs bc
WHERE bc.permission_code IS NOT NULL
  AND bc.permission_code <> ''
  AND NOT EXISTS (
      SELECT 1 FROM resources r
      WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'::uuid
        AND r.code = bc.permission_code
        AND r.deleted_at IS NULL
  );

-- 2) Grant every such button resource to the super-admin strategy (id 1).
INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
SELECT 1, r.id,
       ARRAY['view','create','edit','delete','search','export','import','submit','approve','reject','cancel','confirm','manage','admin']::varchar[],
       now()
FROM resources r
WHERE r.tenant_id = '11111111-1111-1111-1111-111111111111'::uuid
  AND r.deleted_at IS NULL
  AND r.code IN (
      SELECT DISTINCT bc.permission_code
      FROM platform_button_configs bc
      WHERE bc.permission_code IS NOT NULL AND bc.permission_code <> ''
  )
  AND NOT EXISTS (
      SELECT 1 FROM strategy_resources sr
      WHERE sr.strategy_id = 1 AND sr.resource_id = r.id
  );