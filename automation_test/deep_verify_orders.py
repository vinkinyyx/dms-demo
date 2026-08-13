"""
订单模块深度验证脚本（API层）v2
修正：正确提取响应体中的data字段
"""
import sys
import json
sys.path.insert(0, '.')

import config
from utils.api_client import ApiClient

client = ApiClient()
resp = client.post(config.ApiPaths.LOGIN, {
    "tenantCode": "",
    "username": "admin",
    "password": "Sh123456",
})
body = resp.body
token = ""
if isinstance(body, dict):
    token = body.get("accessToken") or body.get("data", {}).get("accessToken") or ""
client.set_token(token, is_admin=False)
print(f"登录成功: token={token[:20]}...")
print("=" * 80)

def deep_get(obj, *keys, default=None):
    """深度取值，兼容多种响应结构"""
    cur = obj
    for k in keys:
        if isinstance(cur, dict):
            cur = cur.get(k)
        else:
            return default
    return cur if cur is not None else default

def get_list_from_resp(resp):
    """从响应中提取列表数据"""
    body = resp.body if hasattr(resp, 'body') else {}
    if not isinstance(body, dict):
        return []
    data = body.get('data', body)
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ['list', 'items', 'records', 'rows', 'content']:
            if isinstance(data.get(key), list):
                return data[key]
    return []

def get_detail_from_resp(resp):
    """从响应中提取详情数据"""
    body = resp.body if hasattr(resp, 'body') else {}
    if not isinstance(body, dict):
        return body
    # 如果有 code+message+data 结构，取data
    if 'code' in body and 'data' in body and 'message' in body:
        return body['data']
    return body

# ========== 1. 销售订单列表 ==========
print("\n【1】销售订单列表")
resp = client.get(config.ApiPaths.SALES_ORDERS, {"page": 1, "pageSize": 3})
so_items = get_list_from_resp(resp)
print(f"  状态: {resp.status_code}, 条数: {len(so_items)}")
if so_items:
    first = so_items[0]
    print(f"  字段数: {len(first)}")
    print(f"  所有字段: {sorted(first.keys())}")
    so_id = first.get('id')
    print(f"  第一条: id={so_id}, code={first.get('code')}, status={first.get('status')}")
else:
    so_id = None
    print(f"  响应body结构: {list(resp.body.keys()) if isinstance(resp.body, dict) else 'unknown'}")

# ========== 2. 销售订单详情 ==========
print("\n【2】销售订单详情")
if so_id:
    detail_resp = client.get(f"{config.ApiPaths.SALES_ORDERS}/{so_id}")
    detail = get_detail_from_resp(detail_resp)
    print(f"  状态: {detail_resp.status_code}")
    if isinstance(detail, dict):
        print(f"  详情字段数: {len(detail)}")
        print(f"  所有字段: {sorted(detail.keys())}")
        list_fields = {k: len(v) for k, v in detail.items() if isinstance(v, list)}
        if list_fields:
            print(f"  列表字段: {list_fields}")
            for k, v in detail.items():
                if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict):
                    print(f"  ✅ [{k}] {len(v)}行明细")
                    print(f"     明细字段: {sorted(v[0].keys())}")
        else:
            print(f"  ❌ 无列表字段（产品明细缺失！）")
        # 检查关键字段
        for k in ['lines', 'items', 'details', 'products', 'orderLines', 'orderItems']:
            if k in detail:
                print(f"  ⚠️  字段 {k}: {type(detail[k]).__name__} = {str(detail[k])[:100]}")
    else:
        print(f"  详情类型: {type(detail)}")
        print(f"  内容: {str(detail)[:300]}")

# ========== 3. 采购订单列表 ==========
print("\n【3】采购订单列表")
po_resp = client.get(config.ApiPaths.PURCHASE_ORDERS, {"page": 1, "pageSize": 3})
po_items = get_list_from_resp(po_resp)
print(f"  状态: {po_resp.status_code}, 条数: {len(po_items)}")
if po_items:
    first = po_items[0]
    print(f"  字段数: {len(first)}")
    print(f"  所有字段: {sorted(first.keys())}")
    po_id = first.get('id')
    print(f"  第一条: id={po_id}, code={first.get('code')}, status={first.get('status')}")
else:
    po_id = None

# ========== 4. 采购订单详情 ==========
print("\n【4】采购订单详情")
if po_id:
    detail_resp = client.get(f"{config.ApiPaths.PURCHASE_ORDERS}/{po_id}")
    detail = get_detail_from_resp(detail_resp)
    print(f"  状态: {detail_resp.status_code}")
    if isinstance(detail, dict):
        print(f"  详情字段数: {len(detail)}")
        print(f"  所有字段: {sorted(detail.keys())}")
        list_fields = {k: len(v) for k, v in detail.items() if isinstance(v, list)}
        if list_fields:
            print(f"  列表字段: {list_fields}")
            for k, v in detail.items():
                if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict):
                    print(f"  ✅ [{k}] {len(v)}行明细")
                    print(f"     明细字段: {sorted(v[0].keys())}")
        else:
            print(f"  ❌ 无列表字段（产品明细缺失！）")
    else:
        print(f"  详情类型: {type(detail)}, 内容: {str(detail)[:200]}")

# ========== 5. 操作记录接口 ==========
print("\n【5】操作记录接口探索")
log_paths = [
    f"/api/operation-logs?resourceType=SALES_ORDER&resourceId={so_id}",
    f"/api/operation-logs?refType=SALES_ORDER&refId={so_id}",
    f"/api/records?type=SALES_ORDER&id={so_id}",
]
for path in log_paths:
    r = client.get(path)
    body = r.body if hasattr(r, 'body') else {}
    items = get_list_from_resp(r)
    mark = "✅" if r.status_code == 200 and len(items) > 0 else "❌"
    print(f"  {mark} {path[:70]} = status={r.status_code}, items={len(items)}")

# ========== 6. 库存查询 ==========
print("\n【6】库存查询（验证库存数据结构）")
if hasattr(config.ApiPaths, 'INVENTORY'):
    inv_resp = client.get(config.ApiPaths.INVENTORY, {"page": 1, "pageSize": 3})
else:
    inv_resp = client.get("/api/inventory", {"page": 1, "pageSize": 3})
inv_items = get_list_from_resp(inv_resp)
print(f"  状态: {inv_resp.status_code}, 条数: {len(inv_items)}")
if inv_items:
    first = inv_items[0]
    print(f"  库存字段: {sorted(first.keys())}")

print("\n" + "=" * 80)
print("API层深度验证完成")
