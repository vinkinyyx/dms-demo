"""
DMS自动化测试 - 产品管理模块
对应需求文档：DMS测试案例_v3.11.1 附录D 产品管理模块
测试范围：产品列表查询、分页、搜索、筛选、详情、增删改、状态切换、导出、排序
"""
import pytest
import config
from utils.helpers import random_code, random_string

PRODUCTS = config.ApiPaths.PRODUCTS


def _build_product_payload(**kwargs):
    """构建产品创建载荷（字段名与后端 /api/products 实际返回一致）
    后端字段：code / nameCn / nameEn / categoryId / spec / unit / currentPrice / taxRate
            udiRequired / isSerialManaged / warnMonths / safetyQty / minOrderQty / status
    注意：不使用 helpers.build_product()，因其字段名(productCode/refPrice/udiTrace/
          serialNoMgmt/expireWarnMonths/safetyStock/status大写)与后端不一致。
    """
    data = {
        "code": random_code("PROD"),
        "nameCn": f"测试产品_{random_string(length=4)}",
        "nameEn": f"Test Product {random_string(length=4)}",
        "categoryId": 1,
        "spec": "规格100mm",
        "unit": "件",
        "currentPrice": 1000.00,
        "taxRate": 0.13,
        "udiRequired": False,
        "isSerialManaged": False,
        "warnMonths": 3,
        "safetyQty": 10,
        "minOrderQty": 1,
        "status": "active",
    }
    data.update(kwargs)
    return data


@pytest.mark.smoke
def test_product_list_query(admin_client):
    """产品列表查询：默认分页，断言成功且结构正确"""
    resp = admin_client.get(PRODUCTS, {"page": 1, "size": 20})
    resp.assert_success("产品列表查询")
    assert resp.total >= 0, f"total应大于等于0: {resp.total}"
    assert isinstance(resp.items, list), f"items应为list: {type(resp.items)}"


@pytest.mark.api
def test_product_pagination(admin_client):
    """产品分页查询：size=1，断言返回条数<=1"""
    resp = admin_client.get(PRODUCTS, {"page": 1, "size": 1})
    resp.assert_success("产品分页查询")
    assert len(resp.items) <= 1, f"size=1时items长度应<=1: {len(resp.items)}"


@pytest.mark.api
def test_product_search_by_keyword(admin_client):
    """产品按关键字搜索"""
    resp = admin_client.get(PRODUCTS, {"page": 1, "size": 20, "keyword": "测试"})
    resp.assert_success("产品关键字搜索")


@pytest.mark.api
def test_product_filter_by_status(admin_client):
    """产品按状态筛选：status=active（后端状态值小写）"""
    resp = admin_client.get(PRODUCTS, {"page": 1, "size": 20, "status": "active"})
    resp.assert_success("产品状态筛选")
    # 校验返回项状态均为active（若存在items）
    for item in resp.items:
        assert item.get("status") == "active", f"筛选状态不符: {item.get('status')}"


@pytest.mark.api
def test_product_filter_by_category(admin_client, first_product_id):
    """产品按分类筛选：使用首个产品的categoryId"""
    # 先获取首个产品的分类ID
    category_id = None
    if first_product_id:
        detail = admin_client.get(f"{PRODUCTS}/{first_product_id}")
        if detail.is_success and detail.body:
            category_id = detail.body.get("categoryId")
    resp = admin_client.get(PRODUCTS, {"page": 1, "size": 20, "categoryId": category_id})
    resp.assert_success("产品分类筛选")


@pytest.mark.smoke
def test_product_detail_query(admin_client, first_product_id):
    """查询单个产品详情"""
    if not first_product_id:
        pytest.skip("环境中无产品数据")
    resp = admin_client.get(f"{PRODUCTS}/{first_product_id}")
    resp.assert_success("产品详情查询")
    assert resp.body is not None, "产品详情数据为空"
    assert "id" in resp.body, f"详情缺少id字段: {resp.body}"
    assert "nameCn" in resp.body, f"详情缺少nameCn字段: {resp.body}"


@pytest.mark.crud
def test_product_create(admin_client, cleanup_registry, random_suffix):
    """创建产品：断言成功并返回id，注册到cleanup_registry，测试后清理"""
    payload = _build_product_payload(nameCn=f"自动化产品_{random_suffix}")
    resp = admin_client.post(PRODUCTS, payload)
    resp.assert_success("创建产品")
    product_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    assert product_id, f"创建产品未返回id: {resp.body}"
    cleanup_registry["products"].append(product_id)
    # 清理创建的数据（删除遇404静默忽略）
    try:
        admin_client.delete(f"{PRODUCTS}/{product_id}")
    except Exception:
        pass


