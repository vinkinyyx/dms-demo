import requests, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE = 'http://8.133.193.238:8083'
r = requests.get(BASE + '/', timeout=10)
import re
m = re.findall(r'/assets/([A-Za-z0-9_-]+\.js)', r.text)
print('all js in index:', m)
for asset in m:
    r2 = requests.get(BASE + '/assets/' + asset, timeout=10)
    body = r2.text
    if 'crud-container' in body or 'ModuleView' in body or 'page-toolbar' in body:
        print(f'  {asset} size={len(body)}')
        for kw in ['usePageLayout', 'getPageLayout', '/api/ui/layout', 'crud-container', 'visibleToolbar', 'visibleRowButtons', 'hasPermission', 'invalidatePageLayoutCache', 'composables', 'buttonKey', 'overflowRowButtons', 'hasResetButton', 'onRowButtonClick']:
            if kw in body: print(f'    contains {kw}')
