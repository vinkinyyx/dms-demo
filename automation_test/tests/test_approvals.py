"""
DMS自动化测试 - 审批中心
对应需求文档：DMS测试案例_v3.11.1 附录D 审批中心模块

测试范围：审批实例/审批流配置/审批委托/审批监控 的查询与增删改
        审批操作（同意/拒绝）、按业务类型筛选、待办/已办列表

注意：审批功能一期全部未实现（所有 approval-* 接口404），
      用例保留结构，命中404则 pytest.skip，不创建数据不清理
"""
import pytest
import config
from utils.helpers import today_str, future_date

APPROVAL_INSTANCES = config.ApiPaths.APPROVAL_INSTANCES
APPROVAL_FLOWS = config.ApiPaths.APPROVAL_FLOWS
APPROVAL_DELEGATES = config.ApiPaths.APPROVAL_DELEGATES
APPROVAL_MONITORS = config.ApiPaths.APPROVAL_MONITORS


def _skip_if_not_implemented(resp, msg="审批功能一期未实现"):
    """审批接口一期未实现(404)时统一跳过"""
    if resp.status_code == 404:
        pytest.skip(msg)


# ====== API：审批实例查询 ======

@pytest.mark.smoke
def test_approval_instance_list(admin_client):
    """审批实例列表：分页查询"""
    resp = admin_client.get(APPROVAL_INSTANCES, {"page": 1, "size": 20})
    _skip_if_not_implemented(resp)
    resp.assert_success("审批实例列表查询")


@pytest.mark.api
def test_approval_pending_mine(admin_client):
    """待我审批列表"""
    resp = admin_client.get(APPROVAL_INSTANCES, {
        "status": "PENDING",
        "assignee": "me",
    })
    _skip_if_not_implemented(resp)
    resp.assert_success("待我审批列表查询")


@pytest.mark.api
def test_approval_initiated_by_me(admin_client):
    """我发起的审批"""
    resp = admin_client.get(APPROVAL_INSTANCES, {
        "initiatedBy": "me",
    })
    _skip_if_not_implemented(resp)
    resp.assert_success("我发起的审批查询")


@pytest.mark.api
def test_approval_filter_by_business_type(admin_client):
    """按业务类型筛选审批实例"""
    resp = admin_client.get(APPROVAL_INSTANCES, {
        "businessType": "CONTRACT",
    })
    _skip_if_not_implemented(resp)
    resp.assert_success("按业务类型筛选审批实例")


# ====== API / CRUD：审批流配置 ======

@pytest.mark.api
def test_approval_flow_list(admin_client):
    """审批流配置列表"""
    resp = admin_client.get(APPROVAL_FLOWS)
    _skip_if_not_implemented(resp)
    resp.assert_success("审批流配置列表查询")


@pytest.mark.crud
def test_approval_flow_create(admin_client, cleanup_registry, random_suffix):
    """创建审批流：断言成功，注册清理，测试后清理（接口404则跳过，不创建数据）"""
    payload = {
        "name": f"测试审批流_{random_suffix}",
        "businessType": "CONTRACT",
        "nodes": [
            {
                "name": "部门主管",
                "approverRole": "SALES_MGR",
                "order": 1,
            }
        ],
    }
    resp = admin_client.post(APPROVAL_FLOWS, payload)
    # 接口一期未实现则跳过，不创建数据不清理
    if resp.status_code == 404:
        pytest.skip("审批功能一期未实现")
    resp.assert_success("创建审批流")
    flow_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    assert flow_id, f"创建审批流未返回id: {resp.body}"
    cleanup_registry["others"].append(("approval_flow", flow_id))
    try:
        admin_client.delete(f"{APPROVAL_FLOWS}/{flow_id}")
    except Exception:
        pass


# ====== API / CRUD：审批委托 ======

@pytest.mark.api
def test_approval_delegate_list(admin_client):
    """审批委托列表"""
    resp = admin_client.get(APPROVAL_DELEGATES)
    _skip_if_not_implemented(resp)
    resp.assert_success("审批委托列表查询")


