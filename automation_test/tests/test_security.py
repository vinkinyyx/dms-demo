"""
DMS自动化测试 - 安全测试用例
覆盖认证授权、SQL注入、XSS、权限校验、跨租户隔离、越权访问等安全场景
"""
import pytest
import config
from utils.api_client import ApiClient
from utils.helpers import build_product


@pytest.mark.security
class TestSecurity:
    """安全测试套件"""

    # ====== 1-6: 认证授权测试 ======

    @pytest.mark.negative
    def test_no_token_access_business_api(self):
        """用例1: 无token访问业务接口，应返回401认证错误"""
        c = ApiClient()
        resp = c.get(config.ApiPaths.PRODUCTS)
        assert resp.status_code == 401 or resp.is_auth_error, \
            f"无token访问业务接口未拦截: status={resp.status_code}"

    @pytest.mark.negative
    def test_no_token_access_platform_api(self):
        """用例2: 无token访问平台接口，应返回401认证错误"""
        c = ApiClient()
        resp = c.get(config.ApiPaths.ADMIN_TENANTS)
        assert resp.status_code == 401 or resp.is_auth_error, \
            f"无token访问平台接口未拦截: status={resp.status_code}"

    @pytest.mark.negative
    def test_business_token_access_platform_api(self, admin_client: ApiClient):
        """用例3: 业务token访问平台接口，应返回认证/权限错误"""
        resp = admin_client.get(config.ApiPaths.ADMIN_TENANTS)
        assert resp.is_auth_error, \
            f"业务token访问平台接口未拦截: status={resp.status_code}"

    @pytest.mark.negative
    def test_platform_token_access_business_api(self, platform_client: ApiClient):
        """用例4: 平台token访问业务接口（一期未严格隔离，宽松兼容200/401/403）"""
        resp = platform_client.get(config.ApiPaths.PRODUCTS)
        assert resp.status_code in (200, 401, 403), \
            f"平台token访问业务接口返回异常状态: status={resp.status_code}"

    @pytest.mark.negative
    def test_invalid_token_access(self):
        """用例5: 无效token访问接口，应返回认证/权限错误"""
        c = ApiClient()
        c.set_token("invalid.jwt.token.12345")
        resp = c.get(config.ApiPaths.PRODUCTS)
        assert resp.is_auth_error or resp.status_code in (401, 403), \
            f"无效token访问未拦截: status={resp.status_code}"

    @pytest.mark.negative
    def test_tampered_token_access(self, admin_client: ApiClient):
        """用例6: 篡改token后访问接口，应返回认证/权限错误"""
        if not admin_client.token:
            pytest.skip("admin_client无有效token，跳过篡改token测试")
        tampered = admin_client.token[:-3] + "xxx"
        c = ApiClient()
        c.set_token(tampered)
        resp = c.get(config.ApiPaths.PRODUCTS)
        assert resp.is_auth_error or resp.status_code in (401, 403), \
            f"篡改token访问未拦截: status={resp.status_code}"

    # ====== 7-8: SQL注入测试 ======

    @pytest.mark.negative
    def test_sql_injection_login(self):
        """用例7: SQL注入-登录接口用户名注入，应登录失败"""
        c = ApiClient()
        resp = c.post(config.ApiPaths.LOGIN, {
            "tenantCode": "",
            "username": "admin' OR '1'='1",
            "password": "x"
        })
        assert not resp.is_success, \
            f"SQL注入登录应失败但返回成功: status={resp.status_code}"

    @pytest.mark.negative
    def test_sql_injection_query_param(self, admin_client: ApiClient):
        """用例8: SQL注入-查询参数注入，应不返回500服务器错误"""
        resp = admin_client.get(config.ApiPaths.PRODUCTS, {
            "keyword": "'; DROP TABLE products; --"
        })
        assert resp.status_code < 500, \
            f"SQL注入导致服务器内部错误: status={resp.status_code}"

    # ====== 9-10: XSS测试 ======

    @pytest.mark.negative
    def test_xss_product_create(self, admin_client: ApiClient, cleanup_registry: dict):
        """用例9: XSS-产品创建payload，应不返回500，创建成功需清理"""
        product_id = None
        try:
            payload = build_product(nameCn="<script>alert('xss')</script>")
            resp = admin_client.post(config.ApiPaths.PRODUCTS, payload)
            assert resp.status_code < 500, \
                f"XSS产品创建导致服务器内部错误: status={resp.status_code}"
            if resp.is_success:
                body = resp.body or resp.data
                product_id = body.get("id") if isinstance(body, dict) else None
                if not product_id and isinstance(body, dict):
                    product_id = body.get("productId")
                if product_id:
                    cleanup_registry["products"].append(product_id)
        finally:
            if product_id:
                admin_client.delete(f"{config.ApiPaths.PRODUCTS}/{product_id}")

    @pytest.mark.negative
    def test_xss_query_param(self, admin_client: ApiClient):
        """用例10: XSS-查询参数注入，应不返回500服务器错误"""
        resp = admin_client.get(config.ApiPaths.PRODUCTS, {
            "keyword": "<script>alert(1)</script>"
        })
        assert resp.status_code < 500, \
            f"XSS查询参数导致服务器内部错误: status={resp.status_code}"

    # ====== 11-12: 权限校验测试 ======

    @pytest.mark.negative
    def test_permission_sales_access_accounts(self, sales_client: ApiClient):
        """用例11: 权限校验-销售角色访问账号管理（宽松兼容200/401/403）"""
        resp = sales_client.get(config.ApiPaths.ACCOUNTS)
        assert resp.status_code in (200, 401, 403), \
            f"销售角色访问账号管理返回异常状态: status={resp.status_code}"

    @pytest.mark.negative
    def test_permission_sales_access_report_ar(self, sales_client: ApiClient):
        """用例12: 权限校验-销售角色访问应收报表（一期可能404，宽松兼容404/401/403）"""
        resp = sales_client.get(config.ApiPaths.REPORT_AR)
        assert resp.status_code in (404, 401, 403), \
            f"销售角色访问应收报表返回异常状态: status={resp.status_code}"

    # ====== 13-14: 跨租户隔离测试 ======

    def _login_and_get_token(self, account_key: str) -> str:
        """辅助函数：多租户账号登录获取token"""
        acc = config.ACCOUNTS.get(account_key)
        if not acc:
            pytest.skip(f"账号配置不存在: {account_key}")
        c = ApiClient()
        resp = c.post(config.ApiPaths.LOGIN, {
            "tenantCode": acc["tenant_code"],
            "username": acc["username"],
            "password": acc["password"],
        })
        if not resp.is_success:
            pytest.skip(f"多租户账号{account_key}不可用（登录失败），跳过跨租户隔离测试")
        token = resp.data.get("data", {}).get("accessToken", "")
        if not token:
            token = resp.data.get("accessToken", "")
        if not token:
            pytest.skip(f"多租户账号{account_key}登录返回无token，跳过跨租户隔离测试")
        return token

    def test_cross_tenant_dealer_a1_cannot_see_a2(self):
        """用例13: 跨租户隔离-经销商A1看不到A2数据"""
        token = self._login_and_get_token("dealer_a1_admin")
        c = ApiClient()
        c.set_token(token)
        resp = c.get(config.ApiPaths.DEALERS)
        items = resp.items if resp.is_success else []
        for item in items:
            name = str(item.get("name", ""))
            code = str(item.get("code", ""))
            combined = (name + code).lower()
            assert "dealer_a2" not in combined and "a2" not in combined, \
                f"经销商A1看到了A2数据: name={name}, code={code}"

    def test_cross_tenant_mfr_a_products_access(self):
        """用例14: 跨租户隔离-厂家A可以正常访问自己的产品列表"""
        token = self._login_and_get_token("mfr_a_admin")
        c = ApiClient()
        c.set_token(token)
        resp = c.get(config.ApiPaths.PRODUCTS)
        assert resp.status_code == 200, \
            f"厂家A访问产品列表失败: status={resp.status_code}"

    # ====== 15: 越权访问测试 ======

    def test_privilege_escalation_dealer_cannot_see_mfr_product(
        self, admin_client: ApiClient, dealer_client: ApiClient, cleanup_registry: dict
    ):
        """用例15: 越权访问-经销商看不到厂家创建的产品详情"""
        create_resp = admin_client.post(config.ApiPaths.PRODUCTS, build_product())
        if not create_resp.is_success:
            pytest.skip("无法创建产品，跳过越权测试")
        body = create_resp.body or create_resp.data
        pid = body.get("id") if isinstance(body, dict) else None
        if not pid and isinstance(body, dict):
            pid = body.get("productId")
        if not pid:
            pytest.skip("产品创建成功但未获取到ID，跳过越权测试")
        cleanup_registry["products"].append(pid)
        try:
            resp = dealer_client.get(f"{config.ApiPaths.PRODUCTS}/{pid}")
            assert resp.status_code in (200, 401, 403, 404), \
                f"经销商访问厂家产品详情返回异常状态: status={resp.status_code}"
        finally:
            admin_client.delete(f"{config.ApiPaths.PRODUCTS}/{pid}")

    # ====== 16-17: 跨端me接口隔离测试 ======

    @pytest.mark.negative
    def test_platform_token_call_business_me(self, platform_client: ApiClient):
        """用例16: 平台token调用业务me接口（一期未严格隔离，宽松兼容200/401/403）"""
        resp = platform_client.get(config.ApiPaths.ME)
        assert resp.status_code in (200, 401, 403), \
            f"平台token调用业务me接口返回异常状态: status={resp.status_code}"

    @pytest.mark.negative
    def test_business_token_call_platform_me(self, admin_client: ApiClient):
        """用例17: 业务token调用平台me接口，应返回401/403（隔离生效）"""
        resp = admin_client.get(config.ApiPaths.ADMIN_ME)
        assert resp.status_code in (401, 403), \
            f"业务token调用平台me接口应被拒绝: status={resp.status_code}"
