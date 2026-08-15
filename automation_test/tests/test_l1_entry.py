"""
L1 入口层测试 - 所有模块API存在性验证
验证每个模块的API接口是否存在、可调用、返回正常结构
覆盖：业务前台24个 + 报表3个 + 审批4个 + 平台后台9个 + 核心API 5个 = 45个入口点
"""
import pytest
import sys
sys.path.insert(0, '.')
import config


# L1入口层：业务前台模块（28个）
BIZ_MODULES = [
    # 基础数据（11个）
    ("产品管理", config.ApiPaths.PRODUCTS, True),
    ("产品分类", config.ApiPaths.CATEGORIES, True),
    ("产品线", config.ApiPaths.PRODUCT_LINES, True),
    ("包装规格", config.ApiPaths.PACKAGE_LEVELS, True),
    ("产品套包", config.ApiPaths.PRODUCT_BUNDLES, True),
    ("经销商管理", config.ApiPaths.DEALERS, True),
    ("医院管理", config.ApiPaths.HOSPITALS, True),
    ("供应商", config.ApiPaths.SUPPLIERS, True),
    ("仓库管理", config.ApiPaths.WAREHOUSES, True),
    ("区域管理", config.ApiPaths.REGIONS, True),
    ("产品价格", config.ApiPaths.PRODUCT_PRICES, True),
    # 合同授权（3个）
    ("合同工作台", config.ApiPaths.CONTRACTS, True),
    ("合同模板", config.ApiPaths.CONTRACT_TEMPLATES, True),
    ("授权管理", config.ApiPaths.AUTHORIZATIONS, True),
    # 订单业务（4个）
    ("销售订单", config.ApiPaths.SALES_ORDERS, True),
    ("采购订单", config.ApiPaths.PURCHASE_ORDERS, True),
    ("销退订单", config.ApiPaths.SALES_RETURNS, True),
    ("采退订单", config.ApiPaths.PURCHASE_RETURNS, True),
    # 库存业务（5个）
    ("库存查询", config.ApiPaths.INVENTORY, True),
    ("收货入库", config.ApiPaths.GOODS_RECEIPTS, True),
    ("销售出库", config.ApiPaths.GOODS_ISSUES, True),
    ("库存移动", config.ApiPaths.STOCK_MOVES, True),
    ("库存调整", config.ApiPaths.STOCK_ADJUSTMENTS, True),
    # 手术与营销（2个）
    ("手术报台", config.ApiPaths.SURGERY_REPORTS, True),
    ("促销规则", config.ApiPaths.PROMOTIONS, True),
    # 用户权限（3个）
    ("用户管理", config.ApiPaths.ACCOUNTS, True),
    ("角色管理", config.ApiPaths.ROLES, True),
    ("销售岗位", config.ApiPaths.SALES_POSITIONS, True),
    # 消息中心（1个）
    ("消息通知", config.ApiPaths.NOTIFICATIONS, True),
    # 产品对码（1个）
    ("产品对码", config.ApiPaths.PRODUCT_MAPPINGS, True),
]

# L1入口层：报表模块（3个）
REPORT_MODULES = [
    ("订单追溯报表", config.ApiPaths.REPORT_ORDER_TRACE, True),
    ("销售排行报表", config.ApiPaths.REPORT_SALES_RANK, True),
    ("库存周转报表", config.ApiPaths.REPORT_INVENTORY_TURNOVER, True),
]

# L1入口层：审批模块（4个）
APPROVAL_MODULES = [
    ("审批实例", config.ApiPaths.APPROVAL_INSTANCES, True),
    ("审批流程", config.ApiPaths.APPROVAL_FLOWS, True),
    ("审批委托", config.ApiPaths.APPROVAL_DELEGATES, True),
    ("审批监控", config.ApiPaths.APPROVAL_MONITORS, True),
]

# L1入口层：平台后台（9个）
ADMIN_MODULES = [
    ("租户管理", config.ApiPaths.ADMIN_TENANTS, True),
    ("平台用户", config.ApiPaths.ADMIN_USERS, True),
    ("字典类型", config.ApiPaths.ADMIN_DICT_TYPES, True),
    ("字典项", config.ApiPaths.ADMIN_DICT_ITEMS, True),
    ("平台菜单", config.ApiPaths.ADMIN_MENUS, True),
    ("审计日志", config.ApiPaths.ADMIN_AUDIT_LOGS, True),
    ("登录日志", config.ApiPaths.ADMIN_LOGIN_LOGS, True),
    ("接口日志", config.ApiPaths.ADMIN_API_LOGS, True),
    ("租户绑定", config.ApiPaths.ADMIN_TENANT_BINDINGS, True),
]