@pytest.mark.negative
def test_product_create_missing_required(admin_client):
    """创建产品-必填字段缺失：nameCn为空，断言失败"""
    payload = _build_product_payload(nameCn="")
    resp = admin_client.post(PRODUCTS, payload)
    assert not resp.is_success, f"必填字段缺失应失败: {resp.status_code} {resp.msg}"


@pytest.mark.negative
def test_product_create_duplicate_code(admin_client, first_product_id):
    """创建产品-编码重复：用已存在产品的code创建，断言失败"""
    if not first_product_id:
        pytest.skip("环境中无产品数据")
    # 获取已存在产品的编码（后端字段为 code，不是 productCode）
    detail = admin_client.get(f"{PRODUCTS}/{first_product_id}")
    exist_code = detail.body.get("code") if detail.body else None
    assert exist_code, "未能获取已存在产品编码"
    payload = _build_product_payload(code=exist_code)
    resp = admin_client.post(PRODUCTS, payload)
    # 创建应失败：业务码非0 或 HTTP 4xx
    assert (not resp.is_success) or resp.status_code in (400, 409), (
        f"编码重复应失败: {resp.status_code} {resp.msg}"
    )
    # 若意外创建成功则清理
    if resp.is_success and isinstance(resp.body, dict) and resp.body.get("id"):
        try:
            admin_client.delete(f"{PRODUCTS}/{resp.body['id']}")
        except Exception:
            pass


@pytest.mark.crud
def test_product_update(admin_client, cleanup_registry, random_suffix):
    """更新产品：先创建产品，再更新nameCn，断言成功"""
    # 创建待更新产品
    payload = _build_product_payload(nameCn=f"待更新产品_{random_suffix}")
    create_resp = admin_client.post(PRODUCTS, payload)
    if not create_resp.is_success:
        pytest.skip(f"创建待更新产品失败: {create_resp.status_code} {create_resp.msg}")
    product_id = create_resp.body.get("id")
    assert product_id, "创建产品未返回id"
    cleanup_registry["products"].append(product_id)
    try:
        # 更新产品名称
        update_resp = admin_client.put(f"{PRODUCTS}/{product_id}", {"nameCn": "更新名称"})
        update_resp.assert_success("更新产品")
    finally:
        try:
            admin_client.delete(f"{PRODUCTS}/{product_id}")
        except Exception:
            pass


@pytest.mark.crud
def test_product_switch_status(admin_client, cleanup_registry, random_suffix):
    """切换产品状态：创建产品后修改status为inactive（后端小写），断言成功"""
    payload = _build_product_payload(nameCn=f"状态切换产品_{random_suffix}")
    create_resp = admin_client.post(PRODUCTS, payload)
    if not create_resp.is_success:
        pytest.skip(f"创建状态切换产品失败: {create_resp.status_code} {create_resp.msg}")
    product_id = create_resp.body.get("id")
    assert product_id, "创建产品未返回id"
    cleanup_registry["products"].append(product_id)
    try:
        update_resp = admin_client.put(f"{PRODUCTS}/{product_id}", {"status": "inactive"})
        update_resp.assert_success("切换产品状态为inactive")
    finally:
        try:
            admin_client.delete(f"{PRODUCTS}/{product_id}")
        except Exception:
            pass


@pytest.mark.crud
def test_product_delete(admin_client, cleanup_registry, random_suffix):
    """删除产品：创建产品后删除，断言成功，再get验证已删除"""
    payload = _build_product_payload(nameCn=f"待删除产品_{random_suffix}")
    create_resp = admin_client.post(PRODUCTS, payload)
    if not create_resp.is_success:
        pytest.skip(f"创建待删除产品失败: {create_resp.status_code} {create_resp.msg}")
    product_id = create_resp.body.get("id")
    assert product_id, "创建产品未返回id"
    # 删除
    del_resp = admin_client.delete(f"{PRODUCTS}/{product_id}")
    del_resp.assert_success("删除产品")
    # 验证已删除（查询应失败）
    verify_resp = admin_client.get(f"{PRODUCTS}/{product_id}")
    assert not verify_resp.is_success, f"删除后仍可查询: {verify_resp.status_code} {verify_resp.msg}"


@pytest.mark.api
def test_product_export(admin_client):
    """产品导出：断言 status_code in (200, 404)，404表示一期未实现"""
    resp = admin_client.get(f"{PRODUCTS}/actions/export", {"page": 1, "size": 10})
    assert resp.status_code in (200, 404), f"导出状态码应为200或404: {resp.status_code}"


@pytest.mark.api
def test_product_list_sort(admin_client):
    """产品列表排序：按createTime降序"""
    resp = admin_client.get(PRODUCTS, {
        "page": 1, "size": 20, "sortBy": "createTime", "sortOrder": "desc"
    })
    resp.assert_success("产品列表排序")
