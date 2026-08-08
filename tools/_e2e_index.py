import requests, sys, re
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE = 'http://8.133.193.238:8083'
r = requests.get(BASE + '/assets/index-DnKJooAP.js', timeout=10)
body = r.text
print('size:', len(body))
# 关键搜索
for kw in ['/api/ui/layout', 'usePageLayout', 'getPageLayout', 'buttonKey', 'visibleToolbar', 'visibleRowButtons', 'hasPermission', 'invalidatePageLayoutCache', 'hasResetButton', 'overflowRowButtons', 'extraToolbarButtons', 'v-has']:
    if kw in body: print(f'  contains {kw}: YES ({body.count(kw)})')
# 看 import /api/ui/layout 段
m = re.search(r'.{0,30}/api/ui/layout.{0,30}', body)
if m: print('found:', m.group())
