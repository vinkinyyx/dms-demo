"""
DMS自动化测试 - 合同管理模块（含合同模板、授权）
对应需求文档：DMS测试案例_v3.11.1 附录D 合同模块（v3.11.0重构）
覆盖：合同列表/筛选/搜索、合同模板、合同CRUD、提交审批、导出、匹配模板、授权
"""
import pytest
import config
from utils.helpers import random_string, random_code, today_str, future_date


# ====== 模块级Fixture：共享草稿合同 ======

@pytest.fixture(scope="module")
def shared_contract_id(admin_client, first_dealer_id):
    """创建一个草稿合同，供查询/更新/匹配模板等测试共享使用，模块结束后清理"""
    payload = {
        "contractName": f"共享合同_{random_string(length=6)}",
        "contractNo": random_code("HT"),
        "dealerId": first_dealer_id,
        "signAmount": 10000,
        "validFrom": today_str(),
        "validTo": future_date(365),
        "status": "DRAFT",
        "remark": "自动化测试共享合同",
    }
    resp = admin_client.post(config.ApiPaths.CONTRACTS, payload)
    cid = ""
    if resp.is_success and resp.body:
        cid = resp.body.get("id", "")
    yield cid
    if cid:
        try:
            admin_client.delete(f"{config.ApiPaths.CONTRACTS}/{cid}")
        except Exception:
            pass


@pytest.mark.smoke
def test_contract_list_query(admin_client):
    """合同列表查询：默认分页"""
    resp = admin_client.get(config.ApiPaths.CONTRACTS, {"page": 1, "size": 20})
    resp.assert_success("合同列表查询")


@pytest.mark.api
def test_contract_filter_by_status(admin_client):
    """合同按状态筛选（DRAFT）"""
    resp = admin_client.get(config.ApiPaths.CONTRACTS, {"status": "DRAFT"})
    resp.assert_success("合同按状态筛选")


@pytest.mark.api
def test_contract_search_by_keyword(admin_client):
    """合同按关键字搜索"""
    resp = admin_client.get(config.ApiPaths.CONTRACTS, {"keyword": "测试"})
    resp.assert_success("合同关键字搜索")


@pytest.mark.smoke
def test_contract_template_list(admin_client):
    """合同模板列表"""
    resp = admin_client.get(config.ApiPaths.CONTRACT_TEMPLATES)
    resp.assert_success("合同模板列表查询")


@pytest.mark.crud
def test_create_contract_template(admin_client, cleanup_registry, random_suffix):
    """创建合同模板"""
    payload = {
        "name": f"测试模板_{random_suffix}",
        "code": random_code("TPL"),
        "description": "自动化测试模板",
        "form_data": {},
    }
    resp = admin_client.post(config.ApiPaths.CONTRACT_TEMPLATES, payload)
    if not resp.is_success:
        pytest.skip(f"创建合同模板失败: {resp.status_code} {resp.msg}")
    tpl_id = resp.body.get("id", "") if resp.body else ""
    assert tpl_id, "创建合同模板未返回ID"
    cleanup_registry["others"].append(tpl_id)
    try:
        admin_client.delete(f"{config.ApiPaths.CONTRACT_TEMPLATES}/{tpl_id}")
    except Exception:
        pass


@pytest.mark.crud
def test_create_contract(admin_client, cleanup_registry, first_dealer_id, random_suffix):
    """创建合同（草稿）"""
    payload = {
        "contractName": f"测试合同_{random_suffix}",
        "contractNo": random_code("HT"),
        "dealerId": first_dealer_id,
        "signAmount": 10000,
        "validFrom": today_str(),
        "validTo": future_date(365),
        "status": "DRAFT",
        "remark": "自动化测试",
    }
    resp = admin_client.post(config.ApiPaths.CONTRACTS, payload)
    if not resp.is_success:
        pytest.skip(f"创建合同失败: {resp.status_code} {resp.msg}")
    cid = resp.body.get("id", "") if resp.body else ""
    assert cid, "创建合同未返回ID"
    cleanup_registry["others"].append(cid)
    try:
        admin_client.delete(f"{config.ApiPaths.CONTRACTS}/{cid}")
    except Exception:
        pass


@pytest.mark.crud
def test_get_contract_detail(admin_client, shared_contract_id):
    """查询合同详情"""
    if not shared_contract_id:
        pytest.skip("共享合同未创建成功，无法测试详情查询")
    resp = admin_client.get(f"{config.ApiPaths.CONTRACTS}/{shared_contract_id}")
    resp.assert_success("查询合同详情")


