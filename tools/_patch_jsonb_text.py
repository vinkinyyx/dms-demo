from pathlib import Path
root=Path(r'D:\Workspace\TRAE\DMS')
for rel in [r'backend\src\main\resources\db\migration\V55__realistic_demo_profile_data.sql', r'backend\src\main\resources\db\migration\V57__complete_profile_demo_data.sql']:
    p=root/rel; s=p.read_text(encoding='utf-8')
    s=s.replace("to_jsonb(CASE WHEN d.level='T1' THEN 'T3' WHEN d.level='T2' THEN 'T2' ELSE 'T1' END)", "to_jsonb(CASE WHEN d.level='T1' THEN text 'T3' WHEN d.level='T2' THEN text 'T2' ELSE text 'T1' END)")
    s=s.replace("to_jsonb('T1'::text)", "to_jsonb(text 'T1')")
    p.write_text(s, encoding='utf-8', newline='\n')
    print('patched', p)
