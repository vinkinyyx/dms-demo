# DMS 测试分层策略（v4.6 测试框架统一重构后生效）

> 目标：一套**全面、不重复、每层只用最合适唯一工具**的测试框架。
> v4.6 起统一工具链（见 §9），历史 PATCH 范围测试（§1~§8）继续有效，命令以 §10 为准。

## 1. 三层分类（按版本号触发）

| 版本 | 触发命令 | 测试集 | 覆盖范围 | 预计耗时 |
|------|----------|--------|----------|----------|
| **PATCH** (4.2.x → 4.2.y) | `node tools/test-scope.cjs --scope=<明确指定>` | 范围深度测试 | 改动模块 + 上下游依赖 + 5 条核心业务流基线 | 2-4 min |
| **MINOR** (4.2 → 4.3) | `node tools/smoke-test.cjs` | 全量冒烟（保持现有） | PC 端所有页面 + Admin + Mobile | 10-12 min |
| **MAJOR** (4.x → 5.0) | `node tools/smoke-test.cjs` + 兼容性 + 性能 | 全量 + 升级验证 | MINOR 全部 + 数据兼容性 + 性能基准 | 20-30 min |

## 2. 范围深度测试的 5 维结构

对每个被测流程，**必须**包含 5 个维度（缺一不算 PATCH 可发布）：

```
流程：<名称>
├─ [前端 UI] Playwright 真实点击
│   ├─ 列表：搜索/重置/分页
│   ├─ 新建：填字段→保存→回列表看到
│   ├─ 详情/编辑：点击行内→改字段→保存
│   ├─ 状态动作：提交/审批/驳回/取消的可见性 + 二次确认
│   └─ 业务按钮：导入/导出/打印/批量
├─ [后端 API] Python requests 直调
│   ├─ 主流程：POST/GET/PUT 各端点返回 200 + DTO 完整
│   ├─ 边界：缺字段/越界/重复提交
│   └─ 性能：单接口 P95 < 2s（生产环境要求）
├─ [数据库] SQL 直查回读
│   ├─ 单据表 status 字段
│   ├─ 关联行：明细行/审批实例/日志
│   ├─ 业务量：库存扣减、价格生效、锁定量
│   └─ 不变量：唯一约束、CHECK 约束
├─ [业务规则]
│   ├─ BOM：母件 vs 子件、组合品
│   ├─ 促销：阶梯/买赠/适用范围
│   ├─ 价格：SALE/PURCHASE 优先级、partnerId 回退
│   ├─ 库存：可用量、锁定量、批次
│   └─ 反向顺序：A 先选 vs B 先选
└─ [异常路径]
    ├─ 提交后撤销
    ├─ 审批驳回后状态回退
    ├─ 重复提交幂等
    └─ 并发：两个用户同时操作
```

## 3. 5 条核心业务流基线（PATCH 必跑）

不管你改了什么模块，**这 5 条 P0 业务流每次 PATCH 都必须跑**（半全量保证）：

| ID | 流程 | 前端路径 | 后端 service | E2E spec |
|----|------|----------|--------------|----------|
| **F1** | 下单→审批→出库→库存扣减 | `/m/orders` → `/m/sales-outs` → `/m/inventory` | `OrderService` / `ApprovalService` / `V4ErpService` | `02-orders.spec.js`, `12-orders-promo-bom-return.spec.js` |
| **F2** | 销退→选发货单→可退量锁定→回写 | `/m/sales-returns` | `SalesReturnService` / `SalesReturnLineSupport` | `10-bugfix-pricing-returns.spec.js`, `13-rma-amount-lineno.spec.js` |
| **F3** | 采购→收货→入库 | `/m/purchase-orders` → `/m/receipts` | `PurchaseOrderService` / `ReceiptService` | `02-orders.spec.js` |
| **F4** | BOM + 促销 + 下单计价 | `/m/orders` + `/m/promotions` | `PromotionService` / `V4Calculator` | `12-orders-promo-bom-return.spec.js`, `14-promo-target-cycle.spec.js` |
| **F5** | 审批流：提交→通过/驳回→状态 | `/approval/todo` | `ApprovalService` / `OrderApprovalExecutionService` | `06-reports-dashboard.spec.js` |

