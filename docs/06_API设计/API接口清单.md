# DMS API 接口清单（合并版）

**当前版本**: v3.7.0
**最后更新**: 2026-07-25

---

## 变更日志

### v3.7.0 (2026-07-25) — 主数据补齐（W1-W2）

#### 新增 API 模块（25 个端点）

#### 1. 产品线 API `/api/product-lines`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/product-lines` | 分页查询（支持 tenant/status/level 筛选） |
| GET | `/api/product-lines/{id}` | 按ID查询 |
| GET | `/api/product-lines/by-level/{level}` | 按层级查（1=BU / 2=产品线 / 3=分类） |
| GET | `/api/product-lines/by-parent/{parentId}` | 按父级ID查子节点 |
| POST | `/api/product-lines` | 创建（带审计日志） |
| PUT | `/api/product-lines/{id}` | 更新（带审计日志） |
| POST | `/api/product-lines/{id}/deactivate` | 停用（带审计日志） |

#### 2. 包装层级 API `/api/product-package-levels`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/product-package-levels` | 分页查询 |
| GET | `/api/product-package-levels/{id}` | 按ID查询 |
| GET | `/api/product-package-levels/by-product/{productId}` | 按产品ID查所有层级 |
| GET | `/api/product-package-levels/by-product/{productId}/roots` | 查根节点层级 |
| GET | `/api/product-package-levels/by-parent/{parentId}` | 查子节点层级 |
| POST | `/api/product-package-levels` | 创建 |
| PUT | `/api/product-package-levels/{id}` | 更新 |
| POST | `/api/product-package-levels/{id}/deactivate` | 停用 |

#### 3. 组套 API `/api/product-bundles`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/product-bundles` | 分页查询 |
| GET | `/api/product-bundles/{id}` | 按ID查询 |
| GET | `/api/product-bundles/by-product/{productId}` | 按产品ID查组套 |
| GET | `/api/product-bundles/{id}/lines` | 查所有明细 |
| GET | `/api/product-bundles/{id}/lines/fixed` | 查固定件明细 |
| POST | `/api/product-bundles` | 创建（pricingType: INHERIT/OVERRIDE/COMPONENT） |
| PUT | `/api/product-bundles/{id}` | 更新 |
| POST | `/api/product-bundles/{id}/deactivate` | 停用 |
| POST | `/api/product-bundles/{id}/lines` | 添加明细 |
| DELETE | `/api/product-bundles/{id}/lines/{lineId}` | 删除明细 |

#### 审计日志
所有 POST/PUT/DELETE 操作均通过 `@OperationLog` 注解自动记录到 `op_log` 表。

### v3.6.2 (2026-07-24)
- 前端 nginx upstream 修复：所有 `/api/*` 路径代理到 `http://dms-test-backend:8080`（之前错误指向不存在的 `dms-backend:8080`，导致所有业务 API 返回 403）
- v3.6.1 配置保留：`/api/operation-log/list/{businessType}/{businessId}` HTTP 200，验证 total=1 条
- **无新增/修改 API**

### v3.6.1 (2026-07-23)
- **后端配置变更**：新增 `MybatisPlusConfig.java` 配置 `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`，修复 MyBatis-Plus 分页查询 500 错误
- **nginx 配置变更**：`nginx-test.conf` `/api/auth/` 保留 rewrite 规则，`/api/` 其余路径取消 rewrite，修复所有业务 API 404 问题
- **数据库变更**：全库 jsonb 列转 text；V24/V25 手动执行并注册到 flyway_schema_history

### v3.6.0 (2026-07-22)
- **新增** `GET /api/reports/order-trace`：订单追溯报表（10 列：单号/订单类型/经销商/下单日期/审核日期/出库日期/收货日期/状态/金额/产品数），基于 `orders` JOIN `sales_outs` JOIN `receipts` 返回全链路追溯数据
- **后端配置变更**：`dms.jwt.access-token-ttl` 默认值改为 `28800000`（8 小时），影响 `/api/auth/refresh` 返回的 access_token 有效期
- **删除接口错误码新增**：`RESOURCE_IN_USE`（资源被引用）、`HAS_REFERENCES`（存在外键引用）、`CANNOT_DELETE`（不可删除），影响 `/api/products/{id}` `/api/product-categories/{id}` `/api/dealers/{id}` `/api/hospitals/{id}` `/api/warehouses/{id}` `/api/suppliers/{id}` `/api/orders/{id}` `/api/purchase-orders/{id}` 共 8 个模块的 DELETE 响应
- **`/api/sales-positions` 模块化**：modules.js 新增 `positions` 配置（`api: '/api/sales-positions'`），前端独立销售岗位页面，接口本身不变
- **`/api/sales-positions/candidate-users`**：仍按既有逻辑只返回 `role=sales` 用户

