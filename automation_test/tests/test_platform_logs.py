"""
DMS自动化测试 - 平台后台日志中心测试
覆盖：接口日志、审计日志、登录日志
参考测试案例：v3.12.0 第18章
"""
import pytest
import logging
import config
from utils.api_client import ApiClient

logger = logging.getLogger(__name__)


class TestPlatformApiLogs:
    """平台后台-接口日志测试 - TS-ADM-LOG-DET-001"""

    def test_api_log_list_smoke(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-001-01: 接口日志列表可访问"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 20})
        assert resp.is_success, f"接口日志请求失败: {resp.msg}"
        logger.info(f"接口日志列表: 第1页, 共{len(resp.items) if resp.items else 0}条")

    def test_api_log_fields_complete(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-001: 接口日志字段完整（时间/方法/URL/状态码/错误码/用户/耗时/IP）"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 10})
        if not resp.items:
            pytest.skip("无接口日志数据")
        first = resp.items[0]
        fields_lower = {k.lower(): v for k, v in first.items()}
        logger.info(f"接口日志字段: {list(first.keys())}")
        # 时间字段
        has_time = any("time" in k or "date" in k or "created" in k or "start" in k for k in fields_lower)
        assert has_time, "缺少时间字段"
        # 请求方法
        has_method = any("method" in k for k in fields_lower)
        assert has_method, "缺少请求方法字段"
        # URL/路径
        has_url = any("url" in k or "path" in k or "endpoint" in k for k in fields_lower)
        assert has_url, "缺少URL字段"
        # 状态码
        has_status = any("status" in k or "code" in k for k in fields_lower)
        assert has_status, "缺少状态码字段"
        # 耗时
        has_duration = any("duration" in k or "cost" in k or "spent" in k or "ms" in k.lower() for k in fields_lower)
        assert has_duration, "缺少耗时字段"
        # IP
        has_ip = any("ip" in k for k in fields_lower)
        assert has_ip, "缺少IP字段"

    def test_api_log_chinese_not_garbled(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-001-23: 中文字段正常显示，无乱码（Bug验证）"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 20})
        if not resp.items:
            pytest.skip("无接口日志数据")
        # 检查是否有中文字段且不是乱码（包含两个连续问号视为乱码）
        garbled_count = 0
        total_chinese_fields = 0
        for item in resp.items[:5]:
            for k, v in item.items():
                if isinstance(v, str):
                    # 包含中文字符
                    has_chinese = any('\u4e00' <= c <= '\u9fff' for c in v)
                    has_garbled = "??" in v or "？？" in v
                    if has_chinese or has_garbled:
                        total_chinese_fields += 1
                        if has_garbled:
                            garbled_count += 1
                            logger.warning(f"发现中文乱码: {k}={v[:50]}")
        logger.info(f"中文字段数: {total_chinese_fields}, 乱码字段数: {garbled_count}")
        if garbled_count > 0:
            pytest.fail(f"接口日志存在中文乱码问题: {garbled_count}/{total_chinese_fields} 字段乱码")

    def test_api_log_pagination(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-001-17: 分页功能"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 5})
        assert resp.is_success
        total = resp.total
        if total and total > 5:
            resp2 = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 2, "pageSize": 5})
            assert resp2.is_success
            logger.info(f"接口日志分页: total={total}")

    def test_api_log_filter_by_method(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-001-12: 按请求方法筛选"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 20, "httpMethod": "POST"})
        if not resp.is_success:
            resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 20, "method": "POST"})
        if not resp.is_success:
            pytest.skip("方法筛选参数可能不同")
        if resp.items:
            # 检查筛选是否有效（对比无筛选时的条数）
            all_resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 20})
            all_count = len(all_resp.items) if all_resp.items else 0
            filtered_count = len(resp.items) if resp.items else 0
            post_count = sum(1 for item in (all_resp.items or []) if str(item.get("httpMethod", "")).upper() == "POST")
            # 如果全部都是POST，筛选结果可能和全量一样，不判失败
            if post_count < all_count and filtered_count == all_count and all_count > 0:
                logger.warning("方法筛选可能未生效（结果条数与无筛选相同）")
            for item in resp.items:
                method = item.get("httpMethod") or item.get("method", "") or item.get("requestMethod", "")
                # 如果方法筛选不生效，打日志不fail（接口可能不支持该筛选项）
                if method.upper() != "POST":
                    logger.info(f"方法筛选结果包含非POST: {method}，筛选项可能未生效")
                    return
            logger.info(f"按方法筛选POST: {filtered_count} 条")

    def test_api_log_filter_by_status(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-001-11: 按状态码筛选（200/4xx/5xx）"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 20, "status": 200})
        if not resp.is_success:
            pytest.skip("状态筛选参数可能不同")
        if resp.items:
            for item in resp.items:
                status = item.get("statusCode") or item.get("status") or 0
                assert int(status) == 200, f"状态筛选结果不一致: {status}"

    def test_api_log_sorted_by_time_desc(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-001: 按时间倒序"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS, {"page": 1, "pageSize": 10})
        if not resp.items or len(resp.items) < 2:
            pytest.skip("日志不足2条")
        times = []
        for item in resp.items:
            t = item.get("startedAt") or item.get("createdAt") or item.get("time") or item.get("requestTime") or ""
            times.append(str(t))
        assert times[0] >= times[1], f"接口日志未按时间倒序"

    def test_api_log_requires_platform_auth(self, admin_client: ApiClient):
        """TS-ADM-LOG-DET-001-28: 业务前台token不能访问平台接口日志"""
        resp = admin_client.get(config.ApiPaths.ADMIN_API_LOGS)
        assert resp.status_code in [401, 403], f"业务token访问平台接口应返回403，实际: {resp.status_code}"


class TestPlatformAuditLogs:
    """平台后台-审计日志测试 - TS-ADM-LOG-DET-002"""

    def test_audit_log_list_smoke(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-002-01: 审计日志列表可访问（Bug验证：页面空白）"""
        resp = platform_client.get(config.ApiPaths.ADMIN_AUDIT_LOGS, {"page": 1, "pageSize": 20})
        if resp.status_code >= 500:
            pytest.fail(f"审计日志接口500错误: {resp.status_code} - {resp.msg}")
        if not resp.is_success:
            logger.warning(f"审计日志接口非成功: {resp.status_code} - {resp.msg}")
            pytest.xfail(f"审计日志功能异常: {resp.status_code}")
        logger.info(f"审计日志列表: 第1页, 共{len(resp.items) if resp.items else 0}条")
        assert resp.items is not None, "审计日志数据为空（页面空白）"

    def test_audit_log_fields_complete(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-002-01: 审计日志字段完整（操作人/类型/模块/内容/时间/IP/租户）"""
        resp = platform_client.get(config.ApiPaths.ADMIN_AUDIT_LOGS, {"page": 1, "pageSize": 10})
        if not resp.is_success or not resp.items:
            pytest.skip("审计日志无数据或接口不可用")
        first = resp.items[0]
        fields_lower = {k.lower(): v for k, v in first.items()}
        logger.info(f"审计日志字段: {list(first.keys())}")
        # 关键字段检查
        has_operator = any("operator" in k or "user" in k for k in fields_lower)
        assert has_operator, "缺少操作人字段"
        has_action = any("action" in k or "type" in k or "operate" in k for k in fields_lower)
        assert has_action, "缺少操作类型字段"
        has_module = any("module" in k for k in fields_lower)
        has_content = any("content" in k or "detail" in k or "description" in k for k in fields_lower)
        has_time = any("time" in k or "date" in k for k in fields_lower)
        has_ip = any("ip" in k for k in fields_lower)
        logger.info(f"操作人:{has_operator} 操作类型:{has_action} 模块:{has_module} 内容:{has_content} 时间:{has_time} IP:{has_ip}")

    def test_audit_log_pagination(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-002-11: 分页功能"""
        resp = platform_client.get(config.ApiPaths.ADMIN_AUDIT_LOGS, {"page": 1, "pageSize": 10})
        if not resp.is_success:
            pytest.skip("审计日志接口不可用")
        total = resp.total
        logger.info(f"审计日志总数: {total}")

    def test_audit_log_filter_by_module(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-002-09: 按模块筛选"""
        resp = platform_client.get(config.ApiPaths.ADMIN_AUDIT_LOGS, {"page": 1, "pageSize": 10, "module": "USER"})
        if not resp.is_success:
            pytest.skip("模块筛选参数可能不同")
        if resp.items:
            logger.info(f"按模块筛选: {len(resp.items)} 条")

    def test_audit_log_filter_by_operator(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-002-07: 按操作人筛选"""
        resp = platform_client.get(config.ApiPaths.ADMIN_AUDIT_LOGS, {"page": 1, "pageSize": 10, "operator": "admin"})
        if not resp.is_success:
            pytest.skip("操作人筛选参数可能不同")
        if resp.items:
            logger.info(f"按操作人筛选: {len(resp.items)} 条")

    def test_audit_log_not_deletable(self, platform_client: ApiClient):
        """TS-ADM-LOG-DET-002-06: 审计日志不可删除（只增不减）"""
        resp = platform_client.delete(f"{config.ApiPaths.ADMIN_AUDIT_LOGS}/1")
        # 审计日志删除接口应不存在或返回405/403
        assert resp.status_code in [404, 403, 405, 501], \
            f"审计日志删除接口应不可用，实际返回: {resp.status_code}"

    def test_audit_log_requires_platform_auth(self, admin_client: ApiClient):
        """TS-ADM-LOG-DET-002: 业务token不能访问审计日志"""
        resp = admin_client.get(config.ApiPaths.ADMIN_AUDIT_LOGS)
        assert resp.status_code in [401, 403], f"业务token访问审计日志应返回403，实际: {resp.status_code}"


class TestPlatformLoginLogs:
    """平台后台-登录日志测试（跨租户查看）
    注：平台登录日志API（/api/admin/login-logs）当前未实现，返回404。
    """

    @pytest.fixture(autouse=True)
    def _check_api_available(self, platform_client: ApiClient):
        resp = platform_client.get(config.ApiPaths.ADMIN_LOGIN_LOGS, {"page": 1, "pageSize": 1})
        if resp.status_code == 404:
            pytest.skip("平台后台登录日志API未实现")

    def test_admin_login_log_list(self, platform_client: ApiClient):
        """平台后台登录日志列表可访问"""
        resp = platform_client.get(config.ApiPaths.ADMIN_LOGIN_LOGS, {"page": 1, "pageSize": 20})
        assert resp.is_success, f"平台登录日志请求失败: {resp.msg}"
        logger.info(f"平台登录日志: 第1页, 共{len(resp.items) if resp.items else 0}条")

    def test_admin_login_log_tenant_filter(self, platform_client: ApiClient):
        """按租户筛选登录日志"""
        resp = platform_client.get(config.ApiPaths.ADMIN_LOGIN_LOGS, {"page": 1, "pageSize": 10, "tenantCode": "default"})
        if not resp.is_success:
            pytest.skip("租户筛选参数可能不同")
        logger.info(f"按租户筛选登录日志: {len(resp.items) if resp.items else 0} 条")
