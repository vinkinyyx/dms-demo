import requests
BASE = 'http://8.133.193.238:8083'
# 1) 拉主页
r = requests.get(BASE + '/', timeout=10)
print('index status:', r.status_code)
# 2) 列出所有 js chunks
import re
js = re.findall(r'/assets/([A-Za-z0-9_-]+\.js)', r.text)
print('js assets in index:', js)
# 3) 遍历 dist 内所有 chunk 看是否含 usePageLayout 的特征 (minify 后保留的)
asset_list = [
    'ModuleView-CgVeEQw_.js',
    'crud-BNeuW65L.js',
    'index-DnKJooAP.js'
]
for asset in asset_list:
    r2 = requests.get(BASE + '/assets/' + asset, timeout=10)
    body = r2.text
    print(f'\n--- {asset} (size {len(body)}) ---')
    # 找特征串
    for kw in ['/api/ui/layout', 'crud-container', 'page-toolbar', 'row-actions', 'visibleToolbar', 'visibleRowButtons', 'clearCache', 'hasPermission', 'invalidatePageLayoutCache', 'buttonKey', 'overflowRowButtons', 'hasResetButton', 'el-dropdown', 'v-has']:
        if kw in body:
            print(f'  contains {kw}: YES')
