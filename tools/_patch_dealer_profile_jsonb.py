from pathlib import Path
p=Path(r'D:\Workspace\TRAE\DMS\backend\src\main\java\com\dms\report\service\DealerProfileService.java')
s=p.read_text(encoding='utf-8')
old=" gross_rebate, COALESCE(CAST(NULLIF(regexp_replace(deductions, '[^0-9.]', '', 'g'), '') AS numeric), 0) AS deduction_amount, "
new=" gross_rebate, COALESCE(CAST(NULLIF(regexp_replace(COALESCE(deductions->>'amount', deductions::text, ''), '[^0-9.]', '', 'g'), '') AS numeric), 0) AS deduction_amount, "
if old not in s:
    raise SystemExit('target snippet not found')
p.write_text(s.replace(old,new), encoding='utf-8', newline='\n')
print('patched DealerProfileService')
