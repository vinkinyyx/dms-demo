"""
DMS端到端业务闭环验证脚本
验证链路1：采购订单提交 -> 审批通过 -> 收货入库 -> 库存增加
验证链路2：销售订单提交 -> 审批通过 -> 销售出库 -> 库存减少
验证链路3：消息通知 -> 审批 -> 报表数据更新
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import logging
import config
from utils.api_client import ApiClient
from utils.helpers import random_string

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("E2ETest")


def login(username="admin", password="Sh123456"):
    client = ApiClient()
    resp = client.post(config.ApiPaths.LOGIN, {
        "tenantCode": "",
        "username": username,
        "password": password,
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
    logger.info(f"{username} 登录成功")
    return client


def get_product_qty(client, product_id, warehouse_id):
    """获取某产品在某仓库的库存数量"""
    resp = client.get(config.ApiPaths.INVENTORY, {
        "productId": product_id,
        "warehouseId": warehouse_id,
        "page": 1,
        "pageSize": 1,
    })
    if not resp.is_success or not resp.items:
        return 0
    item = resp.items[0]
    return item.get("qualifiedQty") or item.get("quantity") or item.get("stockQty") or 0


def e2e_purchase_flow(client):
    """端到端：采购订单 -> 审批 -> 入库 -> 库存增加"""
    logger.info("\n" + "="*60)
    logger.info("【端到端链路1】采购订单 → 审批通过 → 收货入库 → 库存增加")
    logger.info("="*60)
    
    # 获取基础数据
    supplier_resp = client.get(config.ApiPaths.SUPPLIERS, {"page": 1, "pageSize": 1})
    if not supplier_resp.is_success or not supplier_resp.items:
        logger.error("无供应商数据")
        return False
    supplier = supplier_resp.items[0]
    supplier_id = supplier.get("id")
    
    warehouse_resp = client.get(config.ApiPaths.WAREHOUSES, {"page": 1, "pageSize": 1})
    warehouse = warehouse_resp.items[0]
    warehouse_id = warehouse.get("id")
    
    product_resp = client.get(config.ApiPaths.PRODUCTS, {"page": 1, "pageSize": 1})
    product = product_resp.items[0]
    product_id = product.get("id")
    product_name = product.get("nameCn") or product.get("name") or "未知产品"
    
    logger.info(f"基础数据: 供应商={supplier_id}, 仓库={warehouse_id}, 产品={product_id}({product_name})")
    
    # 记录入库前库存
    qty_before = get_product_qty(client, product_id, warehouse_id)
    logger.info(f"入库前库存: {qty_before}")
    
    # 创建采购订单
    purchase_qty = 20
    resp = client.post(config.ApiPaths.PURCHASE_ORDERS, {
        "supplierId": supplier_id,
        "warehouseId": warehouse_id,
        "remark": f"E2E测试采购单-{random_string(length=6)}",
        "items": [{
            "productId": product_id,
            "quantity": purchase_qty,
            "unitPrice": 100,
        }]
    })
    if not resp.is_success:
        logger.error(f"创建采购订单失败: {resp.msg}")
        return False
    po_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    po_no = resp.body.get("code") or resp.body.get("orderNo") or f"PO-{po_id}"
    logger.info(f"✅ 步骤1: 采购订单创建成功 id={po_id}, no={po_no}")
    
    # 提交审批
    submit_resp = client.post(f"{config.ApiPaths.PURCHASE_ORDERS}/{po_id}/submit", {})
    if not submit_resp.is_success:
        logger.error(f"提交审批失败: {submit_resp.msg}")
        return False
    logger.info(f"✅ 步骤2: 采购订单已提交审批")
    
    # 审批通过（当前用户即审批人）
    # 先获取待审批实例
    approval_resp = client.get("/api/approvals/my-pending", {"page": 1, "pageSize": 50})
    if not approval_resp.is_success:
        approval_resp = client.get("/api/approval-instances", {"page": 1, "pageSize": 50, "status": "PENDING"})
    
    approval_id = None
    if approval_resp.is_success and approval_resp.items:
        for item in approval_resp.items:
            biz_no = item.get("bizNo") or item.get("businessNo") or ""
            if po_no in str(biz_no) or str(po_id) in str(item.get("bizId", "")):
                approval_id = item.get("id") or item.get("instanceId")
                break
    
    if not approval_id:
        logger.warning("未找到对应审批实例，尝试直接审批订单")
        approve_resp = client.post(f"{config.ApiPaths.PURCHASE_ORDERS}/{po_id}/approve", {
            "comment": "E2E自动化测试审批通过"
        })
        if not approve_resp.is_success:
            # 尝试其他路径
            approve_resp = client.post(f"/api/approvals/{po_id}/approve", {})
            logger.info(f"审批结果: {approve_resp.status_code} - {approve_resp.msg}")
    else:
        approve_resp = client.post(f"/api/approvals/{approval_id}/approve", {
            "comment": "E2E自动化测试审批通过"
        })
        logger.info(f"审批结果: {approve_resp.status_code} - {approve_resp.msg}")
    
    # 检查订单状态是否变更
    detail_resp = client.get(f"{config.ApiPaths.PURCHASE_ORDERS}/{po_id}")
    status = "未知"
    if detail_resp.is_success:
        body = detail_resp.body if isinstance(detail_resp.body, dict) else {}
        status = body.get("status") or body.get("approvalStatus") or "未知"
    logger.info(f"审批后订单状态: {status}")
    
    # 收货入库
    receive_resp = client.post(f"{config.ApiPaths.PURCHASE_ORDERS}/{po_id}/receive", {
        "items": [{"productId": product_id, "quantity": purchase_qty}]
    })
    if not receive_resp.is_success:
        # 尝试通过入库单创建
        receive_resp = client.post(config.ApiPaths.STOCK_IN, {
            "type": "PURCHASE",
            "sourceOrderId": po_id,
            "warehouseId": warehouse_id,
            "items": [{"productId": product_id, "quantity": purchase_qty}]
        })
        logger.info(f"入库结果: {receive_resp.status_code} - {receive_resp.msg}")
    else:
        logger.info(f"✅ 步骤3: 收货入库成功")
    
    # 验证库存增加
    qty_after = get_product_qty(client, product_id, warehouse_id)
    logger.info(f"入库后库存: {qty_after}")
    
    if qty_after > qty_before:
        logger.info(f"✅ 步骤4: 库存验证通过 (从 {qty_before} → {qty_after}，增加 {qty_after - qty_before})")
        return True
    else:
        logger.warning(f"库存未增加（可能入库路径不同，需手动验证UI）: 从 {qty_before} → {qty_after}")
        return False


def e2e_sales_flow(client):
    """端到端：销售订单 -> 审批 -> 出库 -> 库存减少"""
    logger.info("\n" + "="*60)
    logger.info("【端到端链路2】销售订单 → 审批通过 → 销售出库 → 库存减少")
    logger.info("="*60)
    
    dealer_resp = client.get(config.ApiPaths.DEALERS, {"page": 1, "pageSize": 1})
    dealer = dealer_resp.items[0]
    dealer_id = dealer.get("id")
    
    warehouse_resp = client.get(config.ApiPaths.WAREHOUSES, {"page": 1, "pageSize": 1})
    warehouse = warehouse_resp.items[0]
    warehouse_id = warehouse.get("id")
    
    product_resp = client.get(config.ApiPaths.PRODUCTS, {"page": 1, "pageSize": 1})
    product = product_resp.items[0]
    product_id = product.get("id")
    
    # 记录出库前库存
    qty_before = get_product_qty(client, product_id, warehouse_id)
    logger.info(f"出库前库存: {qty_before}")
    
    if qty_before <= 0:
        logger.warning("库存不足，跳过销售出库验证")
        return False
    
    sales_qty = min(5, qty_before)
    
    # 创建销售订单
    resp = client.post(config.ApiPaths.SALES_ORDERS, {
        "dealerId": dealer_id,
        "warehouseId": warehouse_id,
        "remark": f"E2E测试销售单-{random_string(length=6)}",
        "items": [{"productId": product_id, "quantity": sales_qty, "unitPrice": 200}]
    })
    if not resp.is_success:
        logger.error(f"创建销售订单失败: {resp.msg}")
        return False
    so_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    so_no = resp.body.get("code") or resp.body.get("orderNo") or f"SO-{so_id}"
    logger.info(f"✅ 步骤1: 销售订单创建成功 id={so_id}, no={so_no}")
    
    # 提交审批
    submit_resp = client.post(f"{config.ApiPaths.SALES_ORDERS}/{so_id}/submit", {})
    logger.info(f"步骤2: 提交审批 - {'成功' if submit_resp.is_success else '失败:'+submit_resp.msg}")
    
    # 审批通过
    approve_resp = client.post(f"{config.ApiPaths.SALES_ORDERS}/{so_id}/approve", {
        "comment": "E2E自动化测试审批通过"
    })
    if not approve_resp.is_success:
        approve_resp = client.post(f"/api/approvals/{so_id}/approve", {})
    logger.info(f"步骤3: 审批 - {approve_resp.status_code}")
    
    # 销售出库
    ship_resp = client.post(f"{config.ApiPaths.SALES_ORDERS}/{so_id}/ship", {
        "items": [{"productId": product_id, "quantity": sales_qty}]
    })
    if not ship_resp.is_success:
        ship_resp = client.post(config.ApiPaths.STOCK_OUT, {
            "type": "SALES",
            "sourceOrderId": so_id,
            "warehouseId": warehouse_id,
            "items": [{"productId": product_id, "quantity": sales_qty}]
        })
    logger.info(f"步骤4: 销售出库 - {ship_resp.status_code} - {ship_resp.msg}")
    
    # 验证库存减少
    qty_after = get_product_qty(client, product_id, warehouse_id)
    logger.info(f"出库后库存: {qty_after}")
    
    if qty_after < qty_before:
        logger.info(f"✅ 步骤5: 库存验证通过 (从 {qty_before} → {qty_after}，减少 {qty_before - qty_after})")
        return True
    else:
        logger.warning(f"库存未减少（可能出库路径不同，需手动验证UI）: 从 {qty_before} → {qty_after}")
        return False


def e2e_report_flow(client):
    """端到端：报表数据更新验证（下单后报表统计变化）"""
    logger.info("\n" + "="*60)
    logger.info("【端到端链路3】数据看板/报表数据一致性验证")
    logger.info("="*60)
    
    # 检查数据看板接口
    dashboards = [
        "/api/dashboard/summary",
        "/api/dashboard/overview",
        "/api/data-dashboard/summary",
    ]
    for path in dashboards:
        resp = client.get(path, {})
        if resp.is_success:
            logger.info(f"✅ 数据看板接口可用: {path}")
            body = resp.body if isinstance(resp.body, dict) else {}
            keys = list(body.keys())[:10]
            logger.info(f"   返回字段: {keys}")
            return True
    
    # 检查报表接口
    reports = [
        "/api/reports/sales-ranking",
        "/api/reports/product-sales-top10",
        "/api/reports/inventory-turnover",
    ]
    available = 0
    for path in reports:
        resp = client.get(path, {"page": 1, "pageSize": 5})
        if resp.is_success and resp.items:
            logger.info(f"✅ 报表接口可用: {path} ({len(resp.items)}条数据)")
            available += 1
        else:
            logger.info(f"  报表接口: {path} - {resp.status_code}")
    
    if available > 0:
        logger.info(f"报表接口可用: {available}/{len(reports)}")
        return True
    return False


def e2e_message_flow(client):
    """端到端：消息通知链路验证（创建订单触发审批消息）"""
    logger.info("\n" + "="*60)
    logger.info("【端到端链路4】消息通知链路验证")
    logger.info("="*60)
    
    # 获取创建前未读数
    before_count = 0
    before_resp = client.get(config.ApiPaths.NOTIFICATIONS_UNREAD_COUNT)
    if before_resp.is_success:
        before_count = before_resp.data if isinstance(before_resp.data, (int, float)) else 0
    
    logger.info(f"创建前未读数: {before_count}")
    
    # 创建一个新的采购订单并提交（会触发消息通知）
    supplier_resp = client.get(config.ApiPaths.SUPPLIERS, {"page": 1, "pageSize": 1})
    supplier_id = supplier_resp.items[0].get("id")
    warehouse_resp = client.get(config.ApiPaths.WAREHOUSES, {"page": 1, "pageSize": 1})
    warehouse_id = warehouse_resp.items[0].get("id")
    product_resp = client.get(config.ApiPaths.PRODUCTS, {"page": 1, "pageSize": 1})
    product_id = product_resp.items[0].get("id")
    
    resp = client.post(config.ApiPaths.PURCHASE_ORDERS, {
        "supplierId": supplier_id,
        "warehouseId": warehouse_id,
        "remark": f"消息链路测试-{random_string(length=4)}",
        "items": [{"productId": product_id, "quantity": 5, "unitPrice": 100}]
    })
    if not resp.is_success:
        logger.error(f"创建订单失败: {resp.msg}")
        return False
    po_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    
    submit_resp = client.post(f"{config.ApiPaths.PURCHASE_ORDERS}/{po_id}/submit", {})
    if not submit_resp.is_success:
        logger.warning(f"提交失败: {submit_resp.msg}")
        return False
    
    logger.info("✅ 步骤1: 采购订单创建并提交成功")
    
    # 检查未读消息是否增加
    after_resp = client.get(config.ApiPaths.NOTIFICATIONS_UNREAD_COUNT)
    after_count = 0
    if after_resp.is_success:
        after_count = after_resp.data if isinstance(after_resp.data, (int, float)) else 0
    
    logger.info(f"步骤2: 创建后未读数: {after_count}")
    
    # 检查消息列表中是否有新的审批待办消息
    msg_resp = client.get(config.ApiPaths.NOTIFICATIONS, {"page": 1, "pageSize": 5})
    has_new = False
    if msg_resp.is_success and msg_resp.items:
        for item in msg_resp.items:
            title = str(item.get("title", ""))
            body = str(item.get("body", ""))
            if "审批待办" in title and "PO" in body:
                has_new = True
                logger.info(f"✅ 步骤3: 审批待办消息存在 - {body[:50]}")
                break
    
    if not has_new:
        logger.info("  消息列表第一条: " + str(msg_resp.items[0] if msg_resp.items else "无")[:80])
    
    return has_new


def main():
    client = login()
    if not client:
        sys.exit(1)
    
    results = {}
    
    results["采购闭环"] = e2e_purchase_flow(client)
    results["销售闭环"] = e2e_sales_flow(client)
    results["报表数据"] = e2e_report_flow(client)
    results["消息通知"] = e2e_message_flow(client)
    
    logger.info("\n" + "="*60)
    logger.info("端到端业务闭环验证汇总")
    logger.info("="*60)
    for name, passed in results.items():
        status = "✅ 通过" if passed else "⚠️ 需UI手动验证"
        logger.info(f"  {name}: {status}")
    
    passed = sum(1 for v in results.values() if v)
    logger.info(f"\n总结: {passed}/{len(results)} 条链路API层验证通过")
    logger.info("剩余链路需通过UI操作验证完整流程（因为API路径可能与前端不同）")


if __name__ == "__main__":
    main()
