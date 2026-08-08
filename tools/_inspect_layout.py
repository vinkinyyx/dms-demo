import requests, json
base='http://8.133.193.238:8082'
s=requests.Session()
r=s.post(base+'/api/auth/login', json={'tenantCode':'default','username':'admin','password':'Sh123456'}, timeout=10)
t=r.json()['data']['accessToken']
h={'Authorization':'Bearer '+t}
for k in ['orders','dealer-profile']:
    d=requests.get(base+'/api/ui/layout/'+k, headers=h, timeout=10).json()['data']
    print('===',k,'===')
    print(json.dumps(d, ensure_ascii=False, indent=2)[:6000])
