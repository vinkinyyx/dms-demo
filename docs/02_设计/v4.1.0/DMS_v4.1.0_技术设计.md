# DMS v4.1.0 技术设计

> 版本：v4.1.0
> 日期：2026-08-22
> 对应需求：`docs/01_需求/v4.1.0/DMS_v4.1.0_需求规格.md`

---

## 1. 数据模型

### 1.1 product_prices 新增字段（Flyway V108）

| 字段 | 类型 | 说明 |
|------|------|------|
| price_context | VARCHAR(16) NOT NULL DEFAULT 'STANDALONE' | STANDALONE=单品价, BOM_HEADER=BOM 母件价（0 元头）, BOM_COMPONENT=BOM 子件价 |
| bom_parent_product_id | BIGINT | 当 price_context=BOM_COMPONENT 时指向母件 product_id |

唯一索引 `uk_product_prices_active_context`：
```
(tenant_id, product_id, partner_type, COALESCE(partner_id,0),
 price_scope, price_context, COALESCE(bom_parent_product_id,0))
WHERE deleted_at IS NULL AND status='active'
```

### 1.2 历史数据迁移（V108）

1. 将旧数据中所有作为 BOM 子件出现的 SKU 的 active 销售价复制一条 BOM_COMPONENT 记录（保留原 STANDALONE 作为单品价）。
2. 为每个有 BOM_COMPONENT 的（母件, 经销商）补建 BOM_HEADER 0 元记录。
3. 旧索引 uk_product_prices_scope_partner 删除，新建上下文隔离唯一索引。

---

## 2. 后端设计

### 2.1 V4PricingService.PriceUse

- `STANDALONE`：单品下单，取 STANDALONE 价（经销商 → GLOBAL(0) → GLOBAL(null)），无价抛业务异常。
- `BOM_COMPONENT`：BOM 子件下单，只取 BOM_COMPONENT + bomParentProductId 精确匹配，查不到返回 0 元 Price，不回退单品价。

### 2.2 V4Calculator 计价流程

```
expand():
  for each request line:
    if isBom:
      add PARENT line (金额全部 0)
      for each component:
        buildLine(BOM_COMPONENT, bomParent=母件id)
        读取对应 childDiscounts 设置行折扣
    else:
      buildLine(STANDALONE)
      设置行折扣

  applyLineDiscounts(): 对所有 chargeable 行计算行折扣，得到 afterLineDiscount 金额
  chargeable = !gift && !PARENT && standardAmount > 0

  if applyPromotions:
    遍历 activePromotions
    GIFT/MOQ → applyGift 聚合赠品数量
    FULL_REDUCTION → applyReduction 计算满减金额（按行后金额）

  headerDiscount = V4Money.discount(lineAmountTotal, type, value)
  totalOrderReduction = min(promoReduction + headerDiscount, lineAmountTotal)
  allocateOrderReduction():
    按「行后金额 / 行后金额总额」比例分摊 totalOrderReduction
    2 位小数 HALF_UP，尾差吸收到金额最大行
    每行 finalAmount = standardAmount - lineDiscount - promoShare - headerShare

  result = paid lines + gift lines（赠品 0 元、不可编辑、不参与分摊）
```

关键修复：`buildLine` 正确设置 `lineLevel = bomParent == null ? "NORMAL" : "CHILD"`（之前漏设导致子件行在数据库中变成 NORMAL，提交重算时 childDiscounts 丢失）。

### 2.3 ProductPriceController

- 列表默认隐藏 BOM_COMPONENT（`includeComponents=false`），支持 priceContext / bomParentProductId 过滤。
- 保存 BOM 销售价：1 条 BOM_HEADER（0 元）+ N 条 BOM_COMPONENT。
- `toggleStatus`：BOM_HEADER 生效/失效时级联更新所有 bom_parent_product_id 匹配的 BOM_COMPONENT 状态。
- 详情对 BOM_HEADER 返回 componentPrices 子件价表格。
- count 查询使用独立 cntQ 并绑定参数。

### 2.4 PromotionService

- `replaceRules`：normalizeNumbers 只处理数量/金额字段；targetType=LINE 时 targetProductId 置 null、LINE id 保留；不再误报缺 SKU。
- `toBd` 用 `new BigDecimal(String.valueOf(value))` 修复 BigDecimal 被 instanceof Number 误判。
- activate/deactivate 后端正接口存在且写入 updatedAt/updatedBy。

---

## 3. 前端设计

### 3.1 OrderCreate.vue

- 经销商字段栅格 md=16，订单类型/期望日期各 md=4（同一行）。
- 子件价格查询带 `priceContext=BOM_COMPONENT&bomParentProductId=...`，不回退单品价。
- `canEditDiscount`：赠品和 BOM 母件不可行折扣；BOM 子件和普通行可。
- 「刷新赠品及价格」按钮调用 executePreview(true)，保存/提交前自动刷新。
- promoMessages 以 el-alert 展示命中文案。
- 缺价校验错误显示产品编码+名称。
- 赠品行 productCode/productName 显示但不可编辑/删除。

### 3.2 CrudView.vue / modules.js（产品价格）

