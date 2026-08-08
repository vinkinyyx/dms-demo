import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
# 1) 拉取 index.html 和主要 chunks
import requests
BASE = 'http://8.133.193.238:8083'
r = requests.get(BASE + '/', timeout=10)
# 找入口 js
import re
m = re.findall(r'/assets/(index-[A-Za-z0-9_-]+\.js)', r.text)
print('entry js:', m)
if m:
    r2 = requests.get(BASE + '/assets/' + m[0], timeout=15)
    body = r2.text
    # 关键标识
    print('contains usePageLayout:', 'usePageLayout' in body)
    print('contains extraToolbarButtons:', 'extraToolbarButtons' in body)
    print('contains overflowRowButtons:', 'overflowRowButtons' in body)
    print('contains getPageLayout:', 'getPageLayout' in body)
    print('contains visibleFlatRowButtons:', 'visibleFlatRowButtons' in body)
    print('contains hasResetButton:', 'hasResetButton' in body)
    print('size:', len(body))
# 2) 加载主 chunk
for asset in ['/assets/CrudView-BNeuW65L.js']:
    try:
        r2 = requests.get(BASE + asset, timeout=10)
        print(asset, '->', r2.status_code, 'size', len(r2.text))
    except Exception as e:
        print(asset, 'err', e)
c.close()
