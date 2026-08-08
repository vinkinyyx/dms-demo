from pathlib import Path
root=Path(r'D:\Workspace\TRAE\DMS')
# V55 initial empty deductions
p=root/r'backend\src\main\resources\db\migration\V55__realistic_demo_profile_data.sql'
s=p.read_text(encoding='utf-8')
s=s.replace("  0, '', 0, now(), now(), now(), 0\nFROM dealers d", "  0, '{}'::jsonb, 0, now(), now(), now(), 0\nFROM dealers d")
s=s.replace("  deductions=CASE WHEN id % 4 = 0 THEN '返利扣减:¥' || ROUND(gross_rebate*0.08,2) WHEN id % 4 = 1 THEN '价保扣减:¥' || ROUND(gross_rebate*0.05,2) WHEN id % 4 = 2 THEN '窜货扣减:¥' || ROUND(gross_rebate*0.03,2) ELSE '促销补差:¥' || ROUND(gross_rebate*0.02,2) END,", "  deductions=jsonb_build_object('reason', CASE WHEN id % 4 = 0 THEN '返利扣减' WHEN id % 4 = 1 THEN '价保扣减' WHEN id % 4 = 2 THEN '窜货扣减' ELSE '促销补差' END, 'amount', CASE WHEN id % 4 = 0 THEN ROUND(gross_rebate*0.08,2) WHEN id % 4 = 1 THEN ROUND(gross_rebate*0.05,2) WHEN id % 4 = 2 THEN ROUND(gross_rebate*0.03,2) ELSE ROUND(gross_rebate*0.02,2) END),")
p.write_text(s, encoding='utf-8', newline='\n')
# V57 initial empty deductions
p=root/r'backend\src\main\resources\db\migration\V57__complete_profile_demo_data.sql'
s=p.read_text(encoding='utf-8')
s=s.replace("0, jsonb_build_object('tier', 'T1'), 0, '', 0, now()", "0, jsonb_build_object('tier', 'T1'), 0, '{}'::jsonb, 0, now()")
s=s.replace("    deductions = CASE (rp.id % 5)\n      WHEN 0 THEN '返利扣减:¥' WHEN 1 THEN '价保扣减:¥' WHEN 2 THEN '窜货扣减:¥' WHEN 3 THEN '促销补差:¥' ELSE '考核扣减:¥'\n    END || ROUND(rp.actual_amount * n.rebate_rate * n.deduction_rate, 2),", "    deductions = jsonb_build_object('reason', CASE (rp.id % 5) WHEN 0 THEN '返利扣减' WHEN 1 THEN '价保扣减' WHEN 2 THEN '窜货扣减' WHEN 3 THEN '促销补差' ELSE '考核扣减' END, 'amount', ROUND(rp.actual_amount * n.rebate_rate * n.deduction_rate, 2)),")
p.write_text(s, encoding='utf-8', newline='\n')
print('patched deductions jsonb')
