import requests, json, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE='http://8.133.193.238:8082'
s=requests.Session()
t=s.post(BASE+'/api/auth/login', json={'tenantCode':'default','username':'admin','password':'Sh123456'}, timeout=20).json()['data']['accessToken']
h={'Authorization':'Bearer '+t,'Content-Type':'application/json'}
payload=[{ 'filterKey':'keyword','label':'关键词','componentType':'input','multiple':False,'visible':False,'sortOrder':10}]
r=requests.post(BASE+'/api/tenant-ui/pages/orders/filters', headers=h, json=payload, timeout=20)
print(r.status_code)
print(r.text[:2000])