### v3.5.1 (2026-07-20)
- **导出 API 路径变更**：14个业务模块的导出接口从`GET /{module}/export`改为`GET /{module}/actions/export`，解决`/{id}`路径将"export"解析为ID导致的400错误
  - 涉及模块：products、dealers、hospitals、warehouses、regions、suppliers、product-prices、product-categories、sales-orders、purchase-orders、receipts、stock-moves、inventory-adjustments、surgery-reports
- **模板下载路径同步变更**：从`GET /{module}/export/template`改为`GET /{module}/actions/export/template`
- **数据字典字段映射修复**：`GET /api/dict-items/{typeCode}`返回字段名从`code/name/seq`改为`itemCode/label/value/sortOrder/status`

### v3.5.0 (2026-07-20)
> 本次为前端 Vue3 重构迭代，**后端 API 不变**（复用 v3.4.15 全部接口）。前端新增以下调用模式与页面：
- **前端部署**：Vue3 前端独立部署，端口 8081，Nginx 反向代理 `/api/` 到后端 `http://backend:8080`
- **前端页面路由**（Vue Router history 模式）：
  - PC 端：`/login`、`/`（首页仪表盘）、`/module/:key`（通用业务列表）、`/order-create`（订单创建）、`/positions`（销售岗位）、`/admin`（后台管理）
  - 移动端 H5：`/m/login`、`/m/home`、`/m/orders`、`/m/inventory`、`/m/receipt`（扫码收货）、`/m/report`、`/m/messages`
- **后端接口无变更**：继续使用 v3.4.15 的全部 60+ API（认证/主数据/订单/采购/库存/报表/系统等）
- **数据字典接口**（复用既有）：`GET /api/dict-types`、`GET /api/dict-items?typeCode=xxx`
- **租户管理接口**（复用既有）：`GET/POST/PUT /api/tenants`
- **操作日志接口**（复用既有）：`GET /api/operation-logs?resourceType=&resourceId=`

### v3.4.15 (2026-07-19)
- 新增 `GET /api/authorizations/{id}` 与 `/{id}/detail`：返回授权详情，含 dealerName/categoryNames/terminalNames
- `GET /api/authorizations`：列表回填 dealerName/categoryNames/terminalNames
- `POST /api/product-prices`：修复空日期报错（CAST(? AS DATE)）；`GET` 列表增加 partnerName
- `GET /api/inventory`：新增 serialNo（模糊）、keyword（产品编码/名称模糊）参数，batchNo 改模糊
- `GET /api/lookups/warehouses|dealers|hospitals|categories|products`：过滤软删除记录（deleted_at IS NULL）
- 各业务列表回填名称：销售/销退订单（dealerName/refOrderCode）、采购/采退（warehouseName）、库存移动（from/toWarehouseName）、手术报台（dealer/terminal/warehouseName）
- 新增菜单配置接口：`GET /api/menu-configs`、`POST /api/menu-configs/upsert`、`DELETE /api/menu-configs/{menuKey}`

### v3.4.14 (2026-07-19)
- `PUT /api/products/{id}`、`PUT /api/product-categories/{id}`：支持更新 code（含改后重名校验）
- `GET /api/products`、`/api/product-categories`、`/api/dealers`、`/api/hospitals`、`/api/warehouses`：支持任意实体字段作为查询参数进行后端过滤（字符串模糊、枚举/布尔等值），跨全部数据；page/size/sort 为保留字
- `POST /api/receipts/{id}/cancel-draft`：放开 PARTIAL_RECEIVED 状态取消（与 sales-outs 对称）
- `GET /api/product-prices`：关联产品回显 productCode/productName（关联键仍为 productId）

