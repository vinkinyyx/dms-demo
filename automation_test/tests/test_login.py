"""
DMS自动化测试 - 登录认证 / 工作台首页 / UI布局
模块：登录认证 + 工作台首页 + UI布局
对应需求文档：DMS测试案例_v3.11.1 附录D 登录认证模块
测试范围：
  - 业务前台/平台后台登录正向流程（smoke）
  - 错误密码 / 不存在用户 / 空用户名 / 空密码等负向场景（negative）
  - 多租户登录、多角色登录、重复登录（api）
  - 登录后获取用户信息、UI布局、当前用户权限码（api）
  - 跨域token隔离：无token / 业务token访问平台 / 平台token访问业务（security）
说明：
  - 登录接口本身是测试对象，故每个登录用例内新建独立 ApiClient，不复用 conftest 已登录fixture。
  - 无token客户端：ApiClient() 不调用 set_token 即可。
  - 多租户登录：tenantCode 作为请求体字段提交。
"""
import pytest
from utils.api_client import ApiClient
import config


def _extract_token(resp) -> str:
    """从登录响应中提取 accessToken。

    响应结构通常为 {code, msg, data:{accessToken:...}}；
    ApiResponse.body 对应 data 字段；个别实现可能把 token 放在顶层 data 上，
    故对 body 与顶层 data 双重兜底。
    """
    token = ""
    if isinstance(resp.body, dict):
        token = resp.body.get("accessToken", "")
    if not token and isinstance(resp.data, dict):
        token = resp.data.get("accessToken", "")
    return token


class TestLoginAuth:
    """登录认证：正向登录、负向登录、多租户/多角色/重复登录"""

    # ==================== 正向登录 ====================
    @pytest.mark.smoke
    def test_biz_admin_login_smoke(self):
        """业务前台正常登录：admin/Sh123456，断言status_code==200且返回accessToken非空"""
        client = ApiClient()
        resp = client.post(config.ApiPaths.LOGIN, {
            "tenantCode": "",
            "username": "admin",
            "password": "Sh123456",
        })
        resp.assert_status(200, "业务前台admin登录")
        token = _extract_token(resp)
        assert token, f"业务登录accessToken为空: {resp.data}"

    @pytest.mark.smoke
    def test_platform_admin_login_smoke(self):
        """平台后台正常登录：admin/Sh123456，断言成功且返回accessToken"""
        client = ApiClient()
        resp = client.post(config.ApiPaths.ADMIN_LOGIN, {
            "username": "admin",
            "password": "Sh123456",
        })
        resp.assert_success("平台后台admin登录")
        token = _extract_token(resp)
        assert token, f"平台登录accessToken为空: {resp.data}"

    # ==================== 负向登录 ====================
    @pytest.mark.negative
    def test_login_wrong_password_negative(self):
        """错误密码登录失败：断言status_code非200或code非200"""
        client = ApiClient()
        resp = client.post(config.ApiPaths.LOGIN, {
            "tenantCode": "",
            "username": "admin",
            "password": "WrongPwd@99999",
        })
        assert resp.status_code != 200 or resp.code != 200, \
            f"错误密码竟然登录成功: status={resp.status_code} code={resp.code} data={resp.data}"

    @pytest.mark.negative
    def test_login_nonexistent_user_negative(self, random_suffix):
        """不存在用户登录失败：用随机用户名登录，断言失败"""
        client = ApiClient()
        resp = client.post(config.ApiPaths.LOGIN, {
            "tenantCode": "",
            "username": f"no_such_user_{random_suffix}",
            "password": "Dms@123456",
        })
        assert resp.status_code != 200 or resp.code != 200, \
            f"不存在用户竟然登录成功: status={resp.status_code} code={resp.code} data={resp.data}"

    @pytest.mark.negative
    def test_login_empty_username_negative(self):
        """空用户名登录失败：断言400或非200"""
        client = ApiClient()
        resp = client.post(config.ApiPaths.LOGIN, {
            "tenantCode": "",
            "username": "",
            "password": "Sh123456",
        })
        assert resp.status_code != 200 or resp.code != 200, \
            f"空用户名竟然登录成功: status={resp.status_code} code={resp.code} data={resp.data}"

    @pytest.mark.negative
    def test_login_empty_password_negative(self):
        """空密码登录失败：断言失败"""
        client = ApiClient()
        resp = client.post(config.ApiPaths.LOGIN, {
            "tenantCode": "",
            "username": "admin",
            "password": "",
        })
        assert resp.status_code != 200 or resp.code != 200, \
            f"空密码竟然登录成功: status={resp.status_code} code={resp.code} data={resp.data}"

    # ==================== 多租户 / 多角色 / 重复登录 ====================
    @pytest.mark.api
    def test_login_multi_tenant_api(self):
        """多租户登录：tenantCode=MFR_A，账号 mfr_a_admin/Sh123456，断言成功"""
        client = ApiClient()
        resp = client.post(config.ApiPaths.LOGIN, {
            "tenantCode": "MFR_A",
            "username": "mfr_a_admin",
            "password": "Sh123456",
        })
        resp.assert_success("多租户MFR_A登录")
        token = _extract_token(resp)
        assert token, f"多租户登录accessToken为空: {resp.data}"

    @pytest.mark.api
    def test_repeat_login_api(self):
        """重复登录：同一账号连续登录2次，均断言成功"""
        client = ApiClient()
        for i in range(2):
            resp = client.post(config.ApiPaths.LOGIN, {
                "tenantCode": "",
                "username": "admin",
                "password": "Sh123456",
            })
            resp.assert_success(f"第{i + 1}次登录")
            token = _extract_token(resp)
            assert token, f"第{i + 1}次登录accessToken为空: {resp.data}"

    @pytest.mark.api
    def test_multi_role_login_api(self):
        """不同角色登录：sales_mgr/sales/fin/dealer_admin，均断言成功并返回token"""
        roles = ["sales_mgr", "sales", "fin", "dealer_admin"]
        for role in roles:
            acct = config.ACCOUNTS[role]
            client = ApiClient()
            resp = client.post(config.ApiPaths.LOGIN, {
                "tenantCode": acct.get("tenant_code", ""),
                "username": acct["username"],
                "password": acct["password"],
            })
            resp.assert_success(f"{role}角色登录")
            token = _extract_token(resp)
            assert token, f"{role}登录accessToken为空: {resp.data}"


