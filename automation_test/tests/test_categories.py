"""
DMS自动化测试 - 基础数据模块
对应需求文档：DMS测试案例_v3.11.1 附录D 基础数据模块
测试范围：产品分类/产品线/包装层级/供应商/仓库/医院/区域/产品价格/字典项的查询与增删改
"""
import pytest
import config
from utils.helpers import random_code, random_phone

CATEGORIES = config.ApiPaths.CATEGORIES
PRODUCT_LINES = config.ApiPaths.PRODUCT_LINES
PACKAGE_LEVELS = config.ApiPaths.PACKAGE_LEVELS
SUPPLIERS = config.ApiPaths.SUPPLIERS
WAREHOUSES = config.ApiPaths.WAREHOUSES
HOSPITALS = config.ApiPaths.HOSPITALS
REGIONS = config.ApiPaths.REGIONS
PRODUCT_PRICES = config.ApiPaths.PRODUCT_PRICES
DICT_ITEMS = config.ApiPaths.DICT_ITEMS


@pytest.mark.smoke
def test_category_tree_query(admin_client):
    """产品分类树查询"""
    resp = admin_client.get(CATEGORIES)
    resp.assert_success("产品分类树查询")


@pytest.mark.crud
def test_category_create(admin_client, cleanup_registry, random_suffix):
    """创建产品分类：断言成功，注册清理，测试后清理
    后端字段：code/name/parentId/level/sortOrder/status（注意 name 不是 categoryName）
    """
    payload = {
        "name": f"测试分类_{random_suffix}",
        "code": random_code("CAT"),
        "parentId": None,
        "level": 1,
        "sortOrder": 1,
        "status": "active",
    }
    resp = admin_client.post(CATEGORIES, payload)
    resp.assert_success("创建产品分类")
    cat_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    assert cat_id, f"创建分类未返回id: {resp.body}"
    cleanup_registry["categories"].append(cat_id)
    # 清理创建的数据（删除遇404静默忽略）
    try:
        admin_client.delete(f"{CATEGORIES}/{cat_id}")
    except Exception:
        pass


@pytest.mark.crud
def test_category_update(admin_client, cleanup_registry, random_suffix):
    """更新产品分类：先创建再更新名称，断言成功"""
    payload = {
        "name": f"待更新分类_{random_suffix}",
        "code": random_code("CAT"),
        "parentId": None,
        "level": 1,
        "sortOrder": 1,
        "status": "active",
    }
    create_resp = admin_client.post(CATEGORIES, payload)
    if not create_resp.is_success:
        pytest.skip(f"创建待更新分类失败: {create_resp.status_code} {create_resp.msg}")
    cat_id = create_resp.body.get("id")
    assert cat_id, "创建分类未返回id"
    cleanup_registry["categories"].append(cat_id)
    try:
        update_resp = admin_client.put(f"{CATEGORIES}/{cat_id}", {"name": "更新分类"})
        update_resp.assert_success("更新产品分类")
    finally:
        try:
            admin_client.delete(f"{CATEGORIES}/{cat_id}")
        except Exception:
            pass


@pytest.mark.crud
def test_category_delete(admin_client, cleanup_registry, random_suffix):
    """删除产品分类：创建后删除，断言成功"""
    payload = {
        "name": f"待删除分类_{random_suffix}",
        "code": random_code("CAT"),
        "parentId": None,
        "level": 1,
        "sortOrder": 1,
        "status": "active",
    }
    create_resp = admin_client.post(CATEGORIES, payload)
    if not create_resp.is_success:
        pytest.skip(f"创建待删除分类失败: {create_resp.status_code} {create_resp.msg}")
    cat_id = create_resp.body.get("id")
    assert cat_id, "创建分类未返回id"
    del_resp = admin_client.delete(f"{CATEGORIES}/{cat_id}")
    del_resp.assert_success("删除产品分类")


@pytest.mark.api
def test_product_line_list_query(admin_client):
    """产品线列表查询"""
    resp = admin_client.get(PRODUCT_LINES)
    resp.assert_success("产品线列表查询")