### v3.4.13 (2026-07-19)
- `POST /api/inventory-adjustments`：改为接受扁平结构 `{warehouseId, category:IN/OUT, type, stockStatus, remark, lines:[{productId,batchNo,serialNo,qty}]}`，直接完成增减并写操作日志
- `GET /api/inventory-adjustments/{id}` 与 `/{id}/detail`：新增详情（含仓库名/产品名/批次/序列号/数量明细）
- `POST /api/stock-moves`（v3.7.9 重写）：扁平结构 `{moveType, fromWarehouseId, toWarehouseId, remark, lines:[{srcInventoryId,qty,fromStockStatus,toStockStatus}]}`；moveType=STATUS_ADJUST(仓内状态调整，目标仓=源仓) 或 WAREHOUSE_TRANSFER(跨仓移动，可同时改状态)；明细必须从库存选择（srcInventoryId），后端校验库存归属/在库数/序列号/状态一致性；单号 MV-YYYYMMDD-NNNNN；保存即 COMPLETED，原子扣减+upsert 入库，写明细/流水/操作日志。
- `GET /api/stock-moves` 列表与 `GET /api/stock-moves/{id}`、`/{id}/detail`：返回 moveType、from/toStockStatus，明细含 isSerialManaged/from/toStockStatus/srcInventoryId。
- 库存状态字典：QUALIFIED(合格)/DEFECTIVE(不合格)/QUARANTINED(隔离)/PENDING(待检)。
- `GET /api/sales-outs/{id}/detail`：新增 `sourceOrder`（关联销售订单表头：code/orderType/status/amountInclTax/dealerName/createdAt）
- `GET /api/receipts/{id}/detail`：新增 `sourcePo`（关联采购订单表头：code/status/amountInclTax/supplierName/createdAt）
- `GET /api/products/{id}`：返回 `categoryName`（按 categoryId 回填）
- 所有原生 SQL 列表/详情接口的时间字段统一按 Asia/Shanghai 输出（DateFmt）
- `POST /api/sales-outs/{id}/cancel-draft`、`/api/receipts/{id}/cancel-draft`：补写操作日志（CANCEL）
- 经销商/医院/仓库/产品 create/update/deactivate 均记录操作日志，`GET /api/operation-logs` 可查

### v3.4.12 (2026-07-19)
- `GET /api/receipts/{id}/detail`、`/api/sales-outs/{id}/detail`：新增 `executionLines`（每次每批次收发货记录：seqNo/batchNo/serialNo/qty/operatorName/createdAt）
- `GET /api/purchase-orders/{id}/detail`：join 返回 `supplierName`（兜底 supplierNameRef）、`warehouseName`
- `POST /api/receipts/{id}/execute`、`/api/sales-outs/{id}/execute`：lines 支持同一行多子录入，序列号逐件 qty=1；每笔写执行明细表
- `PUT /api/suppliers/{id}`：全字段 COALESCE；`PUT /api/product-prices/{id}`：补 currency/effectiveDate/expireDate
- suppliers/product-prices/products 的 create/update 写 audit_logs（可经 `/api/operation-logs` 查询）
- 单据号统一 `PREFIX-YYYYMMDD-00001`

### v3.4.11 (2026-07-19)
- **新增** `GET /api/operation-logs?resourceType=&resourceId=` 单据操作日志时间轴
- `POST /api/receipts/{id}/execute`、`/api/sales-outs/{id}/execute`：lines 支持同一 lineId 多条子录入（序列号逐件），字段 `{receiptLineId|salesOutLineId, batchNo, serialNo, qty}`；序列号 qty 必须为 1 且查重；未录入的行自动跳过
- `POST /api/purchase-orders`：入库仓库(warehouseId)必填；正确写入 is_red
- `POST /api/orders`：新增 isRed / refOrderId 字段（销退红字）
- 单据号 DocNo 追加毫秒尾数避免撞号

### v3.4.10 (2026-07-19)
- `POST /api/receipts/{id}/execute`：body.lines[i].qty 为本次收货量（可分次），返回 status = PARTIAL_RECEIVED / COMPLETED
- `POST /api/sales-outs/{id}/execute`：body.lines[i].qty 为本次发货量（可分次），返回 status = PARTIAL_SHIPPED / COMPLETED
- `POST /api/receipts/{id}/cancel-draft`、`POST /api/sales-outs/{id}/cancel-draft`：允许 DRAFT / PARTIAL 状态取消剩余
- `GET /api/lookups/products?dealerId=X`：授权过滤 SQL 与 AuthorizationService 对齐（product_id 精确 / NULL 通配 / category 范围）+ DISTINCT 去重
- 所有列表 API 支持 `?sort=field,asc|desc`（默认 updatedAt,desc）
- 销售下单 orderType 由前端下拉传入（NORMAL/PROMOTION/SAMPLE/TRIAL/REPLACEMENT/CONSIGNMENT）

