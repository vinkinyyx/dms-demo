# DMS v4.1.0 测试场景

> 版本：v4.1.0
> 日期：2026-08-22
> 对应设计：`docs/02_设计/v4.1.0/DMS_v4.1.0_技术设计.md`

---

## 1. 后端单元测试

### V4CalculatorTest

| 用例 | 场景 | 预期 |
|------|------|------|
| bomComponentUsesBomPriceAndChildDiscountHasHighestPriority | BOM 子件取 BOM_COMPONENT 价 1000，行折扣减 100，整单减 100 | 母件 final=0；子件 lineDiscount=100、headerDiscount=100、final=800；不回退单品价 |
| standaloneLinesAllocateAfterLineDiscountByAfterLineDiscountBase | 行1 标准1000 行折扣20%，行2 标准500，整单减270 | 行1 header=166.15 final=633.85；行2 header=103.85 final=396.15；合计 1030.00 |

---

## 2. 浏览器 E2E 场景

### T1. BOM 子件价与单品价双轨

1. 产品价格列表 → 新增 → 选择 BOM 母件 PRD-B001 + 经销商。
2. 系统自动带出子件 PRD-B002、PRD-B003。
3. 分别录入子件价格，保存。
4. 列表中看到 BOM_HEADER 记录（价格用途=BOM 母件价），默认不显示子件行。
5. 同一 SKU PRD-B003 再维护一条单品价（不选 BOM 母件），保存成功。
6. 数据库中 PRD-B003 在该经销商下有两条 active 记录（STANDALONE + BOM_COMPONENT）。
7. BOM_HEADER 点「失效」，其下所有 BOM_COMPONENT 状态变为 inactive。

### T2. 销售订单 BOM 计价

1. 新建销售订单，选经销商，添加 BOM 母件 PRD-B001。
2. 展开子件，子件价格取 BOM_COMPONENT 价；若未维护则单价为 0。
3. 母件行单价/金额显示为子件汇总，但最终金额为 0。
4. 母件行不可行折扣；子件行可设行折扣。
5. 设置子件行折扣后，再设整单折扣，点「刷新赠品及价格」。
6. 验证：先算行折扣，再按行后金额占比分摊整单折扣；出库单价 = final/qty。
7. 提交审批，审批后生成销售出库，母件不参与出库，子件全部出库，状态「已完成」。

### T3. 促销赠品手动刷新

1. 创建满赠规则：PRD-J002 买 2 送 PRD-J003 5 个，启用。
2. 新建订单，添加 PRD-J002 数量 6。
3. 数量变动时不自动产生赠品。
4. 点「刷新赠品及价格」，明细出现 1 行赠品 PRD-J003 数量 15（每满 2 送 5，循环 3 次）。
5. 赠品行产品/数量不可编辑、不可删除、金额 0。
6. 页面顶部显示命中文案。
7. 再点刷新，赠品不重复累加（幂等）。
8. 保存草稿后重新打开，赠品仍在；提交后赠品落库。

### T4. 促销满减

1. 创建满减规则：满 1000 减 100，启用。
2. 订单明细标准金额合计 ≥ 1000。
3. 刷新后总价扣减 100，按行后金额占比分摊到各非赠品/非母件行。
4. 命中文案显示「整单减免 ¥100.00」。

### T5. 促销规则保存与显示

1. 新建满减规则，选择目标 SKU、门槛数量、减免金额，保存成功。
2. 只读页命中 SKU 显示「编码 名称」而非数字 ID。
3. 满赠规则的赠品 SKU、赠送周期（仅赠一次/每满 N 循环）显示中文。
4. 列表页点「启用」，状态变为 active；点「停用」变为 inactive。

### T6. 价格列表过滤与只读

1. 价格列表搜索区有「经销商」「价格类型」「价格用途」过滤。
2. 按经销商过滤正确。
3. 只读页 SKU 显示编码+名称，经销商显示名称，无裸 ID。
4. BOM 母件价只读页展示子件价表格。

### T7. 销售订单布局

1. 新建销售订单，经销商选择框宽度足够完整显示长经销商名称（不截断）。
2. 经销商、订单类型、期望日期在同一行。
3. 缺价时错误提示显示「产品 [PRD-XXX 名称] 没有维护有效销售价格」，不显示 ID。

---

## 3. 数据迁移验证

1. Flyway V108 在测试环境执行成功。
2. 历史 BOM 子件价格被复制为 BOM_COMPONENT（原 STANDALONE 保留）。
3. BOM_HEADER 0 元记录被补建。
4. 唯一索引 uk_product_prices_active_context 创建成功，无 active 重复。
5. 历史 BOM 订单重算价格正常（子件能查到 BOM_COMPONENT 价）。

---

## 4. 回归范围

- 产品价格 CRUD、导入导出
- 销售订单 CRUD、提交、审批、出库、销退
- 促销规则 CRUD、启用/停用、预览试算
- BOM 主数据维护
- 所有只读页/编辑页无裸 ID
- Console 无红色错误，网络无 500/非预期 4xx

---

