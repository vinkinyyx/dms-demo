"""端到端验证：D13 列表页布局统一规范"""
import requests, json, sys
BASE = 'http://8.133.193.238:8082'

def main():
    # 1) 登录 - admin 平台管理员 / default 租户
    s = requests.Session()
    r = s.post(BASE + '/api/auth/login', json={'tenantCode': 'default', 'username': 'admin', 'password': 'Sh123456'}, timeout=10)
    print('=== login ===')
    print(r.status_code, r.text[:200])
    if r.status_code != 200:
        # 试 default 用户名
        r = s.post(BASE + '/api/auth/login', json={'tenantCode': 'default', 'username': 'admin', 'password': 'admin123'}, timeout=10)
        print('login retry:', r.status_code, r.text[:200])
    token = r.json().get('data', {}).get('accessToken') or r.json().get('data', {}).get('token')
    print('token len:', len(token) if token else 0)
    h = {'Authorization': 'Bearer ' + token} if token else {}
    # 2) 拉权限
    print('\n=== /api/me/permissions ===')
    r = s.get(BASE + '/api/me/permissions', headers=h, timeout=10)
    perms = r.json().get('data') or []
    print('perms count:', len(perms), 'sample:', perms[:5])
    # 3) 拉 16 个 pageKey 的布局
    pageKeys = ['products','categories','dealers','hospitals','warehouses','suppliers',
                'contract-apps','contracts','authorizations',
                'orders','sales-returns','purchase-orders','purchase-returns',
                'inventory','sales-outs','receipts','stock-moves','inventory-adjustments',
                'surgery-reports','promotions','dealer-profile',
                'positions','users','roles','api-call-log',
                'product-mappings']
    print('\n=== layout for each pageKey ===')
    for k in pageKeys:
        try:
            r = s.get(BASE + '/api/ui/layout/' + k, headers=h, timeout=10)
            if r.status_code == 200:
                d = r.json().get('data') or {}
                tbar = d.get('toolbar') or []
                rows = d.get('rowButtons') or []
                filt = d.get('filters') or []
                print(f"{k:24s} OK  toolbar={len(tbar):2d}  row={len(rows):2d}  filter={len(filt):2d}")
            else:
                print(f"{k:24s} HTTP {r.status_code}: {r.text[:120]}")
        except Exception as e:
            print(f"{k:24s} ERR {e}")

if __name__ == '__main__':
    main()
