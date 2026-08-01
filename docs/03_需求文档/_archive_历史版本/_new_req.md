# DMS 需求文档 (汇总版)

**当前版本**: v3.7.6
**最后更新**: 2026-07-26
**文档性质**: 最终汇总需求 (Final Aggregate Requirements)
**关联小版本独立文档**: 本目录下 `v3.7.*_*.md` 为每次调整的小版本独立记录，本文档为**汇总**当前所有生效需求

---

## 1. 系统定位

**DMS (Dealer Management System)**: 经销商管理系统，多租户 SaaS。覆盖经销商 / 医院 / 区域 / 产品 / 价格 / 合同 / 授权 / 促销 / 订单 / 采购 / 库存 / 发货 / 收货 / 调整 / 盘点 / 报表 / 手术登记 / RBAC / 工作流 / 字典 / 设置 等 21 个业务域。

## 2. 技术架构

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Element Plus + Vite (PC 端 + 移动端 M* 视图) |
| 后端 | Spring Boot 3 + JPA + Native SQL + AOP (OperationLog) |
| DB | PostgreSQL 14 (多租户 tenant_id, Flyway 迁移) |
| 缓存 | Redis |
| 对象存储 | MinIO |
| 部署 | Docker + docker-compose; Fast 桥 = jar-only Dockerfile.runtime + layer cache |
| 测试环境 | http://8.133.193.238:8083 / backend 8082 / dms_test@5433 |
| 生产环境 | http://8.133.193.238:8081 / backend 8080 / dms@5432 |

## 3. 业务模块全景 (21 个域)

### 3.1 主数据 (Masterdata)
- **产品** products: SKU / 序列号管理 / UDI / 保质期 / 包装层级 / 价格历史
- **产品分类** product_categories (3 级层级) / **产品线** product_lines (3 级: BU/产品线/系列)
- **经销商** dealers (含地址 dealer_addresses, 状态机 active/blocked)
- **医院** hospitals (等级/科室/区域)
- **仓库** warehouses (类型: 总仓/医院仓/中转仓; 状态机)
- **区域** regions (树形)
- **供应商** suppliers (等级字典 supplier_level, GSP 资质)
- **价格** product_prices / price_lists (经销商特价 / 有效期)

### 3.2 合同 / 授权 / 促销
- **合同申请** contract_applications → **合同** contracts (附件 / 签名 / diff / 模板)
- **授权** authorizations (类型/范围/有效期) / 临时授权 temp_authorizations
- **促销** promotions + 规则 promotion_rules + 订单命中 order_promotion_hits

### 3.3 销售订单
- **订单** orders + order_lines (多状态: DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED/PARTIAL_SHIPPED/SHIPPED/COMPLETED)
- **销退订单** orders.is_red=true (复用 orders 表)
- **销售出库** sales_outs + sales_out_lines (子任务执行 sales_out_execution_lines, 部分发货)

### 3.4 采购订单
- **采购订单** purchase_orders + purchase_order_lines
- 状态机: DRAFT → SUBMITTED → APPROVED → RECEIVING → COMPLETED (↘ REJECTED / CANCELLED)
- 采退订单 is_red=true
- **v3.7.5+ 关键行为**:
  - DRAFT: 可编辑 / 提交审批 / 取消
  - SUBMITTED: 仅 审批通过 / 驳回 (不可编辑/取消/删除)
  - APPROVED: 可取消 (仅当未收货) / 可收货入库; 不可删除
  - 提交/审批 记 @OperationLog (businessType=purchaseOrder)
  - 取消时 级联关闭关联 DRAFT/RECEIVING 收货单 + 子单

### 3.5 库存
- **库存** inventory (product + warehouse + batch_no + serial_no + stock_status + qty + exp_date)
- **库存状态** stock_status: U合格 / Q待检 / B不合格 (字典 stock_status)
- **批次管理**: 相同 (product+warehouse+batch+status) 合并
- **序列号管理**: 一对一, 不合并
- **库存事务** inventory_transactions (分区表, 按月)
- **库存调整** inventory_adjustments (盘点/报损/数据修正/其它)
- **库存盘点** stocktakes + stocktake_lines
- **库存移动** stock_moves (from → to 仓库)

### 3.6 收货入库 (核心 v3.7.4-3.7.6)
- **父单** receipts (code=GR-YYYYMMDD-N, 关联 source_po_id)
- **子单** receipt_batches (code=GR-*-M) 实现多次收货
- **子单行** receipt_batch_lines (含 po_line_id, po_line_seq, receipt_line_no)
- 工作流:
  1. PO APPROVED → 自动创建父单 DRAFT
  2. 打开父单 → 创建子单 DRAFT
  3. 子单添加明细 (选 PO 行, 填 qty / batch_no / serial_nos)
  4. 确认收货 → 写库存 PENDING, 更新 PO lines.received_qty, 子单 CONFIRMED + confirmed_at
  5. 取消本次 / 取消剩余
  6. 父单 COMPLETED/CANCELLED → PO 同步 COMPLETED
- 校验: 总数量 ≤ PO 数量 (友好中文提示), 序列号必填 + 个数一致

### 3.7 销售出库 (销售)
- **销售出库** sales_outs (code=GI-YYYYMMDD-N, v3.7.6 起)
- 从订单 approve 自动生成 DRAFT
- 支持 部分发货 / 取消发货 / 完成
- 序列号拣货

### 3.8 借货 / 销售出库事实
- **借货** loans + loan_lines
- **销售出库事实** sales_out_facts (BI 数据来源)

