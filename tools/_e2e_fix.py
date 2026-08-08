import requests, json, sys
BASE = 'http://8.133.193.238:8082'
s = requests.Session()
r = s.post(BASE + '/api/auth/login', json={'tenantCode': 'default', 'username': 'admin', 'password': 'Sh123456'}, timeout=10)
token = r.json()['data']['accessToken']
h = {'Authorization': 'Bearer ' + token}
# 拉 products 全布局详情
r = s.get(BASE + '/api/ui/layout/products', headers=h, timeout=10)
print('products layout:')
print(json.dumps(r.json(), ensure_ascii=False, indent=2)[:3000])
