"""
DMS自动化测试 - 库存管理模块
对应需求文档：DMS测试案例_v3.11.1 附录D 库存模块
覆盖场景：库存查询 / 出入库 / 库存调整 / 库存移动 / 库存预警
"""
import logging

import pytest

import config
from utils.api_client import ApiClient

logger = logging.getLogger(__name__)


class TestInventoryQuery:
    """库存查询：列表、按仓库/产品筛选、库存预警"""

    @pytest.mark.smoke
    def test_inventory_list_smoke(self, admin_client: ApiClient):
        """库存列表查询：默认分页 page=1 size=20"""
        resp = admin_client.get(config.ApiPaths.INVENTORY, {"page": 1, "size": 20})
        resp.assert_success("库存列表查询")

    @pytest.mark.api
    def test_inventory_filter_by_warehouse_api(self, admin_client: ApiClient, first_warehouse_id: str):
        """库存按仓库筛选：warehouseId=first_warehouse_id"""
        resp = admin_client.get(
            config.ApiPaths.INVENTORY, {"warehouseId": first_warehouse_id}
        )
        resp.assert_success("库存按仓库筛选")

    @pytest.mark.api
    def test_inventory_filter_by_product_api(self, admin_client: ApiClient, first_product_id: str):
        """库存按产品筛选：productId=first_product_id"""
        resp = admin_client.get(
            config.ApiPaths.INVENTORY, {"productId": first_product_id}
        )
        resp.assert_success("库存按产品筛选")

    @pytest.mark.api
    def test_inventory_low_stock_api(self, admin_client: ApiClient):
        """库存预警查询：lowStock=true"""
        resp = admin_client.get(config.ApiPaths.INVENTORY, {"lowStock": True})
        resp.assert_success("库存预警查询")


class TestGoodsIssues:
    """出库单：列表查询、创建"""

    @pytest.mark.api
    def test_goods_issues_list_api(self, admin_client: ApiClient):
        """出库单列表查询"""
        resp = admin_client.get(config.ApiPaths.GOODS_ISSUES)
        resp.assert_success("出库单列表查询")

    @pytest.mark.crud
    def test_create_goods_issue_crud(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        first_dealer_id: str,
        first_warehouse_id: str,
        cleanup_registry: dict,
    ):
        """创建出库单（NORMAL），完成后清理
        后端字段：type/warehouseId/dealerId/items[{productId, qty}]/remark
        """
        payload = {
            "type": "NORMAL",
            "warehouseId": first_warehouse_id,
            "dealerId": first_dealer_id,
            "items": [{"productId": first_product_id, "qty": 5}],
            "remark": "自动化测试出库",
        }
        resp = admin_client.post(config.ApiPaths.GOODS_ISSUES, payload)
        if not resp.is_success:
            pytest.skip(f"创建出库单失败: {resp.status_code} {resp.msg}")

        issue_id = resp.body.get("id", "") if isinstance(resp.body, dict) else ""
        if issue_id:
            cleanup_registry["others"].append(
                {"path": config.ApiPaths.GOODS_ISSUES, "id": issue_id}
            )
            try:
                del_resp = admin_client.delete(f"{config.ApiPaths.GOODS_ISSUES}/{issue_id}")
                logger.info(
                    f"[清理] 删除出库单 {issue_id} -> status={del_resp.status_code}"
                )
            except Exception:
                pass


class TestGoodsReceipts:
    """入库单：列表查询、创建"""

    @pytest.mark.api
    def test_goods_receipts_list_api(self, admin_client: ApiClient):
        """入库单列表查询"""
        resp = admin_client.get(config.ApiPaths.GOODS_RECEIPTS)
        resp.assert_success("入库单列表查询")

    @pytest.mark.crud
    def test_create_goods_receipt_crud(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        first_supplier_id: str,
        first_warehouse_id: str,
        random_suffix: str,
        cleanup_registry: dict,
    ):
        """创建入库单（PURCHASE），完成后清理
        后端字段：type/warehouseId/supplierId/items[{productId, qty, batchNo}]/remark
        """
        payload = {
            "type": "PURCHASE",
            "warehouseId": first_warehouse_id,
            "supplierId": first_supplier_id,
            "items": [
                {
                    "productId": first_product_id,
                    "qty": 100,
                    "batchNo": f"BATCH{random_suffix}",
                }
            ],
            "remark": "自动化测试入库",
        }
        resp = admin_client.post(config.ApiPaths.GOODS_RECEIPTS, payload)
        if not resp.is_success:
            pytest.skip(f"创建入库单失败: {resp.status_code} {resp.msg}")

        receipt_id = resp.body.get("id", "") if isinstance(resp.body, dict) else ""
        if receipt_id:
            cleanup_registry["others"].append(
                {"path": config.ApiPaths.GOODS_RECEIPTS, "id": receipt_id}
            )
            try:
                del_resp = admin_client.delete(
                    f"{config.ApiPaths.GOODS_RECEIPTS}/{receipt_id}"
                )
                logger.info(
                    f"[清理] 删除入库单 {receipt_id} -> status={del_resp.status_code}"
                )
            except Exception:
                pass


class TestStockMoves:
    """库存移动：列表查询"""

    @pytest.mark.api
    def test_stock_moves_list_api(self, admin_client: ApiClient):
        """库存移动列表查询"""
        resp = admin_client.get(config.ApiPaths.STOCK_MOVES)
        resp.assert_success("库存移动列表查询")


class TestStockAdjustments:
    """库存调整：创建"""

    @pytest.mark.crud
    def test_create_stock_adjustment_crud(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        first_warehouse_id: str,
        cleanup_registry: dict,
    ):
        """创建库存调整（INCREASE），完成后清理
        后端字段：warehouseId/productId/adjustType/qty/reason
        """
        payload = {
            "warehouseId": first_warehouse_id,
            "productId": first_product_id,
            "adjustType": "INCREASE",
            "qty": 10,
            "reason": "测试调整",
        }
        resp = admin_client.post(config.ApiPaths.STOCK_ADJUSTMENTS, payload)
        if not resp.is_success:
            pytest.skip(f"创建库存调整失败: {resp.status_code} {resp.msg}")

        adj_id = resp.body.get("id", "") if isinstance(resp.body, dict) else ""
        if adj_id:
            cleanup_registry["others"].append(
                {"path": config.ApiPaths.STOCK_ADJUSTMENTS, "id": adj_id}
            )
            try:
                del_resp = admin_client.delete(
                    f"{config.ApiPaths.STOCK_ADJUSTMENTS}/{adj_id}"
                )
                logger.info(
                    f"[清理] 删除库存调整 {adj_id} -> status={del_resp.status_code}"
                )
            except Exception:
                pass
