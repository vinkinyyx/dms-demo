# DMS 自动化测试脚本（v3.11.x）

基于 **Python 3.10+ / pytest / requests** 的 DMS 医疗器械经销商管理系统接口自动化测试。
覆盖 **业务前台 + 平台后台 + 移动端共用接口**，对应需求文档：
- `DMS测试案例_v3.11.1_附录D+平台后台专项.md`（附录 D：业务前台 850+ 用例，附录 E：平台后台 277 条专项）

---

## 目录结构

```
automation_test/
├── README.md                    # 本文件
├── pytest.ini                   # pytest 配置（marker、日志、warnings）
├── requirements.txt             # 依赖清单
├── config.py                    # 环境配置、账号、API 路径常量
├── conftest.py                  # pytest 全局 Fixture（登录客户端、基础数据、清理注册器）
├── utils/
│   ├── api_client.py            # 统一 HTTP 客户端（Auth / 响应包装 / 断言方法）
│   └── helpers.py               # 随机数据、日期、测试数据模板（build_product/dealer）
└── tests/                       # 15 个测试模块，224 条用例
    ├── test_login.py            #  18 条：登录认证 / 工作台 / Token 隔离 / UI 布局
    ├── test_products.py         #  14 条：产品 CRUD / 筛选 / 导出
    ├── test_categories.py       #  15 条：分类/产品线/供应商/仓库/医院/区域/字典
    ├── test_contracts.py        #  14 条：合同 CRUD / 模板 / 授权 / 审批提交 / 导出
    ├── test_sales_orders.py     #  13 条：销售订单 / 销售退货
    ├── test_purchase_orders.py  #  11 条：采购订单 / 采购退货
    ├── test_inventory.py        #  10 条：库存 / 出入库 / 调整 / 预警
    ├── test_surgery.py          #  10 条：手术报台 CRUD / 提交
    ├── test_promotions.py       #  10 条：促销 CRUD / 启用停用
    ├── test_reports.py          #  12 条：6 类报表 + 时间维度 + 导出 + 权限
    ├── test_approvals.py        #  12 条：审批实例 / 流配置 / 委托 / 监控 / 同意拒绝
    ├── test_permissions.py      #  15 条：账号 / 角色 / 岗位 / 权限码 / UI 布局
    ├── test_platform.py         #  18 条：平台租户 CRUD / 用户 / 字典 / 菜单 / 日志 / 绑定
    ├── test_security.py         #  16 条：Token 隔离 / SQL 注入 / XSS / 权限 / 跨租户隔离
    └── test_integration.py      #   5 条：E2E 全链路（产品→订单、采购→出入库、合同→审批、手术→订单、促销→订单）
```

---

## 快速开始

### 1. 安装依赖

```bash
cd automation_test
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate

pip install -r requirements.txt
```

### 2. 环境切换

通过环境变量 `DMS_ENV` 控制，默认 **`test`**：

```powershell
# Windows PowerShell（测试环境，默认）
$env:DMS_ENV = "test"
# Windows PowerShell（生产环境）
$env:DMS_ENV = "prod"
```

| 环境   | 前端入口                    | API 地址                     |
| ------ | --------------------------- | ---------------------------- |
| `test` | http://8.133.193.238:8083   | http://8.133.193.238:8082    |
| `prod` | http://8.133.193.238:8081   | http://8.133.193.238:8080    |

如需改本地开发环境，直接在 `config.py` 的 `ENVIRONMENTS` 里新增 key 即可。

### 3. 运行测试

```bash
# 全部用例（推荐）
pytest -v

# 仅冒烟用例（登录 + 核心CRUD，约 2~3 分钟）
pytest -v -m smoke

# 仅 API 查询类
pytest -v -m "api"

# 仅 CRUD 链路
pytest -v -m "crud"

# 仅安全专项
pytest -v -m "security"

# 仅集成场景（E2E 全链路）
pytest -v -m "integration"

# 单模块运行
pytest tests/test_login.py tests/test_products.py -v

# 生成 HTML 报告
pytest -v --html=reports/report.html --self-contained-html
```

pytest 会自动按 `conftest.py` 顺序：先登录获取 token（session 级，复用整个测试会话），再执行各测试函数。

---

## Fixture 说明（全部已在 conftest.py 定义，可直接注入测试函数）

