import requests, time, sys, json
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE='http://8.133.193.238:8082'
FRONT='http://8.133.193.238:8083'

def wait_health():
    last=''
    for _ in range(60):
        try:
            r=requests.get(BASE+'/actuator/health', timeout=3)
            last=f'{r.status_code} {r.text[:200]}'
            if r.ok and '"UP"' in r.text:
                print('health', r.text)
                return
        except Exception as e:
            last=repr(e)
        time.sleep(2)
    raise SystemExit('backend not healthy: '+last)

def req(method, path, **kwargs):
    r=requests.request(method, BASE+path, timeout=20, **kwargs)
    if r.status_code >= 400:
        raise AssertionError(f'{method} {path} -> {r.status_code}: {r.text[:1000]}')
    return r.json()

wait_health()
front=requests.get(FRONT+'/', timeout=10)
print('frontend', front.status_code, 'has assets', '/assets/' in front.text)
s=requests.Session()
login=req('POST','/api/auth/login', json={'tenantCode':'default','username':'admin','password':'Sh123456'})
token=login['data']['accessToken']
h={'Authorization':'Bearer '+token}

perms=req('GET','/api/me/permissions', headers=h)['data']
for code in ['tenant_ui_config:view','sales_order:reject','sales_order:cancel','dashboard:view','products:view','api_log:view','report_sales_ranking:view']:
    assert code in perms, f'missing permission {code}'
print('permissions', len(perms), 'required codes present')

orders=req('GET','/api/ui/layout/orders', headers=h)['data']
print('orders toolbar', [(b['buttonKey'],b.get('visible')) for b in orders['toolbar']])
print('orders row count', len(orders['rowButtons']), [b['buttonKey'] for b in orders['rowButtons']])
assert len(orders['filters']) >= 4
assert any(b['buttonKey']=='view' for b in orders['rowButtons'])
assert any(b['buttonKey']=='reject' for b in orders['rowButtons'])
assert any(b['buttonKey']=='cancel' for b in orders['rowButtons'])

profile=req('GET','/api/ui/layout/dealer-profile', headers=h)['data']
print('dealer-profile toolbar', [(b['buttonKey'],b.get('visible')) for b in profile['toolbar']])
print('dealer-profile row', [(b['buttonKey'],b['label']) for b in profile['rowButtons']])
view_buttons=[b for b in profile['rowButtons'] if b['buttonKey']=='view']
assert len(view_buttons)==1 and view_buttons[0]['visible'] is True
assert next(b for b in profile['toolbar'] if b['buttonKey']=='search')['visible'] is True
assert next(b for b in profile['toolbar'] if b['buttonKey']=='reset')['visible'] is True
assert all(b['buttonKey'] not in ('import','export','create') for b in profile['toolbar'])
assert [b['buttonKey'] for b in profile['toolbar']] == ['search','reset']


# v3.8.10 regression checks: dashboard menu, dealer profile tabs, API log page.
for code in ['dashboard:view','products:view','api_log:view']:
    assert code in perms, f'missing permission {code}'
dash=requests.get(FRONT+'/dashboard', timeout=10)
assert dash.status_code == 200
api_layout=req('GET','/api/ui/layout/api-call-log', headers=h)['data']
assert any(f['filterKey']=='status' for f in api_layout['filters'])
assert any(b['buttonKey']=='view' for b in api_layout['rowButtons'])
logs=req('GET','/api/api-call-logs?page=1&size=5', headers=h)['data']
assert 'list' in logs
dealers=req('GET','/api/dealers?page=1&size=1', headers=h)['data']
dealer_rows=dealers.get('records') or dealers.get('list') or dealers.get('rows') or []
if dealer_rows:
    did=dealer_rows[0]['id']
    for tab in ['basic','kpi','achievement','rebate','contracts','inventory']:
        tab_res=req('GET',f'/api/dealer-profile/{did}/{tab}', headers=h)
        assert tab_res.get('success') is not False, tab_res
    print('dealer profile tabs ok', did)
print('dashboard and api log ok')

# Tenant filter override: hide keyword, save twice to prove idempotency, then restore.
filters=req('GET','/api/tenant-ui/pages/orders/filters', headers=h)['data']
hidden=[{**f,'visible': f['filterKey']!='keyword'} for f in filters]
for _ in range(2):
    saved=req('POST','/api/tenant-ui/pages/orders/filters', headers=h, json=hidden)['data']
keyword=next(f for f in saved if f['filterKey']=='keyword')
assert keyword['visible'] is False, keyword
orders_after=req('GET','/api/ui/layout/orders', headers=h)['data']
assert next(f for f in orders_after['filters'] if f['filterKey']=='keyword')['visible'] is False
restored=[{**f,'visible':True} for f in req('GET','/api/tenant-ui/pages/orders/filters', headers=h)['data']]
req('POST','/api/tenant-ui/pages/orders/filters', headers=h, json=restored)
print('tenant filters idempotent save ok; hidden/restored keyword')

# Tenant button override: hide create, ensure search/reset remain visible, twice, then restore.
layout=req('GET','/api/ui/layout/orders', headers=h)['data']
buttons=[{k:v for k,v in b.items() if k not in ('id','fromTenant','status')} for b in [*layout['toolbar'], *[{**b, 'scope':'row'} for b in layout['rowButtons']]]]
for b in buttons:
    if b['scope']=='toolbar' and b['buttonKey']=='create': b['visible']=False
for _ in range(2):
    saved_buttons=req('POST','/api/tenant-ui/pages/orders/buttons', headers=h, json={'buttons':buttons})['data']
assert next(b for b in saved_buttons if b['scope']=='toolbar' and b['buttonKey']=='search')['visible'] is True
assert next(b for b in saved_buttons if b['scope']=='toolbar' and b['buttonKey']=='reset')['visible'] is True
assert next(b for b in saved_buttons if b['scope']=='toolbar' and b['buttonKey']=='create')['visible'] is False
orders_buttons=req('GET','/api/ui/layout/orders', headers=h)['data']
assert next(b for b in orders_buttons['toolbar'] if b['buttonKey']=='create')['visible'] is False
for b in buttons:
    if b['scope']=='toolbar' and b['buttonKey']=='create': b['visible']=True
req('POST','/api/tenant-ui/pages/orders/buttons', headers=h, json={'buttons':buttons})
print('tenant buttons idempotent save ok; create hidden/restored; search/reset forced visible')

# Role permissions update and rollback.
roles=req('GET','/api/roles', headers=h)['data']
role=next((r for r in roles if r.get('code')=='admin' or r.get('name')=='管理员'), roles[0])
rid=role['id']
before=req('GET',f'/api/roles/{rid}/permissions', headers=h)['data']
original=set(before['selectedCodes'])
resources=before['resources']
optional=next((r['code'] for r in resources if r['code'] not in original and r['type'] in ('menu','button','api')), None)
removed=next((c for c in original if c not in ('tenant_ui_config:view','role:view','user:view')), None)
trial=set(original)
if optional: trial.add(optional)
if removed: trial.discard(removed)
req('PUT',f'/api/roles/{rid}/permissions', headers=h, json={'resourceCodes':sorted(trial)})
after=set(req('GET',f'/api/roles/{rid}/permissions', headers=h)['data']['selectedCodes'])
assert after == trial, (sorted(after), sorted(trial))
req('PUT',f'/api/roles/{rid}/permissions', headers=h, json={'resourceCodes':sorted(original)})
rolled=set(req('GET',f'/api/roles/{rid}/permissions', headers=h)['data']['selectedCodes'])
assert rolled == original
print('role permissions update/rollback ok', rid, 'codes', len(original))
print('E2E PASS')