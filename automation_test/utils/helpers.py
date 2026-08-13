"""
DMS自动化测试 - 辅助工具函数
"""
import random
import string
import time
import uuid
from datetime import datetime, timedelta
from typing import Optional


def random_string(prefix: str = "", length: int = 6) -> str:
    """生成随机字符串"""
    chars = string.ascii_letters + string.digits
    return prefix + "".join(random.choices(chars, k=length))


def random_code(prefix: str = "TEST") -> str:
    """生成随机编码（大写+数字）"""
    chars = string.ascii_uppercase + string.digits
    return prefix + "-" + "".join(random.choices(chars, k=6))


def random_phone() -> str:
    """生成随机手机号"""
    return "13" + "".join(random.choices(string.digits, k=9))


def random_email() -> str:
    """生成随机邮箱"""
    return f"test_{random_string(length=6)}@dms-test.com"


def uuid_str() -> str:
    """生成UUID字符串"""
    return str(uuid.uuid4())


def today_str(fmt: str = "%Y-%m-%d") -> str:
    """今天的日期字符串"""
    return datetime.now().strftime(fmt)


def now_str(fmt: str = "%Y-%m-%d %H:%M:%S") -> str:
    """当前时间字符串"""
    return datetime.now().strftime(fmt)


def future_date(days: int = 30, fmt: str = "%Y-%m-%d") -> str:
    """未来日期"""
    return (datetime.now() + timedelta(days=days)).strftime(fmt)


def past_date(days: int = 30, fmt: str = "%Y-%m-%d") -> str:
    """过去日期"""
    return (datetime.now() - timedelta(days=days)).strftime(fmt)


def wait(seconds: float):
    """等待"""
    time.sleep(seconds)


def merge_dict(base: dict, override: dict) -> dict:
    """合并字典（override覆盖base）"""
    result = dict(base)
    if override:
        result.update(override)
    return result


# ====== 测试数据模板 ======
PRODUCT_TEMPLATE = {
    "productCode": "",
    "nameCn": "",
    "nameEn": "",
    "productType": "高值耗材",
    "categoryId": None,
    "spec": "测试规格100mm",
    "unit": "件",
    "refPrice": 6800.00,
    "taxRate": 0.13,
    "udiTrace": False,
    "serialNoMgmt": False,
    "expireWarnMonths": 3,
    "safetyStock": 10,
    "minOrderQty": 1,
    "status": "ACTIVE",
}

DEALER_TEMPLATE = {
    "code": "",
    "name": "",
    "level": "A",
    "region": "华东",
    "contactPerson": "测试联系人",
    "phone": "",
    "email": "",
    "address": "测试地址",
    "creditCode": "",
    "status": "ACTIVE",
}

SO_TEMPLATE = {
    "type": "NORMAL",
    "dealerId": None,
    "warehouseId": None,
    "items": [],
    "remark": "自动化测试订单",
}

PO_TEMPLATE = {
    "supplierId": None,
    "warehouseId": None,
    "type": "NORMAL",
    "items": [],
    "remark": "自动化测试采购单",
}


def build_product(**kwargs) -> dict:
    """构建产品测试数据"""
    data = dict(PRODUCT_TEMPLATE)
    data["productCode"] = random_code("PROD")
    data["nameCn"] = f"测试产品_{random_string(length=4)}"
    data["nameEn"] = f"Test Product {random_string(length=4)}"
    data["phone"] = random_phone() if "phone" not in kwargs else kwargs["phone"]
    data.update(kwargs)
    return data


def build_dealer(**kwargs) -> dict:
    """构建经销商测试数据"""
    data = dict(DEALER_TEMPLATE)
    data["code"] = random_code("D")
    data["name"] = f"测试经销商_{random_string(length=4)}"
    data["phone"] = random_phone()
    data["email"] = random_email()
    data["creditCode"] = random_string(length=18)
    data.update(kwargs)
    return data
