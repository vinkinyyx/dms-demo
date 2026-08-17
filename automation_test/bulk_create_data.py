import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import logging
import random
import config
from utils.api_client import ApiClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("BulkDataCreator")

BATCH_SIZE = 20
CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"


def rand_str(length=6):
    return "".join(random.choice(CHARS) for _ in range(length))


def login_admin():
    client = ApiClient()
    resp = client.post(config.ApiPaths.LOGIN, {
        "tenantCode": "",
        "username": "admin",
        "password": "Sh123456",
    })
    if not resp.is_success:
        logger.error(f"登录失败: {resp.msg}")
        return None
    token = ""
    if isinstance(resp.body, dict) and resp.body.get("accessToken"):
        token = resp.body["accessToken"]
    elif isinstance(resp.data, dict):
        d = resp.data.get("data", {})
        if isinstance(d, dict) and d.get("accessToken"):
            token = d["accessToken"]
    if not token:
        logger.error("未获取到token")
        return None
    client.set_token(token)
    logger.info("admin登录成功")
    return client


def safe_create(client, path, data, name_field="name"):
    resp = client.post(path, data)
    if resp.is_success:
        detail = resp.body
        if isinstance(detail, dict) and ('id' in detail):
            return detail['id']
        if isinstance(resp.data, dict):
            d = resp.data.get('data', {})
            if isinstance(d, dict) and 'id' in d:
                return d['id']
        logger.info(f"  创建成功: {data.get(name_field, 'unknown')}")
        return True
    else:
        logger.warning(f"  创建失败 [{resp.status_code}]: {resp.msg}")
        return None