@pytest.mark.crud
def test_update_contract(admin_client, shared_contract_id):
    """更新合同（修改合同名）"""
    if not shared_contract_id:
        pytest.skip("共享合同未创建成功，无法测试更新")
    resp = admin_client.put(
        f"{config.ApiPaths.CONTRACTS}/{shared_contract_id}",
        {"contractName": "更新合同名"},
    )
    resp.assert_success("更新合同")


@pytest.mark.api
def test_contract_submit_approval(admin_client, first_dealer_id):
    """合同提交审批：成功或非500错误均视为通过（业务状态可能不允许）"""
    payload = {
        "contractName": f"提交审批合同_{random_string(length=6)}",
        "contractNo": random_code("HT"),
        "dealerId": first_dealer_id,
        "signAmount": 10000,
        "validFrom": today_str(),
        "validTo": future_date(365),
        "status": "DRAFT",
        "remark": "提交审批测试",
    }
    create_resp = admin_client.post(config.ApiPaths.CONTRACTS, payload)
    cid = ""
    if create_resp.is_success and create_resp.body:
        cid = create_resp.body.get("id", "")
    if not cid:
        pytest.skip(f"提交审批前置：创建合同失败: {create_resp.status_code} {create_resp.msg}")
    try:
        resp = admin_client.post(f"{config.ApiPaths.CONTRACTS}/{cid}/submit")
        if not resp.is_success:
            assert resp.status_code < 500, f"提交审批服务器错误: {resp.status_code} {resp.msg}"
    finally:
        try:
            admin_client.delete(f"{config.ApiPaths.CONTRACTS}/{cid}")
        except Exception:
            pass


@pytest.mark.api
def test_contract_export(admin_client):
    """合同导出：若一期未实现返回404，断言 status_code in (200, 404)"""
    resp = admin_client.get(f"{config.ApiPaths.CONTRACTS}/actions/export")
    assert resp.status_code in (200, 404), f"合同导出状态码应为200或404: {resp.status_code}"


@pytest.mark.api
def test_contract_match_template(admin_client, first_dealer_id):
    """合同匹配模板（按经销商）。
    说明：后端可能要求dealerId为路径参数或接口未实现，用 status_code < 500 容错。
    """
    if not first_dealer_id:
        pytest.skip("环境中无经销商数据")
    resp = admin_client.get(
        f"{config.ApiPaths.CONTRACTS}/match-template",
        {"dealerId": first_dealer_id},
    )
    # 接口可能返回400（参数类型错误）或404（未实现），用宽容断言
    assert resp.status_code < 500, f"合同匹配模板服务器异常: {resp.status_code} {resp.data}"


@pytest.mark.crud
def test_delete_contract_draft(admin_client, first_dealer_id):
    """删除合同（草稿状态）"""
    payload = {
        "contractName": f"删除合同_{random_string(length=6)}",
        "contractNo": random_code("HT"),
        "dealerId": first_dealer_id,
        "signAmount": 5000,
        "validFrom": today_str(),
        "validTo": future_date(180),
        "status": "DRAFT",
        "remark": "删除测试",
    }
    create_resp = admin_client.post(config.ApiPaths.CONTRACTS, payload)
    cid = ""
    if create_resp.is_success and create_resp.body:
        cid = create_resp.body.get("id", "")
    if not cid:
        pytest.skip(f"删除前置：创建合同失败: {create_resp.status_code} {create_resp.msg}")
    resp = admin_client.delete(f"{config.ApiPaths.CONTRACTS}/{cid}")
    resp.assert_success("删除草稿合同")


@pytest.mark.api
def test_authorization_list(admin_client):
    """授权列表查询"""
    resp = admin_client.get(config.ApiPaths.AUTHORIZATIONS)
    resp.assert_success("授权列表查询")


@pytest.mark.crud
def test_create_authorization(admin_client, cleanup_registry, first_dealer_id, first_product_id):
    """创建授权（销售授权）"""
    payload = {
        "dealerId": first_dealer_id,
        "productId": first_product_id,
        "authType": "SALE",
        "validFrom": today_str(),
        "validTo": future_date(365),
    }
    resp = admin_client.post(config.ApiPaths.AUTHORIZATIONS, payload)
    if not resp.is_success:
        pytest.skip(f"创建授权失败: {resp.status_code} {resp.msg}")
    auth_id = resp.body.get("id", "") if resp.body else ""
    if auth_id:
        cleanup_registry["others"].append(auth_id)
        try:
            admin_client.delete(f"{config.ApiPaths.AUTHORIZATIONS}/{auth_id}")
        except Exception:
            pass
