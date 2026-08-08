from pathlib import Path
ch=lambda *codes: ''.join(chr(c) for c in codes)
content=f"""-- V71: repair API log filter label and status dictionary typo.
UPDATE platform_filter_configs
SET label = '{ch(29366,24577)}', updated_at = now()
WHERE page_key = 'api-call-log'
  AND filter_key = 'status'
  AND tenant_type = 'ALL';

UPDATE tenant_filter_configs
SET label = '{ch(29366,24577)}', updated_at = now()
WHERE page_key = 'api-call-log'
  AND filter_key = 'status';

UPDATE dict_items
SET name = '500 {ch(26381,21153,22120,38169,35823)}'
WHERE code = '500'
  AND type_id IN (SELECT id FROM dict_types WHERE code = 'api_call_status');
"""
Path('backend/src/main/resources/db/migration/V71__repair_api_log_filter_label.sql').write_text(content, encoding='utf-8', newline='\n')
print(Path('backend/src/main/resources/db/migration/V71__repair_api_log_filter_label.sql').read_bytes())
