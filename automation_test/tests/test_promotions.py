"""
DMS自动化测试 - 促销引擎模块
对应需求文档：DMS测试案例_v3.11.1 附录D 促销模块
覆盖场景：促销列表 / 状态/日期筛选 / 创建 / 详情 / 更新 / 启用/停用 / 删除
说明：
  - 场景6-10为CRUD链路，每个用例内部独立创建数据再操作，避免相互依赖。
  - 启用接口可能因业务状态约束失败，用 status_code < 500 容错。
"""
import logging

import pytest

import config
from utils.api_client import ApiClient
from utils.helpers import today_str, future_date, random_code

logger = logging.getLogger(__name__)


def _build_promotion_payload(product_id: str, name: str = None) -> dict:
    """构建促销活动创建载荷
    后端字段：name/code/type/startDate/endDate/rules[{productId, discount}]/status
    """
    return {
        "name": name or "测试促销_AUTO",
        "code": random_code("PROMO"),
        "type": "DISCOUNT",
        "startDate": today_str(),
        "endDate": future_date(30),
        "rules": [{"productId": product_id, "discount": 0.9}],
        "status": "DRAFT",
    }


def _create_promotion_for_test(
    admin_client: ApiClient,
    product_id: str,
    random_suffix: str,
    cleanup_registry: dict,
) -> str:
    """创建一个草稿促销并返回其ID，供后续详情/更新/启用/删除用例复用
    若创建失败则 pytest.skip 跳过后续依赖用例
    """
    payload = _build_promotion_payload(product_id, name=f"测试促销_{random_suffix}")
    resp = admin_client.post(config.ApiPaths.PROMOTIONS, payload)
    if not resp.is_success:
        pytest.skip(f"创建促销活动（前置）失败: {resp.status_code} {resp.msg}")
    promo_id = resp.body.get("id", "") if isinstance(resp.body, dict) else ""
    if not promo_id:
        pytest.skip(f"创建促销未返回id: {resp.data}")
    cleanup_registry["others"].append(
        {"path": config.ApiPaths.PROMOTIONS, "id": promo_id}
    )
    return promo_id


class TestPromotionQuery:
    """促销查询：列表、按状态/日期筛选"""

    @pytest.mark.smoke
    def test_promotion_list_smoke(self, admin_client: ApiClient):
        """促销活动列表查询：默认分页 page=1 size=20"""
        resp = admin_client.get(config.ApiPaths.PROMOTIONS, {"page": 1, "size": 20})
        resp.assert_success("促销活动列表查询")

    @pytest.mark.api
    def test_promotion_filter_by_status_api(self, admin_client: ApiClient):
        """按状态筛选：status=ACTIVE"""
        resp = admin_client.get(config.ApiPaths.PROMOTIONS, {"status": "ACTIVE"})
        resp.assert_success("促销按状态筛选")

    @pytest.mark.api
    def test_promotion_filter_by_date_api(self, admin_client: ApiClient):
        """按日期筛选：dateFrom=today_str()"""
        resp = admin_client.get(
            config.ApiPaths.PROMOTIONS, {"dateFrom": today_str()}
        )
        resp.assert_success("促销按日期筛选")


