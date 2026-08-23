# DMS v4.0.0-bugfix.6 技术设计

## 1. 后端实现
- `ProductPriceController` 新增 `GET /api/product-prices/{id}`，列表和详情统一返回采购/销售归一化字段：`priceType/priceScope/inclPrice/exclPrice/taxRate/currency/partnerId/partnerName`。
- 销售价创建支持 `componentPrices`：选择 BOM 母件时只为每个子件创建价格，不创建母件 0 元价格；采购价通过 active BOM 检测拒绝母件。
- 价格查找 `V4PricingService` 强制过滤 `price_scope='SALE'`，避免采购价污染销售下单。
- `V4Line` 新增 `lineLevel/groupHeader/bomParentLineId`，`V4OrderService.insertLines` 先插入母件行获得 ID，再回填子件 `bom_parent_line_id`，同一订单重复 BOM 用 `bom_group_no` 隔离。
- `V4Calculator` 将 BOM 展开为零价母件行和子件计费行；母件行不参与折扣，行折扣和整单折扣只分摊到子件。
- `ProductBundleService` 列表改为原生查询，支持多关键词和按母件 SKU 编码/名称排序；`activeByProduct` 回填子件 SKU 编码/名称。
- BOM 子件数量校验统一为 `>= 1`。
- 销售订单列表返回 `shippedQty`，用于前端隐藏已有出库记录的取消按钮；后端取消仍校验已出库数量。
- 通用 `SpecUtil` 将关键词拆分为多个 token，对 `code/name/nameCn/nameEn/spec` 逐 token OR 匹配。

## 2. 前端实现
- `CrudView.vue`：产品选择器支持选择事件；产品价格销售价选择 BOM 后展示“BOM子件销售价”表格，逐行维护含税价/税率并自动计算不含税价。
- `CrudView.vue`：当模块配置了 `editableWhen` 或 `editPath` 时，按状态注入编辑按钮；当配置 `deletableWhen` 时按状态注入删除按钮；订单取消按钮支持 `rowButtonPermissions`。
- `modules.js`：BOM draft 显示编辑按钮；产品价格类型改为 SALE/PURCHASE；销售订单取消按钮仅按后端发货数量判断可见性。
- `OrderCreate.vue`：保存只提交顶层行，后端负责 BOM 展开；选择 BOM 时读取 active BOM 并展开子件；修复行折扣百分比和整单折扣前端预览。
- 所有新建表单仍由通用 `openForm(null)` 和专用页面 `resetForm()` 清空历史数据。

## 3. 数据与兼容
- 本轮无需新增数据库字段；复用 V96-V104 已创建字段。
- 已存在的旧全局价/经销商价数据在销售价查询中必须具备 `price_scope='SALE'`；采购价不会再作为销售价命中。
- 旧订单缺少 `line_level/is_group_header/bom_parent_line_id` 时，详情仍可通过 `bomParentLineId` 和产品字段降级展示；新建/编辑后的订单写入完整层级字段。

## 4. 部署
- 后端：`mvn -o package -DskipTests`，部署容器 `dms-test-backend`。
- 前端：`npm run build`，部署容器 `dms-test-nginx`。
- 不部署生产环境；生产和测试 SSH 密码均以 `docs/DMS登录信息手册.md` 为准。
