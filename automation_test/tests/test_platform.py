"""
DMS自动化测试 - 平台后台模块测试
覆盖租户管理、用户管理、字典管理、菜单管理、日志查询、安全认证等场景

说明：平台后台一期大量接口未实现（返回404），相关用例先请求探测，
若 status_code == 404 则 pytest.skip；可用接口（menus / api-call-logs）正常断言。
"""
import pytest
import config
from utils.api_client import ApiClient
from utils.helpers import random_string, random_code, random_phone, random_email, today_str


def _skip_if_not_implemented(client: ApiClient, path: str, name: str):
    """探测平台接口是否一期未实现（404），未实现则跳过当前用例"""
    resp = client.get(path, {"page": 1, "size": 1})
    if resp.status_code == 404:
        pytest.skip(f"该平台接口一期未实现: {name}")


class TestPlatform:
    """平台后台测试类"""

    @pytest.mark.smoke
    def test_tenant_list_smoke(self, platform_client: ApiClient):
        """租户列表查询 - smoke"""
        resp = platform_client.get(config.ApiPaths.ADMIN_TENANTS, {"page": 1, "size": 20})
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 租户列表")
        resp.assert_success("租户列表查询失败")
        assert resp.total >= 0, "租户总数不应为负"

    @pytest.mark.api
    def test_tenant_filter_by_status_api(self, platform_client: ApiClient):
        """租户按状态筛选"""
        resp = platform_client.get(config.ApiPaths.ADMIN_TENANTS, {"status": "ACTIVE"})
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 租户列表")
        resp.assert_success("租户按状态筛选失败")

    @pytest.mark.api
    def test_tenant_search_by_keyword_api(self, platform_client: ApiClient):
        """租户关键字搜索"""
        resp = platform_client.get(config.ApiPaths.ADMIN_TENANTS, {"keyword": "MFR"})
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 租户列表")
        resp.assert_success("租户关键字搜索失败")

    @pytest.mark.crud
    def test_tenant_create_crud(self, platform_client: ApiClient, cleanup_registry: dict, random_suffix: str):
        """创建租户 - crud"""
        # 先探测list接口，404则不创建数据直接跳过
        _skip_if_not_implemented(platform_client, config.ApiPaths.ADMIN_TENANTS, "租户管理")

        payload = {
            "name": f"测试租户_{random_suffix}",
            "code": random_code("TENANT"),
            "type": "MFR",
            "contactPerson": "张三",
            "phone": random_phone(),
            "status": "ACTIVE",
        }
        resp = platform_client.post(config.ApiPaths.ADMIN_TENANTS, payload)
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 创建租户")
        resp.assert_success("创建租户失败")

        tid = resp.body.get("id") if resp.body else None
        if not tid:
            tid = resp.data.get("data", {}).get("id")

        if tid:
            cleanup_registry["others"].append(f"tenant:{tid}")
            del_resp = platform_client.delete(f"{config.ApiPaths.ADMIN_TENANTS}/{tid}")
            assert del_resp.is_success or del_resp.status_code == 200, f"清理租户失败: {del_resp.msg}"

    @pytest.mark.crud
    def test_tenant_detail_crud(self, platform_client: ApiClient, cleanup_registry: dict, random_suffix: str):
        """查询租户详情 - crud"""
        _skip_if_not_implemented(platform_client, config.ApiPaths.ADMIN_TENANTS, "租户管理")

        create_payload = {
            "name": f"测试租户详情_{random_suffix}",
            "code": random_code("TENANT"),
            "type": "DEALER",
            "contactPerson": "李四",
            "phone": random_phone(),
            "status": "ACTIVE",
        }
        create_resp = platform_client.post(config.ApiPaths.ADMIN_TENANTS, create_payload)
        if create_resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 创建租户")
        create_resp.assert_success("创建租户失败（详情测试前置）")

        tid = create_resp.body.get("id") if create_resp.body else None
        if not tid:
            tid = create_resp.data.get("data", {}).get("id")

        try:
            assert tid, "创建租户后未获取到ID"
            cleanup_registry["others"].append(f"tenant:{tid}")

            detail_resp = platform_client.get(f"{config.ApiPaths.ADMIN_TENANTS}/{tid}")
            detail_resp.assert_success("查询租户详情失败")
            assert detail_resp.body is not None, "租户详情数据为空"
        finally:
            if tid:
                platform_client.delete(f"{config.ApiPaths.ADMIN_TENANTS}/{tid}")

    @pytest.mark.crud
    def test_tenant_update_crud(self, platform_client: ApiClient, cleanup_registry: dict, random_suffix: str):
        """更新租户 - crud"""
        _skip_if_not_implemented(platform_client, config.ApiPaths.ADMIN_TENANTS, "租户管理")

        create_payload = {
            "name": f"测试租户更新_{random_suffix}",
            "code": random_code("TENANT"),
            "type": "MFR",
            "contactPerson": "王五",
            "phone": random_phone(),
            "status": "ACTIVE",
        }
        create_resp = platform_client.post(config.ApiPaths.ADMIN_TENANTS, create_payload)
        if create_resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 创建租户")
        create_resp.assert_success("创建租户失败（更新测试前置）")

        tid = create_resp.body.get("id") if create_resp.body else None
        if not tid:
            tid = create_resp.data.get("data", {}).get("id")

        try:
            assert tid, "创建租户后未获取到ID"
            cleanup_registry["others"].append(f"tenant:{tid}")

            update_payload = {
                "name": f"测试租户更新_{random_suffix}_已修改",
                "contactPerson": "王五（已修改）",
            }
            update_resp = platform_client.put(f"{config.ApiPaths.ADMIN_TENANTS}/{tid}", update_payload)
            update_resp.assert_success("更新租户失败")
        finally:
            if tid:
                platform_client.delete(f"{config.ApiPaths.ADMIN_TENANTS}/{tid}")

    @pytest.mark.api
    def test_tenant_deactivate_api(self, platform_client: ApiClient, cleanup_registry: dict, random_suffix: str):
        """租户停用"""
        _skip_if_not_implemented(platform_client, config.ApiPaths.ADMIN_TENANTS, "租户管理")

        create_payload = {
            "name": f"测试租户停用_{random_suffix}",
            "code": random_code("TENANT"),
            "type": "MFR",
            "contactPerson": "赵六",
            "phone": random_phone(),
            "status": "ACTIVE",
        }
        create_resp = platform_client.post(config.ApiPaths.ADMIN_TENANTS, create_payload)
        if create_resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 创建租户")
        create_resp.assert_success("创建租户失败（停用测试前置）")

        tid = create_resp.body.get("id") if create_resp.body else None
        if not tid:
            tid = create_resp.data.get("data", {}).get("id")

        try:
            assert tid, "创建租户后未获取到ID"
            cleanup_registry["others"].append(f"tenant:{tid}")

            deactivate_resp = platform_client.put(
                f"{config.ApiPaths.ADMIN_TENANTS}/{tid}",
                {"status": "INACTIVE"},
            )
            deactivate_resp.assert_success("租户停用失败")
        finally:
            if tid:
                platform_client.delete(f"{config.ApiPaths.ADMIN_TENANTS}/{tid}")

    @pytest.mark.crud
    def test_tenant_delete_crud(self, platform_client: ApiClient, cleanup_registry: dict, random_suffix: str):
        """删除租户 - crud"""
        _skip_if_not_implemented(platform_client, config.ApiPaths.ADMIN_TENANTS, "租户管理")

        create_payload = {
            "name": f"测试租户删除_{random_suffix}",
            "code": random_code("TENANT"),
            "type": "DEALER",
            "contactPerson": "孙七",
            "phone": random_phone(),
            "status": "ACTIVE",
        }
        create_resp = platform_client.post(config.ApiPaths.ADMIN_TENANTS, create_payload)
        if create_resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 创建租户")
        create_resp.assert_success("创建租户失败（删除测试前置）")

        tid = create_resp.body.get("id") if create_resp.body else None
        if not tid:
            tid = create_resp.data.get("data", {}).get("id")

        assert tid, "创建租户后未获取到ID"
        cleanup_registry["others"].append(f"tenant:{tid}")

        delete_resp = platform_client.delete(f"{config.ApiPaths.ADMIN_TENANTS}/{tid}")
        assert delete_resp.is_success or delete_resp.status_code == 200, f"删除租户失败: {delete_resp.msg}"

    @pytest.mark.smoke
    def test_admin_user_list_smoke(self, platform_client: ApiClient):
        """平台用户列表 - smoke"""
        resp = platform_client.get(config.ApiPaths.ADMIN_USERS)
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 平台用户列表")
        resp.assert_success("平台用户列表查询失败")

    @pytest.mark.api
    def test_dict_type_list_api(self, platform_client: ApiClient):
        """字典类型列表"""
        resp = platform_client.get(config.ApiPaths.ADMIN_DICT_TYPES)
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 字典类型列表")
        resp.assert_success("字典类型列表查询失败")

    @pytest.mark.crud
    def test_dict_type_create_crud(self, platform_client: ApiClient, cleanup_registry: dict, random_suffix: str):
        """创建字典类型 - crud"""
        # 先探测list接口，404则不创建数据直接跳过
        _skip_if_not_implemented(platform_client, config.ApiPaths.ADMIN_DICT_TYPES, "字典类型管理")

        payload = {
            "name": f"测试字典_{random_suffix}",
            "code": random_code("DICT"),
            "description": "测试",
        }
        resp = platform_client.post(config.ApiPaths.ADMIN_DICT_TYPES, payload)
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 创建字典类型")
        resp.assert_success("创建字典类型失败")

        did = resp.body.get("id") if resp.body else None
        if not did:
            did = resp.data.get("data", {}).get("id")

        if did:
            cleanup_registry["others"].append(f"dict_type:{did}")
            del_resp = platform_client.delete(f"{config.ApiPaths.ADMIN_DICT_TYPES}/{did}")
            assert del_resp.is_success or del_resp.status_code == 200, f"清理字典类型失败: {del_resp.msg}"

    @pytest.mark.api
    def test_dict_item_list_api(self, platform_client: ApiClient):
        """字典项列表"""
        resp = platform_client.get(config.ApiPaths.ADMIN_DICT_ITEMS, {"dictType": "product_type"})
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 字典项列表")
        resp.assert_success("字典项列表查询失败")

    @pytest.mark.api
    def test_menu_list_api(self, platform_client: ApiClient):
        """菜单列表（一期可用，返回list）"""
        resp = platform_client.get(config.ApiPaths.ADMIN_MENUS)
        # 可用接口：断言成功 或 body为list
        assert resp.is_success or isinstance(resp.body, list), \
            f"菜单列表查询失败: status={resp.status_code}, body类型={type(resp.body).__name__}"

    @pytest.mark.smoke
    def test_audit_log_list_smoke(self, platform_client: ApiClient):
        """审计日志 - smoke"""
        resp = platform_client.get(config.ApiPaths.ADMIN_AUDIT_LOGS, {"page": 1, "size": 20})
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 审计日志")
        resp.assert_success("审计日志查询失败")

    @pytest.mark.api
    def test_login_log_list_api(self, platform_client: ApiClient):
        """登录日志"""
        resp = platform_client.get(config.ApiPaths.ADMIN_LOGIN_LOGS)
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 登录日志")
        resp.assert_success("登录日志查询失败")

    @pytest.mark.api
    def test_api_log_list_api(self, platform_client: ApiClient):
        """API调用日志（一期可用，返回分页dict）"""
        resp = platform_client.get(config.ApiPaths.ADMIN_API_LOGS)
        assert resp.is_success, f"API调用日志查询失败: status={resp.status_code}, msg={resp.msg}"
        assert resp.total >= 0, f"API调用日志total不应为负: {resp.total}"

    @pytest.mark.api
    def test_tenant_binding_list_api(self, platform_client: ApiClient):
        """租户经销商绑定列表"""
        resp = platform_client.get(config.ApiPaths.ADMIN_TENANT_BINDINGS)
        if resp.status_code == 404:
            pytest.skip("该平台接口一期未实现: 租户经销商绑定列表")
        resp.assert_success("租户经销商绑定列表查询失败")

    @pytest.mark.security
    def test_biz_token_access_platform_security(self, admin_client: ApiClient):
        """业务token访问平台接口 - security
        后端一期隔离生效：业务token访问 /api/admin/* 返回401/403
        （即使接口本身未实现404，隔离层会先拦截返回401）
        """
        resp = admin_client.get(config.ApiPaths.ADMIN_TENANTS)
        assert resp.is_auth_error or resp.status_code in (401, 403), \
            f"业务token应被平台接口拒绝，实际Status={resp.status_code} Code={resp.code}"
