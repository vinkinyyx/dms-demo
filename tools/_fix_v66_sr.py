from pathlib import Path
p=Path('backend/src/main/resources/db/migration/V66__tenant_filter_override_and_layout_fixes.sql')
s=p.read_text(encoding='utf-8')
s=s.replace('INSERT INTO strategy_resources (strategy_id, resource_id)\nSELECT DISTINCT sr.strategy_id, r_new.id', "INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)\nSELECT DISTINCT sr.strategy_id, r_new.id, ARRAY['view']::varchar[], now()")
p.write_text(s, encoding='utf-8', newline='\n')
