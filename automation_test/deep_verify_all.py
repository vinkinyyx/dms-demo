"""
全模块API深度验证脚本
验证每个模块：列表字段完整性、详情字段完整性、是否有明细lines
"""
import sys
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
print(f"登录成功")
print("=" * 80)

def get_list(resp):
    b = resp.body if hasattr(resp, 'body') else {}
    if not isinstance(b, dict): return []
    d = b.get('data', b)
    if isinstance(d, list): return d
    if isinstance(d, dict):
        for k in ['list', 'items', 'records', 'rows']:
            if isinstance(d.get(k), list): return d[k]
    return []

def get_detail(resp):
    b = resp.body if hasattr(resp, 'body') else {}
    if isinstance(b, dict) and 'code' in b and 'data' in b and 'message' in b:
        return b['data']
    return b

def analyze_detail(name, detail):
    """分析详情数据，返回字段数、列表字段等信息"""
    if not isinstance(detail, dict):
        return f"  ❌ {name}: 不是dict, type={type(detail).__name__}"
    lines = len(detail)
    list_fields = {k: len(v) for k, v in detail.items() if isinstance(v, list)}
    non_empty_lists = {k: v for k, v in list_fields.items() if v > 0}
    empty_lists = {k: v for k, v in list_fields.items() if v == 0}
    
    result = [f"  {name}: 字段数={lines}"]
    result.append(f"    所有字段: {sorted(detail.keys())}")
    if non_empty_lists:
        result.append(f"    ✅ 有数据列表: {non_empty_lists}")
        # 打印每个有数据列表的字段
        for k, v in detail.items():
            if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict):
                result.append(f"      [{k}] 字段: {sorted(v[0].keys())}")
    if empty_lists:
        result.append(f"    ⚠️  空列表(可能缺数据): {list(empty_lists.keys())}")
    if not list_fields:
        result.append(f"    ❌ 完全没有列表字段")
    return "\n".join(result)

modules = [
    ("销售订单", getattr(config.ApiPaths, 'SALES_ORDERS', '/api/sales-orders')),
    ("采购订单", getattr(config.ApiPaths, 'PURCHASE_ORDERS', '/api/purchase-orders')),
    ("销退订单", getattr(config.ApiPaths, 'SALES_RETURNS', '/api/sales-returns')),
    ("采退订单", getattr(config.ApiPaths, 'PURCHASE_RETURNS', '/api/purchase-returns')),
    ("合同", getattr(config.ApiPaths, 'CONTRACTS', '/api/contracts')),
    ("授权", getattr(config.ApiPaths, 'AUTHORIZATIONS', '/api/authorizations')),
    ("产品", getattr(config.ApiPaths, 'PRODUCTS', '/api/products')),
    ("经销商", getattr(config.ApiPaths, 'DEALERS', '/api/dealers')),
    ("供应商", getattr(config.ApiPaths, 'SUPPLIERS', '/api/suppliers')),
    ("仓库", getattr(config.ApiPaths, 'WAREHOUSES', '/api/warehouses')),
    ("手术报台", getattr(config.ApiPaths, 'SURGERY_REPORTS', '/api/surgery-reports')),
    ("收货入库", getattr(config.ApiPaths, 'GOODS_RECEIPTS', '/api/receipts')),
    ("销售出库", getattr(config.ApiPaths, 'GOODS_ISSUES', '/api/sales-outs')),
    ("库存移动", getattr(config.ApiPaths, 'STOCK_MOVES', '/api/stock-moves')),
    ("库存调整", getattr(config.ApiPaths, 'STOCK_ADJUSTMENTS', '/api/inventory-adjustments')),
]

for name, path in modules:
    print(f"\n{'='*60}")
    print(f"【{name}】 {path}")
    
    # 1. 列表
    try:
        resp = client.get(path, {"page": 1, "pageSize": 2})
        items = get_list(resp)
        print(f"  列表: status={resp.status_code}, count={len(items)}")
        if items:
            print(f"    列表字段数: {len(items[0])}")
            item_id = items[0].get('id')
            print(f"    第一条: id={item_id}, code={items[0].get('code') or items[0].get('name', 'N/A')}")
            
            # 2. 详情（如果有ID）
            if item_id:
                try:
                    detail_resp = client.get(f"{path}/{item_id}")
                    detail = get_detail(detail_resp)
                    print(analyze_detail("详情", detail))
                except Exception as e:
                    print(f"    详情请求异常: {e}")
        else:
            print(f"    (无数据，跳过详情)")
    except Exception as e:
        print(f"  异常: {e}")

print("\n" + "=" * 80)
print("全模块API深度验证完成")
