# DMS 测试分层策略（v4.2.2 起生效）

> 目标：避免每次 PATCH 发布都跑 10 分钟全量回归；按"改动范围"精准测试。

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
