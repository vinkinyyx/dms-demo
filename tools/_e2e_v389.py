import requests, json, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE='http://8.133.193.238:8082'
s=requests.Session()
r=s.post(BASE+'/api/auth/login', json={'tenantCode':'default','username':'admin','password':'Sh123456'}, timeout=20)
r.raise_for_status()
t=r.json()['data']['accessToken']
h={'Authorization':'Bearer '+t}
print('health', requests.get(BASE+'/actuator/health', timeout=10).json())
perms=requests.get(BASE+'/api/me/permissions', headers=h, timeout=10).json()['data']
print('permissions', len(perms), 'tenant_ui_config:view' in perms, 'sales_order:reject' in perms, 'sales_order:cancel' in perms)
for k in ['orders','dealer-profile']:
    d=requests.get(BASE+f'/api/ui/layout/{k}', headers=h, timeout=10).json()['data']
    print('\n==',k,'==')
    print('toolbar', [(b['buttonKey'],b['label'],b.get('visible')) for b in d['toolbar']])
    print('row', [(b['buttonKey'],b['label'],b.get('permissionCode')) for b in d['rowButtons']])
    print('filters', [(f['filterKey'],f['label']) for f in d['filters']])
roles=requests.get(BASE+'/api/roles', headers=h, timeout=10).json()['data']
print('\nroles sample', [(r['id'],r['code'],r['name']) for r in roles[:5]])
rid=roles[0]['id']
rp=requests.get(BASE+f'/api/roles/{rid}/permissions', headers=h, timeout=10).json()['data']
print('role permissions', rid, len(rp.get('resources') or []), len(rp.get('selectedCodes') or []))
# tenant filter save/load roundtrip
payload=[{ 'filterKey':'keyword','label':'关键词','componentType':'input','multiple':False,'visible':False,'sortOrder':10},
         { 'filterKey':'status','label':'状态','componentType':'select','dictType':'sales_order_status','multiple':False,'visible':True,'sortOrder':20}]
saved=requests.post(BASE+'/api/tenant-ui/pages/orders/filters', headers=h, json=payload, timeout=10).json()
print('tenant filters override saved', [(f['filterKey'],f['label'],f['visible']) for f in saved['data']])
# reset to all visible
reset=[{**f,'visible':True} for f in requests.get(BASE+'/api/tenant-ui/pages/orders/filters', headers=h, timeout=10).json()['data']]
requests.post(BASE+'/api/tenant-ui/pages/orders/filters', headers=h, json=reset, timeout=10)
print('tenant filters reset ok')
