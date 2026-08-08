from pathlib import Path
p=Path('backend/src/main/resources/db/migration/V66__tenant_filter_override_and_layout_fixes.sql')
s=p.read_text(encoding='utf-8')
s=s.replace("'[\"view\"]'::jsonb", "ARRAY['view']::varchar[]")
# The append already used jsonb too via PowerShell content displayed; replace all.
s=s.replace("'[\"view\"]'::jsonb", "ARRAY['view']::varchar[]")
p.write_text(s, encoding='utf-8', newline='\n')
