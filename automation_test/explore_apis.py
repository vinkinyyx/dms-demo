"""
探索平台后台和审批模块的实际API路径
因为预设路径返回404，需要找到正确的API
"""
import sys
sys.path.insert(0, '.')

import config
from utils.api_client import ApiClient

client = ApiClient()
resp = client.post(config.ApiPaths.ADMIN_LOGIN, {
    "username": "admin",
    "password": "Sh123456",
})
body = resp.body
token = ""
if isinstance(body, dict):
    token = body.get("accessToken") or body.get("data", {}).get("accessToken") or ""
client.set_token(token, is_admin=True)
print(f"平台后台登录状态: {'成功' if token else '失败'}")
print(f"Token长度: {len(token)}")
print("=" * 60)

candidate_paths = [
    # 租户
    ("/api/admin/tenants", "租户列表"),
    ("/api/admin/tenant/list", "租户列表2"),
    ("/api/admin/tenants/page", "租户分页"),
    # 用户
    ("/api/admin/users", "平台用户"),
    ("/api/admin/user/list", "平台用户2"),
    # 字典
    ("/api/admin/dict-types", "字典类型"),
    ("/api/admin/dict/type/list", "字典类型2"),
    ("/api/admin/dict-items", "字典项"),
    # 菜单
    ("/api/admin/menus", "平台菜单"),
    ("/api/admin/menu/list", "平台菜单2"),
    # 日志
    ("/api/admin/audit-logs", "审计日志"),
    ("/api/admin/audit-log/list", "审计日志2"),
    ("/api/admin/login-logs", "登录日志"),
    ("/api/admin/api-call-logs", "接口日志"),
    # 绑定
    ("/api/admin/tenant-dealer-bindings", "租户绑定"),
    ("/api/admin/bindings", "租户绑定2"),
]

print("\n【平台后台API路径探测】")
for path, name in candidate_paths:
    try:
        r = client.get(path, {"page": 1, "pageSize": 1})
        status = r.status_code
        body_data = r.body if hasattr(r, 'body') else {}
        code = body_data.get('code', 'N/A') if isinstance(body_data, dict) else 'N/A'
        msg = (body_data.get('message', '') if isinstance(body_data, dict) else '')[:40]
        icon = "✅" if status == 200 and (code == 0 or code == '0') else "❌"
        print(f"  {icon} [{status}] {name}: {path} (code={code}, msg={msg})")
    except Exception as e:
        print(f"  💥 {name}: {path} - {e}")

print("\n" + "=" * 60)
print("【业务前台审批/日志类API路径探测】")

biz_client = ApiClient()
biz_resp = biz_client.post(config.ApiPaths.LOGIN, {
    "tenantCode": "",
    "username": "sys_admin",
    "password": "Dms@123456",
})
biz_body = biz_resp.body
biz_token = ""
if isinstance(biz_body, dict):
    biz_token = biz_body.get("accessToken") or biz_body.get("data", {}).get("accessToken") or ""
biz_client.set_token(biz_token, is_admin=False)
print(f"业务前台登录: {'成功' if biz_token else '失败'}")

biz_candidates = [
    # 审批
    ("/api/approval-instances", "审批实例"),
    ("/api/approvals", "审批列表"),
    ("/api/approval/list", "审批列表2"),
    ("/api/approval-flows", "审批流程"),
    ("/api/approval-flow/list", "审批流程2"),
    ("/api/approval-delegates", "审批委托"),
    ("/api/approval-monitors", "审批监控"),
    ("/api/approval-todo", "待审批"),
    ("/api/approvals/todo", "待审批2"),
    ("/api/approvals/done", "已审批"),
    # 字典
    ("/api/dict-items", "数据字典"),
    ("/api/dict/list", "数据字典2"),
    ("/api/dicts", "数据字典3"),
    # 操作日志
    ("/api/operation-log", "操作日志"),
    ("/api/operation-logs", "操作日志2"),
    ("/api/logs/operation", "操作日志3"),
    # 登录日志
    ("/api/login-logs", "登录日志"),
    ("/api/logs/login", "登录日志2"),
    # 消息中心详情
    ("/api/notifications", "消息列表"),
    ("/api/notifications/1", "消息详情1"),
    ("/api/notification/1", "消息详情2"),
]

for path, name in biz_candidates:
    try:
        r = biz_client.get(path, {"page": 1, "pageSize": 1})
        status = r.status_code
        body_data = r.body if hasattr(r, 'body') else {}
        code = body_data.get('code', 'N/A') if isinstance(body_data, dict) else 'N/A'
        msg = (body_data.get('message', '') if isinstance(body_data, dict) else '')[:40]
        icon = "✅" if status == 200 and (code == 0 or code == '0') else "❌"
        print(f"  {icon} [{status}] {name}: {path} (code={code}, msg={msg})")
    except Exception as e:
        print(f"  💥 {name}: {path} - {e}")

print("\n探测完成")
