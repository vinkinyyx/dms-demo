import requests, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE='http://8.133.193.238:8081'
s=requests.Session()
r=s.post(BASE+'/api/admin/auth/login',json={'username':'admin','password':'Sh123456'},timeout=10)
print('login',r.status_code)
t=r.json()['data']['accessToken']
s.headers['Authorization']='Bearer '+t
eps=['/api/admin/tenants/stats','/api/admin/tenants/manufacturers?page=1&size=5','/api/admin/tenants/dealers?page=1&size=5','/api/admin/dicts/types','/api/admin/tenant-admins?page=1&size=5','/api/admin/role-templates']
for ep in eps:
 rr=s.get(BASE+ep,timeout=15)
 print(rr.status_code, ep, rr.text[:300].replace('\n',' '))
 if rr.status_code>=500: sys.exit(1)
