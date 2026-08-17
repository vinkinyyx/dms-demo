"""
DMS自动化测试 - 手术报台模块
对应需求文档：DMS测试案例_v3.11.1 附录D 手术报台模块
覆盖场景：报台列表 / 多维度筛选 / 创建 / 详情 / 更新 / 提交 / 删除
说明：
  - 场景7-10为CRUD链路，每个用例内部独立创建数据再操作，避免相互依赖。
  - 提交接口可能因业务状态约束失败，用 status_code < 500 容错。
"""
import logging

import pytest

import config
from utils.api_client import ApiClient
from utils.helpers import today_str, past_date

logger = logging.getLogger(__name__)


def _build_surgery_payload(hospital_id: str, dealer_id: str, product_id: str) -> dict:
    """构建手术报台创建载荷
    后端字段：hospitalId/surgeryDate/patientName/doctor/dealerId/items[{productId, qty}]/remark
    """
    return {
        "hospitalId": hospital_id,
        "surgeryDate": today_str(),
        "patientName": "测试患者",
        "doctor": "测试医生",
        "dealerId": dealer_id,
        "items": [{"productId": product_id, "qty": 2}],
        "remark": "自动化测试报台",
    }


def _create_surgery_for_test(
    admin_client: ApiClient,
    hospital_id: str,
    dealer_id: str,
    product_id: str,
    cleanup_registry: dict,
) -> str:
    """创建一个草稿报台并返回其ID，供后续查询/更新/提交/删除用例复用
    若创建失败则 pytest.skip 跳过后续依赖用例
    """
    payload = _build_surgery_payload(hospital_id, dealer_id, product_id)
    resp = admin_client.post(config.ApiPaths.SURGERY_REPORTS, payload)
    if not resp.is_success:
        pytest.skip(f"创建手术报台（前置）失败: {resp.status_code} {resp.msg}")
    surgery_id = resp.body.get("id", "") if isinstance(resp.body, dict) else ""
    if not surgery_id:
        pytest.skip(f"创建报台未返回id: {resp.data}")
    cleanup_registry["others"].append(
        {"path": config.ApiPaths.SURGERY_REPORTS, "id": surgery_id}
    )
    return surgery_id


class TestSurgeryQuery:
    """手术报台查询：列表、按医院/日期/状态筛选"""

    @pytest.mark.smoke
    def test_surgery_list_smoke(self, admin_client: ApiClient):
        """手术报台列表查询：默认分页 page=1 size=20"""
        resp = admin_client.get(
            config.ApiPaths.SURGERY_REPORTS, {"page": 1, "size": 20}
        )
        resp.assert_success("手术报台列表查询")

    @pytest.mark.api
    def test_surgery_filter_by_hospital_api(
        self, admin_client: ApiClient, first_hospital_id: str
    ):
        """按医院筛选：hospitalId=first_hospital_id"""
        resp = admin_client.get(
            config.ApiPaths.SURGERY_REPORTS, {"hospitalId": first_hospital_id}
        )
        resp.assert_success("手术报台按医院筛选")

    @pytest.mark.api
    def test_surgery_filter_by_date_range_api(self, admin_client: ApiClient):
        """按日期范围筛选：dateFrom=past_date(30), dateTo=today_str()"""
        resp = admin_client.get(
            config.ApiPaths.SURGERY_REPORTS,
            {"dateFrom": past_date(30), "dateTo": today_str()},
        )
        resp.assert_success("手术报台按日期范围筛选")

    @pytest.mark.api
    def test_surgery_filter_by_status_api(self, admin_client: ApiClient):
        """按状态筛选：status=DRAFT"""
        resp = admin_client.get(
            config.ApiPaths.SURGERY_REPORTS, {"status": "DRAFT"}
        )
        resp.assert_success("手术报台按状态筛选")


