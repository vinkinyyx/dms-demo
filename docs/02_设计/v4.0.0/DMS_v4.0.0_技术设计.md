# DMS v4.0.0 技术设计

## 1. 模块策略
- 复用 orders/order_lines 承载销售订单和销退订单，避免新增并行订单主表。
- 复用 sales_outs/sales_out_lines 承载销售出库子单和红字销售出库子单。
- 升级 product_bundles 为 BOM 主数据，增加版本号和版本状态；订单行保存 BOM 展开快照。
- 复用 product_lines 作为产品层次树，产品通过 product_line_id 关联。
- ERP 集成新增独立服务和回调表，订单头保存最近一次集成状态。

## 2. 数据库变更
- tenants.modules_enabled 中使用 inventoryEnabled 控制库存和采购能力。
- product_prices 增加 sales_price_excl_tax、tax_rate、valid_from、valid_to；保留 sales_price 作为含税价快照。
- product_bundles 增加 bom_version、version_status；唯一约束调整为 tenant_id + product_id + code + bom_version。
- products 增加 product_line_id；保留 category_id 和 product_type。
- order_lines 增加标准价、行折扣、促销折扣、整单分摊折扣、税额、不含税金额、最终金额、BOM 快照字段。
- orders 增加 header_discount_type、header_discount_value、tax_amount、amount_excl_tax、erp_status、erp_error、line_close_reason 等字段。
- sales_out_lines 增加 source_order_line_id、bom_parent_product_id、bom_version、bom_group_no、component_qty、return_locked_qty、returned_qty。
- 新增 erp_outbound_callbacks 保存回调幂等键、ERP 单号、方向、处理状态和原始报文。

## 3. 价格与金额
- 取价优先级：经销商当前有效价 > 全局当前有效价。
- 价格保存不含税单价和税率，含税单价 = 不含税单价 × (1 + 税率)。
- BOM 存在销售价时，以 BOM 含税总价作为基准，按子件标准含税金额占比分摊；无 BOM 价时按子件价合计。
- 计算顺序：取价和 BOM 分摊 → 行折扣 → 促销折扣/赠品 → 整单折扣 → 税额拆分。
- 所有折扣按含税金额录入，固定金额直接扣减，比例使用 0~1。
- 整单折扣按有偿行剩余含税金额占比分摊，最大余额法处理尾差。
- 不含税金额 = 含税最终金额 / (1 + 税率)，税额 = 含税最终金额 - 不含税金额。
- 任一行或整单最终含税金额小于 0 时拒绝提交，并返回具体行号和原因。

## 4. 状态流
### 销售订单
- DRAFT → PENDING_APPROVAL → APPROVED → PARTIAL_OUTBOUND → COMPLETED。
- PENDING_APPROVAL 可被驳回为 REJECTED 或撤回为 DRAFT。
- APPROVED 在无销售出库结果时可 CANCELLED，并触发 ERP 取消推送。
- 有部分出库后不能整单取消，但可关闭未发子件行，全部发完或关闭后完成。

### 销退订单
- DRAFT → PENDING_APPROVAL → APPROVED → PARTIAL_RED_OUTBOUND → COMPLETED。
- 提交时锁定可退数量；驳回、取消释放锁定。
- 红字销售出库回调后累加已退数量，全部退完则完成。

## 5. ERP 接口
- POST /api/erp/sales-orders/{id}/push：审批通过后内部调用，当前为空实现并记录待推送/成功。
- POST /api/erp/sales-returns/{id}/push：销退审批通过后内部调用。
- POST /api/erp/outbound-callbacks：接收出库结果。
  - direction：FORWARD/RED。
  - sourceOrderId/sourceOrderCode：来源订单。
  - erpOutboundNo：ERP 出库单号。
  - warehouseCode/warehouseName：出库仓库。
  - lines：orderLineId/productCode、qty、batchNo、serialNo、outboundDate。
  - idempotencyKey：幂等键。
- POST /api/sales-orders/{id}/simulate-ship：模拟正向发货，调用与 ERP 回调相同的应用服务。

## 6. 权限与菜单
- 后端新增能力检查：库存关闭时采购、库存、仓库、供应商接口返回业务错误。
- 前端登录后获取租户模块开关，菜单按开关过滤。
- 模拟发货使用 sales_order:simulate_ship 权限，默认授予管理员和演示角色。
- 销售订单列表不再渲染状态按钮，仅保留查看/编辑、删除和模拟发货。

## 7. BOM 与促销
- 销售订单保存时，若选择 BOM，按当前选择版本展开为子件行；BOM 母件仅作为展示和分组信息保存在子件行。
- 促销引擎以展开后的子件行作为计算输入。
- A 命中范围包括 SKU 本身和 product_line_id 及其所有子节点下的产品。
- 满赠生成 is_gift=true 的 SKU 行；满减固定金额按命中行金额占比分摊。
- BOM 子件可以独立分批发货；订单完成条件为所有子件已出库或已关闭。