### v3.4.9 (2026-07-19)
- **新增** `GET/POST/PUT /api/suppliers` 供应商 CRUD；`GET /api/lookups/suppliers` 下拉
- **新增** `GET/POST/PUT /api/product-prices` 价格主数据；支持 partnerType (GLOBAL/DEALER/SUPPLIER) + partnerId 多维价格
- `/api/lookups/products` 返回增加 `unitType` (EA/SET) 和 `priceRetail`（从 product_prices GLOBAL 兜底取）
- 销售下单 body 结构确认为**平铺**：`{orderType, dealerId, remark, lines:[{productId, qty, unitPrice}]}`
- 采购下单 body 结构：`{supplierId, supplierName, warehouseId, lines:[...]}`

### v3.4.8 (2026-07-19)
- **新增** `GET /api/sales-positions/candidate-users`：仅返回 role=sales 用户 + boundPositionId/boundPositionName
- 采购审批 POST /api/purchase-orders/{id}/approve 会**自动创建 RK 草稿**（源码已实现，无需再单独走 receive 端点）
- 前端调用侧规范：销售/采购下单不再跳独立页 order-create.html，全部走内嵌 Tab 提交 POST /api/orders 或 /api/purchase-orders

### v3.4.7 (2026-07-19)
- `/api/dashboard/*` 7 个接口全部支持筛选参数：`?period={today,week,month,quarter,year,all}&dealerId={x}&status={x}&orderType={x}`
- `PUT /api/sales-positions/{id}/bind-user`：userId=null 表示解绑；绑定新人时先自动清空该岗位其他绑定
- `PUT /api/sales-positions/{id}/bind-dealers`：全量替换语义（未包含的自动解挂），已属其他岗位的经销商自动跳过（业务规则）
- `GET /api/sales-positions` 列表返回增加 `boundUserId` 字段
- `/api/dealers` 返回 `salesPositionId` 字段（Entity 映射）
- 移除 `/api/sales-org/tree`（已被销售岗位取代）

### v3.4.6 (2026-07-19)
- `GET /api/sales-outs` 覆盖：返回 dealerName/sourceOrderId/sourceOrderCode/autoCreated
- `GET /api/receipts` 覆盖：返回 warehouseName/sourcePoId/sourcePoCode/autoCreated
- 所有列表 API 支持任意字段作为 filter query 参数

### v3.4.5 (2026-07-19)
- **/api/lookups/products** 支持 `dealerId` 参数，按经销商授权过滤
- 报表 5 张全部返回丰富字段
- 单号前缀 SO/RSO/PO/RPO/CK/RK

### v3.4.4
- 新增 `/api/sales-outs/{id}/detail`、`/api/receipts/{id}/detail`、`/api/orders/{id}/detail`、`/api/purchase-orders/{id}/detail`
- 库存列表 `/api/inventory` 返回 join 后的产品/仓库信息

### v3.4
- 库存查询修复 500 错误
- 新增 `/api/inventory/available-lots` 可选批次查询
- 订单/采购审批自动生成对应出/入库草稿
- `/api/sales-outs/{id}/execute` + `/cancel-draft`（收批次弹窗）
- `/api/receipts/{id}/execute` + `/cancel-draft`
- 岗位 CRUD：`/api/sales-positions/*`
- 仪表盘 7 图表：`/api/dashboard/*`

### v3.3
- 三角色权限（admin/sales/dealer）
- 手术报台 `/api/surgery-reports`
- 5 张常规报表 `/api/reports/*`

### v3.2
- 库存状态机 `/api/inventory-status/*`
- 授权改产品分类

### v3.1
- 所有列表 API 分页规范化
- Excel 导入导出

### v3.0
- 采购销售拆分 `/api/purchase-orders/*`

---

## API 全景