## 4. 范围识别契约（手动指定）

### 4.1 用户契约
> **每次提出 PATCH 修补时，必须显式说明涉及哪些业务流 / 模块。**
> 如果你只说"销售出库有问题"，Codex 必须像对待新需求一样向你确认：
> - 这个问题属于哪个业务流？（F1 下单链路？F4 促销链路？其他？）
> - 上下游依赖是否要一起测？（F1 的 F1 上游 = 价格/促销/审批模板；F1 下游 = 销退/库存）
> - 是否需要回归 5 条核心业务流？（默认要）

### 4.2 Codex 必须主动反问的场景
触发以下任一条件时，Codex **必须**反问（按 AGENTS.md 阶段 A 规则）：

1. 你只说"X 有问题"未说明"X 改什么 / 影响什么"
2. 你提到的功能跨多个 service（如下单问题可能涉及 Order + Pricing + Promotion + Approval）
3. 改动涉及数据库 schema（Flyway 迁移）
4. 改动涉及 4.2.0 已重构的胖 controller 下沉模块

反问模板：
```
需求范围确认：
- 你提到 [X 问题]，我理解涉及的直接模块是 [A, B]
- 上游依赖 [C, D] 是否也需要测？（如下单 → 审批/价格/促销）
- 下游影响 [E, F] 是否需要测？（如下单 → 销退/库存）
- 是否需要把 5 条核心业务流也跑一遍？（默认要）
```

### 4.3 范围参数格式
```bash
# 单流程
node tools/test-scope.cjs --scope=sales-out
# 多流程（覆盖核心流 + 改动流）
node tools/test-scope.cjs --scope=sales-out,sales-return,order
# 加额外范围（逗号分隔）
node tools/test-scope.cjs --scope=sales-out --include=order
# 强制跑全部 5 条核心流
node tools/test-scope.cjs --scope=sales-out --core-flows=all
# dry-run（仅输出待测清单，不执行）
node tools/test-scope.cjs --scope=sales-out --dry-run
```

## 5. PATCH 发布检查清单（自检）

发布前必须通过：

- [ ] 已与用户确认涉及的业务流与上下游（步骤 4）
- [ ] 已运行 `node tools/test-scope.cjs --scope=<...> --dry-run` 输出待测清单
- [ ] 范围内所有 5 维测试 100% 通过
- [ ] 5 条核心业务流基线测试 100% 通过（除非用户明确说"这次改动不影响 F1-F5"）
- [ ] 部署后 L1 静态（npm run test:all -- --level=1）通过
- [ ] 部署后 L2 API 主接口（npm run test:all -- --level=2）通过
- [ ] 部署后生产 3 端 /actuator/health 200
- [ ] CHANGELOG.md 顶部条目 + AGENTS.md 当前版本号同步

## 6. MINOR/MAJOR 发布检查清单（自检）

发布前必须通过：

- [ ] 上述 PATCH 全部 7 项
- [ ] 已运行 `node tools/smoke-test.cjs` 全量冒烟，PASS 率 ≥ 99%
- [ ] MINOR：CHANGELOG 顶部新增 v4.x.0 条目，覆盖本版本所有新功能
- [ ] MAJOR：数据兼容性脚本（V*-downgrade.sql）已写好并测试
- [ ] MAJOR：生产数据备份（pg_dump）已完成
- [ ] MAJOR：30 分钟回滚路径已演练

## 7. 测试工具与脚本

| 脚本 | 用途 | 触发场景 |
|------|------|----------|
| `tools/smoke-test.cjs` | 全量冒烟（PC + Admin + Mobile 三端 5 步） | MINOR/MAJOR 部署前 |
| `tools/test-scope.cjs` | 范围深度测试调度（5 维） | PATCH 部署前 |
| `automation_test/e2e/specs/*.spec.js` | Playwright E2E（15 个 spec） | PATCH/MINOR 被 `test-scope.cjs` 调度 |
| `backend/src/test/**/*.java` | Junit 集成测试 | 开发期 |
| `npm run test:all` | L1 + L2 + L3 + L4 全套 | CI 或重大发布前 |