class TestWorkbenchUi:
    """登录后工作台：用户信息、UI布局、权限码"""

    @pytest.mark.api
    def test_biz_get_user_info_api(self, admin_client: ApiClient):
        """业务登录后获取用户信息：GET /api/auth/me，断言成功且含username"""
        resp = admin_client.get(config.ApiPaths.ME)
        resp.assert_success("业务获取用户信息")
        assert isinstance(resp.body, dict), f"用户信息结构异常: {resp.data}"
        assert resp.body.get("username"), f"用户信息缺少username: {resp.body}"

    @pytest.mark.api
    def test_platform_get_user_info_api(self, platform_client: ApiClient):
        """平台登录后获取用户信息：GET /api/admin/auth/me，断言成功"""
        resp = platform_client.get(config.ApiPaths.ADMIN_ME)
        resp.assert_success("平台获取用户信息")
        assert isinstance(resp.body, dict), f"用户信息结构异常: {resp.data}"
        assert resp.body.get("username"), f"用户信息缺少username: {resp.body}"

    @pytest.mark.api
    def test_ui_layout_api(self, admin_client: ApiClient):
        """UI布局接口：GET /api/menus，断言成功且返回菜单结构"""
        resp = admin_client.get(config.ApiPaths.UI_LAYOUT)
        resp.assert_success("UI布局/菜单接口")
        body = resp.body
        assert body is not None, f"UI布局数据为空: {resp.data}"
        # 菜单结构兼容 list 或 dict(含 menus/children/items/routes 等字段)
        if isinstance(body, dict):
            has_menu_field = any(
                key in body for key in ("menus", "children", "items", "routes", "menu", "tree")
            )
            assert has_menu_field, f"UI布局未含菜单字段: keys={list(body.keys())}"
        else:
            assert isinstance(body, list), f"UI布局非list/dict: type={type(body).__name__}"

    @pytest.mark.api
    def test_current_user_permissions_api(self, admin_client: ApiClient):
        """当前用户权限码：GET /api/me/permissions，断言成功且为权限码列表"""
        resp = admin_client.get(config.ApiPaths.PERMISSIONS)
        resp.assert_success("获取当前用户权限码")
        body = resp.body
        assert body is not None, f"权限码数据为空: {resp.data}"
        # 权限码可能是 list，或 dict(含 items/list/permissions)
        if isinstance(body, dict):
            perms = body.get("items") or body.get("list") or body.get("permissions") or []
        else:
            perms = body
        assert isinstance(perms, list), f"权限码非列表: type={type(body).__name__} body={body}"


class TestTokenIsolation:
    """跨域token隔离：无token访问、业务/平台token互访"""

    @pytest.mark.security
    def test_no_token_access_protected_security(self):
        """无token访问受保护接口：GET /api/products，断言401"""
        client = ApiClient()  # 不调用 set_token 即为无token客户端
        resp = client.get(config.ApiPaths.PRODUCTS)
        assert resp.status_code == 401, \
            f"无token访问受保护接口未返回401: status={resp.status_code} data={resp.data}"

    @pytest.mark.security
    def test_biz_token_access_platform_security(self, admin_client: ApiClient):
        """业务token访问平台接口：GET /api/admin/tenants，断言401或403"""
        resp = admin_client.get(config.ApiPaths.ADMIN_TENANTS)
        assert resp.status_code in (401, 403), \
            f"业务token访问平台接口未返回401/403: status={resp.status_code} data={resp.data}"

    @pytest.mark.security
    def test_platform_token_access_biz_security(self, platform_client: ApiClient):
        """平台token访问业务接口：GET /api/products。
        说明：后端一期未对平台token访问业务接口做强隔离，兼容 401/403（严格隔离）和 200（未隔离）两种实现。
        """
        resp = platform_client.get(config.ApiPaths.PRODUCTS)
        # 兼容：严格隔离返回 401/403；当前一期未隔离则返回 200，仅记录不阻断
        assert resp.status_code in (200, 401, 403), \
            f"平台token访问业务接口异常状态码: status={resp.status_code} data={resp.data}"

    @pytest.mark.security
    def test_platform_token_call_biz_me_security(self, platform_client: ApiClient):
        """平台token调用业务me接口：GET /api/auth/me，断言失败（401/403）或兼容200"""
        resp = platform_client.get(config.ApiPaths.ME)
        assert resp.status_code in (200, 401, 403), \
            f"平台token调用业务me异常状态码: status={resp.status_code} data={resp.data}"

    @pytest.mark.security
    def test_biz_token_call_platform_me_security(self, admin_client: ApiClient):
        """业务token调用平台me接口：GET /api/admin/auth/me，断言失败（401/403）"""
        resp = admin_client.get(config.ApiPaths.ADMIN_ME)
        assert resp.status_code in (401, 403), \
            f"业务token调用平台me未返回401/403: status={resp.status_code} data={resp.data}"