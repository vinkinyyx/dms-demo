from pathlib import Path
p=Path('tools/_prod_smoke.py')
s=p.read_text(encoding='utf-8')
s=s.replace("{'username':'admin','password':'admin123'}","{'username':'admin','password':'Sh123456'}")
s=s.replace("('reports-inventory','/api/reports/inventory?limit=5'),", "('reports-inventory-aging','/api/reports/inventory-aging?limit=5'),")
p.write_text(s, encoding='utf-8')
print('updated smoke script')
