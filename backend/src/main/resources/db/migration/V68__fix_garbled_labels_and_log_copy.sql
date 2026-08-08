-- V68: repair labels damaged by earlier encoding mistakes.
-- Platform defaults and existing tenant overrides are both normalized by stable button_key,
-- preserving tenant-level visibility/sort overrides while restoring readable labels.
UPDATE platform_button_configs
SET label = CASE button_key
    WHEN 'search' THEN '??'
    WHEN 'reset' THEN '??'
    WHEN 'import' THEN '??'
    WHEN 'export' THEN '??'
    WHEN 'create' THEN '??'
    WHEN 'view' THEN '??'
    WHEN 'edit' THEN '??'
    WHEN 'delete' THEN '??'
    WHEN 'submit' THEN '??'
    WHEN 'approve' THEN '??'
    WHEN 'reject' THEN '??'
    WHEN 'cancel' THEN '??'
    WHEN 'confirm' THEN '??'
    WHEN 'open' THEN '??'
    ELSE label
END,
updated_at = now()
WHERE scope IN ('toolbar', 'row')
  AND (
    button_key IN ('search','reset','import','export','create','view','edit','delete','submit','approve','reject','cancel','confirm','open')
  );

UPDATE platform_button_configs
SET label = '????', visible = TRUE, status = 'active', updated_at = now()
WHERE page_key = 'dealer-profile' AND scope = 'row' AND button_key = 'view';

UPDATE dict_items
SET name = CASE code
    WHEN '200' THEN '200 ??'
    WHEN '400' THEN '400 ????'
    WHEN '401' THEN '401 ???'
    WHEN '403' THEN '403 ????'
    WHEN '404' THEN '404 ???'
    WHEN '500' THEN '500 ?????'
    ELSE name
END
WHERE type_id IN (SELECT id FROM dict_types WHERE code = 'api_call_status')
  AND code IN ('200','400','401','403','404','500');