| Fixture                | Scope    | 说明                                                         |
| ---------------------- | -------- | ------------------------------------------------------------ |
| `admin_client`         | session  | 业务前台 admin / Sh123456，全部权限                          |
| `platform_client`      | session  | 平台后台 admin / Sh123456，管理跨租户平台数据                |
| `sales_client`         | session  | 销售角色 `sales` / Dms@123456                                |
| `sales_mgr_client`     | session  | 销售经理 `sales_mgr` / Dms@123456                            |
| `fin_client`           | session  | 财务 `fin` / Dms@123456                                      |
| `dealer_client`        | session  | 经销商管理员 `dealer_admin` / Dms@123456                     |
| `first_product_id`     | session  | 首个产品 ID（无则空串，可 `pytest.skip` 跳过）               |
| `first_dealer_id`      | session  | 首个经销商 ID                                                |
| `first_warehouse_id`   | session  | 首个仓库 ID                                                  |
| `first_supplier_id`    | session  | 首个供应商 ID                                                |
| `first_hospital_id`    | session  | 首个医院 ID                                                  |
| `cleanup_registry`     | function | 测试数据清理注册器（dict，含 products/orders/categories/others，conftest teardown 只记日志，实际删除在测试内手动调用 `delete`） |
| `random_suffix`        | function | 8 位随机串：`f"产品_{random_suffix}"` 避免数据冲突            |
| `assert_helper`        | function | 通用断言：`assert_pagination` / `assert_not_empty` / `assert_in_list` |

---

## API 客户端与响应断言

所有 HTTP 通过 `utils.api_client.ApiClient` 发起：

```python
# 1. 带鉴权的查询
resp = admin_client.get(config.ApiPaths.PRODUCTS, params={"page": 1, "size": 20})
resp.assert_success("产品列表查询失败")
assert resp.total >= 0
assert isinstance(resp.items, list)

# 2. 创建
payload = helpers.build_product(nameCn="测试")
resp = admin_client.post(config.ApiPaths.PRODUCTS, json_data=payload)
resp.assert_success("创建产品失败")
pid = resp.body.get("id")

# 3. 断言鉴权失败（401/403）
resp = sales_client.get(config.ApiPaths.ACCOUNTS)
assert resp.is_auth_error, "销售应无权访问账号管理"
```

响应对象 `ApiResponse` 属性/方法：

| 成员 / 方法               | 说明                                                         |
| ------------------------- | ------------------------------------------------------------ |
| `resp.status_code`        | HTTP 状态码                                                  |
| `resp.code`               | 业务码（默认 200=成功）                                      |
| `resp.msg`                | 业务消息                                                     |
| `resp.body`               | `data` 字段内容（对象或列表）                                |
| `resp.total`              | 列表总条数                                                   |
| `resp.items`              | 列表数据（兼容 items / list 两种字段名）                     |
| `resp.is_success`         | `status=200 and code=200`                                   |
| `resp.is_auth_error`      | `status in (401, 403)`                                       |
| `resp.assert_success(msg)`| 失败抛 AssertionError，附带 status/code/msg                  |
| `resp.assert_status(code, msg)` | 校验 HTTP 状态码                                        |
| `resp.assert_code(code, msg)`   | 校验业务码                                              |
| `resp.assert_msg_contains(kw)`  | 消息包含关键词                                          |

---

## 测试数据约定

1. **唯一性**：创建型用 `random_code("PROD")` / `random_string(8)` 生成唯一编码名称，避免污染环境。
2. **清理**：所有 `POST` 创建后，在断言成功后**立即**调用 `admin_client.delete(f"{PATH}/{id}")` 删除，并同步注册到 `cleanup_registry`（conftest teardown 会日志提示）。
3. **容错**：状态流转（`submit` / `approve` / `activate`）只断言 `status_code < 500`，不强制业务成功——因为草稿/审批状态由具体业务规则决定，非 5xx 即可。
4. **跳过**：`first_xxx_id` 为空或多租户账号登录失败时，使用 `pytest.skip("无xxx数据")`，不报错。

---

## Marker 清单

已注册（`pytest.ini`）+ 通用标记：

```
smoke       冒烟（登录/核心 CRUD，上线前必跑）
api         纯查询类接口
crud        新增/修改/删除 链路
negative    异常/负向用例（错误参数、空字段、注入）
security    安全专项（Token、SQL注入、XSS、权限、租户隔离）
integration 端到端集成场景
e2e         同 integration，用于 --html 报告按标记分组
export      导入导出相关
```

---

## 交付核查清单（执行前确认）

- [ ] `pip install -r requirements.txt` 依赖已安装
- [ ] 测试环境 8.133.193.238:8082 后端可达（浏览器访问 `/actuator/health` 返回 `{"status":"UP"}`）
- [ ] 默认账号可正常登录（admin/Sh123456、sys_admin/Dms@123456）
- [ ] 测试租户已具备最少 1 条 产品 / 经销商 / 仓库 / 供应商 / 医院 基础数据

---

## 结果与后续可选项

1. **首次运行建议**：`pytest -v -m smoke` 验证环境和基础链路 → `pytest -v -m "api or crud"` → 最后 `pytest -v` 全量。
2. **CI 集成**：可直接接入 Jenkins / GitLab CI，使用 `--junitxml=junit.xml --html=reports/report.html` 双报告。
3. **扩展前端 E2E**：当前为 API 级自动化，如需补 UI 自动化，可在 `automation_test/` 下新增 `e2e_ui/` 用 Playwright，复用 `config.py` 账号和基础数据。
4. **接口字段对齐**：若实际后端字段名与用例假设不同（如 `contractName` → `title`），只需修改对应测试文件的 payload 字段，不影响框架层。
