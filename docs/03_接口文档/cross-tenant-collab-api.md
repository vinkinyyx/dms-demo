# 厂家↔经销商跨租户订单协同 接口说明

> 版本：v4.5.4（2026-08-31）｜ v4.5.0 起含正向（采购→销售、出库→收货），v4.5.4 新增反向退货（采退→销退、红字出库→红字入库）
> 适用：同一 SaaS 内厂家（MANUFACTURER 租户）与归属它的经销商（DEALER 租户）之间的采购↔销售↔出库↔收货自动协同。
> 说明：本能力**不新增对外 HTTP 接口**，而是在同库内由既有业务接口触发、跨租户自动转换对方单据。本文档说明触发接口、内部转换行为、数据契约与错误处理。
> 后端实现：`com.dms.collab.CrossTenantCollabService`；数据表：`cross_tenant_doc_links`、`product_mappings`、`tenant_dealer_bindings`、`suppliers.manufacturer_tenant_id`。

---

## 1. 触发点总览

| 触发接口（业务前台 JWT） | 触发时机 | 内部动作 | 事务/失败 |
|----|----|----|----|
| `POST /api/purchase-orders/{id}/submit` | 经销商采购单**提交**后 | 若供应商＝平台厂家，在厂家租户生成草稿销售单并回写厂家销售单号（路径 A） | 同事务；对码缺失/异常整笔回滚，采购单退回 DRAFT |
| `POST /api/sales-outs/{id}/partial-ship` | 厂家销售出库**发货**后（状态回写后） | 向经销商租户回传待收货入库单；无来源采购单时先自动补建已审批采购单（路径 B） | 同发货事务；对码缺失/异常整笔回滚（库存不扣、出库单留 DRAFT） |
| `GET /api/tenant/features` | 前端登录后加载菜单 | 返回 `{inventoryEnabled, purchaseEnabled}`，厂家关闭进销存时隐藏采购/库存菜单 | 只读 |

> 跨租户写入机制：厂家与经销商同库，转换时按**目标租户 tenant_id** 显式原生 SQL 写对方业务表；期间临时切换 `TenantContext`（供单号生成器按目标租户取号），结束还原。

---

## 2. 路径 A：经销商采购单 → 厂家草稿销售单

触发：`POST /api/purchase-orders/{id}/submit`（采购单供应商的 `suppliers.manufacturer_tenant_id` 非空＝平台厂家）。

内部生成（厂家租户）：
- `orders`：`order_type=NORMAL`、`dealer_id`=该经销商绑定的厂家侧 dealer 主数据、`status=DRAFT`、金额 0、`customer_po_code`=经销商采购单号、`remark` 注明来源。
- `order_lines`：产品按产品对码转为**厂家产品**，`qty`=采购量（1:1），`unit_price=0`、`tax_rate=0.13`（价格由厂家补）。

回写（经销商租户）：
- `purchase_orders.vendor_order_code` = 厂家销售订单号；采购单列表/详情返回字段 `vendorOrderCode`（前端"厂家销售单号"列）。
- `cross_tenant_doc_links` 写一行 `link_type=PO_TO_SALES_ORDER`，`line_refs` 记录行级溯源（poLineId/poSeq、双方产品编码、qty）。

前置条件：
- 采购单供应商必须是平台厂家（`suppliers.manufacturer_tenant_id` 指向厂家租户）；普通供应商采购不触发。
- 该经销商租户已通过 `tenant_dealer_bindings` 绑定厂家侧 dealer 主数据。
- 采购明细每个经销商产品在 `product_mappings` 有 active 对码。

## 3. 出库回传：厂家销售出库 → 经销商待收货（路径 A/B 共用）

触发：`POST /api/sales-outs/{id}/partial-ship`，body `lines[]`（本次发货行）。

入参参与转换的字段（每行）：`expectedLineId`、`productId`（厂家产品）、`warehouseId`、`qty`、`batchNo`、`serialNo?`。

