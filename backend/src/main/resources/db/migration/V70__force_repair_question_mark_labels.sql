-- V70: force repair ASCII question-mark labels produced by earlier encoding failures.
UPDATE platform_button_configs
SET label = CASE button_key
    WHEN 'search' THEN '查询'
    WHEN 'reset' THEN '重置'
    WHEN 'import' THEN '导入'
    WHEN 'export' THEN '导出'
    WHEN 'create' THEN '新增'
    WHEN 'view' THEN '查看'
    WHEN 'edit' THEN '编辑'
    WHEN 'delete' THEN '删除'
    WHEN 'submit' THEN '提交'
    WHEN 'approve' THEN '审批'
    WHEN 'reject' THEN '驱回'
    WHEN 'cancel' THEN '取消'
    WHEN 'confirm' THEN '确认'
    WHEN 'open' THEN '打开'
    ELSE label
END,
updated_at = now()
WHERE scope IN ('toolbar', 'row')
  AND button_key IN ('search','reset','import','export','create','view','edit','delete','submit','approve','reject','cancel','confirm','open')
  AND label ~ '^[?]+$';

UPDATE platform_button_configs
SET label = '查看画像', visible = TRUE, status = 'active', updated_at = now()
WHERE page_key = 'dealer-profile'
  AND scope = 'row'
  AND button_key = 'view';

UPDATE tenant_filter_configs
SET label = '状态', updated_at = now()
WHERE page_key = 'api-call-log'
  AND filter_key = 'status';

UPDATE dict_items
SET name = CASE code
    WHEN '200' THEN '200 成功'
    WHEN '400' THEN '400 请求错误'
    WHEN '401' THEN '401 未认证'
    WHEN '403' THEN '403 无权限'
    WHEN '404' THEN '404 不存在'
    WHEN '500' THEN '500 服务器器错误'
    ELSE name
END
WHERE type_id IN (SELECT id FROM dict_types WHERE code = 'api_call_status')
  AND code IN ('200','400','401','403','404','500');
