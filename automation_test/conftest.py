"""
DMS自动化测试 - Pytest全局Fixture
提供登录Token、API客户端、测试数据清理等公共能力
"""
import pytest
import logging
from utils.api_client import ApiClient
from utils.helpers import random_string
import config

# ====== 日志配置 ======
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("DMS-AutoTest")


# ====== Session级Fixture：登录Token ======

def _extract_token(resp) -> str:
    """统一token提取：兼容 resp.body.accessToken 和 resp.data.data.accessToken"""
    if isinstance(resp.body, dict) and resp.body.get("accessToken"):
        return resp.body["accessToken"]
    if isinstance(resp.data, dict):
        d = resp.data.get("data", {})
        if isinstance(d, dict) and d.get("accessToken"):
            return d["accessToken"]
    return ""


@pytest.fixture(scope="session")
def admin_client() -> ApiClient:
    """业务前台管理员Token（sys_admin角色，全部权限）"""
    client = ApiClient()
    resp = client.post(config.ApiPaths.LOGIN, {
        "tenantCode": "",
        "username": "admin",
        "password": "Sh123456",
    })
    assert resp.is_success, f"业务登录失败: Status={resp.status_code} Code={resp.code} Body={resp.data}"
    token = _extract_token(resp)
    assert token, f"业务登录未返回token: {resp.data}"
    client.set_token(token, is_admin=False)
    logger.info("✅ 业务前台admin登录成功")
    return client


@pytest.fixture(scope="session")
def platform_client() -> ApiClient:
    """平台后台管理员Token"""
    client = ApiClient()
    resp = client.post(config.ApiPaths.ADMIN_LOGIN, {
        "username": "admin",
        "password": "Sh123456",
    })
    assert resp.is_success, f"平台登录失败: Status={resp.status_code} Code={resp.code} Body={resp.data}"
    token = _extract_token(resp)
    assert token, f"平台登录未返回token: {resp.data}"
    client.set_token(token, is_admin=True)
    logger.info("✅ 平台后台admin登录成功")
    return client


@pytest.fixture(scope="session")
def sales_client() -> ApiClient:
    """销售角色Token"""
    client = ApiClient()
    resp = client.post(config.ApiPaths.LOGIN, {
        "tenantCode": "",
        "username": "sales",
        "password": "Dms@123456",
    })
    token = _extract_token(resp)
    assert token, f"sales登录失败: {resp.data}"
    client.set_token(token)
    logger.info("✅ sales角色登录成功")
    return client


@pytest.fixture(scope="session")
def sales_mgr_client() -> ApiClient:
    """销售经理角色Token"""
    client = ApiClient()
    resp = client.post(config.ApiPaths.LOGIN, {
        "tenantCode": "",
        "username": "sales_mgr",
        "password": "Dms@123456",
    })
    token = _extract_token(resp)
    assert token, f"sales_mgr登录失败: {resp.data}"
    client.set_token(token)
    logger.info("✅ sales_mgr角色登录成功")
    return client


@pytest.fixture(scope="session")
def fin_client() -> ApiClient:
    """财务角色Token"""
    client = ApiClient()
    resp = client.post(config.ApiPaths.LOGIN, {
        "tenantCode": "",
        "username": "fin",
        "password": "Dms@123456",
    })
    token = _extract_token(resp)
    assert token, f"fin登录失败: {resp.data}"
    client.set_token(token)
    logger.info("✅ fin角色登录成功")
    return client


@pytest.fixture(scope="session")
def dealer_client() -> ApiClient:
    """经销商角色Token"""
    client = ApiClient()
    resp = client.post(config.ApiPaths.LOGIN, {
        "tenantCode": "",
        "username": "dealer_admin",
        "password": "Dms@123456",
    })
    token = _extract_token(resp)
    assert token, f"dealer_admin登录失败: {resp.data}"
    client.set_token(token)
    logger.info("✅ dealer_admin角色登录成功")
    return client


# ====== Session级Fixture：基础数据ID ======

@pytest.fixture(scope="session")
def first_product_id(admin_client: ApiClient) -> str:
    """获取第一个产品ID"""
    resp = admin_client.get(config.ApiPaths.PRODUCTS, {"page": 1, "size": 1})
    items = resp.items
    if items:
        return items[0].get("id", "")
    return ""


@pytest.fixture(scope="session")
def first_dealer_id(admin_client: ApiClient) -> str:
    """获取第一个经销商ID"""
    resp = admin_client.get(config.ApiPaths.DEALERS, {"page": 1, "size": 1})
    items = resp.items
    if items:
        return items[0].get("id", "")
    return ""


@pytest.fixture(scope="session")
def first_warehouse_id(admin_client: ApiClient) -> str:
    """获取第一个仓库ID"""
    resp = admin_client.get(config.ApiPaths.WAREHOUSES, {"page": 1, "size": 1})
    items = resp.items
    if items:
        return items[0].get("id", "")
    return ""


@pytest.fixture(scope="session")
def first_supplier_id(admin_client: ApiClient) -> str:
    """获取第一个供应商ID"""
    resp = admin_client.get(config.ApiPaths.SUPPLIERS, {"page": 1, "size": 1})
    items = resp.items
    if items:
        return items[0].get("id", "")
    return ""


@pytest.fixture(scope="session")
def first_hospital_id(admin_client: ApiClient) -> str:
    """获取第一个医院ID"""
    resp = admin_client.get(config.ApiPaths.HOSPITALS, {"page": 1, "size": 1})
    items = resp.items
    if items:
        return items[0].get("id", "")
    return ""


# ====== Function级Fixture：测试数据清理 ======

@pytest.fixture
def cleanup_registry():
    """测试数据清理注册器：测试中创建的数据ID注册到此处，测试后自动清理"""
    registry = {"products": [], "dealers": [], "orders": [], "categories": [], "others": []}

    yield registry

    # fixture销毁时不自动清理（避免误删环境数据），需要手动在测试中清理
    # 这里只做日志记录
    for key, ids in registry.items():
        if ids:
            logger.info(f"[清理提示] {key} 待清理ID: {ids}")


# ====== 通用Fixture ======

@pytest.fixture
def random_suffix() -> str:
    """随机后缀，用于测试数据唯一性"""
    return random_string(length=8)


@pytest.fixture
def assert_helper():
    """断言辅助"""
    class AssertHelper:
        @staticmethod
        def assert_not_empty(value, msg="值不能为空"):
            assert value is not None and value != "", msg

        @staticmethod
        def assert_in_list(item, items, msg="元素不在列表中"):
            assert item in items, f"{msg}: {item} not in {items}"

        @staticmethod
        def assert_pagination(resp):
            """断言分页响应"""
            assert resp.is_success, f"分页请求失败: {resp.msg}"
            assert resp.body is not None, "分页数据为空"
            assert "total" in resp.body or "items" in resp.body or "list" in resp.body, "分页结构不正确"

    return AssertHelper()