## v4.1.1 销退金额一致性与赠品过滤（2026-08-22）

### 缺陷复现
- 销退单 `RS-20260822-00002`：表头退货金额 ¥253.93，明细页汇总仅 ¥61.08，对不上。
- 根因：只读页 `lineTotal()` 用 `min(本次退货数, 当前可退数)` 计算行总价；单据审批后 `returned_qty` 已累计，可退数归零，导致已保存的退货行被错误显示为 ¥0；`el-input-number` 的 `:max` 也把已保存的退货数显示成 0。

### 修复点
1. 只读态行总价直接取持久化的 `finalAmount`（兜底 `subtotal`/`qty*unitPrice`），明细汇总恒等于表头 `final_amount`。
2. 只读态 `el-input-number` 的 `max` 不再用剩余可退数裁剪已保存数量。
3. 销售出库草稿生成（`AutoDocGenerator`）跳过 `is_gift=true` 赠品和 `line_level='PARENT'` BOM 母件，并写 `source_order_line_id`。
4. 销退「选择发货单」`/shipped-outs/{id}/lines` 过滤赠品/母件行。
5. 销退可退明细与详情新增「发货行」`lineNo`、「订单行」`orderLineNo`。

### 验证用例（E2E `13-rma-amount-lineno.spec.js`）
- `rma-lines-exclude-gift`：GI-20260822-00002 的赠品 PRD-J003（sourceOutLineId=190）不出现在可退明细。
- `rma-lines-carry-lineno`：所有可退明细带 `lineNo`。
- `rma-header-equals-line-sum`：新建销退单 `SUM(finalAmount) == header.finalAmount`（验证 48.22=48.22）。
- `rma-detail-has-lineno`：详情行带 `lineNo`/`orderLineNo`。

### 数据修复说明
- 已存在的销退单数据本身正确（header=253.93，三行 finalAmount=61.07+192.86+0），仅前端显示错误，无需数据迁移。
- 历史出库单中已生成的赠品行保留（金额为 0，不影响金额），新建/重生成出库单不再包含赠品/母件行。


---

## 附录：v4.1.2 测试场景（2026-08-22）

### E2E：14-promo-target-cycle.spec.js
| 用例 | 预期 |
|------|------|
| 满A减钱选SKU保存/回读 | 保存成功，ruleDetail 含 targetProductId/reduceAmount，无 giftProductId |
| 满A减钱选产品层次保存 | 保存成功，targetProductLineId 正确 |
| 满赠 LINE + EVERY_N 保存/回读 | cycle=EVERY_N、everyN、giftProductId、targetProductLineId 正确持久化 |
| 负向校验 | 缺赠品 / 缺everyN / 满减缺金额 均返回 4xx |
| preview 满减 SKU | product1(2800)×2 减免500 → final=5100，消息含「减免」 |
| preview 满赠 EVERY_N | 创伤线 product1×8 → 赠 product3×3，赠品行 final=0 |
| preview 产品线隔离 | 脊柱 product7×8 不触发创伤线赠品 |
| preview ONCE+EVERY_N 叠加 | product1×8 → 共4件赠品 |
| preview 低于门槛 | qty=1 不赠 |
| 浏览器实点 | 促销列表渲染、新建弹窗打开、Console 无红错 |

### 单元：V4CalculatorPromotionTest（7 个全绿）
新增「门槛A vs 每满N步长」用例锁定 `1+floor((hit-A)/everyN)` 语义。

### 修复验证
- 计价：preview 普通单品正常返回价格（不再报未维护价格）。
- 环境：V112 迁移成功；测试环境 admin 登录页资源以 /admin/ 加载且为 application/javascript；冒烟 272/272。

---

## 附录：v4.1.3 测试场景（2026-08-22）

| 编号 | 场景 | 预期 |
|------|------|------|
| T-v4.1.3-1 | 销售订单提交自动审批 → 模拟出库 | 出库单详情存在 CONFIRMED 发货子单，batch_lines 行号从1递增、含批号/序列号/单价，BOM 母件不在其中 |
| T-v4.1.3-2 | 含促销赠品的订单模拟出库 | 赠品行（is_gift）不生成出库行/批次行 |
| T-v4.1.3-3 | 打开已存在销退单 → 点新建 | 原发货单信息、经销商、明细全部清空 |
| T-v4.1.3-4 | 在销退编辑页 → 跳促销规则详情 | Network 无对旧销退单的后续请求，Console 无红错，不弹「销退订单不存在」 |
| T-v4.1.3-5 | 满A减钱=仅一次，命中数≥A | 整单只减一次固定金额 |
| T-v4.1.3-6 | 满A减钱=每满N循环，qty=6, A=2, N=2 | 减免 3 次（1+floor((6-2)/2)），提示文案含「每满2循环」 |
| T-v4.1.3-7 | 满减规则选每满N循环但不填N | 保存被拒绝并提示填写每满N数量 |

自动化：新增 `automation_test/e2e/specs/15-outbound-batch-rma-cache.spec.js`；冒烟 272/272，E2E 13/14/15 全绿。