## 8. 文档维护

- `tools/scope-map.json` 维护模块→路径/API/表/E2E 的映射；新增/删除模块时同步更新
- 每次 PATCH 后如有范围变化，更新本文件 §3 的核心流表格
- 测试失败/发现的 bug 必须写进 `.memory/layers/layer3-lessons.md`

---

## 9. 统一测试框架（v4.6 重构，每个关注点唯一工具）

> 原则：同一测试关注点只用**一个**最合适工具，不堆叠；能复用已有底座就不引入新工具；
> 工具不合适就替换（本次已用 Playwright Test 替换散装 `.cjs` + Python 冒烟在主编排中的角色，删除未用的 H2）。

### 9.1 工具选型（唯一职责）

| 层 | 工具（唯一） | 测什么 | 位置 |
|----|--------------|--------|------|
| L1 静态门禁 | `tools/lint-static.js` | 硬编码 API 路径、中文乱码、`console.log`、ISO/UTC 日期直渲染 | `tools/` |
| L2 后端单元 | JUnit5 + Mockito + AssertJ | 计价引擎、BOM、代金券分摊、审批状态机等纯逻辑 | `backend/src/test/**/*Test.java` |
| L2 后端集成（免 Docker） | Spring Boot Test + MockMvc + **zonky 嵌入式 PostgreSQL14** | 控制器/仓储/Flyway 真实 PG，Redis/Minio 用 mock | `backend/src/test/**/*IntegrationTest.java` |
| L2 后端集成（真实基建） | **Testcontainers（仅 Redis7）** + failsafe `*IT` | 登录限流、token 黑名单、分布式状态；无 Docker 自动跳过 | `backend/src/test/java/com/dms/it/**/*IT.java` |
| L2 覆盖率 | **JaCoCo** | 后端行覆盖门禁（`mvn verify`） | `backend/target/site/jacoco` |
| L3 前端单元 | **Vitest + @vue/test-utils** | 金额/分摊/格式化纯函数、组件交互；覆盖率门禁 | `frontend-vue/src/**/*.spec.js` |
| L4 黑盒 API | **Playwright Test（request）** | 三端登录、核心列表接口、业务流 API 直调 | `tests/api/` |
| L4 黑盒 UI 三端 | **Playwright Test（browser）** | PC/Admin/H5 真实点击、Console/Network 监听、外键显名称 | `tests/ui-pc|ui-admin|ui-mobile/` |
| L4 部署 GATE | **Playwright Test** | 铁律9：全部用户入口逐条浏览器验证 + health UP | `tests/gate/deploy-gate.spec.js` |
| L4 数据库回读 | Node **`pg`** | 关键操作后 SQL 回读金额/状态/库存/回滚 | `tests/helpers/db.js`（`PGHOST` 等环境变量） |
| L5 性能 | **k6**（JS） | 核心只读接口阶梯压测，p95<800ms、错误率<1% | `tests/performance/smoke-load.js` |
| L6 安全 | **OWASP ZAP**（DAST） | 多租户越权、注入、XSS、token 越权 | `tests/security/README.md` |
| 测试数据 | **@faker-js/faker**（按需） | 造经销商/产品/订单，前后置清理 | fixtures |

**已去重/替换**：删除从未使用的 H2 依赖；PostgreSQL 集成统一用 zonky 嵌入式 PG（免 Docker），
不重复引 testcontainers-postgresql；Testcontainers **只**承担真实 Redis；Python `api_smoke.py`
与散装 `.cjs` 审计脚本不再进主编排（等价能力由 `tests/api`、`tests/gate` 覆盖），旧文件保留备查。
报告统一用各工具自带 HTML（JaCoCo/Vitest/Playwright），不额外引入报告平台。

### 9.2 目录结构