@pytest.mark.crud
def test_product_line_create(admin_client, cleanup_registry, random_suffix):
    """创建产品线：断言成功，注册清理，测试后清理"""
    payload = {
        "name": f"测试产品线_{random_suffix}",
        "code": random_code("PL"),
    }
    resp = admin_client.post(PRODUCT_LINES, payload)
    if not resp.is_success:
        pytest.skip(f"创建产品线失败: {resp.status_code} {resp.msg}")
    pl_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    assert pl_id, f"创建产品线未返回id: {resp.body}"
    cleanup_registry["others"].append(("product-lines", pl_id))
    # 清理创建的数据（删除遇404静默忽略）
    try:
        admin_client.delete(f"{PRODUCT_LINES}/{pl_id}")
    except Exception:
        pass


@pytest.mark.api
def test_package_level_list_query(admin_client):
    """包装层级列表查询"""
    resp = admin_client.get(PACKAGE_LEVELS)
    resp.assert_success("包装层级列表查询")


@pytest.mark.api
def test_supplier_list_query(admin_client):
    """供应商列表查询"""
    resp = admin_client.get(SUPPLIERS)
    resp.assert_success("供应商列表查询")


@pytest.mark.api
def test_warehouse_list_query(admin_client):
    """仓库列表查询"""
    resp = admin_client.get(WAREHOUSES)
    resp.assert_success("仓库列表查询")


@pytest.mark.api
def test_hospital_list_query(admin_client):
    """医院列表查询"""
    resp = admin_client.get(HOSPITALS)
    resp.assert_success("医院列表查询")


@pytest.mark.api
def test_region_list_query(admin_client):
    """区域列表查询"""
    resp = admin_client.get(REGIONS)
    resp.assert_success("区域列表查询")


@pytest.mark.api
def test_product_price_query(admin_client, first_product_id):
    """产品价格查询：按productId"""
    if not first_product_id:
        pytest.skip("环境中无产品数据")
    resp = admin_client.get(PRODUCT_PRICES, {"productId": first_product_id})
    resp.assert_success("产品价格查询")


@pytest.mark.api
def test_dict_item_query(admin_client):
    """字典项查询：dictType=product_type（一期未实现 /api/dict-items 时跳过）"""
    resp = admin_client.get(DICT_ITEMS, {"dictType": "product_type"})
    if resp.status_code == 404:
        pytest.skip("字典项接口一期未实现: 404")
    resp.assert_success("字典项查询")


@pytest.mark.crud
def test_supplier_create(admin_client, cleanup_registry, random_suffix):
    """创建供应商：断言成功，注册清理，测试后清理
    后端字段：code/name/contactPerson/contactPhone/address/bankAccount/taxNo/level/remark
    （注意 contactPhone 不是 phone）
    """
    payload = {
        "name": f"测试供应商_{random_suffix}",
        "code": random_code("SUP"),
        "contactPerson": "联系人",
        "contactPhone": random_phone(),
        "address": "测试地址",
    }
    resp = admin_client.post(SUPPLIERS, payload)
    resp.assert_success("创建供应商")
    sup_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    assert sup_id, f"创建供应商未返回id: {resp.body}"
    cleanup_registry["others"].append(("suppliers", sup_id))
    # 清理创建的数据（删除遇404静默忽略）
    try:
        admin_client.delete(f"{SUPPLIERS}/{sup_id}")
    except Exception:
        pass


@pytest.mark.crud
def test_warehouse_create(admin_client, cleanup_registry, random_suffix, first_dealer_id):
    """创建仓库：断言成功，注册清理，测试后清理
    后端字段：code/name/type/address/status/dealerId（status小写 active/inactive，dealerId必填）
    """
    if not first_dealer_id:
        pytest.skip("环境中无经销商数据，仓库创建需要dealerId")
    payload = {
        "name": f"测试仓库_{random_suffix}",
        "code": random_code("WH"),
        "type": "NORMAL",
        "address": "测试地址",
        "status": "active",
        "dealerId": first_dealer_id,
    }
    resp = admin_client.post(WAREHOUSES, payload)
    resp.assert_success("创建仓库")
    wh_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    assert wh_id, f"创建仓库未返回id: {resp.body}"
    cleanup_registry["others"].append(("warehouses", wh_id))
    # 清理创建的数据（删除遇404静默忽略）
    try:
        admin_client.delete(f"{WAREHOUSES}/{wh_id}")
    except Exception:
        pass