内部生成（经销商租户）：
- **路径判定**：按出库单来源销售订单 `sales_order_id` 查台账 `PO_TO_SALES_ORDER`；命中则复用其采购单（路径 A）；未命中则**路径 B** 先建采购单。
- 路径 B 采购单 `purchase_orders`：`status=APPROVED`（不再审批）、`supplier_id`=平台厂家供应商、`vendor_order_code`=厂家销售单号；明细产品按对码转为**经销商产品**、单价 0。
- 收货单 `receipts`：`receipt_type=PURCHASE`、`ref_doc_type='sales_out'`、`ref_doc_id`=厂家出库单 id、`source_po_id`=采购单 id、`status=PENDING`。
- 收货行 `receipt_lines`：产品＝**经销商产品**（对码转换）、`batch_no`/`serial_no` 透传、`expected_qty`=本次发货量、`received_qty=0`、**不写单价**。
- `cross_tenant_doc_links` 写一行 `link_type=SALES_OUT_TO_RECEIPT`。

经销商后续：在自己租户对该 PENDING 收货单做**收货确认**（复用既有收货接口/流程），确认后入经销商库存。


---

## 3B. 反向退货协同（v4.5.4）：采退 ↔ 销退、红字出库 → 红字入库

退货链路与正向**逐表对称**：经销商把货退给厂家，账面用红字单据冲销原正向单据。

| 链路 | 触发方（经销商租户） | 自动生成（厂家租户） | link_type |
|----|----|----|----|
| 路径 C：采退单 → 红字销退草稿 | 采退订单提交 | 红字销售订单（`orders.is_red=true`, DRAFT） | `PR_TO_RED_SALES_ORDER` |
| 路径 D：红字出库发货 → 红字待收货 | 红字销售出库（采退 RGI）批次发货确认 | 红字销退入库单（`receipts.is_red=true`, PENDING） | `RED_OUT_TO_RED_RECEIPT` |

### 路径 C：经销商采退单 → 厂家红字销退订单草稿

触发：`POST /api/purchase-returns/{id}/submit`（采退单＝`purchase_orders.is_red=true`，且供应商的 `suppliers.manufacturer_tenant_id` 非空＝平台厂家）。

内部生成（厂家租户）：
- `orders`：`order_type=NORMAL`、`is_red=true`、`dealer_id`=该经销商绑定的厂家侧 dealer 主数据、`status=DRAFT`、金额 0、`customer_po_code`=经销商采退单号、`remark` 注明来源与退货原因。
- `order_lines`：产品按对码转为**厂家产品**，`qty`=采退量（1:1），`unit_price=0`、`tax_rate=0.13`。

回写（经销商租户）：
- `purchase_orders.vendor_order_code` = 厂家红字销退订单号。
- `cross_tenant_doc_links` 写一行 `link_type=PR_TO_RED_SALES_ORDER`，`line_refs` 记录行级溯源（prLineId/prSeq、双方产品编码、qty）。

厂家后续：该红字销退订单为 **DRAFT，需厂家人工提交审批**；审批通过后由既有 `AutoDocGenerator.createSalesOutForOrder` 自动生成红字销售出库单（GIR，`sales_outs.is_red=true`）——与正向出库单同一套机制，仅红字方向相反。

### 路径 D：经销商红字出库发货 → 厂家红字销退入库（待收货）

发货批次接口序列：`POST /api/sales-outs/{id}/batches` 创建批次（响应 data 含 `id`、`salesOutId`、`code`、`seq`、`status`）→ `PUT /api/sales-out-batches/{batchId}` 保存明细 → `POST /api/sales-out-batches/{batchId}/confirm` 确认发货。

触发：`POST /api/sales-outs/batches/{batchId}/confirm`，当发货单为红字（`sales_outs.is_red=true`，即采退自动生成的 RGI）且来源于一张已协同（路径 C）的采退单时。

入参参与转换的字段（每发货行）：`productId`（经销商产品）、`qty`、`batchNo`、`serialNo?`；幂等键取本次发货执行行 id（`sales_out_batch_lines.id`）。

内部生成（厂家租户）：
- 仅当该红字出库 `source_po_id` 能在台账查到 `PR_TO_RED_SALES_ORDER` 时触发；否则静默跳过（非协同采退）。
- 入库单 `receipts`：`is_red=true`、`receipt_type='SALES_RETURN'`、`ref_doc_type='sales_return'`、`ref_doc_id`=厂家红字销退订单 id、`dealer_id`=厂家侧 dealer 主数据、`warehouse_id`=厂家默认仓（缺失自动补建 `COLLAB-DEFAULT-WH`）、`status=PENDING`、`auto_created=true`。
- 入库行 `receipt_lines`：产品＝**厂家产品**（对码转换）、`batch_no`/`serial_no` 透传、`expected_qty`=本次发货量、`received_qty=0`、不写单价。
- `cross_tenant_doc_links` 写一行 `link_type=RED_OUT_TO_RED_RECEIPT`。

