"""
全模块API深度验证脚本 - 第二轮（非核心模块）
排除：订单/库存/报台（已在第一轮验证）
覆盖：合同/授权/产品/经销商/供应商/仓库/促销/消息/审批/报表/用户/角色/字典 + 平台后台
"""
import sys
sys.path.insert(0, '.')

import config
from utils.api_client import ApiClient

client = ApiClient()
resp = client.post(config.ApiPaths.LOGIN, {
    "tenantCode": "",
    "username": "sys_admin",
    "password": "Dms@123456",
})
body = resp.body
token = ""
if isinstance(body, dict):
    token = body.get("accessToken") or body.get("data", {}).get("accessToken") or ""
client.set_token(token, is_admin=False)
print(f"✅ 业务前台登录成功")
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
        for k, v in detail.items():
            if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict):
                result.append(f"      [{k}] 字段: {sorted(v[0].keys())}")
    if empty_lists:
        result.append(f"    ⚠️  空列表(可能缺数据): {list(empty_lists.keys())}")
    if not list_fields:
        result.append(f"    ❌ 完全没有列表字段")
    return "\n".join(result)

# ========== 业务前台模块 ==========
biz_modules = [
    ("合同工作台", "/api/contracts"),
    ("合同模板", "/api/contract-templates"),
    ("授权管理", "/api/authorizations"),
    ("产品管理", "/api/products"),
    ("产品分类", "/api/product-categories"),
    ("产品线", "/api/product-lines"),
    ("包装规格", "/api/product-package-levels"),
    ("产品套包", "/api/product-bundles"),
    ("经销商管理", "/api/dealers"),
    ("医院管理", "/api/hospitals"),
    ("供应商管理", "/api/suppliers"),
    ("仓库管理", "/api/warehouses"),
    ("区域管理", "/api/regions"),
    ("产品价格", "/api/product-prices"),
    ("促销活动", "/api/promotions"),
    ("消息中心", "/api/notifications"),
    ("登录日志", "/api/login-logs"),
    ("审批实例", "/api/approval-instances"),
    ("审批流程", "/api/approval-flows"),
    ("审批委托", "/api/approval-delegates"),
    ("审批监控", "/api/approval-monitors"),
    ("用户管理", "/api/users"),
    ("角色管理", "/api/roles"),
    ("销售岗位", "/api/sales-positions"),
    ("菜单管理", "/api/menus"),
    ("数据字典", "/api/dict-items"),
    ("操作日志", "/api/operation-log"),
    ("订单追溯报表", "/api/reports/order-trace"),
    ("销售排行报表", "/api/reports/sales-ranking"),
    ("库存周转报表", "/api/reports/inventory-turnover"),
]

results = []

print("\n" + "=" * 80)
print("【第一部分】业务前台API深度验证")
print("=" * 80)

for name, path in biz_modules:
    print(f"\n{'='*60}")
    print(f"【{name}】 {path}")
    
    module_result = {"name": name, "path": path, "list_count": 0, "list_fields": 0, 
                     "detail_fields": 0, "list_data_fields": [], "empty_lists": [],
                     "has_detail": False, "error": ""}
    
    try:
        resp = client.get(path, {"page": 1, "pageSize": 2})
        items = get_list(resp)
        module_result["list_count"] = len(items)
        print(f"  列表: status={resp.status_code}, count={len(items)}")
        
        if items:
            module_result["list_fields"] = len(items[0])
            print(f"    列表字段数: {len(items[0])}")
            item_id = items[0].get('id')
            print(f"    第一条: id={item_id}, code/name={items[0].get('code') or items[0].get('name', 'N/A')}")
            
            if item_id:
                try:
                    detail_resp = client.get(f"{path}/{item_id}")
                    detail = get_detail(detail_resp)
                    if isinstance(detail, dict):
                        module_result["has_detail"] = True
                        module_result["detail_fields"] = len(detail)
                        print(analyze_detail("详情", detail))
                        
                        list_fields = {k: len(v) for k, v in detail.items() if isinstance(v, list)}
                        module_result["empty_lists"] = [k for k, v in list_fields.items() if v == 0]
                        module_result["list_data_fields"] = [k for k, v in list_fields.items() if v > 0]
                    else:
                        module_result["error"] = f"详情非dict: {type(detail).__name__}"
                        print(f"    ⚠️  详情非dict: {type(detail).__name__}")
                except Exception as e:
                    module_result["error"] = str(e)
                    print(f"    详情请求异常: {e}")
        else:
            print(f"    (无数据，跳过详情)")
    except Exception as e:
        module_result["error"] = str(e)
        print(f"  异常: {e}")
    
    results.append(module_result)

