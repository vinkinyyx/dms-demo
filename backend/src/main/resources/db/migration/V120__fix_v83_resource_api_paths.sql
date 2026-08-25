-- V120: 修正 V83 写入的资源 path 与后端 Controller @RequestMapping 不一致
-- V83 给 SYS_ADMIN 补授的部分 API path 使用了历史/前端路由名，与实际 Controller 路径不符，
-- 导致 Spring Security 按 path 做权限匹配时命不中真实接口。这里按 code 直接 UPDATE 修正：
--   position:view          /api/positions/**           -> /api/sales-positions/**
--   contract_template:manage /api/contracts/templates/** -> /api/contract-templates/**
--   tenant_ui_config:view  /api/tenant-page-configs/** -> /api/tenant-ui/**

UPDATE resources
SET path = '/api/sales-positions/**',
    updated_at = now()
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
  AND code = 'position:view'
  AND path = '/api/positions/**'
  AND deleted_at IS NULL;

UPDATE resources
SET path = '/api/contract-templates/**',
    updated_at = now()
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
  AND code = 'contract_template:manage'
  AND path = '/api/contracts/templates/**'
  AND deleted_at IS NULL;

UPDATE resources
SET path = '/api/tenant-ui/**',
    updated_at = now()
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
  AND code = 'tenant_ui_config:view'
  AND path = '/api/tenant-page-configs/**'
  AND deleted_at IS NULL;