```
tests/
  playwright.config.js        # 5 个 project: api/pc/admin/mobile/gate
  helpers/env.js              # 环境地址/账号/铁律9入口清单（唯一来源）
  helpers/db.js               # pg 只读回读（未配置 PGHOST 时 skip）
  fixtures/test-fixtures.js   # authedApi/authedPage + Console/Network 错误守卫
  api/                        # 黑盒 API（request）
  ui-pc/ ui-admin/ ui-mobile/ # 三端真实浏览器
  gate/deploy-gate.spec.js    # 铁律9 部署后首检
  performance/smoke-load.js   # k6
  security/README.md          # ZAP 运行方式
backend/src/test/java/com/dms/it/   # *IT 真实 Redis（Testcontainers，无 Docker 跳过）
frontend-vue/src/utils/money.js      # 全站唯一金额工具（元/分/分摊）
```

## 10. 分级触发矩阵（什么时候跑哪级）

| 时机 | 必跑层级 | 命令 | 说明 |
|------|----------|------|------|
| 本地开发/提交前 | L1 + L2(单测) + L3 | `npm run lint:static` `npm run test:backend` `npm run test:frontend` | 秒级~分钟级，免外部环境 |
| PATCH 补丁 | L1+L2+L3 + 受影响模块 L4 | `npm run test:all`（L4 按 `--grep <模块>`） | 配合 §4 范围契约 + 5 维 |
| MINOR 小版本 | 全部 L1~L4 | `npm run test:all` | 三端全量 + 5 条核心流 |
| MAJOR 大版本 | L1~L4 + L5 性能 + 数据迁移 | `npm run test:all` + `npm run test:perf` | 另做升级/兼容性验证 |
| 部署/配置变更后（第一个动作） | **L4 部署 GATE** | `npm run test:gate` | 铁律9 强制，先于一切功能验证 |
| 发版前/容量评估 | L5 性能 | `BASE=.. TOKEN=.. npm run test:perf` | 需 k6；不进每次 CI |
| 发版前/安全评审 | L6 安全 | 见 `tests/security/README.md` | 需 Docker 跑 ZAP，高危清零 |
| CI（有 Docker） | L1~L4 含真实 Redis | `BACKEND_IT=1 npm run test:all` | `*IT` 真实执行 |

`npm run test:all` = L1 静态 → L2 后端 → L3 前端 → L4 Playwright（黑盒，需被测环境可达；
`SKIP_E2E=1` 可跳过 L4；`BACKEND_IT=1` 追加真实 Redis 集成测试）。

### 10.1 环境变量

- `E2E_BASE`：被测根地址（默认 `https://dms-dev.mysolmed.com`）
- `PW_PROJECTS`：只跑某些 project，如 `pc,mobile`；`E2E_HEADED=1` 有头调试
- `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD`：配置后启用 SQL 回读断言，否则回读 skip
- `BACKEND_IT=1`：后端 `mvn verify` 跑 Testcontainers 真实 Redis（需本机/CI 有 Docker）
- `BASE/TOKEN`：k6 压测目标与登录 token

## 11. 五维与业务流在新框架中的落位

- 前端 UI 真实点击 / 状态按钮回读 → `tests/ui-*/`（Playwright，错误守卫 `assertClean()`）
- 后端 API 直调 → `tests/api/`（黑盒）+ `backend *IntegrationTest`（白盒 MockMvc）
- 数据库 SQL 回读 → `tests/helpers/db.js` + 后端 `*Test/*IT` 直接断言仓储
- 业务规则（BOM/促销/价格/库存/反向顺序）→ 后端 `V4CalculatorTest`/`PromotionEngineTest` + 前端 `money.spec.js`
- 异常路径（拒绝/退回/撤回/重复/并发）→ 后端 `SalesOrderApprovalOutboundChainTest` 等 + 真实 Redis `*IT`
- 折扣/计价 D1~D10 → 前端 `money.apportion` 单测 + 后端计价引擎测试；审批回滚资源（代金券/库存锁）以后端 `*IT` + 测试环境 L4 双重验证
