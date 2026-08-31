# 厂家↔经销商跨租户订单协同 接口说明

> 版本：v4.5.0（2026-08-30）
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

## 4. 产品对码契约

- 对码表 `product_mappings`：`manufacturer_tenant_id`、`dealer_tenant_id`、`manufacturer_product_id/code`、`dealer_product_id/code`、`conversion_rate`（本期固定 1:1）、`status`（active 才参与转换）。
- 由**厂家在业务前台**维护（产品对码菜单）。
- 转换方向：
  - 采购转销售：`dealer_product_id → manufacturer_product_id`。
  - 出库转收货：`manufacturer_product_id → dealer_product_id`。
- **对码缺失即阻断**：整笔转换失败，错误信息列出缺失的产品编码+名称，例如：
  - 采购转单：`以下物料厂家尚未对码，无法转销售订单，请厂家先完成对码：[A1-P999 ...]`
  - 出库回传：`以下物料厂家尚未对码，出库回传失败，请厂家先完成对码后重新发货：[COLLAB-MFR-NOMAP]`

## 5. 幂等

- `cross_tenant_doc_links.sales_out_id` 唯一：同一张厂家出库单只回传一次，重复发货/重试不重复建收货单。
- `cross_tenant_doc_links.po_id`（link_type=PO_TO_SALES_ORDER）唯一：同一张采购单只转一张厂家销售单。
- 服务内先查台账再写入，双保险。

## 6. 价格与边界

- 价格/折扣**不跨租户**：采购转销售不带价（厂家销售单金额 0 待补折扣审批）；出库回传不带价（收货行无单价，经销商成本自维护）。
- 回传只带：产品（转码后）、数量（1:1）、批次号、序列号。
- 本期不做：单据**取消**跨租户联动、**红冲**（销退/采退）跨租户联动、库存跨租户共享；经销商不可直接编辑厂家租户销售单。

## 7. 租户模块开关接口

`GET /api/tenant/features`（业务前台 JWT）

响应 `data`：
```json
{ "inventoryEnabled": false, "purchaseEnabled": false }
```
- 仅对 `userType=vendor`（厂家用户）生效菜单过滤；dealer 用户后端 `TenantFeatureGuard.isDealerUser()` 放行，采购/收货功能不受厂家开关影响。
- v4.5.0 起所有 MANUFACTURER 租户默认 `false`（Flyway V140 固化）。