def create_categories(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建产品分类 ({count}个) ===")
    success = 0
    for i in range(count):
        code = f"AUTO-CAT-{rand_str(6)}"
        data = {
            "code": code,
            "name": f"自动分类_{rand_str(6)}",
            "level": 1,
            "sortOrder": random.randint(1, 100),
            "status": "active",
        }
        result = safe_create(client, config.ApiPaths.CATEGORIES, data)
        if result:
            success += 1
    logger.info(f"产品分类创建完成: {success}/{count}")
    return success


def create_dealers(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建经销商 ({count}个) ===")
    success = 0
    levels = ['A', 'B', 'C', 'D']
    for i in range(count):
        code = f"AUTO-DL-{rand_str(6)}"
        data = {
            "code": code,
            "name": f"自动经销商_{rand_str(6)}",
            "level": random.choice(levels),
            "contactName": f"联系人{rand_str(4)}",
            "contactPhone": f"138{random.randint(10000000, 99999999)}",
            "status": "active",
        }
        result = safe_create(client, config.ApiPaths.DEALERS, data)
        if result:
            success += 1
    logger.info(f"经销商创建完成: {success}/{count}")
    return success


def create_warehouses(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建仓库 ({count}个) ===")
    success = 0
    types = ['NORMAL', 'TRANSIT', 'RETURN', 'SCRAP']

    dealer_resp = client.get(config.ApiPaths.DEALERS, {"page": 1, "size": 5})
    dealers = dealer_resp.items if dealer_resp.items else []
    dealer_ids = [d.get('id') for d in dealers if d.get('id')]

    for i in range(count):
        code = f"AUTO-WH-{rand_str(5)}"
        data = {
            "code": code,
            "name": f"自动仓库_{rand_str(5)}",
            "type": random.choice(types),
            "address": f"自动地址_{rand_str(8)}",
            "manager": f"管理员{rand_str(4)}",
            "status": "active",
        }
        if dealer_ids:
            data['dealerId'] = random.choice(dealer_ids)
        result = safe_create(client, config.ApiPaths.WAREHOUSES, data)
        if result:
            success += 1
    logger.info(f"仓库创建完成: {success}/{count}")
    return success


def create_suppliers(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建供应商 ({count}个) ===")
    success = 0
    for i in range(count):
        code = f"AUTO-SUP-{rand_str(6)}"
        data = {
            "code": code,
            "name": f"自动供应商_{rand_str(6)}",
            "contactName": f"联系人{rand_str(4)}",
            "contactPhone": f"139{random.randint(10000000, 99999999)}",
            "status": "active",
        }
        result = safe_create(client, config.ApiPaths.SUPPLIERS, data)
        if result:
            success += 1
    logger.info(f"供应商创建完成: {success}/{count}")
    return success


def create_hospitals(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建医院 ({count}个) ===")
    success = 0
    levels = ['三甲', '三乙', '二甲', '二乙', '一级']
    for i in range(count):
        code = f"AUTO-HOS-{rand_str(6)}"
        data = {
            "code": code,
            "name": f"自动医院_{rand_str(6)}",
            "level": random.choice(levels),
            "contactName": f"主任{rand_str(4)}",
            "contactPhone": f"137{random.randint(10000000, 99999999)}",
            "status": "active",
        }
        result = safe_create(client, config.ApiPaths.HOSPITALS, data)
        if result:
            success += 1
    logger.info(f"医院创建完成: {success}/{count}")
    return success


def create_regions(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建区域 ({count}个) ===")
    success = 0
    for i in range(count):
        code = f"AUTO-REG-{rand_str(5)}"
        data = {
            "code": code,
            "name": f"自动区域_{rand_str(5)}",
            "level": random.randint(1, 3),
            "sortOrder": random.randint(1, 100),
            "status": "active",
        }
        result = safe_create(client, config.ApiPaths.REGIONS, data)
        if result:
            success += 1
    logger.info(f"区域创建完成: {success}/{count}")
    return success


def create_roles(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建角色 ({count}个) ===")
    success = 0
    types = ['admin', 'custom', 'sales', 'finance']
    for i in range(count):
        code = f"AUTO-ROLE-{rand_str(6)}"
        data = {
            "code": code,
            "name": f"自动角色_{rand_str(8)}",
            "type": random.choice(types),
            "status": "active",
        }
        result = safe_create(client, config.ApiPaths.ROLES, data)
        if result:
            success += 1
    logger.info(f"角色创建完成: {success}/{count}")
    return success


def create_sales_positions(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建销售岗位 ({count}个) ===")
    success = 0
    for i in range(count):
        code = f"AUTO-SP-{rand_str(5)}"
        data = {
            "code": code,
            "name": f"自动岗位_{rand_str(5)}",
            "status": "active",
        }
        result = safe_create(client, config.ApiPaths.SALES_POSITIONS, data)
        if result:
            success += 1
    logger.info(f"销售岗位创建完成: {success}/{count}")
    return success


def create_promotions(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建促销规则 ({count}个) ===")
    success = 0
    types = ['DISCOUNT', 'FULL_REDUCTION', 'GIFT']
    for i in range(count):
        code = f"AUTO-PROMO-{rand_str(5)}"
        data = {
            "code": code,
            "name": f"自动促销_{rand_str(5)}",
            "type": random.choice(types),
            "priority": random.randint(1, 10),
            "status": "draft",
        }
        result = safe_create(client, config.ApiPaths.PROMOTIONS, data)
        if result:
            success += 1
    logger.info(f"促销规则创建完成: {success}/{count}")
    return success


def create_products(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建产品 ({count}个) ===")
    success = 0
    resp = client.get(config.ApiPaths.CATEGORIES, {"page": 1, "size": 5})
    categories = resp.items if resp.items else []
    cat_ids = [c.get('id') for c in categories if c.get('id')]
    for i in range(count):
        code = f"AUTO-PRD-{rand_str(6)}"
        data = {
            "code": code,
            "name": f"自动产品_{rand_str(8)}",
            "specification": f"规格{rand_str(4)}",
            "unit": "个",
            "currentPrice": round(random.uniform(10, 5000), 2),
            "status": "active",
        }
        if cat_ids:
            data['categoryId'] = random.choice(cat_ids)
        result = safe_create(client, config.ApiPaths.PRODUCTS, data)
        if result:
            success += 1
    logger.info(f"产品创建完成: {success}/{count}")
    return success


def create_accounts(client, count=BATCH_SIZE):
    logger.info(f"=== 批量创建用户账号 ({count}个) ===")
    success = 0
    role_resp = client.get(config.ApiPaths.ROLES, {"page": 1, "size": 5})
    roles = role_resp.items if role_resp.items else []
    role_ids = [r.get('id') for r in roles if r.get('id')]
    for i in range(count):
        username = f"auto_user_{rand_str(6).lower()}"
        data = {
            "username": username,
            "name": f"自动用户_{rand_str(5)}",
            "password": "Sh123456",
            "email": f"{username}@test.com",
            "phone": f"136{random.randint(10000000, 99999999)}",
            "status": "active",
            "roleIds": role_ids[:2] if role_ids else [],
        }
        result = safe_create(client, config.ApiPaths.ACCOUNTS, data)
        if result:
            success += 1
    logger.info(f"用户账号创建完成: {success}/{count}")
    return success


def show_current_stats(client):
    logger.info("=== 当前数据量统计 ===")
    modules = [
        ("产品管理", config.ApiPaths.PRODUCTS),
        ("产品分类", config.ApiPaths.CATEGORIES),
        ("经销商管理", config.ApiPaths.DEALERS),
        ("医院管理", config.ApiPaths.HOSPITALS),
        ("供应商", config.ApiPaths.SUPPLIERS),
        ("仓库管理", config.ApiPaths.WAREHOUSES),
        ("区域管理", config.ApiPaths.REGIONS),
        ("角色管理", config.ApiPaths.ROLES),
        ("销售岗位", config.ApiPaths.SALES_POSITIONS),
        ("促销规则", config.ApiPaths.PROMOTIONS),
        ("用户管理", config.ApiPaths.ACCOUNTS),
        ("销售订单", config.ApiPaths.SALES_ORDERS),
        ("采购订单", config.ApiPaths.PURCHASE_ORDERS),
        ("库存查询", config.ApiPaths.INVENTORY),
        ("收货入库", config.ApiPaths.GOODS_RECEIPTS),
        ("销售出库", config.ApiPaths.GOODS_ISSUES),
        ("合同工作台", config.ApiPaths.CONTRACTS),
        ("授权管理", config.ApiPaths.AUTHORIZATIONS),
        ("手术报台", config.ApiPaths.SURGERY_REPORTS),
    ]
    for name, path in modules:
        try:
            resp = client.get(path, {"page": 1, "size": 1})
            total = resp.total if hasattr(resp, 'total') else 0
            items_len = len(resp.items) if resp.items else 0
            logger.info(f"  {name:12s}: total={total:>6d} (本页{items_len}条)")
        except Exception as e:
            logger.warning(f"  {name:12s}: 查询失败 - {e}")


def main():
    client = login_admin()
    if not client:
        return

    logger.info("=" * 60)
    logger.info("DMS批量测试数据创建工具")
    logger.info("=" * 60)

    logger.info("\n[创建前数据统计]")
    show_current_stats(client)

    logger.info("\n[开始批量创建]")
    total_created = 0

    total_created += create_categories(client, BATCH_SIZE)
    total_created += create_dealers(client, BATCH_SIZE)
    total_created += create_warehouses(client, BATCH_SIZE)
    total_created += create_suppliers(client, BATCH_SIZE)
    total_created += create_hospitals(client, BATCH_SIZE)
    total_created += create_regions(client, BATCH_SIZE)
    total_created += create_roles(client, BATCH_SIZE)
    total_created += create_sales_positions(client, BATCH_SIZE)
    total_created += create_promotions(client, BATCH_SIZE)
    total_created += create_products(client, BATCH_SIZE)
    total_created += create_accounts(client, BATCH_SIZE)

    logger.info(f"\n=== 批量创建完成，总成功: {total_created} 条 ===")

    logger.info("\n[创建后数据统计]")
    show_current_stats(client)

    logger.info("\n数据扩充完成，可以重新运行自动化测试验证skipped减少情况")


if __name__ == "__main__":
    main()
