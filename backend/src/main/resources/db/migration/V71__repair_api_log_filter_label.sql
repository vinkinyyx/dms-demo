-- V71: repair API log filter label and status dictionary typo.
UPDATE platform_filter_configs
SET label = '状态', updated_at = now()
WHERE page_key = 'api-call-log'
  AND filter_key = 'status'
  AND tenant_type = 'ALL';

UPDATE tenant_filter_configs
SET label = '状态', updated_at = now()
WHERE page_key = 'api-call-log'
  AND filter_key = 'status';

UPDATE dict_items
SET name = '500 服务器错误'
WHERE code = '500'
  AND type_id IN (SELECT id FROM dict_types WHERE code = 'api_call_status');

UPDATE platform_button_configs
SET visible = FALSE, status = 'inactive', updated_at = now()
WHERE page_key = 'api-call-log'
  AND scope = 'toolbar'
  AND button_key = 'export';
