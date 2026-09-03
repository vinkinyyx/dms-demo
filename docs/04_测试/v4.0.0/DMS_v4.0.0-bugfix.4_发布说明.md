# DMS v4.0.0-bugfix.4 发布说明（BOM 展开与计价修复 / 价格不可删除）

- 版本号：v4.0.0-bugfix.4
- 发布日期：2026-08-19
- 部署环境：测试环境 http://43.128.145.141/
- 演示账号：租户 `default` / 账号 `sys_admin` / 密码 `Dms@123456`
- 服务器：`ubuntu@43.128.145.141` / `Welcomeyyx0616`
- 上一版本：v4.0.0-bugfix.3

> 请测试同学执行 **Ctrl + Shift + R 强制刷新** 后再验证。

## 一、本版修复

### 1. BOM 销售订单只展开一个子件（严重）
- 现象：销售订单选择 BOM 母件后，即使 BOM 有多个子件（如 BDL-309-STD 含 331×1、332×2），订单只落 1 行。
- 根因：`V4PricingService.bomLines(...)` 查询 BOM 子件时末尾误带 `LIMIT 1`，且排序写在主表 `pb` 上。
- 修复：去除 `LIMIT 1`，按 `pbl.sort_order, pbl.id` 排序，返回全部子件。
- 影响文件：`backend/src/main/java/com/dms/v4/V4PricingService.java`。

### 2. BOM 价格被"打包价覆盖"，与"子件各自维护价格、母件不维护价格"冲突
- 现象：即使子件各自有经销商价格，订单里 BOM 行金额仍按历史 `product_bundles.bundle_price`（OVERRIDE 打包价）分摊，覆盖了子件真实价格。
- 修复：`V4Calculator` 不再取母件打包价做覆盖分摊；BOM 金额 = 各子件（数量×配比×各自经销商价）之和。子件可单独维护价格（含金额为 0 的子件）。
- 影响文件：`backend/src/main/java/com/dms/v4/V4Calculator.java`。

### 3. 产品价格仍可被 DELETE 删除
- 现象：v4 需求规定价格一经创建不可删除/编辑，只能生效/失效；但 `DELETE /api/product-prices/{id}` 仍做软删除。
- 修复：`DELETE` 直接返回业务错误"价格记录不允许删除，请使用失效操作"；`PUT` 仅允许更新 `status`；新增 `POST /{id}/activate`、`POST /{id}/deactivate`；列表过滤已删除数据并支持按 `partnerId` 过滤。
- 影响文件：`backend/src/main/java/com/dms/masterdata/controller/ProductPriceController.java`。

## 二、验证结论（测试环境真实数据）

- 销售订单创建含 BOM(309) ×2 + 单品(347)×10：BOM 展开为 2 个子件行（331×2、332×4），子件取各自经销商价（810、860），母件行不单独计价、不打折。
- 行折扣按"整行"而非"每个 EA"：单品 1460×10=14600，行金额折扣 −100 → 14500。
- 整单金额折扣按各行折后金额比例分摊到每个 EA，并写入 `order_lines.final_amount/header_discount_amount`，传出库单。
- 模拟出库：`POST /api/sales-orders/{id}/simulate-ship` 生成 GI 单，出库行 `unit_price` = 折后每 EA 单价（14500/10=1450）。
- 提交订单不校验授权（sys_admin 租户管理员自动审批通过）。
- 价格 `DELETE` 返回 400 业务错误，生效/失效接口 200。
- 销退：选择发货单只列有可退数量的单；退货带出原订单/出库单价；提交进入审批。
- 21 个业务菜单列表页 Playwright headless Chromium 实测：均有表格、无白屏、无控制台错误、无"批量删除"、每行有"查看"。

---

## 三、bugfix.4 续作补充修复（2026-08-19 晚）

复核测试时发现以下更深层缺陷，已一并修复并重新部署：

### 4. BOM 下单完全不展开（严重，回归核心）
- 现象：选择 BOM 母件下单报“产品 X 没有有效销售价格”，BOM 被当作单品计价。
- 根因：`V4PricingService.isBom()` 读取 `products.is_bundle` 列，但该列从未被维护（恒为 false），导致所有 BOM 判定失效。
- 修复：`isBom()` 改为以 `product_bundles` 是否存在 `version_status='active'` 记录为准；同步修复 `PromotionService.isBom()`。
- 影响文件：`backend/src/main/java/com/dms/v4/V4PricingService.java`、`backend/src/main/java/com/dms/promotion/service/PromotionService.java`。

### 5. BOM 行折扣未分摊到子件
- 现象：对母件行设置行折扣（整行减 100），子件行 `line_discount_amount` 为 0。
- 根因：BOM 子件构建时 `applyDiscount=false`，行折扣只在母件上计算而母件不落行。
- 修复：在 `V4Calculator` BOM 分支，按各子件标准金额比例把整行折扣分摊到子件的 `lineDiscountAmount`（母件不打折、不落行）。
- 影响文件：`backend/src/main/java/com/dms/v4/V4Calculator.java`。

### 6. 整单（表头）折扣被重复计算
- 现象：表头减 100 元，实际子件合计减免 200 元。
- 根因：`expand()` 内部已调用 `applyHeaderDiscount()`，`V4OrderService.calculate()` 又调用一次。
- 修复：移除 `V4OrderService` 中的重复调用。
- 影响文件：`backend/src/main/java/com/dms/v4/V4OrderService.java`。

### 7. BOM 母件仍可创建价格
- 现象：`POST /api/product-prices` 未校验 SKU 是否为 BOM 母件，可给母件建价。
- 修复：创建价格时校验该 SKU 存在 active BOM 则拒绝；产品列表支持 `excludeBundle=true` 过滤（价格页 SKU 选择器已带此参数，现真正生效）；`PUT` 价格只允许改 status，携带其它字段一律拒绝。
- 影响文件：`backend/src/main/java/com/dms/masterdata/controller/ProductPriceController.java`、`backend/src/main/java/com/dms/masterdata/service/ProductService.java`。

### 8. 编辑 BOM 草稿子件报 500（唯一约束冲突）
- 现象：修改草稿 BOM 子件保存报 `duplicate key value violates unique constraint "uk_pbl_bundle_child"`。
- 根因：`replaceLines()` 软删除旧行后未 flush，同一事务内插入同 child 行时部分唯一索引未生效。
- 修复：软删除后 `flush()`；新建版本保持原版本 active，草稿发布时再转历史（避免无 active BOM 影响下单）。
- 影响文件：`backend/src/main/java/com/dms/masterdata/service/ProductBundleService.java`。

### 验证结论（补充）
- API 全链路回归 3/3 通过：列表筛选、价格不可编辑删除/促销不可删除、BOM 组件价/新版本/订单折扣平摊/出库/销退。
- 关键数值：BOM（子件 100×1 + 50×3，母件×2）标准金额 500，行折扣 −100（按 40/60 分摊），表头折扣 −100（按折后金额分摊 40/60），最终 300。
- 销退带出原出库单价、整数数量、提交进审批；出库每 EA 单价正确。
- 24 个业务列表页 Playwright 实测均有表格、http 200、无控制台错误、无白屏；销售订单/销退/销售出库的编码链接与查看均在列表区域内打开。