# ========== 平台后台模块 ==========
print("\n\n" + "=" * 80)
print("【第二部分】平台后台API深度验证")
print("=" * 80)

admin_client = ApiClient()
admin_resp = admin_client.post(config.ApiPaths.ADMIN_LOGIN, {
    "username": "admin",
    "password": "Sh123456",
})
admin_body = admin_resp.body
admin_token = ""
if isinstance(admin_body, dict):
    admin_token = admin_body.get("accessToken") or admin_body.get("data", {}).get("accessToken") or ""
admin_client.set_token(admin_token, is_admin=True)
print(f"✅ 平台后台登录成功")

admin_modules = [
    ("租户管理", "/api/admin/tenants"),
    ("平台用户", "/api/admin/users"),
    ("字典类型", "/api/admin/dict-types"),
    ("字典项", "/api/admin/dict-items"),
    ("平台菜单", "/api/admin/menus"),
    ("审计日志", "/api/admin/audit-logs"),
    ("登录日志", "/api/admin/login-logs"),
    ("接口日志", "/api/admin/api-call-logs"),
    ("租户绑定", "/api/admin/tenant-dealer-bindings"),
]

for name, path in admin_modules:
    print(f"\n{'='*60}")
    print(f"【{name}】 {path}")
    
    module_result = {"name": f"[后台]{name}", "path": path, "list_count": 0, "list_fields": 0,
                     "detail_fields": 0, "list_data_fields": [], "empty_lists": [],
                     "has_detail": False, "error": ""}
    
    try:
        resp = admin_client.get(path, {"page": 1, "pageSize": 2})
        items = get_list(resp)
        module_result["list_count"] = len(items)
        print(f"  列表: status={resp.status_code}, count={len(items)}")
        
        if items:
            module_result["list_fields"] = len(items[0])
            print(f"    列表字段数: {len(items[0])}")
            item_id = items[0].get('id')
            print(f"    第一条: id={item_id}, code/name={items[0].get('code') or items[0].get('name', 'N/A')}")
            
            if item_id:
                try:
                    detail_resp = admin_client.get(f"{path}/{item_id}")
                    detail = get_detail(detail_resp)
                    if isinstance(detail, dict):
                        module_result["has_detail"] = True
                        module_result["detail_fields"] = len(detail)
                        print(analyze_detail("详情", detail))
                        
                        list_fields = {k: len(v) for k, v in detail.items() if isinstance(v, list)}
                        module_result["empty_lists"] = [k for k, v in list_fields.items() if v == 0]
                        module_result["list_data_fields"] = [k for k, v in list_fields.items() if v > 0]
                    else:
                        module_result["error"] = f"详情非dict: {type(detail).__name__}"
                        print(f"    ⚠️  详情非dict: {type(detail).__name__}")
                except Exception as e:
                    module_result["error"] = str(e)
                    print(f"    详情请求异常: {e}")
        else:
            print(f"    (无数据，跳过详情)")
    except Exception as e:
        module_result["error"] = str(e)
        print(f"  异常: {e}")
    
    results.append(module_result)

# ========== 汇总输出 ==========
print("\n\n" + "=" * 80)
print("【汇总表】所有模块API深度验证结果")
print("=" * 80)
print(f"{'模块':<20} {'列表数':>6} {'列表字段':>8} {'详情字段':>8} {'有数据列表':<20} {'空列表':<20} {'状态':<10}")
print("-" * 100)

issues = []
for r in results:
    status = "✅正常"
    if r["error"]:
        status = "❌异常"
    elif not r["has_detail"] and r["list_count"] > 0:
        status = "⚠️ 无详情"
    elif r["empty_lists"] and len(r["empty_lists"]) > 2:
        status = "⚠️ 多空列表"
    
    list_data = ",".join(r["list_data_fields"][:2]) if r["list_data_fields"] else "-"
    empty_data = ",".join(r["empty_lists"][:3]) if r["empty_lists"] else "-"
    
    print(f"{r['name']:<20} {r['list_count']:>6} {r['list_fields']:>8} {r['detail_fields']:>8} {list_data:<20} {empty_data:<20} {status:<10}")
    
    if status != "✅正常":
        issues.append(r)

print(f"\n总模块数: {len(results)}")
print(f"有问题模块数: {len(issues)}")
print(f"正常模块数: {len(results) - len(issues)}")

if issues:
    print("\n⚠️  问题清单:")
    for r in issues:
        print(f"  - {r['name']}: {r.get('error') or ('空列表: ' + ','.join(r['empty_lists']))}")

print("\n全模块API深度验证完成")
