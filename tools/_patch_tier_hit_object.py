from pathlib import Path
root=Path(r'D:\Workspace\TRAE\DMS')
p=root/r'backend\src\main\resources\db\migration\V55__realistic_demo_profile_data.sql'
s=p.read_text(encoding='utf-8')
s=s.replace("to_jsonb(CASE WHEN d.level='T1' THEN text 'T3' WHEN d.level='T2' THEN text 'T2' ELSE text 'T1' END)", "jsonb_build_object('tier', CASE WHEN d.level='T1' THEN 'T3' WHEN d.level='T2' THEN 'T2' ELSE 'T1' END)")
p.write_text(s, encoding='utf-8', newline='\n')
p=root/r'backend\src\main\resources\db\migration\V57__complete_profile_demo_data.sql'
s=p.read_text(encoding='utf-8')
s=s.replace("to_jsonb(text 'T1')", "jsonb_build_object('tier', 'T1')")
s=s.replace("tier_hit = n.tier_hit::jsonb,", "tier_hit = jsonb_build_object('tier', n.tier_hit),")
p.write_text(s, encoding='utf-8', newline='\n')
print('patched tier_hit to object jsonb')
