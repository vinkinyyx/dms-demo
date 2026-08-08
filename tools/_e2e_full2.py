"""D13 端到端验证：login → /api/me/permissions → 16 pageKey layout → 验证按钮/搜索字段数"""
import requests, json, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE = 'http://8.133.193.238:8082'

def login(tenant='default', user='admin', pwd='Sh123456'):
    s = requests.Session()
    r = s.post(BASE + '/api/auth/login', json={'tenantCode': tenant, 'username': user, 'password': pwd}, timeout=10)
    if r.status_code != 200: return None, r.text
    d = r.json().get('data', {})
    return d.get('accessToken'), None

def main():
    token, err = login()
    if not token:
        print('login failed:', err[:200])
        return
    h = {'Authorization': 'Bearer ' + token}
    # 1) permissions
    r = requests.get(BASE + '/api/me/permissions', headers=h, timeout=10)
    perms = r.json().get('data') or []
    print(f'admin permissions: {len(perms)}')
    # 2) 16 pageKey 拉布局
    pages = ['products','categories','dealers','hospitals','warehouses','suppliers',
             'contract-apps','contracts','authorizations',
             'orders','sales-returns','purchase-orders','purchase-returns',
             'inventory','sales-outs','receipts','stock-moves','inventory-adjustments',
             'surgery-reports','promotions','dealer-profile',
             'positions','users','roles','api-call-log',
             'product-mappings']
    print('\n{0:24s} {1:6s} {2:6s} {3:6s} {4:5s}'.format('pageKey','tbar','row','flt','hasFilters'))
    for k in pages:
        r = requests.get(BASE + '/api/ui/layout/' + k, headers=h, timeout=10)
        if r.status_code != 200:
            print(f'{k:24s} HTTP {r.status_code}')
            continue
        d = r.json().get('data') or {}
        tbar = d.get('toolbar') or []
        rows = d.get('rowButtons') or []
        filt = d.get('filters') or []
        # 检查搜索/查询是否在 toolbar 第一位
        has_search_btn = any(b.get('buttonKey') == 'search' for b in tbar)
        has_reset = any(b.get('buttonKey') == 'reset' for b in tbar)
        # 检查 list 字段都有 至少 1 个 search 类 input
        has_keyword_filter = any(f.get('componentType') == 'input' and f.get('filterKey') == 'keyword' for f in filt)
        # 检查行内按钮是否 > 4 折叠
        rows_n = len(rows)
        # 实际会折叠的判断
        will_fold = rows_n > 4
        marker = 'OK'
        if not has_keyword_filter: marker = 'NO_KW'
        print(f'{k:24s} {len(tbar):>6d} {rows_n:>6d} {len(filt):>6d}  {marker}  search={has_search_btn} reset={has_reset} fold>4={will_fold}')
    # 3) 验证一些按钮的 permissionCode
    print('\n=== button permission codes sample ===')
    r = requests.get(BASE + '/api/ui/layout/orders', headers=h, timeout=10)
    d = r.json().get('data') or {}
    for b in (d.get('toolbar') or []) + (d.get('rowButtons') or []):
        pc = b.get('permissionCode')
        if pc:
            hit = pc in perms
            print(f'  {b["buttonKey"]:10s} -> {pc:35s} admin_has={hit}')

if __name__ == '__main__':
    main()