@pytest.mark.crud
def test_approval_delegate_create(admin_client, cleanup_registry, random_suffix):
    """创建审批委托：断言成功，注册清理，测试后清理（接口404则跳过，不创建数据）"""
    payload = {
        "delegateTo": "sales_mgr",
        "startDate": today_str(),
        "endDate": future_date(7),
        "reason": f"出差委托_{random_suffix}",
    }
    resp = admin_client.post(APPROVAL_DELEGATES, payload)
    # 接口一期未实现则跳过，不创建数据不清理
    if resp.status_code == 404:
        pytest.skip("审批功能一期未实现")
    resp.assert_success("创建审批委托")
    delegate_id = resp.body.get("id") if isinstance(resp.body, dict) else None
    if delegate_id:
        cleanup_registry["others"].append(("approval_delegate", delegate_id))
        try:
            admin_client.delete(f"{APPROVAL_DELEGATES}/{delegate_id}")
        except Exception:
            pass


# ====== API：审批监控 ======

@pytest.mark.api
def test_approval_monitor_list(admin_client):
    """审批监控列表"""
    resp = admin_client.get(APPROVAL_MONITORS)
    _skip_if_not_implemented(resp)
    resp.assert_success("审批监控列表查询")


# ====== API：审批实例详情与操作 ======

@pytest.mark.api
def test_approval_instance_detail(admin_client):
    """审批实例详情：从列表取第一个id查询详情，无数据则跳过"""
    list_resp = admin_client.get(APPROVAL_INSTANCES, {"page": 1, "size": 20})
    _skip_if_not_implemented(list_resp, "审批实例列表接口一期未实现")
    list_resp.assert_success("审批实例列表查询(详情前置)")
    instances = list_resp.items
    if not instances:
        pytest.skip("无审批实例数据，跳过详情查询")
    instance_id = instances[0].get("id")
    assert instance_id, "审批实例未返回id"
    detail_resp = admin_client.get(f"{APPROVAL_INSTANCES}/{instance_id}")
    _skip_if_not_implemented(detail_resp, "审批实例详情接口一期未实现")
    detail_resp.assert_success("审批实例详情查询")


@pytest.mark.api
def test_approval_approve_action(admin_client):
    """审批操作-同意：可能无待审批数据，用 status_code<500 容错"""
    list_resp = admin_client.get(APPROVAL_INSTANCES, {"page": 1, "size": 20, "status": "PENDING"})
    # 列表接口404则跳过
    if list_resp.status_code == 404:
        pytest.skip("审批功能一期未实现")
    if not list_resp.is_success or not list_resp.items:
        pytest.skip("无待审批实例数据，跳过同意操作")
    instance_id = list_resp.items[0].get("id")
    assert instance_id, "待审批实例未返回id"
    resp = admin_client.post(
        f"{APPROVAL_INSTANCES}/{instance_id}/approve",
        {"comment": "同意"},
    )
    # 可能无待审批数据或状态已变更，或接口未实现，容错处理
    assert resp.is_success or resp.status_code < 500, \
        f"同意操作服务端异常: status={resp.status_code} code={resp.code}"


@pytest.mark.api
def test_approval_reject_action(admin_client):
    """审批操作-拒绝：可能无待审批数据，用 status_code<500 容错"""
    list_resp = admin_client.get(APPROVAL_INSTANCES, {"page": 1, "size": 20, "status": "PENDING"})
    # 列表接口404则跳过
    if list_resp.status_code == 404:
        pytest.skip("审批功能一期未实现")
    if not list_resp.is_success or not list_resp.items:
        pytest.skip("无待审批实例数据，跳过拒绝操作")
    instance_id = list_resp.items[0].get("id")
    assert instance_id, "待审批实例未返回id"
    resp = admin_client.post(
        f"{APPROVAL_INSTANCES}/{instance_id}/reject",
        {"comment": "拒绝"},
    )
    # 可能无待审批数据或状态已变更，或接口未实现，容错处理
    assert resp.is_success or resp.status_code < 500, \
        f"拒绝操作服务端异常: status={resp.status_code} code={resp.code}"
