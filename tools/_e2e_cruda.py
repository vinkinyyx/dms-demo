import requests
BASE = 'http://8.133.193.238:8083'
# 列出 assets
r = requests.get(BASE + '/', timeout=10)
# 直接拉 crud 块
for asset in ['/assets/crud-BNeuW65L.js', '/assets/index-DnKJooAP.js']:
    r2 = requests.get(BASE + asset, timeout=10)
    body = r2.text
    print(f'--- {asset} (size {len(body)}) ---')
    for keyword in ['usePageLayout', 'extraToolbarButtons', 'overflowRowButtons', 'getPageLayout', 'visibleFlatRowButtons', 'hasResetButton', 'onRowButtonClick', 'visibleRowButtons', 'invalidatePageLayoutCache']:
        print(f'  contains {keyword}:', keyword in body)
