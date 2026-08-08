import requests
BASE = 'http://8.133.193.238:8083'
# 1) 拉主页
r = requests.get(BASE + '/', timeout=10)
print('index status:', r.status_code)
# 2) 拉 ModuleView 块 - 内嵌 CrudView + usePageLayout
import re
m = re.search(r'/assets/ModuleView-([A-Za-z0-9_-]+)\.js', r.text)
if m:
    asset = '/assets/ModuleView-' + m.group(1) + '.js'
    r2 = requests.get(BASE + asset, timeout=10)
    body = r2.text
    print(f'ModuleView asset: {asset}, size={len(body)}')
    # minify 后变量名短，但字符串保留
    for kw in ['/api/ui/layout', 'v-has', 'has-button', 'buttonKey', 'rowButton', 'crud-container', 'page-toolbar']:
        if kw in body:
            print(f'  contains {kw}: YES')
        else:
            print(f'  contains {kw}: NO')
    # 关键：检查是否内联了 usePageLayout 的返回结构 (minify 后)
    # 原代码 return { layout, loading, error, load, refresh, hasPermission, visibleToolbar, visibleRowButtons, clearCache }
    # minify 后变量名短，但 property 名保留
    for prop in ['visibleToolbar', 'visibleRowButtons', 'clearCache', 'hasPermission']:
        if prop in body:
            print(f'  return prop {prop}: YES')
