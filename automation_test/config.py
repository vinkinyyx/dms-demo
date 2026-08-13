"""
DMS自动化测试 - 环境配置
支持测试环境/生产环境切换，通过环境变量DMS_ENV控制
"""
import os

# ====== 环境选择 ======
DMS_ENV = os.getenv("DMS_ENV", "test")  # test | prod

# ====== 环境配置 ======
ENVIRONMENTS = {
    "test": {
        "base_url": "http://43.128.145.141",
        "api_base": "http://43.128.145.141",
        "frontend_url": "http://43.128.145.141",
        "admin_url": "http://43.128.145.141/admin",
        "mobile_url": "http://43.128.145.141/mobile",
    },
    "prod": {
        "base_url": "http://8.133.193.238:8081",
        "api_base": "http://8.133.193.238:8080",
        "frontend_url": "http://8.133.193.238:8081",
        "admin_url": "http://8.133.193.238:8081/admin",
        "mobile_url": "http://8.133.193.238:8081/mobile",
    },
}

# ====== 当前环境 ======
ENV = ENVIRONMENTS[DMS_ENV]
# 允许通过 DMS_BASE_URL 覆盖（例如临时指向不同端口/实例）
_override = os.getenv("DMS_BASE_URL", "").rstrip("/")
BASE_URL = _override or ENV["base_url"]
API_BASE = _override or ENV["api_base"]

# ====== 默认账号 ======
ACCOUNTS = {
    # 业务前台 - 默认管理员
    "admin": {"username": "admin", "password": "Sh123456", "tenant_code": ""},
    # 业务前台 - 测试账号（统一密码Dms@123456）
    "sys_admin": {"username": "sys_admin", "password": "Dms@123456", "tenant_code": ""},
    "sales_mgr": {"username": "sales_mgr", "password": "Dms@123456", "tenant_code": ""},
    "sales": {"username": "sales", "password": "Dms@123456", "tenant_code": ""},
    "cs": {"username": "cs", "password": "Dms@123456", "tenant_code": ""},
    "biz": {"username": "biz", "password": "Dms@123456", "tenant_code": ""},
    "fin": {"username": "fin", "password": "Dms@123456", "tenant_code": ""},
    "contract": {"username": "contract", "password": "Dms@123456", "tenant_code": ""},
    "dealer_admin": {"username": "dealer_admin", "password": "Dms@123456", "tenant_code": ""},
    # 多租户账号
    "mfr_a_admin": {"username": "mfr_a_admin", "password": "Sh123456", "tenant_code": "MFR_A"},
    "mfr_b_admin": {"username": "mfr_b_admin", "password": "Sh123456", "tenant_code": "MFR_B"},
    "dealer_a1_admin": {"username": "dealer_a1_admin", "password": "Sh123456", "tenant_code": "DEALER_A1"},
    # 平台后台
    "platform_admin": {"username": "admin", "password": "Sh123456"},
}

# ====== API路径常量 ======
class ApiPaths:
    # 认证
    LOGIN = "/api/auth/login"
    REFRESH = "/api/auth/refresh"
    ME = "/api/auth/me"
    ADMIN_LOGIN = "/api/admin/auth/login"
    ADMIN_ME = "/api/admin/auth/me"

    # 基础数据
    PRODUCTS = "/api/products"
    CATEGORIES = "/api/product-categories"
    PRODUCT_LINES = "/api/product-lines"
    PACKAGE_LEVELS = "/api/product-package-levels"
    PRODUCT_BUNDLES = "/api/product-bundles"
    DEALERS = "/api/dealers"
    HOSPITALS = "/api/hospitals"
    WAREHOUSES = "/api/warehouses"
    REGIONS = "/api/regions"
    SUPPLIERS = "/api/suppliers"
    PRODUCT_PRICES = "/api/product-prices"

    # 合同
    CONTRACTS = "/api/contracts"
    CONTRACT_TEMPLATES = "/api/contract-templates"
    AUTHORIZATIONS = "/api/authorizations"

    # 订单
    SALES_ORDERS = "/api/sales-orders"
    PURCHASE_ORDERS = "/api/purchase-orders"
    SALES_RETURNS = "/api/sales-returns"
    PURCHASE_RETURNS = "/api/purchase-returns"

    # 库存
    INVENTORY = "/api/inventory"
    GOODS_ISSUES = "/api/sales-outs"
    GOODS_RECEIPTS = "/api/receipts"
    STOCK_MOVES = "/api/stock-moves"
    STOCK_ADJUSTMENTS = "/api/inventory-adjustments"

    # 手术与营销
    SURGERY_REPORTS = "/api/surgery-reports"
    PROMOTIONS = "/api/promotions"

    # 报表
    REPORT_ORDER_TRACE = "/api/reports/order-trace"
    REPORT_SALES_RANK = "/api/reports/sales-ranking"
    REPORT_TOP_PRODUCTS = "/api/reports/top-products"           # 一期未实现，测试跳过
    REPORT_INVENTORY_TURNOVER = "/api/reports/inventory-turnover"
    REPORT_SURGERY_STAT = "/api/reports/surgery-stat"           # 一期未实现，测试跳过
    REPORT_AR = "/api/reports/accounts-receivable"              # 一期未实现，测试跳过

    # 产品对码
    PRODUCT_MAPPINGS = "/api/product-mappings"

    # 审批
    APPROVAL_INSTANCES = "/api/approval-instances"
    APPROVAL_FLOWS = "/api/approval-flows"
    APPROVAL_DELEGATES = "/api/approval-delegates"
    APPROVAL_MONITORS = "/api/approval-monitors"

    # 用户与权限
    ACCOUNTS = "/api/users"
    ROLES = "/api/roles"
    SALES_POSITIONS = "/api/sales-positions"
    PERMISSIONS = "/api/me/permissions"
    UI_LAYOUT = "/api/menus"

    # 平台后台
    ADMIN_TENANTS = "/api/admin/tenants"
    ADMIN_USERS = "/api/admin/users"
    ADMIN_DICT_TYPES = "/api/admin/dict-types"
    ADMIN_DICT_ITEMS = "/api/admin/dict-items"
    ADMIN_MENUS = "/api/admin/menus"
    ADMIN_AUDIT_LOGS = "/api/admin/audit-logs"
    ADMIN_LOGIN_LOGS = "/api/admin/login-logs"
    ADMIN_API_LOGS = "/api/admin/api-call-logs"
    ADMIN_TENANT_BINDINGS = "/api/admin/tenant-dealer-bindings"

    # 通用
    DICT_ITEMS = "/api/dict-items"
    OPERATION_LOGS = "/api/operation-log"

    # P0新功能 - 消息中心
    NOTIFICATIONS = "/api/notifications"
    NOTIFICATIONS_MARK_READ = "/api/notifications/mark-read"
    NOTIFICATIONS_MARK_ALL_READ = "/api/notifications/mark-all-read"
    NOTIFICATIONS_UNREAD_COUNT = "/api/notifications/unread-count"

    # P0新功能 - 登录日志
    LOGIN_LOGS = "/api/login-logs"

    # P0新功能 - 审批详情摘要
    APPROVAL_DETAIL_SUMMARY = "/api/approval-instances/{id}/summary"

# ====== 超时设置 ======
TIMEOUT = 30  # 默认请求超时秒数
LONG_TIMEOUT = 120  # 导入导出等长操作超时

# ====== 测试数据默认值 ======
DEFAULT_PAGE_SIZE = 20
DEFAULT_PAGE = 1
