# DMS v4.0.0-bugfix.2 技术设计

## 1. 总体方案
- 复用 CrudView 配置化框架，为产品、BOM、价格、促销补齐独立只读页和操作日志；销售订单、销退沿用独立页面。
- 后端坚持“价格只增不改、状态可控”，价格记录写入后不可 UPDATE 业务字段，只能切换 status；删除接口对价格返回业务错误。
- BOM 版本化采用“当前版本复制 + 旧版本置 history”，新版本母件头字段锁定。
- 销售订单 BOM 明细改为母件行+子件行的树形结构，母件行金额由子件聚合，子件承接折扣与价格分摊。

## 2. 数据库变更（V99）
- products：废弃 current_price、tax_rate（列保留以兼容历史，API 与前端不再暴露）。
- product_bundles：bom_version 改为字符串自增（1,2,3…）或语义版本 v1/v2，由后端生成；新增 created_by、version_note（可选）。
- order_lines：补充 bom_parent_line_id（自引用，指向同单据母件行）、line_level（PARENT/CHILD）、is_group_header；unit_price 记录分摊后的每 EA 成交价。
- 产品价格保持只增：新增状态变更使用 UPDATE product_prices SET status=...，不提供删除。
- 修正测试数据：sales_out_lines/sales_outs/order_lines 中 qty 非整数的数据更新为整数（如 0.5→1）。
- 具体迁移脚本：backend/src/main/resources/db/migration/V99__bugfix2_masterdata_bom_price_order.sql。

## 3. 产品管理
- 后端 ProductService 的创建/更新 DTO 移除 currentPrice、taxRate 的写入；列表与详情 VO 移除这两个字段。
- 分类下拉：GET /api/lookups/categories 返回扁平/树形分类（含 id、name、parentId）。
- 状态下拉：固定 [{ACTIVE,生效},{INACTIVE,失效}]。
- 前端 modules.js products：移除 currentPrice/taxRate 列与表单项；filter 分类改为 select(远程)、状态改为 ACTIVE/INACTIVE；batchDelete=false。
- 只读页路由 /products/:id，复用 CrudView 详情抽屉数据但以独立页面渲染，加载操作日志 GET /api/operation-logs?businessType=product&businessId=:id。

## 4. BOM 版本化
- POST /api/product-bundles/{id}/new-version：
  1. 校验当前版本存在且为 active/current；
  2. 将同 productId 的当前版本置为 history（version_status='history'）；
  3. 复制 product_bundles 记录，bom_version 自增，version_status='active'，母件头字段（productId/code/name）沿用且不可通过编辑接口修改；
  4. 复制子件到 product_bundle_lines，子件可在新版本编辑。
- 版本号生成：取同 productId 最大版本号数字+1，无需用户输入；前端 bomVersion 字段只读。
- 删除接口返回业务错误（不支持删除）；前端不显示删除按钮。
- 编辑页保存时，若记录为当前版本，仅允许修改子件、有效期、说明；母件头字段只读。
- 只读页 /product-bundles/:id 展示母件信息、子件表、版本历史与操作日志。

## 5. 产品价格
- 价格记录创建后：PUT /api/product-prices/{id} 仅接受 status（生效/失效）；传入 productId、合作方、价格、税率、币种、有效期等业务字段直接返回业务错误；DELETE 返回业务错误“价格记录不可删除”。
- 列表与详情：productCode/productName、partnerName、含税价、不含税价(只读)、税率、币种、有效期、状态。
- 前端价格表单：
  - 含税销售价(salesPrice)、税率(taxRate)可编辑；不含税销售价(salesPriceExclTax)只读，自动 = salesPrice / (1+taxRate)，4 位小数。
  - 采购同理：purchasePrice / taxRate 可编辑，purchasePriceExclTax 只读。
  - 币种 select 使用字母代码（CNY/USD/EUR…），不显示中文。
  - 产品选择器只返回非 BOM 母件产品（is_bundle=false）；BOM 母件不允许建价。
- 价格按 partnerType(GLOBAL/DEALER) + partnerId 隔离；同 SKU+合作方+时间段不可重叠（后端校验）。
- 只读页 /product-prices/:id。

