# DMS v4.0.0-bugfix.9 技术设计

**版本**: v4.0.0-bugfix.9
**日期**: 2026-08-21

## 1. 后端

### 1.1 试算入参增加 applyPromotions
`POST /api/sales-orders/preview` 请求体新增布尔字段 `applyPromotions`：
- `false`（价格预览）：展开 BOM、解析价格、应用行折扣与整单折扣，但跳过促销（不生成赠品、不应用满减），不返回命中提示。
- `true`（刷新赠品及价格 / 保存 / 提交）：完整执行促销、生成赠品、返回 `promotionMessages`。

`V4Calculator.expand(...)` 增加 `boolean applyPromotions` 参数，替换原未使用的 `refresh` 参数。

### 1.2 幂等
`expand` 遍历请求行时跳过 `isGift=true` 和 `lineLevel=CHILD`；进入促销阶段先 `removeIf(V4Line::isGift)`，再根据当前计费行重新计算赠品。无论前端是否回传赠品行，结果都以本次计费行为准，杜绝赠品累加。

### 1.3 统一折扣分摊
1. 展开 BOM 并解析价格，得到计费行（排除赠品与 BOM 母件）。
2. 行折扣：`V4Money.discount(std, type, value)`，BOM 行折扣作用于该组子件标准金额合计。
3. 若 `applyPromotions=true`：
   - 计算买赠，按 productId 聚合赠品数量；
   - 计算满减，得到促销减免总额 `promoReduction`（作用于命中行的标准金额合计，AMOUNT 取固定值封顶，RATE 按比例）。
4. 整单折扣：`V4Money.discount(计费行标准金额合计, type, value)`。
5. `totalDiscount = lineDiscountTotal + promoReduction + headerDiscount`。
6. 一次性按标准金额占比分摊到计费行：`share = totalDiscount × std / stdTotal`，2 位小数 HALF_UP，尾差加到标准金额最大的行；`finalAmount = max(0, std − share)`。
7. 为保持明细展示列含义，将每个计费行的分摊额按行折扣/促销/整单三类的原始占比（line:promo:header 在总折扣中的比例）拆分写入 `lineDiscountAmount/promoDiscountAmount/headerDiscountAmount`，三者之和等于分摊额；赠品三项均为 0。
8. 重新计算税额：`amountExclTax` 与 `taxAmount` 由 `finalAmount` 与税率拆分。

### 1.4 命中提示
`activePromotions` 查询补 `name, code` 字段；`applyPromotions` 执行时收集 `List<String> promotionMessages`，通过新增的 `V4CalcResult`（含 `lines` 与 `promotionMessages`）返回。`previewSalesOrder` 将其放入响应；创建/更新/提交始终以 `applyPromotions=true` 计算，保证落库赠品与金额一致。

## 2. 前端 `OrderCreate.vue`
1. 明细卡片头部新增“刷新赠品及价格”按钮（`Refresh` 图标），点击调用 `refreshPromotions()`，带 loading。
2. 拆分为两个方法：
   - `runPricePreview(applyPromotions=false)`：防抖触发，供数量/产品/折扣变化调用；仅更新计费行金额，保留已有赠品行（按返回结果替换 form.lines，再把现有赠品追加到末尾）。
   - `refreshPromotions(applyPromotions=true)`：按钮与保存/提交前调用；以服务端结果整体替换行（含新赠品），并更新 `form.promoMessages`。
3. `buildPreviewPayload` 增加 `applyPromotions`；价格预览不发送赠品行（`editableRoots` 已排除赠品）。
4. `el-alert` 展示 `promoMessages`（info，可关闭）。
5. 赠品/子件行：产品不可选、数量禁用、无删除按钮（已有 `canPickProduct/canEditQty/canDeleteRow` 控制，复核模板绑定）。
6. 保存/提交：先 `await refreshPromotions()` 再构建 payload。

## 3. 涉及文件
- `backend/src/main/java/com/dms/v4/V4Calculator.java`
- `backend/src/main/java/com/dms/v4/V4CalcResult.java`（新增）
- `backend/src/main/java/com/dms/v4/V4OrderService.java`
- `backend/src/main/java/com/dms/v4/V4PricingService.java`（promotions 查询补字段）
- `frontend-vue/src/views/OrderCreate.vue`