- 价格列表新增「价格用途」列、经销商过滤、价格类型过滤。
- BOM 价格维护提交 componentPrices。
- 促销规则明细 targetType 切换时 SKU/产品层次列显隐正确。

### 3.3 ResourceDetail.vue

- `fieldDisplay`：productId/partnerId/bomParentProductId 显示编码+名称。
- `pickerDisplay`：通用 *Id 字段按 xxxCode+xxxName 拼装，不显示裸 ID。
- 价格详情 BOM_HEADER 展示 componentPrices 子件表格。

---

## 4. API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/product-prices?priceContext=&bomParentProductId=&includeComponents= | 支持价格用途过滤 |
| POST | /api/product-prices | BODY 含 componentPrices 时保存 BOM 母件+子件价 |
| POST | /api/product-prices/{id}/activate | 级联生效子件价 |
| POST | /api/product-prices/{id}/deactivate | 级联失效子件价 |
| POST | /api/sales-orders/preview | 支持 applyPromotions、childDiscounts |

---

## 5. 版本决策（写入项目记忆）

- 本次升级到 v4.1.0（MINOR），因为包含 BOM 价格双轨的数据结构变更。
- 今后每次小修复（bugfix）自动升 PATCH（如 4.1.0 → 4.1.1）。
- MINOR（4.1 → 4.2）和 MAJOR（4.x → 5.0）升级由用户决定，Codex 不自动升。

---

## v4.1.1 补充设计（2026-08-22）

- `AutoDocGenerator.createSalesOutForOrder`：从 `order_lines` 读取时增加 `line_level`、`is_gift`，跳过 `PARENT`/赠品；插入 `sales_out_lines` 时回填 `source_order_line_id`。
- `SalesReturnController.shippedOutLines`：LEFT JOIN `order_lines`（按 `source_order_line_id`），过滤 `is_gift=true` 或 `line_level='PARENT'` 的出库行；返回 `lineNo`（sol.seq）与 `orderLineNo`（ol.seq）。
- `SalesReturnController.get`：详情行返回 `source_line_no`（出库行 seq）与 `order_line_no`（订单行 seq）。
- 前端 `SalesReturnEdit.vue`：新增「发货行/订单行」列；只读态 `lineTotal()` 取持久化 `finalAmount`；只读态 `el-input-number` 的 `max` 不裁剪已保存 `qty`。
- 金额口径：`orders.final_amount = SUM(order_lines.final_amount)`，由后端 `calcTotal/insertLines` 保证；前端只读汇总与之对齐。


---

## 附录：v4.1.2 设计变更（2026-08-22）

- **CrudView 明细行校验**：必填校验以 `{...formData,...row}` 为上下文执行列 `showIf/showWhen`，隐藏列跳过。modules.js 给命中SKU/命中产品层次/每满N数量补 required。
- **V4Calculator.applyGift**：thresholdQty=起赠门槛A，everyN=循环步长；ONCE 命中即赠1，EVERY_N 为 `1+floor((hit-A)/everyN)`；满减不涉及赠品字段。
- **PromotionService.replaceRules**：新增 targetType(SKU/LINE)、cycle(ONCE/EVERY_N)、EVERY_N 时 everyN>0 的后端校验。
- **price_scope 归一化**：新增 Flyway V112，幂等把 product_prices.price_scope 旧值 SALES/GLOBAL/DEALER 归一化为 SALE；reset 种子 SQL 统一输出 SALE。
- **测试环境 admin 基路径**：admin 测试构建必须用 `VITE_BASE=/admin/`（生产用默认 /dms/admin/），deploy_test.py 增加资源前缀校验。

---

## 附录：v4.1.3 技术变更（2026-08-22）

- `V4ErpService.receiveOutbound`：为每个出库行写递增 `seq`，出库完成后创建一张 `CONFIRMED` 的 `sales_out_batches`（code=GI-xxx-1）并逐行写 `sales_out_batch_lines`（expected_line_id/seq、ship_line_no、product_id、warehouse_id、qty、batch_no、serial_no、unit_price）；行循环跳过 BOM 母件（line_level=PARENT）和促销赠品（is_gift）。`warehouse_id` 按 body → 订单仓库 → 默认仓库兜底。
- `layout/index.vue` + 路由 `meta.noCache`：所有单据编辑/新建页禁用 `<keep-alive>`，组件每次进入重新挂载，避免新建残留和被缓存实例的定时器/watch 发起跨页泄漏请求。
- `SalesReturnEdit.vue`：`onBeforeUnmount` 清理 `dealerTimer`。
- `PromotionService.replaceRules`：FULL_REDUCTION 也校验 `cycle`（ONCE/EVERY_N）及 EVERY_N 时 `everyN>0`。
- `V4Calculator.applyReduction`：ONCE 达门槛减一次；EVERY_N 次数 = `1 + floor((hit - A)/everyN)`，单次减免（固定金额或比例）× 次数后以命中行折后金额封顶。
- `LinesEditor.colTitle` 支持函数型列标题；`modules.js` 周期/每满N列对 GIFT 与 FULL_REDUCTION 都显示。
