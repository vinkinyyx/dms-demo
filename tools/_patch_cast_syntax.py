from pathlib import Path
p=Path(r'D:\Workspace\TRAE\DMS\backend\src\main\java\com\dms\report\service\DealerProfileService.java')
s=p.read_text(encoding='utf-8')
s=s.replace("deductions::text", "CAST(deductions AS text)")
p.write_text(s, encoding='utf-8', newline='\n')
print('patched cast syntax')
