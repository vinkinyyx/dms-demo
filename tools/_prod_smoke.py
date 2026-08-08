import requests, json, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE='http://8.133.193.238:8081'
s=requests.Session()
results=[]
def check(name, method, path, token=None, **kwargs):
    headers=kwargs.pop('headers',{}) or {}
    if token: headers['Authorization']='Bearer '+token
    try:
        r=s.request(method, BASE+path, headers=headers, timeout=20, **kwargs)
        ok=r.status_code < 500
        try: body=r.json()
        except Exception: body=r.text[:200]
        results.append((ok,name,r.status_code,body if r.status_code>=400 else ''))
        print(f'[{r.status_code}] {name} {path}')
        if r.status_code>=400: print(json.dumps(body, ensure_ascii=False)[:1000])
        return r
    except Exception as e:
        results.append((False,name,'EXC',str(e)))
        print(f'[EXC] {name}: {e}')
check('home','GET','/')
check('admin-shell','GET','/admin/')
r=check('business-login','POST','/api/auth/login',json={'tenantCode':'default','username':'admin','password':'Sh123456'})
biz=r.json()['data']['accessToken']
for name,path in [
 ('me','/api/auth/me'),
 ('dashboard-kpi','/api/dashboard/kpi?period=month'),
 ('orders','/api/orders?page=1&size=5'),
 ('stocktakes','/api/stocktakes?page=1&size=5'),
 ('operation-log-for-order','/api/operation-log/list/ORDER/1?pageNum=1&pageSize=5'),
 ('dealer-profile-1','/api/dealer-profile/1'),
 ('dealer-profile-rebate','/api/dealer-profile/1/rebate'),
 ('reports-sales-ranking','/api/reports/sales-ranking?limit=5'),
 ('reports-inventory-aging','/api/reports/inventory-aging?limit=5'),
 ('reports-surgery','/api/reports/surgery-stats?limit=5')
]: check(name,'GET',path,token=biz)
r=check('platform-login','POST','/api/admin/auth/login',json={'username':'admin','password':'Sh123456'})
admin=r.json()['data']['accessToken']
for name,path in [
 ('admin-me','/api/admin/auth/me'),
 ('admin-tenants','/api/admin/tenants?page=1&size=5'),
 ('admin-tenant-admins','/api/admin/tenant-admins?page=1&size=5'),
 ('admin-role-templates','/api/admin/role-templates'),
 ('admin-dicts','/api/admin/dicts')
]: check(name,'GET',path,token=admin)
print('\nSUMMARY', sum(1 for x in results if x[0]), '/', len(results), 'ok')
if not all(x[0] for x in results): sys.exit(1)