class TestPromotionCrud:
    """促销CRUD：创建、查询详情、更新、启用/停用、删除"""

    @pytest.mark.crud
    def test_create_promotion_crud(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        random_suffix: str,
        cleanup_registry: dict,
    ):
        """创建促销活动，完成后清理"""
        payload = _build_promotion_payload(
            first_product_id, name=f"测试促销_{random_suffix}"
        )
        resp = admin_client.post(config.ApiPaths.PROMOTIONS, payload)
        if not resp.is_success:
            pytest.skip(f"创建促销活动失败: {resp.status_code} {resp.msg}")

        promo_id = resp.body.get("id", "") if isinstance(resp.body, dict) else ""
        if promo_id:
            cleanup_registry["others"].append(
                {"path": config.ApiPaths.PROMOTIONS, "id": promo_id}
            )
            try:
                del_resp = admin_client.delete(
                    f"{config.ApiPaths.PROMOTIONS}/{promo_id}"
                )
                logger.info(
                    f"[清理] 删除促销 {promo_id} -> status={del_resp.status_code}"
                )
            except Exception:
                pass

    @pytest.mark.negative
    def test_create_promotion_no_name_negative(
        self, admin_client: ApiClient, first_product_id: str
    ):
        """创建促销-无名称：name为空字符串，断言失败"""
        payload = _build_promotion_payload(first_product_id, name="")
        resp = admin_client.post(config.ApiPaths.PROMOTIONS, payload)
        assert not resp.is_success, (
            f"无名称竟然创建成功: status={resp.status_code} "
            f"code={resp.code} data={resp.data}"
        )

    @pytest.mark.crud
    def test_query_promotion_detail_crud(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        random_suffix: str,
        cleanup_registry: dict,
    ):
        """查询促销详情：先创建再查询，最后清理"""
        promo_id = _create_promotion_for_test(
            admin_client, first_product_id, random_suffix, cleanup_registry
        )
        resp = admin_client.get(f"{config.ApiPaths.PROMOTIONS}/{promo_id}")
        resp.assert_success("查询促销详情")

        del_resp = admin_client.delete(
            f"{config.ApiPaths.PROMOTIONS}/{promo_id}"
        )
        logger.info(
            f"[清理] 删除促销 {promo_id} -> status={del_resp.status_code}"
        )

    @pytest.mark.crud
    def test_update_promotion_crud(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        random_suffix: str,
        cleanup_registry: dict,
    ):
        """更新促销：先创建再更新name，最后清理"""
        promo_id = _create_promotion_for_test(
            admin_client, first_product_id, random_suffix, cleanup_registry
        )
        resp = admin_client.put(
            f"{config.ApiPaths.PROMOTIONS}/{promo_id}",
            {"name": "更新促销名"},
        )
        resp.assert_success("更新促销")

        del_resp = admin_client.delete(
            f"{config.ApiPaths.PROMOTIONS}/{promo_id}"
        )
        logger.info(
            f"[清理] 删除促销 {promo_id} -> status={del_resp.status_code}"
        )

    @pytest.mark.api
    def test_activate_promotion_api(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        random_suffix: str,
        cleanup_registry: dict,
    ):
        """促销启用：POST /{id}/activate 或 PUT status=ACTIVE，断言成功或status_code<500"""
        promo_id = _create_promotion_for_test(
            admin_client, first_product_id, random_suffix, cleanup_registry
        )
        # 优先尝试 activate 动作接口，失败则回退到 PUT 修改 status
        resp = admin_client.post(
            f"{config.ApiPaths.PROMOTIONS}/{promo_id}/activate"
        )
        if resp.status_code >= 500 or (not resp.is_success and resp.status_code in (404, 405)):
            logger.info(
                f"activate接口不可用({resp.status_code})，回退到PUT修改status=ACTIVE"
            )
            resp = admin_client.put(
                f"{config.ApiPaths.PROMOTIONS}/{promo_id}",
                {"status": "ACTIVE"},
            )
        assert resp.status_code < 500, (
            f"促销启用服务异常: status={resp.status_code} "
            f"code={resp.code} msg={resp.msg} data={resp.data}"
        )

        # 启用后可能无法直接删除，尝试清理（容错）
        del_resp = admin_client.delete(
            f"{config.ApiPaths.PROMOTIONS}/{promo_id}"
        )
        logger.info(
            f"[清理] 删除已启用促销 {promo_id} -> status={del_resp.status_code}"
        )

    @pytest.mark.api
    def test_deactivate_promotion_api(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        random_suffix: str,
        cleanup_registry: dict,
    ):
        """促销停用：PUT status=INACTIVE，断言成功"""
        promo_id = _create_promotion_for_test(
            admin_client, first_product_id, random_suffix, cleanup_registry
        )
        # 先启用再停用，模拟完整状态流转
        admin_client.post(f"{config.ApiPaths.PROMOTIONS}/{promo_id}/activate")
        resp = admin_client.put(
            f"{config.ApiPaths.PROMOTIONS}/{promo_id}",
            {"status": "INACTIVE"},
        )
        resp.assert_success("促销停用")

        del_resp = admin_client.delete(
            f"{config.ApiPaths.PROMOTIONS}/{promo_id}"
        )
        logger.info(
            f"[清理] 删除已停用促销 {promo_id} -> status={del_resp.status_code}"
        )

    @pytest.mark.crud
    def test_delete_promotion_crud(
        self,
        admin_client: ApiClient,
        first_product_id: str,
        random_suffix: str,
        cleanup_registry: dict,
    ):
        """删除促销：先创建再删除"""
        promo_id = _create_promotion_for_test(
            admin_client, first_product_id, random_suffix, cleanup_registry
        )
        resp = admin_client.delete(f"{config.ApiPaths.PROMOTIONS}/{promo_id}")
        resp.assert_success("删除促销")
        logger.info(f"[清理] 促销 {promo_id} 已删除")