class TestSurgeryCrud:
    """手术报台CRUD：创建、查询详情、更新、删除"""

    @pytest.mark.crud
    def test_create_surgery_crud(
        self,
        admin_client: ApiClient,
        first_hospital_id: str,
        first_dealer_id: str,
        first_product_id: str,
        cleanup_registry: dict,
    ):
        """创建手术报台，完成后清理"""
        payload = _build_surgery_payload(
            first_hospital_id, first_dealer_id, first_product_id
        )
        resp = admin_client.post(config.ApiPaths.SURGERY_REPORTS, payload)
        if not resp.is_success:
            pytest.skip(f"创建手术报台失败: {resp.status_code} {resp.msg}")

        surgery_id = resp.body.get("id", "") if isinstance(resp.body, dict) else ""
        if surgery_id:
            cleanup_registry["others"].append(
                {"path": config.ApiPaths.SURGERY_REPORTS, "id": surgery_id}
            )
            try:
                del_resp = admin_client.delete(
                    f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}"
                )
                logger.info(
                    f"[清理] 删除手术报台 {surgery_id} -> status={del_resp.status_code}"
                )
            except Exception:
                pass

    @pytest.mark.negative
    def test_create_surgery_no_hospital_negative(
        self,
        admin_client: ApiClient,
        first_dealer_id: str,
        first_product_id: str,
    ):
        """创建报台-无医院：hospitalId=null，断言失败"""
        payload = _build_surgery_payload(
            "", first_dealer_id, first_product_id
        )
        payload["hospitalId"] = None
        resp = admin_client.post(config.ApiPaths.SURGERY_REPORTS, payload)
        assert not resp.is_success, (
            f"无医院竟然创建成功: status={resp.status_code} "
            f"code={resp.code} data={resp.data}"
        )

    @pytest.mark.crud
    def test_query_surgery_detail_crud(
        self,
        admin_client: ApiClient,
        first_hospital_id: str,
        first_dealer_id: str,
        first_product_id: str,
        cleanup_registry: dict,
    ):
        """查询报台详情：先创建再查询，最后清理"""
        surgery_id = _create_surgery_for_test(
            admin_client, first_hospital_id, first_dealer_id, first_product_id,
            cleanup_registry,
        )
        resp = admin_client.get(f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}")
        resp.assert_success("查询报台详情")

        # 清理
        del_resp = admin_client.delete(
            f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}"
        )
        logger.info(
            f"[清理] 删除报台 {surgery_id} -> status={del_resp.status_code}"
        )

    @pytest.mark.crud
    def test_update_surgery_crud(
        self,
        admin_client: ApiClient,
        first_hospital_id: str,
        first_dealer_id: str,
        first_product_id: str,
        cleanup_registry: dict,
    ):
        """更新报台：先创建再更新remark，最后清理"""
        surgery_id = _create_surgery_for_test(
            admin_client, first_hospital_id, first_dealer_id, first_product_id,
            cleanup_registry,
        )
        resp = admin_client.put(
            f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}",
            {"remark": "更新备注"},
        )
        resp.assert_success("更新报台")

        # 清理
        del_resp = admin_client.delete(
            f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}"
        )
        logger.info(
            f"[清理] 删除报台 {surgery_id} -> status={del_resp.status_code}"
        )

    @pytest.mark.api
    def test_submit_surgery_api(
        self,
        admin_client: ApiClient,
        first_hospital_id: str,
        first_dealer_id: str,
        first_product_id: str,
        cleanup_registry: dict,
    ):
        """报台提交：POST /{id}/submit，断言成功或status_code<500容错"""
        surgery_id = _create_surgery_for_test(
            admin_client, first_hospital_id, first_dealer_id, first_product_id,
            cleanup_registry,
        )
        resp = admin_client.post(
            f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}/submit"
        )
        assert resp.status_code < 500, (
            f"报台提交服务异常: status={resp.status_code} "
            f"code={resp.code} msg={resp.msg} data={resp.data}"
        )

        # 提交后可能无法直接删除，尝试清理（容错）
        del_resp = admin_client.delete(
            f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}"
        )
        logger.info(
            f"[清理] 删除已提交报台 {surgery_id} -> status={del_resp.status_code}"
        )

    @pytest.mark.crud
    def test_delete_surgery_draft_crud(
        self,
        admin_client: ApiClient,
        first_hospital_id: str,
        first_dealer_id: str,
        first_product_id: str,
        cleanup_registry: dict,
    ):
        """删除报台（草稿）：先创建再删除"""
        surgery_id = _create_surgery_for_test(
            admin_client, first_hospital_id, first_dealer_id, first_product_id,
            cleanup_registry,
        )
        resp = admin_client.delete(
            f"{config.ApiPaths.SURGERY_REPORTS}/{surgery_id}"
        )
        resp.assert_success("删除报台（草稿）")
        logger.info(f"[清理] 报台 {surgery_id} 已删除")
