from pathlib import Path
for rel in [r'backend\src\main\resources\db\migration\V55__realistic_demo_profile_data.sql', r'backend\src\main\resources\db\migration\V57__complete_profile_demo_data.sql']:
    p = Path(rel)
    s = p.read_text(encoding='utf-8')
    s = s.replace("  CASE WHEN d.level='T1' THEN 'T3' WHEN d.level='T2' THEN 'T2' ELSE 'T1' END,\n  0, '', 0, now()", "  to_jsonb(CASE WHEN d.level='T1' THEN 'T3' WHEN d.level='T2' THEN 'T2' ELSE 'T1' END),\n  0, '', 0, now()")
    s = s.replace("    0, 'T1', 0, '', 0, now(), now(), now(), 0", "    0, to_jsonb('T1'::text), 0, '', 0, now(), now(), now(), 0")
    s = s.replace("    tier_hit = n.tier_hit,", "    tier_hit = n.tier_hit::jsonb,")
    p.write_text(s, encoding='utf-8', newline='\n')
    print('patched', rel)