@pytest.mark.api
@pytest.mark.L1
class TestL1BizModules:
    """业务前台L1入口层 - 28个模块API存在性验证"""

    @pytest.mark.parametrize("module_name,api_path,expected_ok",
                             [(m[0], m[1], m[2]) for m in BIZ_MODULES])
    def test_biz_module_api_exists(self, admin_client, module_name, api_path, expected_ok):
        """业务前台各模块列表API存在性验证：HTTP 200 + 业务成功"""
        resp = admin_client.get(api_path, {"page": 1, "pageSize": 1})
        if expected_ok:
            resp.assert_success(f"{module_name}列表查询")
            assert resp.status_code == 200, f"{module_name} API不可达: {resp.status_code}"
        else:
            pytest.xfail(f"{module_name} API未实现（已知）")

    @pytest.mark.parametrize("module_name,api_path,expected_ok",
                             [(m[0], m[1], m[2]) for m in BIZ_MODULES if m[2]])
    def test_biz_module_response_structure(self, admin_client, module_name, api_path, expected_ok):
        """业务前台各模块API返回结构检查：顶层必须有code+data"""
        resp = admin_client.get(api_path, {"page": 1, "pageSize": 1})
        resp.assert_success(f"{module_name}列表查询")
        body = resp.data
        assert isinstance(body, dict), f"{module_name} 返回非dict"
        assert 'code' in body, f"{module_name} 返回缺少code字段"
        assert 'data' in body, f"{module_name} 返回缺少data字段"


@pytest.mark.api
@pytest.mark.L1
class TestL1ReportModules:
    """报表模块L1入口层验证"""

    @pytest.mark.parametrize("module_name,api_path,expected_ok",
                             [(m[0], m[1], m[2]) for m in REPORT_MODULES])
    def test_report_api_exists(self, admin_client, module_name, api_path, expected_ok):
        """报表API存在性验证"""
        resp = admin_client.get(api_path, {})
        if expected_ok:
            assert resp.status_code == 200, f"{module_name} API不可达: {resp.status_code}"
        else:
            pytest.xfail(f"{module_name} API未实现（已知）")


@pytest.mark.api
@pytest.mark.L1
class TestL1ApprovalModules:
    """审批模块L1入口层验证"""

    @pytest.mark.parametrize("module_name,api_path,expected_ok",
                             [(m[0], m[1], m[2]) for m in APPROVAL_MODULES])
    def test_approval_api_exists(self, admin_client, module_name, api_path, expected_ok):
        """审批模块API存在性验证"""
        resp = admin_client.get(api_path, {"page": 1, "pageSize": 1})
        assert resp.status_code == 200, f"{module_name} API不可达: {resp.status_code}"


@pytest.mark.api
@pytest.mark.L1
class TestL1AdminModules:
    """平台后台L1入口层 - 9个模块API存在性验证"""

    @pytest.mark.parametrize("module_name,api_path,expected_ok",
                             [(m[0], m[1], m[2]) for m in ADMIN_MODULES])
    def test_admin_module_api_exists(self, platform_client, module_name, api_path, expected_ok):
        """平台后台各模块API存在性验证"""
        resp = platform_client.get(api_path, {"page": 1, "pageSize": 1})
        if expected_ok:
            assert resp.status_code == 200, f"{module_name} API不可达: {resp.status_code}"
        else:
            pytest.xfail(f"{module_name} API未实现（已知BUG-020）")


@pytest.mark.api
@pytest.mark.L1
class TestL1CoreApis:
    """核心L1入口 - 登录/用户信息/菜单等基础API"""

    def test_login_api_exists(self, admin_client):
        """业务登录接口存在且可用（通过admin_client fixture已验证登录成功）"""
        assert admin_client.token is not None and admin_client.token != "", "登录API未返回有效token"

    def test_me_api_exists(self, admin_client):
        """获取当前用户信息接口存在"""
        resp = admin_client.get(config.ApiPaths.ME)
        resp.assert_success("获取当前用户信息")
        assert resp.status_code == 200, "用户信息API不可达"

    def test_permissions_api_exists(self, admin_client):
        """权限列表接口存在"""
        resp = admin_client.get(config.ApiPaths.PERMISSIONS)
        assert resp.status_code == 200, "权限列表API不可达"

    def test_ui_layout_api_exists(self, admin_client):
        """菜单布局接口存在"""
        resp = admin_client.get(config.ApiPaths.UI_LAYOUT)
        assert resp.status_code == 200, "菜单布局API不可达"

    def test_admin_login_api_exists(self, platform_client):
        """平台后台登录接口存在（通过platform_client fixture已验证登录成功）"""
        assert platform_client.token is not None and platform_client.token != "", "平台登录API未返回有效token"

    def test_admin_me_api_exists(self, platform_client):
        """平台后台当前用户信息接口存在"""
        resp = platform_client.get(config.ApiPaths.ADMIN_ME)
        assert resp.status_code == 200, "平台用户信息API不可达"
