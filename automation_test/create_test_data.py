"""
DMS测试数据创建脚本
创建各类审批待办数据 + 手术报台数据，用于补测
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import logging
import config
from utils.api_client import ApiClient
from utils.helpers import random_string

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("TestDataCreator")


def login_admin():
    """登录业务前台admin"""
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


def get_first_id(client, path, name="data"):
    """获取第一条数据ID"""
    resp = client.get(path, {"page": 1, "pageSize": 5})
    if not resp.is_success or not resp.items:
        logger.warning(f"无{name}数据")
        return None, resp.items if resp.items else []
    first = resp.items[0]
    return first.get("id") or first.get("Id"), resp.items


def create_sales_order(client):
    """创建销售订单并提交审批"""
    logger.info("=== 创建销售订单 ===")
    dealer_id, _ = get_first_id(client, config.ApiPaths.DEALERS, "经销商")
    if not dealer_id:
        logger.error("无经销商数据，无法创建销售订单")
        return None
    product_id, _ = get_first_id(client, config.ApiPaths.PRODUCTS, "产品")
    if not product_id:
        logger.error("无产品数据")
        return None
    warehouse_id, _ = get_first_id(client, config.ApiPaths.WAREHOUSES, "仓库")
    suffix = random_string(length=4)
    payload = {
        "dealerId": dealer_id,
        "warehouseId": warehouse_id,
        "remark": f"自动化测试订单-{suffix}",
        "items": [{
            "productId": product_id,
            "quantity": 10,
            "unitPrice": 100,
        }]
    }
    resp = client.post(config.ApiPaths.SALES_ORDERS, payload)
    if not resp.is_success:
        logger.error(f"创建销售订单失败: {resp.msg}")
        return None
    order_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    if not order_id:
        logger.error("销售订单创建成功但未返回id")
        return None
    logger.info(f"销售订单创建成功: id={order_id}")

    submit_resp = client.post(f"{config.ApiPaths.SALES_ORDERS}/{order_id}/submit", {})
    if submit_resp.is_success:
        logger.info(f"销售订单已提交审批: {order_id}")
    else:
        logger.warning(f"销售订单提交失败: {submit_resp.msg}")
    return order_id


def create_purchase_order(client):
    """创建采购订单并提交审批"""
    logger.info("=== 创建采购订单 ===")
    supplier_id, _ = get_first_id(client, config.ApiPaths.SUPPLIERS, "供应商")
    if not supplier_id:
        logger.error("无供应商数据")
        return None
    product_id, _ = get_first_id(client, config.ApiPaths.PRODUCTS, "产品")
    warehouse_id, _ = get_first_id(client, config.ApiPaths.WAREHOUSES, "仓库")
    suffix = random_string(length=4)
    payload = {
        "supplierId": supplier_id,
        "warehouseId": warehouse_id,
        "remark": f"自动化测试采购单-{suffix}",
        "items": [{
            "productId": product_id,
            "quantity": 50,
            "unitPrice": 80,
        }]
    }
    resp = client.post(config.ApiPaths.PURCHASE_ORDERS, payload)
    if not resp.is_success:
        logger.error(f"创建采购订单失败: {resp.msg}")
        return None
    order_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    if not order_id:
        logger.error("采购订单创建成功但未返回id")
        return None
    logger.info(f"采购订单创建成功: id={order_id}")

    submit_resp = client.post(f"{config.ApiPaths.PURCHASE_ORDERS}/{order_id}/submit", {})
    if submit_resp.is_success:
        logger.info(f"采购订单已提交审批: {order_id}")
    else:
        logger.warning(f"采购订单提交失败: {submit_resp.msg}")
    return order_id


def create_sales_return(client):
    """创建销售退货单并提交审批"""
    logger.info("=== 创建销售退货单 ===")
    orders_resp = client.get(config.ApiPaths.SALES_ORDERS, {"page": 1, "pageSize": 10, "status": "APPROVED"})
    if not orders_resp.is_success or not orders_resp.items:
        logger.warning("无已审批销售订单，无法创建销退")
        return None
    so = orders_resp.items[0]
    so_id = so.get("id")
    so_no = so.get("code") or so.get("orderNo") or ""
    dealer_id = so.get("dealerId")
    warehouse_id = so.get("warehouseId")
    
    items = so.get("items") or []
    if not items:
        detail_resp = client.get(f"{config.ApiPaths.SALES_ORDERS}/{so_id}")
        if detail_resp.is_success:
            body = detail_resp.body
            items = body.get("items") if isinstance(body, dict) else []
    
    if not items:
        logger.warning("销售订单无明细，无法创建销退")
        return None
    
    suffix = random_string(length=4)
    return_items = []
    for item in items[:1]:
        pid = item.get("productId") or item.get("id")
        qty = min(item.get("quantity", 1), 2)
        return_items.append({"productId": pid, "quantity": qty, "unitPrice": item.get("unitPrice", 0)})
    
    payload = {
        "sourceOrderId": so_id,
        "dealerId": dealer_id,
        "warehouseId": warehouse_id,
        "remark": f"自动化测试销退-{suffix}",
        "items": return_items,
        "reason": "质量问题",
    }
    resp = client.post(config.ApiPaths.SALES_RETURNS, payload)
    if not resp.is_success:
        logger.warning(f"创建销售退货失败: {resp.msg}")
        return None
    ret_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    logger.info(f"销售退货创建成功: id={ret_id}")
    
    if ret_id:
        submit_resp = client.post(f"{config.ApiPaths.SALES_RETURNS}/{ret_id}/submit", {})
        logger.info(f"销退提交审批: {'成功' if submit_resp.is_success else '失败 - ' + submit_resp.msg}")
    return ret_id


def create_purchase_return(client):
    """创建采购退货单并提交审批"""
    logger.info("=== 创建采购退货单 ===")
    orders_resp = client.get(config.ApiPaths.PURCHASE_ORDERS, {"page": 1, "pageSize": 10})
    if not orders_resp.is_success or not orders_resp.items:
        logger.warning("无采购订单，无法创建采退")
        return None
    po = orders_resp.items[0]
    po_id = po.get("id")
    supplier_id = po.get("supplierId")
    warehouse_id = po.get("warehouseId")
    
    suffix = random_string(length=4)
    product_id, _ = get_first_id(client, config.ApiPaths.PRODUCTS, "产品")
    
    payload = {
        "sourceOrderId": po_id,
        "supplierId": supplier_id,
        "warehouseId": warehouse_id,
        "remark": f"自动化测试采退-{suffix}",
        "items": [{"productId": product_id, "quantity": 5, "unitPrice": 80}],
        "reason": "规格不符",
    }
    resp = client.post(config.ApiPaths.PURCHASE_RETURNS, payload)
    if not resp.is_success:
        logger.warning(f"创建采购退货失败: {resp.msg}")
        return None
    ret_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    logger.info(f"采购退货创建成功: id={ret_id}")
    
    if ret_id:
        submit_resp = client.post(f"{config.ApiPaths.PURCHASE_RETURNS}/{ret_id}/submit", {})
        logger.info(f"采退提交审批: {'成功' if submit_resp.is_success else '失败 - ' + submit_resp.msg}")
    return ret_id


def create_contract(client):
    """创建合同并提交审批"""
    logger.info("=== 创建合同 ===")
    dealer_id, _ = get_first_id(client, config.ApiPaths.DEALERS, "经销商")
    suffix = random_string(length=4)
    payload = {
        "dealerId": dealer_id,
        "name": f"自动化测试合同-{suffix}",
        "contractNo": f"CT-{suffix.upper()}",
        "type": "SALES",
        "startDate": "2026-08-01",
        "endDate": "2027-07-31",
        "amount": 100000,
        "remark": f"自动化测试",
    }
    resp = client.post(config.ApiPaths.CONTRACTS, payload)
    if not resp.is_success:
        logger.warning(f"创建合同失败: {resp.msg}")
        return None
    contract_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    logger.info(f"合同创建成功: id={contract_id}")
    
    if contract_id:
        submit_resp = client.post(f"{config.ApiPaths.CONTRACTS}/{contract_id}/submit", {})
        logger.info(f"合同提交审批: {'成功' if submit_resp.is_success else '失败 - ' + submit_resp.msg}")
    return contract_id


def create_authorization(client):
    """创建授权申请并提交审批"""
    logger.info("=== 创建授权申请 ===")
    dealer_id, _ = get_first_id(client, config.ApiPaths.DEALERS, "经销商")
    hospital_id, _ = get_first_id(client, config.ApiPaths.HOSPITALS, "医院")
    suffix = random_string(length=4)
    payload = {
        "dealerId": dealer_id,
        "hospitalId": hospital_id,
        "productScope": "全部产品",
        "startDate": "2026-08-01",
        "endDate": "2026-12-31",
        "remark": f"自动化测试授权-{suffix}",
    }
    resp = client.post(config.ApiPaths.AUTHORIZATIONS, payload)
    if not resp.is_success:
        logger.warning(f"创建授权失败: {resp.msg}")
        return None
    auth_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    logger.info(f"授权创建成功: id={auth_id}")
    
    if auth_id:
        submit_resp = client.post(f"{config.ApiPaths.AUTHORIZATIONS}/{auth_id}/submit", {})
        logger.info(f"授权提交审批: {'成功' if submit_resp.is_success else '失败 - ' + submit_resp.msg}")
    return auth_id


def create_surgery_report(client):
    """创建手术报台"""
    logger.info("=== 创建手术报台 ===")
    hospital_id, _ = get_first_id(client, config.ApiPaths.HOSPITALS, "医院")
    dealer_id, _ = get_first_id(client, config.ApiPaths.DEALERS, "经销商")
    warehouse_id, _ = get_first_id(client, config.ApiPaths.WAREHOUSES, "仓库")
    product_id, _ = get_first_id(client, config.ApiPaths.PRODUCTS, "产品")
    suffix = random_string(length=4)
    payload = {
        "hospitalId": hospital_id,
        "dealerId": dealer_id,
        "warehouseId": warehouse_id,
        "surgeryDate": "2026-08-13",
        "surgeryType": "骨科手术",
        "doctorName": "张医生",
        "patientName": f"患者-{suffix}",
        "remark": f"自动化测试报台-{suffix}",
        "products": [{
            "productId": product_id,
            "quantity": 2,
        }],
    }
    resp = client.post(config.ApiPaths.SURGERY_REPORTS, payload)
    if not resp.is_success:
        logger.warning(f"创建手术报台失败: {resp.msg}")
        return None
    report_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    logger.info(f"手术报台创建成功: id={report_id}")
    return report_id


def main():
    client = login_admin()
    if not client:
        sys.exit(1)
    
    results = {}
    
    so_id = create_sales_order(client)
    results["销售订单"] = so_id
    
    po_id = create_purchase_order(client)
    results["采购订单"] = po_id
    
    sr_id = create_sales_return(client)
    results["销售退货"] = sr_id
    
    pr_id = create_purchase_return(client)
    results["采购退货"] = pr_id
    
    contract_id = create_contract(client)
    results["合同"] = contract_id
    
    auth_id = create_authorization(client)
    results["授权申请"] = auth_id
    
    surgery_id = create_surgery_report(client)
    results["手术报台"] = surgery_id
    
    logger.info("\n===== 数据创建汇总 =====")
    for name, oid in results.items():
        status = "✅ 成功" if oid else "❌ 失败"
        logger.info(f"{name}: {status} (id={oid})")
    
    # 获取待审批数量
    appr_resp = client.get(config.ApiPaths.APPROVAL_INSTANCES, {"page": 1, "pageSize": 50, "status": "PENDING"})
    if appr_resp.is_success:
        logger.info(f"\n当前待审批实例总数: {appr_resp.total}")
        if appr_resp.items:
            for item in appr_resp.items[:10]:
                biz_type = item.get("bizType") or item.get("type") or ""
                biz_no = item.get("bizNo") or item.get("businessNo") or ""
                logger.info(f"  - {biz_type}: {biz_no} (id={item.get('id')})")


if __name__ == "__main__":
    main()