### 🔐 认证
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/auth/refresh`

### 👥 用户/RBAC
- `GET /api/users`（三角色过滤）
- `GET /api/roles` `/api/permissions`

### 🏢 主数据
- `GET/POST/PUT /api/dealers`
- `GET/POST/PUT /api/hospitals`
- `GET/POST/PUT /api/products`
- `GET/POST/PUT /api/warehouses`
- `GET/POST/PUT /api/product-categories`
- `GET/POST/PUT /api/authorizations`

### 👔 销售岗位（v3.4）
- `GET /api/sales-positions?page&size` — 列表
- `POST /api/sales-positions` — 创建
- `GET /api/sales-positions/tree` — 层级树
- `PUT /api/sales-positions/{id}/bind-user` — 绑定账号
- `PUT /api/sales-positions/{id}/bind-dealers` — 挂载经销商
- `GET /api/sales-positions/my-scope` — 当前用户数据范围

### 📤 销售订单 (SO/RSO) — v3.7.7 起使用新端点
- `GET/POST /api/sales-orders`（v3.7.7 新端点，原生 SQL，镜像 PurchaseOrderController）
- `GET /api/sales-orders/{id}` — 详情（含 lines + allowedActions）
- `PUT /api/sales-orders/{id}` — 更新（仅 DRAFT）
- `DELETE /api/sales-orders/{id}` — 删除（仅 DRAFT 软删除）
- `POST /api/sales-orders/{id}/submit` — 提交审批
- `POST /api/sales-orders/{id}/approve` — 审批通过并自动生成 GI-* 销售出库草稿
- `POST /api/sales-orders/{id}/reject`
- `POST /api/sales-orders/{id}/cancel` — 仅 DRAFT/APPROVED，校验并级联取消出库单
- `GET /api/sales-orders/actions/export`、`POST /api/sales-orders/batch-import`
- 旧端点 `/api/orders`（JPA 实现）仅用于销退红字订单（`extraParams.isRed=true`）

### 📥 采购订单 (PO/RPO)
- `GET/POST /api/purchase-orders`
- `POST /api/purchase-orders/{id}/approve` — 触发自动建 RK

### 🛒 销售出库 (XS) — v3.7.7 表结构对齐收货入库
- `GET /api/sales-outs`（BizDocListController，返回 warehouseName/sourceOrderCode）
- `GET /api/sales-outs/{id}/detail` — 返回 head + lines（应发）+ shippedLines（执行记录）+ soLines（订单行）+ sourceOrder（来源销售订单）
- `POST /api/sales-outs/{id}/partial-ship` — body `{lines:[{expectedLineId, productId, warehouseId, batchNo, serialNo, qty, unitPrice}]}`，按应发行累计校验，自动回写订单 SHIPPING/COMPLETED
- `POST /api/sales-outs/{id}/cancel-partial` — 按已发货执行行恢复库存
- `POST /api/sales-outs/{id}/cancel-full` — 整单作废，全部库存回滚
- `POST /api/sales-outs/{id}/red-cancel` — 红字冲销

#### v3.7.8 销售出库子单（发货批次）模型
- `POST /api/sales-outs/{id}/batches` — 创建一张 DRAFT 发货子单（code = 父单号-序号）
- `PUT  /api/sales-out-batches/{bid}` — 保存子单明细（整单覆盖）。body `{lines:[{expectedLineId, shipLineNo, productId, warehouseId, qty, stockBatchId, batchNo, serialNo, unitPrice}]}`
- `POST /api/sales-out-batches/{bid}/confirm` — 确认发货：扣减 QUALIFIED 合格库存（批次合并/序列号逐件）、写库存流水、置序列号 shipped_at、累加应发行 shipped_qty、回写父单与源订单状态
- `POST /api/sales-out-batches/{bid}/cancel` — 取消本次（仅 DRAFT，不影响库存）
- `POST /api/sales-outs/{id}/cancel-remaining` — 取消剩余待发（未发数置 cancelled_qty、取消 DRAFT 子单、父单 COMPLETED/CANCELLED、回写源订单）
- 批次/序列号约束：必须来自该仓该物料的在库合格库存（`GET /api/inventory/available-batches`、`/available-serials`），后端校验存在且数量足够；序列号产品每行 qty=1 且必选在库序列号
- 状态：父单 DRAFT/APPROVED -> PARTIAL_SHIPPED -> COMPLETED；源订单 APPROVED -> SHIPPING -> COMPLETED；销售订单仅在未发货时可取消


### 📦 采购入库 (RK)
- `GET /api/receipts`
- `GET /api/receipts/{id}/detail`
- `POST /api/receipts/{id}/execute` — **手工录入**批次 → 增 PENDING
- `POST /api/receipts/{id}/cancel-draft`

### 🏥 手术植入报台
- `GET/POST /api/surgery-reports`

### 📊 库存
- `GET /api/inventory` — 分页 + join 产品/仓库/经销商
- `GET /api/inventory/available-lots?productId&warehouseId` — 可选批次
- `GET/POST /api/inventory-adjustments` — 库存调整
- `GET/POST /api/stock-moves` — 库存移动

### 🔍 Lookup（下拉数据源）
- `GET /api/lookups/dealers?keyword&limit`
- `GET /api/lookups/products?keyword&limit&dealerId` ⭐ v3.4.5 支持授权过滤
- `GET /api/lookups/hospitals` `/warehouses` `/categories` `/regions` `/contracts` `/orders`

### 📈 仪表盘
- `GET /api/dashboard/kpi` — 8 KPI
- `GET /api/dashboard/inventory-pie`
- `GET /api/dashboard/sales-trend` — 12 月趋势
- `GET /api/dashboard/order-funnel`
- `GET /api/dashboard/top-dealers`
- `GET /api/dashboard/top-hospitals`
- `GET /api/dashboard/activity-7d`

### 📋 业务报表（v3.4.5 字段丰富）
- `GET /api/reports/sales-ranking` — 编码/级别/订单均值/审批数/最近下单
- `GET /api/reports/product-top10` — 编码/规格/单位/订单数/经销商数/均价
- `GET /api/reports/inventory-turnover` — 合格/待检/不合格/批次数/周转天数
- `GET /api/reports/surgery-stats` — 编码/级别/经销商数/医生数/平均植入/最近手术
- `GET /api/reports/receivables` — 编码/级别/账龄 30/60/90/最早未收
- `GET /api/reports/overview` — 概览
- `GET /api/reports/order-trace` — v3.6.0 订单追溯（单号/类型/经销商/下单/审核/出库/收货/状态/金额/产品数）

### 📊 通用规范
所有列表 API 支持：
- `?page=1&size=20` 分页
- `?sort=field,desc` 排序（驼峰属性）
- `?keyword=xxx` 关键字搜索
- 返回结构 `{code, message, data:{total, page, size, list}}`


---

## v3.7.6 变更 (2026-07-26)

### 单号规则 (返回 code 字段)
| 业务 | v3.7.5 前 | v3.7.6 后 |
|------|-----------|-----------|
| 收货入库 | `RK-YYYYMMDD-N` | `GR-YYYYMMDD-N` |
| 收货子单 | `RK-*-M` | `GR-*-M` |
| 销售出库 | `CK-*` / `SO-timestamp` | `GI-YYYYMMDD-N` |
| 采购退入库 | `RRK-*` | `GRR-*` |
| 销退出库 | `RCK-*` | `GIR-*` |

### /api/purchase-orders list 响应字段新增
- `auditUserName` (string): 审核人姓名, users JOIN po.approved_by
- `auditAt` (string): 审核时间 (对应 po.approved_at)

### /api/purchase-orders/{id}/cancel 副作用
- 级联 `UPDATE receipts SET status='CANCELLED'` where source_po_id = {id} AND status IN (DRAFT/RECEIVING/PARTIAL_RECEIVED/APPROVED)
- 级联 `UPDATE receipt_batches SET status='CANCELLED', cancel_reason='源 PO 已取消'` for DRAFT 子单

### /api/receipt-batches/{bid}/confirm 副作用
- 更新 receipt_batches.confirmed_at = now(), confirmed_by = current user
- 更新 receipts.received_at, receipts.status (可能 COMPLETED)
- 更新 purchase_orders.status (可能 COMPLETED, completed_at = now())

### /api/operation-log/list/receipt/{receiptId}
- businessId 已由 aspect 统一为 receiptId (从 result.receiptId 提取)
- 一次收货完整流程可查到: CREATE / UPDATE 更新明细 / UPDATE 确认收货 (或 UPDATE 取消本次)