厂家后续：对该 PENDING 红字入库单做**收货确认**（复用既有收货批次接口）。红字销退入库语义为「收到客户退回、重新质检」，库存按厂家既有红字销退入库流程处理（`+PENDING 待检`）。

> 库存方向说明：退货的库存冲销由**各租户既有红字流程**负责（经销商红字出库 `deductBatch(isRed)`、厂家红字入库确认），跨租户层只负责自动建对对方的红字单据，不重复记库存。
---

## 4. 产品对码契约

- 对码表 `product_mappings`：`manufacturer_tenant_id`、`dealer_tenant_id`、`manufacturer_product_id/code`、`dealer_product_id/code`、`conversion_rate`（本期固定 1:1）、`status`（active 才参与转换）。
- 由**厂家在业务前台**维护（产品对码菜单）。
- 转换方向：
  - 采购转销售（路径A）：`dealer_product_id → manufacturer_product_id`。
  - 出库转收货（路径B）：`manufacturer_product_id → dealer_product_id`。
  - 采退转销退（路径C）：`dealer_product_id → manufacturer_product_id`。
  - 红字出库转红字入库（路径D）：源单是经销商红字出库（产品为经销商产品），按 `dealer_product_id` 查对码映射，目标厂家入库行落 `manufacturer_product_id`（产品转为厂家产品）。
- **对码缺失即阻断**：整笔转换失败，错误信息列出缺失的产品编码+名称，例如：
  - 采购转单：`以下物料厂家尚未对码，无法转销售订单，请厂家先完成对码：[A1-P999 ...]`
  - 出库回传：`以下物料厂家尚未对码，出库回传失败，请厂家先完成对码后重新发货：[COLLAB-MFR-NOMAP]`
  - 采退转销退：`以下物料厂家尚未对码，采退无法转销退单，请厂家先完成对码：[A1-P999 ...]`
  - 红字出库回传：`以下物料厂家尚未对码，红字出库回传失败，请厂家先完成对码后重新发货：[A1-P999 ...]`
  - 未绑定厂家客户主数据：`经销商租户尚未绑定厂家客户主数据，采退无法转厂家销退单，请联系厂家完成绑定`

## 5. 幂等

- **路径A/路径C（单据转换）**：服务内先按 `po_id + link_type` 查台账，已存在则直接返回已生成的厂家单号，不重复建单。
  - `PO_TO_SALES_ORDER`：一张采购单只转一张厂家销售单。
  - `PR_TO_RED_SALES_ORDER`：一张采退单只转一张厂家红字销退单。
- **路径B/路径D（发货回传）**：幂等粒度为`出库单 + 本次发货执行行（line_refs.outLineId）`；同一张出库单分批发货，每批回传一张收货单；已回传过的执行行重试时跳过（v4.5.1 V142 起 sales_out_id 不再唯一，由应用层按执行行去重）。
- 服务内先查台账再写入，双保险。

## 6. 价格与边界

- 价格/折扣**不跨租户**：采购转销售不带价（厂家销售单金额 0 待补折扣审批）；出库回传不带价（收货行无单价，经销商成本自维护）。
- 回传只带：产品（转码后）、数量（1:1）、批次号、序列号。
- v4.5.4 起**红冲（采退↔销退）跨租户联动已实现**（路径 C/D）；仍不做：单据**取消/驳回/撤回**的跨租户联动、库存跨租户共享；经销商不可直接编辑厂家租户单据。
- 反向链路价格同样不跨租户：厂家红字销退单金额 0，由厂家补折扣/审批；红字入库行无单价。

## 7. 租户模块开关接口

`GET /api/tenant/features`（业务前台 JWT）

响应 `data`：
```json
{ "inventoryEnabled": false, "purchaseEnabled": false }
```
- 仅对 `userType=vendor`（厂家用户）生效菜单过滤；dealer 用户后端 `TenantFeatureGuard.isDealerUser()` 放行，采购/收货功能不受厂家开关影响。
- v4.5.0 起所有 MANUFACTURER 租户默认 `false`（Flyway V140 固化）。
