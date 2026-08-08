import requests, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE = 'http://8.133.193.238:8083'
r = requests.get(BASE + '/assets/admin-C0LHcp2M.js', timeout=10)
body = r.text
print('admin-C0LHcp2M size:', len(body))
for kw in ['/api/ui/layout', '/api/button-configs', 'getMyPermissions', 'usePageLayout', 'refresh-cache']:
    if kw in body: print(f'  contains {kw}: YES')