### 3.9 手术登记
- **手术** surgery_reports + surgery_report_lines (医院 / 医生 / 经销商 / 产品使用)

### 3.10 RMA
- **RMA 授权** rma_authorizations / **RMA 订单** rma_orders

### 3.11 分销
- **分销发货** distribution_shipments + distribution_lines

### 3.12 报表 (4 类只读)
- 销售业绩排行 / 产品销售 TOP10 / 库存周转 / 订单追溯 / 应收款 / 手术统计

### 3.13 系统 / RBAC
- 用户 users (锁定/激活/微信绑定/must_change_password)
- 角色 roles + 数据权限 data_scopes + 策略 strategies
- 岗位 positions + position_users + position_dealers (销售岗位树)
- 菜单 menu_configs / 租户模块 tenant_modules
- 工作流 workflows + workflow_nodes
- 字典 dict_types + dict_items
- 设置 system_settings
- 通知 notifications
- 登录日志 user_login_logs
- 操作日志 operation_log (@OperationLog AOP 记录 CREATE/UPDATE/APPROVE/REJECT/DELETE)
- 审计 audit_logs (按月分区)

### 3.14 异步任务
- async_jobs / approval_tasks / approval_history

## 4. 全局规则

### 4.1 单号规则 (v3.7.6)
格式 `PREFIX-YYYYMMDD-NNNNN`, 由 `doc_no_sequences` 表原子自增

| 业务 | 前缀 | 示例 |
|------|------|------|
| 采购订单 | PO | PO-20260726-00015 |
| 采退订单 | RPO | RPO-20260726-00001 |
| 销售订单 | SO | SO-20260726-00001 |
| 销退订单 | SR | SR-20260726-00001 |
| **收货入库** | **GR** | GR-20260726-00003 |
| **收货子单** | GR + `-M` | GR-20260726-00003-1 |
| 采购退入库 | GRR | GRR-20260726-00001 |
| **销售出库** | **GI** | GI-20260726-00001 |
| 销退出库 | GIR | GIR-20260726-00001 |
| 库存调整 | ADJ | ADJ-20260726-00001 |
| 库存移动 | MV | MV-20260726-00001 |
| 合同申请 | CT-APP | CT-APP-20260726-00001 |
| 合同 | CT | CT-20260726-00001 |
| 分销发货 | DS | DS-20260726-00001 |
| RMA | RMA | RMA-20260726-00001 |
| RMA 授权 | RMAA | RMAA-20260726-00001 |

### 4.2 多租户
- 所有业务表含 `tenant_id` (UUID), TenantContext 透传
- 字典 / 序列号 / 工作流按租户隔离
- 默认租户 default, UUID `11111111-1111-1111-1111-111111111111`

### 4.3 状态机
| 域 | 状态 |
|----|------|
| 通用单据 | DRAFT → SUBMITTED → APPROVED → ... → COMPLETED / CANCELLED / REJECTED |
| 采购 | DRAFT → SUBMITTED → APPROVED → RECEIVING → COMPLETED |
| 收货父单 | DRAFT → PARTIAL_RECEIVED → COMPLETED / CANCELLED |
| 收货子单 | DRAFT → CONFIRMED / CANCELLED |
| 库存 | QUALIFIED(U合格) / PENDING(Q待检) / DEFECTIVE(B不合格) |
| 用户 | active / inactive / locked / blocked |
| 合同 | draft → pending_approval → effective → expired / terminated |

### 4.4 操作日志
- AOP `@OperationLog(businessType, action, remark)` 注解于 Controller
- 写入 `operation_log` 表 (tenant_code + business_type + business_id + operator + action + remark + created_at)
- 前端在详情/编辑页底部展示 (`/api/operation-log/list/{businessType}/{businessId}`)
- 覆盖业务: 采购订单 / 收货子单 / 销售订单 / 库存调整 / 等

### 4.5 权限
- RBAC: roles / user_roles / resources / role_strategies / data_scopes
- 岗位 positions 树形, 销售岗位 → 经销商映射 sales_dealer_mapping

### 4.6 字典
- dict_types + dict_items (按租户隔离 + 系统级 scope)
- 常用: stock_status / supplier_level / order_status / receipt_status / inventory_adjust_type / promotion_type / auth_type 等

## 5. 当前测试环境

- URL: http://8.133.193.238:8083
- 登录: default / admin / Sh123456
- DB: dms_test @ 5433 (容器 dms-test-postgres)
- 后端: dms-test-backend @ 8082 (容器)
- 前端: dms-test-frontend @ 8083 (容器, nginx → backend)
- 部署: `powershell -NoProfile -File .\.trae\skills\dms-deploy\deploy-fast.ps1 -Env test -Target both`

## 6. 当前生产环境

- URL: http://8.133.193.238:8081
- DB: dms @ 5432 (容器 dms-postgres)
- 后端: dms-backend @ 8080
- 前端: dms-frontend-vue @ 8081
- **未在 v3.7.x 部署**, 待用户明确指令 "推送正式环境" 才动

---

## 附录 A: 版本变更日志 (保留 v3.7.0 起的完整历史)

详见文件下半部分。

---

## 附录 B: 小版本独立需求文档

按版本目录存放于本目录:
- `v3.7.3_采购收货需求整理_20260726.md` (9 项)
- `v3.7.4_采购收货子单化_20260726.md` (子单结构 + U/Q/B)
- `v3.7.5_采购收货二次调整_20260726.md` (9 项)
- `v3.7.6_采购收货三次调整_20260726.md` (6 项)
- `v3.7.0 / v3.7.1 / v3.7.2 / 更早` → 见下方版本变更日志