## 6. 促销规则
- 规则创建后不可删除，只允许启用/停用；DELETE 返回业务错误。
- 前端按 promoType 动态渲染规则明细：
  - GIFT（满A赠B）：targetType(SKU/LINE)、targetProductId/targetLineId、thresholdQty、giftProductId(必须 is_bundle=false)、giftQty、cycle(ONCE/EVERY_N)、everyN。
  - REDUCTION（满A减钱）：targetType、targetProductId/targetLineId、thresholdQty、reduceAmount。
- “满A”门槛统一为数量（thresholdQty）。
- 移除表单中的折扣方式(discountType)、折扣值(discountValue)字段提交与展示。
- 只读页 /promotions/:id 展示头信息、规则明细 JSON 结构化展示、操作日志。

## 7. 销售订单 BOM 树形与价格
- 明细数据结构：选择 BOM 母件时，先生成一条 PARENT 行（bom_parent_line_id 为空、line_level=PARENT，productId=母件、qty=用户输入），再为每个子件生成 CHILD 行（bom_parent_line_id=母件行临时 id、line_level=CHILD、qty=母件 qty×配比）。
- 前端表格以 tree-props 展示母子层级；母件行：数量可编辑，单价/折扣只读；子件行：数量只读（随母件数量联动），可录行折扣。
- 后端保存：母件行与子件行都落 order_lines，母件行金额为子件合计，仅用于树形展示；订单头金额、折扣分摊、促销、出库、销退均排除 PARENT 展示行，折扣与促销只作用于 CHILD/普通有偿 SKU 行。
- 行折扣：以整行金额(standardPriceInclTax × qty)为基准，AMOUNT 直接减整行，PERCENT 按比例；不再出现“每个 EA 都减一次”的问题（核对并修复前端 recalcRow）。
- 整单折扣：作用于所有有偿行（含子件与普通 SKU），按金额占比分摊，尾差记入最大行；分摊结果落 order_lines.header_discount_amount，并重算 unit_price = final_amount/qty（4 位小数），作为出库与销退单价来源。
- 促销命中：子件/普通有偿行返回 promoDiscountAmount，并在明细展示“已命中促销X”标签；赠品行 isGift=true、金额为 0，不参与整单金额、出库和销退。
- 销售订单编辑页加“提交”按钮（调用 /submit）；保存/提交移除授权校验（OrderService 中去掉 AuthorizationCheck）。
- 防重复提交：保存按钮 loading + 幂等令牌（前端生成 requestId，后端基于 idempotency 表或状态判断 60s 内重复请求直接返回上次结果）。
- 取消按钮 router.push('/orders')，修复 404。
- 列表日期筛选：后端 SalesOrderController.list 增加 createdFrom/createdTo 参数。
- 状态下拉加载订单状态字典；经销商下拉使用 /api/lookups/dealers。
- 模拟出库按钮：订单详情在 APPROVED 状态显示，按钮权限点 sales_order:simulate_ship（仅租户管理员），调用 POST /api/sales-orders/{id}/simulate-ship。

## 8. 销售出库与销退
- 出库行继承订单行 unit_price（分摊后单价）与 final_amount 比例；销退选择出库单时带出该单价作为退货单价。
- shipped-outs 接口默认过滤无可退数量（returnableQty<=0）的单据，只返回至少存在一行可退的发货单。
- 数量整数：前后端对发货/退货数量校验为整数（BigDecimal scale=0）；数据修复脚本修正小数历史数据。
- 销退提交成功后跳转 /sales-returns/{id} 或列表，修复 404。

## 9. 操作日志
- 统一入口 GET /api/operation-logs?businessType={type}&businessId={id}，返回时间线（操作人、时间、动作、变更前后）。
- 业务类型：product、product_bundle、product_price、promotion、sales_order、sales_return。
- 只读页底部展示操作日志组件 <OperationTimeline />。

## 10. 前端布局规范
- 表单使用 el-row + el-col :span="8"（三列），备注类长文本 :span="24" type=textarea :rows="3"。
- 输入框宽度 100%，下拉与输入同高；卡片间距统一 12px；标签宽度 110px。
- 列表筛选区使用 el-form inline 左对齐，工具栏右对齐，避免换行错位。

---

## 附录：v4.0.0-bugfix.3 业务菜单统一实现（2026-08-18）

