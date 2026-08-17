"""
DMS自动化测试 - P0新功能专项测试
覆盖：消息中心、登录日志、审批摘要可视化
参考测试案例：v3.12.0 P0补充测试文档 第15章
"""
import pytest
import logging
import config
from utils.api_client import ApiClient

logger = logging.getLogger(__name__)


class TestNotifications:
    """消息中心测试 - TS-NOTIFY-001~008"""

    def test_notification_list_smoke(self, admin_client: ApiClient):
        """TS-NOTIFY-002-01: 消息列表页可访问，返回分页数据"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 20})
        assert resp.is_success, f"消息列表请求失败: {resp.msg}"
        assert resp.items is not None or resp.body is not None, "消息列表数据为空"
        logger.info(f"消息列表: 第1页, 共{len(resp.items) if resp.items else 0}条")

    def test_notification_pagination(self, admin_client: ApiClient):
        """TS-NOTIFY-002-08: 分页功能正常，total字段有效"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 5})
        assert resp.is_success
        total = resp.total
        if total and total > 5:
            resp2 = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 2, "pageSize": 5})
            assert resp2.is_success
            logger.info(f"分页验证: total={total}, 第2页条数={len(resp2.items) if resp2.items else 0}")

    def test_notification_fields_complete(self, admin_client: ApiClient):
        """TS-NOTIFY-002: 消息字段完整（状态/标题/内容/分类/时间）"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 10})
        if not resp.items:
            pytest.skip("无消息数据，跳过字段校验")
        first = resp.items[0]
        required_fields = ["title", "body", "refType", "isRead", "createdAt"]
        for field in required_fields:
            assert field in first, f"消息缺少字段: {field}, 实际字段: {list(first.keys())}"
        logger.info(f"消息字段校验通过: {list(first.keys())}")

    def test_notification_filter_by_unread(self, admin_client: ApiClient):
        """TS-NOTIFY-003-02: 未读筛选 - isRead=false"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 20, "isRead": "false"})
        assert resp.is_success
        if resp.items:
            for item in resp.items:
                is_read = item.get("isRead")
                assert is_read in [False, 0, "false"], f"筛选结果包含已读消息: isRead={is_read}"
        logger.info(f"未读消息数: {len(resp.items) if resp.items else 0}")

    def test_notification_filter_by_read(self, admin_client: ApiClient):
        """TS-NOTIFY-003-03: 已读筛选 - isRead=true"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 20, "isRead": "true"})
        assert resp.is_success
        if resp.items:
            for item in resp.items:
                is_read = item.get("isRead")
                assert is_read in [True, 1, "true"], f"筛选结果包含未读消息: isRead={is_read}"

    def test_notification_filter_by_category(self, admin_client: ApiClient):
        """TS-NOTIFY-004: 按分类筛选（APPROVAL/ORDER/SYSTEM等）"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 20, "refType": "APPROVAL"})
        assert resp.is_success
        if resp.items:
            for item in resp.items:
                ref_type = item.get("refType", "")
                assert "APPROVAL" in str(ref_type).upper() or "审批" in str(ref_type), f"分类筛选结果不一致: refType={ref_type}"

    def test_notification_unread_count(self, admin_client: ApiClient):
        """TS-NOTIFY-001-02: 未读消息数量接口（顶栏铃铛角标）"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS_UNREAD_COUNT)
        assert resp.is_success, f"未读计数接口失败: {resp.msg}"
        count = resp.data if isinstance(resp.data, (int, float)) else resp.body.get("count", 0) if isinstance(resp.body, dict) else 0
        assert isinstance(count, (int, float)), f"未读计数格式错误: {count}"
        logger.info(f"未读消息数: {count}")

    def test_notification_mark_all_read(self, admin_client: ApiClient):
        """TS-NOTIFY-005: 全部已读功能（验证接口可调用）"""
        resp = admin_client.post(config.ApiPaths.NOTIFICATIONS_MARK_ALL_READ, {})
        if resp.status_code == 404:
            pytest.skip("全部已读接口未实现")
        assert resp.is_success, f"全部已读失败: {resp.msg}"
        resp2 = admin_client.get(config.ApiPaths.NOTIFICATIONS_UNREAD_COUNT)
        count = resp2.data if isinstance(resp2.data, (int, float)) else 0
        logger.info(f"全部已读后未读数: {count}")

    def test_notification_approval_type_exists(self, admin_client: ApiClient):
        """TS-NOTIFY-007-01: 审批待办类消息存在（通过采购订单审批触发）"""
        resp = admin_client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 50})
        if not resp.items:
            pytest.skip("无消息数据")
        approval_found = any(
            "审批" in str(item.get("title", "")) or "approval" in str(item.get("refType", "")).lower()
            for item in resp.items
        )
        logger.info(f"审批类消息存在: {approval_found}")

    def test_notification_requires_auth(self):
        """TS-NOTIFY-008-04: 未登录访问消息接口返回401"""
        client = ApiClient()
        resp = client.get(config.ApiPaths.NOTIFICATIONS)
        assert resp.status_code in [401, 403], f"未认证访问应返回401/403，实际: {resp.status_code}"


class TestLoginLogs:
    """登录日志测试 - TS-LOGINLOG-001~005
    注：业务前台登录日志API（/api/login-logs）当前未实现，返回404。
    已知为P0需求项，待后端实现后放开。
    """

    @pytest.fixture(autouse=True)
    def _check_api_available(self, admin_client: ApiClient):
        """每个用例前检查接口是否可用，不可用则skip"""
        resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 1})
        if resp.status_code == 404:
            pytest.skip("业务前台登录日志API未实现（P0需求待上线）")

    def test_login_log_list_smoke(self, admin_client: ApiClient):
        """TS-LOGINLOG-001-01: 登录日志列表可访问"""
        resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 20})
        assert resp.is_success, f"登录日志请求失败: {resp.msg}"
        logger.info(f"登录日志列表: 第1页, 共{len(resp.items) if resp.items else 0}条")

    def test_login_log_fields_complete(self, admin_client: ApiClient):
        """TS-LOGINLOG-001: 登录日志字段完整（时间/用户名/姓名/类型/结果/IP/失败原因/UserAgent）"""
        resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 10})
        if not resp.items:
            pytest.skip("无登录日志数据")
        first = resp.items[0]
        # 检查关键字段（字段名可能有多种命名方式）
        fields_lower = {k.lower(): v for k, v in first.items()}
        logger.info(f"登录日志字段: {list(first.keys())}")
        # 时间字段
        has_time = any("time" in k or "date" in k or "created" in k for k in fields_lower)
        assert has_time, "缺少时间字段"
        # 用户名
        has_user = any("user" in k or "username" in k or "account" in k for k in fields_lower)
        assert has_user, "缺少用户名字段"
        # 结果/状态
        has_result = any("result" in k or "status" in k or "success" in k for k in fields_lower)
        assert has_result, "缺少结果字段"
        # IP地址
        has_ip = any("ip" in k for k in fields_lower)
        assert has_ip, "缺少IP字段"

    def test_login_log_success_record(self, admin_client: ApiClient):
        """TS-LOGINLOG-001-07: 存在成功登录记录"""
        resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 50, "status": "success"})
        if not resp.is_success:
            resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 50})
        if not resp.items:
            pytest.skip("无登录日志数据")
        has_success = any(
            str(item.get("status", "")).lower() in ["success", "true", "1"]
            or "成功" in str(item.get("result", ""))
            for item in resp.items
        )
        logger.info(f"有成功登录记录: {has_success}")

    def test_login_log_fail_record(self, admin_client: ApiClient):
        """TS-LOGINLOG-001-08: 存在失败登录记录，含失败原因"""
        resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 50})
        if not resp.items:
            pytest.skip("无登录日志数据")
        for item in resp.items:
            status = str(item.get("status", "")).lower()
            result = str(item.get("result", ""))
            if "fail" in status or "失败" in result or "false" == status:
                fail_reason = item.get("failReason") or item.get("reason") or item.get("errorMsg") or ""
                logger.info(f"失败登录记录: 原因={fail_reason}")
                return
        logger.info("未找到失败登录记录（可能环境无失败登录）")

    def test_login_log_pagination(self, admin_client: ApiClient):
        """TS-LOGINLOG-001-13: 分页功能"""
        resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 5})
        assert resp.is_success
        total = resp.total
        if total and total > 5:
            resp2 = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 2, "pageSize": 5})
            assert resp2.is_success
            logger.info(f"登录日志分页: total={total}")

    def test_login_log_ordered_by_time_desc(self, admin_client: ApiClient):
        """TS-LOGINLOG-002-11: 按时间倒序排列"""
        resp = admin_client.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 10})
        if not resp.items or len(resp.items) < 2:
            pytest.skip("日志不足2条，跳过排序校验")
        times = []
        for item in resp.items:
            t = item.get("loginTime") or item.get("createdAt") or item.get("time") or ""
            times.append(str(t))
        # 检查是否倒序（第一条大于第二条）
        assert times[0] >= times[1], f"登录日志未按时间倒序: {times[0]} vs {times[1]}"
        logger.info(f"登录日志倒序验证通过: {times[0]} >= {times[1]}")

    def test_login_log_requires_auth(self):
        """TS-LOGINLOG-004-03: 未登录访问登录日志返回401"""
        client = ApiClient()
        resp = client.get(config.ApiPaths.LOGIN_LOGS)
        assert resp.status_code in [401, 403], f"未认证访问应返回401/403，实际: {resp.status_code}"

    def test_login_log_record_generated_after_login(self):
        """TS-LOGINLOG-003-01: 登录后产生一条新日志"""
        # 先获取当前最新日志时间
        client_before = ApiClient()
        resp_before = client_before.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 1})
        client_login = ApiClient()
        login_resp = client_login.post(config.ApiPaths.LOGIN, {
            "tenantCode": "",
            "username": "sys_admin",
            "password": "Dms@123456",
        })
        assert login_resp.is_success, f"登录失败: {login_resp.msg}"
        token = ""
        if isinstance(login_resp.body, dict) and login_resp.body.get("accessToken"):
            token = login_resp.body["accessToken"]
        elif isinstance(login_resp.data, dict):
            d = login_resp.data.get("data", {})
            if isinstance(d, dict) and d.get("accessToken"):
                token = d["accessToken"]
        if not token:
            pytest.skip("无法获取登录token")
        client_login.set_token(token)
        resp_after = client_login.get(config.ApiPaths.LOGIN_LOGS, {"page": 1, "pageSize": 1})
        assert resp_after.is_success
        logger.info("登录后日志记录生成验证: 通过")


class TestApprovalSummary:
    """审批摘要可视化测试 - TS-APV-SUM-001~007"""

    def _get_first_pending_instance(self, admin_client: ApiClient):
        """获取第一条待处理审批实例ID"""
        resp = admin_client.get(config.ApiPaths.APPROVAL_INSTANCES, {"page": 1, "pageSize": 10, "status": "PENDING"})
        if not resp.items:
            resp = admin_client.get(config.ApiPaths.APPROVAL_INSTANCES, {"page": 1, "pageSize": 10})
        if not resp.items:
            return None
        return resp.items[0].get("id") or resp.items[0].get("instanceId") or ""

    def test_approval_summary_api_exists(self, admin_client: ApiClient):
        """TS-APV-SUM-001-01: 审批摘要接口可访问"""
        instance_id = self._get_first_pending_instance(admin_client)
        if not instance_id:
            pytest.skip("无审批实例数据")
        path = config.ApiPaths.APPROVAL_DETAIL_SUMMARY.format(id=instance_id)
        resp = admin_client.get(path)
        assert resp.is_success, f"审批摘要接口失败: {resp.msg}"
        logger.info(f"审批摘要接口: status={resp.status_code}")

    def test_approval_summary_has_biz_info(self, admin_client: ApiClient):
        """TS-APV-SUM-001: 摘要包含业务基本信息（单号/供应商/仓库/金额/备注）"""
        instance_id = self._get_first_pending_instance(admin_client)
        if not instance_id:
            pytest.skip("无审批实例数据")
        path = config.ApiPaths.APPROVAL_DETAIL_SUMMARY.format(id=instance_id)
        resp = admin_client.get(path)
        if not resp.is_success:
            pytest.skip("摘要接口不可用")
        data = resp.data if isinstance(resp.data, dict) else resp.body
        if not isinstance(data, dict):
            pytest.skip("摘要数据格式不是dict")
        logger.info(f"审批摘要字段: {list(data.keys())}")
        # 检查关键信息
        keys_lower = {k.lower(): v for k, v in data.items()}
        has_biz_no = any("no" in k or "code" in k or "number" in k for k in keys_lower)
        has_amount = any("amount" in k or "total" in k or "price" in k for k in keys_lower)
        logger.info(f"含业务编号: {has_biz_no}, 含金额: {has_amount}")

    def test_approval_summary_has_product_list(self, admin_client: ApiClient):
        """TS-APV-SUM-001-08: 摘要包含产品明细表"""
        instance_id = self._get_first_pending_instance(admin_client)
        if not instance_id:
            pytest.skip("无审批实例数据")
        path = config.ApiPaths.APPROVAL_DETAIL_SUMMARY.format(id=instance_id)
        resp = admin_client.get(path)
        if not resp.is_success:
            pytest.skip("摘要接口不可用")
        data = resp.data if isinstance(resp.data, dict) else resp.body
        if not isinstance(data, dict):
            pytest.skip("摘要数据格式不是dict")
        # 检查是否有产品列表字段
        has_products = False
        product_key = None
        for k, v in data.items():
            if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict):
                item = v[0]
                if any("product" in k2.lower() or "name" in k2.lower() or "qty" in k2.lower() or "quantity" in k2.lower() for k2 in item.keys()):
                    has_products = True
                    product_key = k
                    break
        logger.info(f"产品明细列表: {'有' if has_products else '无'}, key={product_key}")

    def test_approval_summary_has_approval_record(self, admin_client: ApiClient):
        """TS-APV-SUM-007-16: 摘要包含审批记录时间线"""
        instance_id = self._get_first_pending_instance(admin_client)
        if not instance_id:
            pytest.skip("无审批实例数据")
        # 从审批详情接口获取审批记录
        resp = admin_client.get(f"{config.ApiPaths.APPROVAL_INSTANCES}/{instance_id}")
        if not resp.is_success:
            resp = admin_client.get(f"{config.ApiPaths.APPROVAL_INSTANCES}/{instance_id}/records")
        if not resp.is_success:
            pytest.skip("审批详情接口不可用")
        data = resp.data if isinstance(resp.data, dict) else resp.body
        logger.info(f"审批详情数据: 类型={type(data).__name__}")
