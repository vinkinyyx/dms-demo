# DMS API 接口清单（合并版）

**当前版本**: v3.5.1  
**最后更新**: 2026-07-20

---

## 变更日志

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
- `POST /api/stock-moves`：改为扁平结构 `{fromWarehouseId, toWarehouseId, stockStatus, remark, lines}`，源仓出库+目标仓入库并写操作日志
- `GET /api/stock-moves/{id}` 与 `/{id}/detail`：新增详情（源/目标仓名 + 明细）
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

### 📤 销售订单 (SO/RSO)
- `GET/POST /api/orders`
- `GET /api/orders/{id}/detail`
- `POST /api/orders/{id}/submit`
- `POST /api/orders/{id}/approve` — 触发自动建 CK
- `POST /api/orders/{id}/reject`
- `POST /api/orders/{id}/cancel`

### 📥 采购订单 (PO/RPO)
- `GET/POST /api/purchase-orders`
- `POST /api/purchase-orders/{id}/approve` — 触发自动建 RK

### 🛒 销售出库 (CK)
- `GET /api/sales-outs`
- `GET /api/sales-outs/{id}/detail`
- `POST /api/sales-outs/{id}/execute` — 弹窗填批次 → 扣 QUALIFIED
- `POST /api/sales-outs/{id}/cancel-draft`

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

### 📊 通用规范
所有列表 API 支持：
- `?page=1&size=20` 分页
- `?sort=field,desc` 排序（驼峰属性）
- `?keyword=xxx` 关键字搜索
- 返回结构 `{code, message, data:{total, page, size, list}}`
