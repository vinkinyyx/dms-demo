import requests
BASE = 'http://8.133.193.238:8083'
for asset in ['ModuleView-CgVeEQw_.js', 'index-DnKJooAP.js', 'crud-BNeuW65L.js']:
    r2 = requests.get(BASE + '/assets/' + asset, timeout=10)
    body = r2.text
    print(f'\n--- {asset} size {len(body)} ---')
    for kw in ['getPageLayout', '/api/ui/layout', 'composables', 'refresh-cache', 'platform_button_configs', 'ui/layout', 'usePageLayout']:
        if kw in body:
            print(f'  contains {kw}: YES')