### 通用组件 `frontend-vue/src/components/CrudView.vue`
- `canBatchDelete = computed(() => false)`：全局关闭批量删除。
- `maxFlatRowButtons = 2` + `overflowRowButtons`：行操作超过 2 个折叠到"更多"下拉。
- `operationWidth` 按可见按钮数动态取值（1=96 / 2=170 / >2=110）。
- 列宽：窄列（w≤90）保留固定 width，其余使用 min-width，表格随容器自适应。
- 只读分离：`openDetail` 修复，编码链接与"查看"一律打开只读抽屉/只读路由；`canEdit` 只读页恒为 false。
- 筛选下拉：
  - `DICT_FALLBACK` 提供各模块状态枚举兜底。
  - `ensureRemoteFilterOptions` 远程加载 categories/dealers/suppliers/regions/warehouses。
  - `selectFilterOptions` 支持 `filter.dictType`、`getDictOptions` 响应式数组及内联 options；`getDictOptions` 返回数组带 `__dictType` 标记以触发加载。
- 操作日志：详情抽屉统一标题"操作日志"，合并 `/api/operation-logs`（审计）与 `/api/operation-log/list/{bizType}/{id}`（业务）。
- 表单：`formColSpan=8`（每行 3 列），文本域/明细占整行；CSS 统一输入框 100% 宽、label 130px。

### 自定义页面
- 合同工作台/合同模板/审批流配置/报表订阅：行操作超过 2 个时折叠；合同编码链接跳转只读详情。
- `contract/ContractDetail.vue`：新增操作日志卡片。
- `config/modules.js`：库存状态筛选改为内联枚举；价格/BOM/促销行动作配置 noEdit/noDelete/生效失效/新版本。

---

## 附录：v4.0.0-bugfix.4 技术修正（2026-08-19）

### A. V4PricingService.bomLines 修复
- 原实现末尾 `LIMIT 1` 且 `ORDER BY pb.updated_at, pb.id`，导致只返回一个子件。
- 修正：去掉 `LIMIT 1`，改为 `ORDER BY pbl.sort_order, pbl.id`，返回该版本全部子件。

### B. V4Calculator BOM 计价修正
- 原实现取 `pricing.bomPrice(...)`（含历史 OVERRIDE `bundle_price`）并通过 `allocateBomPrice` 把打包价覆盖分摊到子件，与需求冲突。
- 修正：BOM 展开时各子件直接用自身 `salesPrice(dealerId)`，金额 = 单价 × 配比 × 母件数量；BOM 总额为子件之和。母件行不单独计价、不参与行折扣。

### C. ProductPriceController 删除禁用
- `DELETE /api/product-prices/{id}` 改为抛 `BUSINESS_RULE_VIOLATION`。
- `PUT` 仅在 body 含 `status` 时切换状态；新增 `POST /{id}/activate`、`POST /{id}/deactivate`。
- 列表过滤 `deleted_at IS NULL`，新增 `partnerId` 查询参数；返回 `purchase_price_excl_tax/sales_price_excl_tax/tax_rate/valid_from/valid_to`。

### D. 订单价格分摊
- 行折扣按整行金额计算（`V4Money.discount(std, type, value)`），金额型即整行减固定额。
- 整单折扣在折后（含行折扣、促销折扣）按各付费行 `finalAmount` 比例分摊，尾差落到金额最大行；写入 `order_lines.header_discount_amount/final_amount/amount_excl_tax/tax_amount`。
- 模拟出库 `V4ErpService.simulateShip` 把 `finalAmount/qty` 作为出库行 `unit_price` 传出（每 EA 成交价），供销退带回。

---

## 附录：v4.0.0-bugfix.4 续作技术变更（2026-08-19）

- `V4PricingService.isBom()`：改为查 `product_bundles` active 记录，不再读不可靠的 `products.is_bundle`。
- `PromotionService.isBom()`：同步改造。
- `V4Calculator`：BOM 分支用 `allocateLineDiscount()` 按子件标准金额比例分摊整行折扣；表头折扣仅在 `expand()` 内分摊一次。
- `V4OrderService.calculate()`：移除对 `applyHeaderDiscount()` 的重复调用。
- `ProductPriceController.create()`：拒绝为 active BOM 母件建价；`update(PUT)` 仅接受 `status` 字段。
- `ProductService.list()`：支持 `excludeBundle=true` 过滤掉存在 active BOM 的母件。
- `ProductBundleService.createNewVersion()`：原版本保持 active；`replaceLines()` 软删除后强制 flush，修复唯一约束冲突。
