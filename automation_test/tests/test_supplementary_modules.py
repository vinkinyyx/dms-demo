"""
补充覆盖：此前自动化未覆盖但已实现的模块/接口。
- 邮件日志 /api/email-logs
- 异步任务 /api/async-tasks
- 首页/驾驶舱 /api/home/dashboard, /api/dashboard/*
- 经销商画像 /api/dealer-profile
- 发票 / RMA / 分销 接口存在性（一期允许返回非500）
- 审批待办/模板接口存在性
对应需求 v2.0：第六篇商务、第七篇报表画像、第十二篇日志、第十三篇系统能力。
"""
import pytest
from utils.api_client import ApiClient
import config


class TestEmailLogs:
    """邮件发送日志（通知与日志模块）"""

    def test_email_log_list(self, admin_client: ApiClient):
        resp = admin_client.get("/api/email-logs", {"page": 1, "size": 10})
        assert resp.status_code == 200, resp.msg
        assert resp.is_success, f"邮件日志列表失败: {resp.code} {resp.msg}"
        assert isinstance(resp.items, list)

    def test_email_log_requires_auth(self):
        c = ApiClient()
        resp = c.get("/api/email-logs")
        assert resp.status_code in (401, 403), "未登录应拒绝访问邮件日志"

    def test_email_log_send_test_endpoint(self, admin_client: ApiClient):
        """测试发送端点存在（不真正发邮件：缺参数应返回400，而非404/500）"""
        resp = admin_client.post("/api/email-logs/test", {})
        # 可能要求 to 字段 -> 400；或成功 -> 200；都说明端点存在可用
        assert resp.status_code in (200, 400), f"邮件测试发送端点异常: {resp.status_code} {resp.msg}"


class TestAsyncTasks:
    """导入导出异步任务"""

    def test_async_task_list(self, admin_client: ApiClient):
        resp = admin_client.get("/api/async-tasks", {"page": 1, "size": 10})
        assert resp.status_code == 200, resp.msg
        assert resp.is_success, f"异步任务列表失败: {resp.code} {resp.msg}"
        assert isinstance(resp.items, list)

    def test_async_task_requires_auth(self):
        c = ApiClient()
        resp = c.get("/api/async-tasks")
        assert resp.status_code in (401, 403)


class TestHomeAndDashboard:
    """首页与数据驾驶舱组件"""

    def test_home_dashboard(self, admin_client: ApiClient):
        resp = admin_client.get("/api/home/dashboard")
        assert resp.status_code == 200, resp.msg
        assert resp.is_success, f"首页看板失败: {resp.code} {resp.msg}"

    @pytest.mark.parametrize("widget", [
        "/api/dashboard/summary", "/api/dashboard/kpi",
        "/api/dashboard/inventory-pie", "/api/dashboard/sales-trend",
        "/api/dashboard/order-funnel", "/api/dashboard/top-dealers",
        "/api/dashboard/activity-7d",
    ])
    def test_dashboard_widgets(self, admin_client: ApiClient, widget):
        resp = admin_client.get(widget)
        # 驾驶舱组件必须可达且不500（无数据时返回空结构也可）
        assert resp.status_code == 200, f"{widget} -> {resp.status_code} {resp.msg}"
        assert resp.code in (0, 200), f"{widget} 业务码异常: {resp.code} {resp.msg}"


class TestDealerProfile:
    """经销商360画像"""

    def test_dealer_profile_requires_id(self, admin_client: ApiClient):
        resp = admin_client.get("/api/dealer-profile/0")
        # 不存在的经销商应返回 404/400/空，而不是 500
        assert resp.status_code in (200, 400, 404), resp.msg

    def test_dealer_profile_real(self, admin_client: ApiClient):
        dealers = admin_client.get(config.ApiPaths.DEALERS, {"page": 1, "size": 1})
        if dealers.items:
            did = dealers.items[0].get("id")
            resp = admin_client.get(f"/api/dealer-profile/{did}")
            assert resp.status_code in (200, 404), f"画像接口异常: {resp.status_code} {resp.msg}"
            if resp.status_code == 200:
                assert resp.code in (0, 200), resp.msg


class TestIndustryModulesPresence:
    """发票/RMA/分销等行业模块：一期允许未实现，但端点必须存在且不报500。
    这些是需求 v2.0 第四/六篇列出的能力，用于防止接口被误删或启动报错。"""

    @pytest.mark.parametrize("path", [
        "/api/sales-invoices",
        "/api/purchase-invoices",
        "/api/rma-orders",
        "/api/distribution-shipments",
    ])
    def test_module_endpoint_alive(self, admin_client: ApiClient, path):
        resp = admin_client.get(path, {"page": 1, "size": 5})
        # 接受 200（已实现）或 40x（未启用/无权限）；禁止 500/404(接口丢失)
        assert resp.status_code < 500, f"{path} 服务器错误: {resp.status_code}"
        assert resp.status_code != 404, f"{path} 接口不存在（404）"


class TestApprovalEndpoints:
    """审批中心待办/模板接口存在性"""

    def test_approval_todo_endpoint(self, admin_client: ApiClient):
        resp = admin_client.get("/api/approval/todo")
        assert resp.status_code in (200, 404), f"审批待办接口: {resp.status_code}"

    def test_approval_template_list(self, admin_client: ApiClient):
        resp = admin_client.get(config.ApiPaths.APPROVAL_FLOWS, {"page": 1, "size": 5})
        assert resp.status_code == 200, resp.msg
        assert isinstance(resp.items, list)
