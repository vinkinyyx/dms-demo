# DMS API 设计

> 生成时间：2026-08-14 17:51  
> 本文由 `reorg_docs_v2.py` 自动合并。今后该类设计请直接在本文件追加章节，不要再新建独立文件。

## 章节来源

1. 一、内部 API 接口清单 — `02_设计\API\API接口清单.md`
2. 二、DMS 对外开放接口 — `02_设计\API\DMS对外开放接口文档.md`
3. 三、订单传输接口 — `02_设计\API\订单传输接口文档.md`

---

## 阅读说明

本文件合并了内部 API 接口清单、对外开放接口、订单传输接口三部分。新增接口请追加到本文件。


---

## 一、内部 API 接口清单


## DMS API 接口清单（合并版）

**当前版本**: v3.11.1
**最后更新**: 2026-08-09

---

### 变更日志
> 对外开放接口（HMAC 鉴权）见独立文档：`docs/06_API设计/DMS对外开放接口文档.md`，含 POST /open/api/sales-orders、POST /open/api/purchase-orders。

#### v3.11.1 (2026-08-09) — 合同工作台导出接口补全

- 新增 GET /api/contracts/actions/export：按当前筛选（status/keyword/dealerId/category，最多 10000 条）导出 xlsx，16 列（ID/合同编号/合同名称/分类/申请类型/经销商ID/甲方/乙方/签约金额/有效期起/有效期止/状态/提交时间/生效时间/创建时间/更新时间）。
- 复用 ExcelExportUtils.exportMapToExcel；返回 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet，文件名 contracts_yyyy-MM-dd.xlsx。
- 前端 ContractWorkspace 工具栏新增「导出」按钮（blob 下载）。
- 价格/商务政策字段通过合同模板 form_data 承载，不在 contracts 表加硬字段。

#### v3.11.0 (2026-08-09) — 合同模块重构（方案 A：合并申请/合同为单一实体 + 模板驱动）

##### 一、合同接口
详见下文「合同模块接口（v3.11.0 重构）」章节。

##### 二、移除/变更
- v3.11.0 曾移除 `GET /api/contracts/actions/export`；已于 v3.11.1 恢复为合同工作台工具栏导出（xlsx，16 列）。
- 废弃全部 `/api/contract-applications/**`：合同申请与合同合并为 `/api/contracts`，单实体贯穿全生命周期。
- 合同导入模板接口不再提供：合同通过模板驱动的成稿生成，无需 Excel 导入。

##### 三、新增接口摘要
- 合同模板：`GET/POST/PUT /api/contract-templates`、`POST /api/contract-templates/{id}/publish`、`POST /api/contract-templates/{id}/new-version`、`POST /api/contract-templates/parse-docx`。
- 合同：`GET/POST/PUT/DELETE /api/contracts`、`POST /api/contracts/{id}/submit`、`POST /api/contracts/{id}/withdraw`、`GET /api/contracts/{id}/preview-docx`、`GET /api/contracts/match-template`、`POST /api/contracts/{id}/attachments`、`DELETE /api/contracts/{id}/attachments/{attId}`。
- 审批回调（合同业务类型 `CONTRACT`）：复用 v3.10.0 审批中心接口，业务实例由 `ContractApprovalCallback` 驱动合同状态流转。

#### v3.9.2 (2026-08-06) — 列表页布局 / 按钮配置 / 权限下发 (D13)
#### v3.9.3 (2026-08-06 晚) — button 资源对账 + pageKey 全量灌种 + 老页面迁移

新增迁移：
- V61 
bac_button_resources：6 个非系统租户各补 128 条 	ype=button 的 
bac_resources。
- V62 platform_button_configs_seed：16 个 pageKey 共 120 条平台默认按钮 seed。
- V63 utton_resource_auto_link：trigger，新增 button 资源自动挂到 strategy 1（“全部权限”）。

前端：
- `ApiCallLog.vue` / `ProductMappings.vue` 重构为 ListPageLayout；`ListPageLayout.vue` 加 defineExpose({ load })。



新增接口：
- GET /api/ui/layout/{pageKey} — 聚合下发 filter + page + toolbar + rowButtons 四套配置（合并平台默认 + 租户覆盖）。
- GET /api/button-configs/pages/{pageKey}/{scope} — 业务前台只读：scope 为 toolbar / row，返回按 sortOrder 排序的按钮列表。
- GET /api/admin/buttons?pageKey=&tenantType= — 管理后台：列出某页所有按钮（默认 + 覆盖）。
- POST /api/admin/buttons/batch — 管理后台：批量保存，body 字段 `scopeLevel` 取值 PLATFORM_DEFAULT / TENANT_OVERRIDE。
- POST /api/admin/buttons/refresh-cache — 刷新 Redis 缓存。
- GET /api/me/permissions — 当前用户全量资源权限码（resource.code），前端 v-has 指令使用。

修复：
- V60__api_call_log_transfer_fields.sql 中文单引号 ‘’ 改为 ASCII '，Flyway 不再校验失败。

#### v3.9.1 (2026-08-05) — 导入/导出接口补齐与修正

##### 1. 新增导出接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/authorizations/actions/export` | 授权列表导出（含经销商、授权产品分类、授权医院/终端名称） |

##### 2. 新增导入模板接口（模板总数 8 → 14）

`GET /api/{module}/actions/export/template`，新增模块：`sales-orders`、`purchase-orders`、`inventory-adjustments`、`surgery-reports`、`stock-moves`、`receipts`。模板表头与各自 `POST /api/{module}/batch-import` 读取的列名严格一致。

##### 3. 导入接口行为变更

| 接口 | 变更 |
|------|------|
| `POST /api/warehouses/batch-import` | 模板与导入列新增必填项 `经销商ID`（`warehouses.dealer_id` NOT NULL）；缺失时返回“经销商ID不能为空” |
| `POST /api/suppliers/batch-import` | 改为原生 SQL upsert（原 JPA 实体与实表不符导致 500）；`状态` 兼容 `1/0`、`启用/停用`、`active/inactive` |
| `POST /api/sales-orders/batch-import` | 修复 `::jsonb` 被 Hibernate 误当命名参数导致的整批回滚 |
| `POST /api/purchase-orders/batch-import` | 补单号生成（`code` NOT NULL UNIQUE），原先必然全行失败 |
| `POST /api/products/batch-import` | 修复 Excel 文本/数字混合列的 `ClassCastException` |
| `POST /api/surgery-reports/batch-import` | 导入状态 `DRAFT` → `COMPLETED`；手术日期补空值校验 |
| `POST /api/receipts/batch-import` | 补 `@Transactional`，数值转换容错 |

日期列统一经 `ExcelImportUtils.toDateString()` 归一化为 `yyyy-MM-dd` 后以 `CAST(? AS date)` 入库。

##### 4. 接口调用日志
`ApiCallLogFilter` 仅对文本类 Content-Type 记录响应体；导出类二进制响应记为 `<binary N bytes, content-type=...>`，修复之前每次导出都报 `invalid byte sequence for encoding "UTF8": 0x00` 的问题。

##### 5. 仍未提供的导出（前端已隐藏按钮，非缺陷）
`/api/inventory/actions/export`、`/api/sales-outs/actions/export`、`/api/orders/actions/export/template`、`/api/contracts|authorizations/actions/export/template`（合同/授权为只读模块，无导入需求）。

#### v3.8.2 (2026-08-02) — 接口调用日志模块

##### 1. 接口调用日志 `/api/admin/api-call-logs`（仅 admin）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/api-call-logs` | 分页查询，支持 direction/system/method/statusCode/keyword/startTime/endTime |
| GET | `/api/admin/api-call-logs/{id}` | 详情（含请求头、请求体、响应体、错误信息） |

查询参数：`page`(默认1)、`size`(默认20，最大200)、`direction`(IN/OUT)、`system`(ERP/WMS/HR/UDI/CA/第三方)、`method`(GET/POST/PUT/DELETE/PATCH)、`statusCode`、`keyword`(路径/URL/用户名/appKey 模糊)、`startTime`/`endTime`(yyyy-MM-dd HH:mm:ss)。

列表项字段：`id、direction、system、endpoint、httpMethod、path、statusCode、bizCode、success、clientIp、username、appKey、spentMs、startedAt`。

##### 2. 入站调用自动记录
- 所有 `/api/**` 请求由 `ApiCallLogFilter` 自动记录到 `api_call_log`（direction=IN），含方法、路径、状态码、业务码、耗时、调用方、请求/响应体摘要（截断 32KB）。
- 排除 `/actuator/**`、`/swagger-ui/**`、`/v3/api-docs/**`、`/api/admin/api-call-logs/**`（避免查看日志自身产生噪音）。

##### 3. 出站外部调用（DMS 调外部系统）
- 统一通过 `ApiCallLogService.callExternal(ExternalCall)` 发起，自动记录 direction=OUT，字段含 system/endpoint/url/方法/请求头/请求体/响应体/状态码/耗时/错误。
- 未来新增对接系统直接复用，无需重复写日志。示例：

```java
var call = new ApiCallLogService.ExternalCall();
call.system = "ERP";
call.endpoint = "stock.query";
call.url = "https://erp.example.com/api/stock";
call.method = "POST";
call.headers = Map.of("Content-Type", "application/json", "Authorization", "Bearer xxx");
call.body = "{\"productCodes\":[\"PROD-000002\"]}";
ApiCallLogService.ExternalResult r = apiCallLogService.callExternal(call);
```

##### 4. 获取 Token
- `POST /api/auth/login`，Body：`{"tenantCode":"default","username":"admin","password":"Sh123456"}`，返回 `data.accessToken`；后续请求头加 `Authorization: Bearer <accessToken>`。
- Token 过期可用 `POST /api/auth/refresh`（Body：`{"refreshToken":"..."}`）换新。

#### v3.7.0 (2026-07-25) — 主数据补齐（W1-W2）

##### 新增 API 模块（25 个端点）

##### 1. 产品线 API `/api/product-lines`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/product-lines` | 分页查询（支持 tenant/status/level 筛选） |
| GET | `/api/product-lines/{id}` | 按ID查询 |
| GET | `/api/product-lines/by-level/{level}` | 按层级查（1=BU / 2=产品线 / 3=分类） |
| GET | `/api/product-lines/by-parent/{parentId}` | 按父级ID查子节点 |
| POST | `/api/product-lines` | 创建（带审计日志） |
| PUT | `/api/product-lines/{id}` | 更新（带审计日志） |
| POST | `/api/product-lines/{id}/deactivate` | 停用（带审计日志） |

##### 2. 包装层级 API `/api/product-package-levels`

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

##### 3. 组套 API `/api/product-bundles`

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

##### 审计日志
所有 POST/PUT/DELETE 操作均通过 `@OperationLog` 注解自动记录到 `op_log` 表。

#### v3.6.2 (2026-07-24)
- 前端 nginx upstream 修复：所有 `/api/*` 路径代理到 `http://dms-test-backend:8080`（之前错误指向不存在的 `dms-backend:8080`，导致所有业务 API 返回 403）
- v3.6.1 配置保留：`/api/operation-log/list/{businessType}/{businessId}` HTTP 200，验证 total=1 条
- **无新增/修改 API**

#### v3.6.1 (2026-07-23)
- **后端配置变更**：新增 `MybatisPlusConfig.java` 配置 `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`，修复 MyBatis-Plus 分页查询 500 错误
- **nginx 配置变更**：`nginx-test.conf` `/api/auth/` 保留 rewrite 规则，`/api/` 其余路径取消 rewrite，修复所有业务 API 404 问题
- **数据库变更**：全库 jsonb 列转 text；V24/V25 手动执行并注册到 flyway_schema_history

#### v3.6.0 (2026-07-22)
- **新增** `GET /api/reports/order-trace`：订单追溯报表（10 列：单号/订单类型/经销商/下单日期/审核日期/出库日期/收货日期/状态/金额/产品数），基于 `orders` JOIN `sales_outs` JOIN `receipts` 返回全链路追溯数据
- **后端配置变更**：`dms.jwt.access-token-ttl` 默认值改为 `28800000`（8 小时），影响 `/api/auth/refresh` 返回的 access_token 有效期
- **删除接口错误码新增**：`RESOURCE_IN_USE`（资源被引用）、`HAS_REFERENCES`（存在外键引用）、`CANNOT_DELETE`（不可删除），影响 `/api/products/{id}` `/api/product-categories/{id}` `/api/dealers/{id}` `/api/hospitals/{id}` `/api/warehouses/{id}` `/api/suppliers/{id}` `/api/orders/{id}` `/api/purchase-orders/{id}` 共 8 个模块的 DELETE 响应
- **`/api/sales-positions` 模块化**：modules.js 新增 `positions` 配置（`api: '/api/sales-positions'`），前端独立销售岗位页面，接口本身不变
- **`/api/sales-positions/candidate-users`**：仍按既有逻辑只返回 `role=sales` 用户

#### v3.5.1 (2026-07-20)
- **导出 API 路径变更**：14个业务模块的导出接口从`GET /{module}/export`改为`GET /{module}/actions/export`，解决`/{id}`路径将"export"解析为ID导致的400错误
  - 涉及模块：products、dealers、hospitals、warehouses、regions、suppliers、product-prices、product-categories、sales-orders、purchase-orders、receipts、stock-moves、inventory-adjustments、surgery-reports
- **模板下载路径同步变更**：从`GET /{module}/export/template`改为`GET /{module}/actions/export/template`
- **数据字典字段映射修复**：`GET /api/dict-items/{typeCode}`返回字段名从`code/name/seq`改为`itemCode/label/value/sortOrder/status`

#### v3.5.0 (2026-07-20)
> 本次为前端 Vue3 重构迭代，**后端 API 不变**（复用 v3.4.15 全部接口）。前端新增以下调用模式与页面：
- **前端部署**：Vue3 前端独立部署，端口 8081，Nginx 反向代理 `/api/` 到后端 `http://backend:8080`
- **前端页面路由**（Vue Router history 模式）：
  - PC 端：`/login`、`/`（首页仪表盘）、`/module/:key`（通用业务列表）、`/order-create`（订单创建）、`/positions`（销售岗位）、`/admin`（后台管理）
  - 移动端 H5：`/m/login`、`/m/home`、`/m/orders`、`/m/inventory`、`/m/receipt`（扫码收货）、`/m/report`、`/m/messages`
- **后端接口无变更**：继续使用 v3.4.15 的全部 60+ API（认证/主数据/订单/采购/库存/报表/系统等）
- **数据字典接口**（复用既有）：`GET /api/dict-types`、`GET /api/dict-items?typeCode=xxx`
- **租户管理接口**（复用既有）：`GET/POST/PUT /api/tenants`
- **操作日志接口**（复用既有）：`GET /api/operation-logs?resourceType=&resourceId=`

#### v3.4.15 (2026-07-19)
- 新增 `GET /api/authorizations/{id}` 与 `/{id}/detail`：返回授权详情，含 dealerName/categoryNames/terminalNames
- `GET /api/authorizations`：列表回填 dealerName/categoryNames/terminalNames
- `POST /api/product-prices`：修复空日期报错（CAST(? AS DATE)）；`GET` 列表增加 partnerName
- `GET /api/inventory`：新增 serialNo（模糊）、keyword（产品编码/名称模糊）参数，batchNo 改模糊
- `GET /api/lookups/warehouses|dealers|hospitals|categories|products`：过滤软删除记录（deleted_at IS NULL）
- 各业务列表回填名称：销售/销退订单（dealerName/refOrderCode）、采购/采退（warehouseName）、库存移动（from/toWarehouseName）、手术报台（dealer/terminal/warehouseName）
- 新增菜单配置接口：`GET /api/menu-configs`、`POST /api/menu-configs/upsert`、`DELETE /api/menu-configs/{menuKey}`

#### v3.4.14 (2026-07-19)
- `PUT /api/products/{id}`、`PUT /api/product-categories/{id}`：支持更新 code（含改后重名校验）
- `GET /api/products`、`/api/product-categories`、`/api/dealers`、`/api/hospitals`、`/api/warehouses`：支持任意实体字段作为查询参数进行后端过滤（字符串模糊、枚举/布尔等值），跨全部数据；page/size/sort 为保留字
- `POST /api/receipts/{id}/cancel-draft`：放开 PARTIAL_RECEIVED 状态取消（与 sales-outs 对称）
- `GET /api/product-prices`：关联产品回显 productCode/productName（关联键仍为 productId）

#### v3.4.13 (2026-07-19)
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

#### v3.4.12 (2026-07-19)
- `GET /api/receipts/{id}/detail`、`/api/sales-outs/{id}/detail`：新增 `executionLines`（每次每批次收发货记录：seqNo/batchNo/serialNo/qty/operatorName/createdAt）
- `GET /api/purchase-orders/{id}/detail`：join 返回 `supplierName`（兜底 supplierNameRef）、`warehouseName`
- `POST /api/receipts/{id}/execute`、`/api/sales-outs/{id}/execute`：lines 支持同一行多子录入，序列号逐件 qty=1；每笔写执行明细表
- `PUT /api/suppliers/{id}`：全字段 COALESCE；`PUT /api/product-prices/{id}`：补 currency/effectiveDate/expireDate
- suppliers/product-prices/products 的 create/update 写 audit_logs（可经 `/api/operation-logs` 查询）
- 单据号统一 `PREFIX-YYYYMMDD-00001`

#### v3.4.11 (2026-07-19)
- **新增** `GET /api/operation-logs?resourceType=&resourceId=` 单据操作日志时间轴
- `POST /api/receipts/{id}/execute`、`/api/sales-outs/{id}/execute`：lines 支持同一 lineId 多条子录入（序列号逐件），字段 `{receiptLineId|salesOutLineId, batchNo, serialNo, qty}`；序列号 qty 必须为 1 且查重；未录入的行自动跳过
- `POST /api/purchase-orders`：入库仓库(warehouseId)必填；正确写入 is_red
- `POST /api/orders`：新增 isRed / refOrderId 字段（销退红字）
- 单据号 DocNo 追加毫秒尾数避免撞号

#### v3.4.10 (2026-07-19)
- `POST /api/receipts/{id}/execute`：body.lines[i].qty 为本次收货量（可分次），返回 status = PARTIAL_RECEIVED / COMPLETED
- `POST /api/sales-outs/{id}/execute`：body.lines[i].qty 为本次发货量（可分次），返回 status = PARTIAL_SHIPPED / COMPLETED
- `POST /api/receipts/{id}/cancel-draft`、`POST /api/sales-outs/{id}/cancel-draft`：允许 DRAFT / PARTIAL 状态取消剩余
- `GET /api/lookups/products?dealerId=X`：授权过滤 SQL 与 AuthorizationService 对齐（product_id 精确 / NULL 通配 / category 范围）+ DISTINCT 去重
- 所有列表 API 支持 `?sort=field,asc|desc`（默认 updatedAt,desc）
- 销售下单 orderType 由前端下拉传入（NORMAL/PROMOTION/SAMPLE/TRIAL/REPLACEMENT/CONSIGNMENT）

#### v3.4.9 (2026-07-19)
- **新增** `GET/POST/PUT /api/suppliers` 供应商 CRUD；`GET /api/lookups/suppliers` 下拉
- **新增** `GET/POST/PUT /api/product-prices` 价格主数据；支持 partnerType (GLOBAL/DEALER/SUPPLIER) + partnerId 多维价格
- `/api/lookups/products` 返回增加 `unitType` (EA/SET) 和 `priceRetail`（从 product_prices GLOBAL 兜底取）
- 销售下单 body 结构确认为**平铺**：`{orderType, dealerId, remark, lines:[{productId, qty, unitPrice}]}`
- 采购下单 body 结构：`{supplierId, supplierName, warehouseId, lines:[...]}`

#### v3.4.8 (2026-07-19)
- **新增** `GET /api/sales-positions/candidate-users`：仅返回 role=sales 用户 + boundPositionId/boundPositionName
- 采购审批 POST /api/purchase-orders/{id}/approve 会**自动创建 RK 草稿**（源码已实现，无需再单独走 receive 端点）
- 前端调用侧规范：销售/采购下单不再跳独立页 order-create.html，全部走内嵌 Tab 提交 POST /api/orders 或 /api/purchase-orders

#### v3.4.7 (2026-07-19)
- `/api/dashboard/*` 7 个接口全部支持筛选参数：`?period={today,week,month,quarter,year,all}&dealerId={x}&status={x}&orderType={x}`
- `PUT /api/sales-positions/{id}/bind-user`：userId=null 表示解绑；绑定新人时先自动清空该岗位其他绑定
- `PUT /api/sales-positions/{id}/bind-dealers`：全量替换语义（未包含的自动解挂），已属其他岗位的经销商自动跳过（业务规则）
- `GET /api/sales-positions` 列表返回增加 `boundUserId` 字段
- `/api/dealers` 返回 `salesPositionId` 字段（Entity 映射）
- 移除 `/api/sales-org/tree`（已被销售岗位取代）

#### v3.4.6 (2026-07-19)
- `GET /api/sales-outs` 覆盖：返回 dealerName/sourceOrderId/sourceOrderCode/autoCreated
- `GET /api/receipts` 覆盖：返回 warehouseName/sourcePoId/sourcePoCode/autoCreated
- 所有列表 API 支持任意字段作为 filter query 参数

#### v3.4.5 (2026-07-19)
- **/api/lookups/products** 支持 `dealerId` 参数，按经销商授权过滤
- 报表 5 张全部返回丰富字段
- 单号前缀 SO/RSO/PO/RPO/CK/RK

#### v3.4.4
- 新增 `/api/sales-outs/{id}/detail`、`/api/receipts/{id}/detail`、`/api/orders/{id}/detail`、`/api/purchase-orders/{id}/detail`
- 库存列表 `/api/inventory` 返回 join 后的产品/仓库信息

#### v3.4
- 库存查询修复 500 错误
- 新增 `/api/inventory/available-lots` 可选批次查询
- 订单/采购审批自动生成对应出/入库草稿
- `/api/sales-outs/{id}/execute` + `/cancel-draft`（收批次弹窗）
- `/api/receipts/{id}/execute` + `/cancel-draft`
- 岗位 CRUD：`/api/sales-positions/*`
- 仪表盘 7 图表：`/api/dashboard/*`

#### v3.3
- 三角色权限（admin/sales/dealer）
- 手术报台 `/api/surgery-reports`
- 5 张常规报表 `/api/reports/*`

#### v3.2
- 库存状态机 `/api/inventory-status/*`
- 授权改产品分类

#### v3.1
- 所有列表 API 分页规范化
- Excel 导入导出

#### v3.0
- 采购销售拆分 `/api/purchase-orders/*`

---

### API 全景

#### 🔐 认证
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/auth/refresh`

#### 👥 用户/RBAC
- `GET /api/users`（三角色过滤）
- `GET /api/roles` `/api/permissions`

#### 🏢 主数据
- `GET/POST/PUT /api/dealers`
- `GET/POST/PUT /api/hospitals`
- `GET/POST/PUT /api/products`
- `GET/POST/PUT /api/warehouses`
- `GET/POST/PUT /api/product-categories`
- `GET/POST/PUT /api/authorizations`

#### 👔 销售岗位（v3.4）
- `GET /api/sales-positions?page&size` — 列表
- `POST /api/sales-positions` — 创建
- `GET /api/sales-positions/tree` — 层级树
- `PUT /api/sales-positions/{id}/bind-user` — 绑定账号
- `PUT /api/sales-positions/{id}/bind-dealers` — 挂载经销商
- `GET /api/sales-positions/my-scope` — 当前用户数据范围

#### 📤 销售订单 (SO/RSO) — v3.7.7 起使用新端点
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

#### 📥 采购订单 (PO/RPO)
- `GET/POST /api/purchase-orders`
- `POST /api/purchase-orders/{id}/approve` — 触发自动建 RK

#### 🛒 销售出库 (XS) — v3.7.7 表结构对齐收货入库
- `GET /api/sales-outs`（BizDocListController，返回 warehouseName/sourceOrderCode）
- `GET /api/sales-outs/{id}/detail` — 返回 head + lines（应发）+ shippedLines（执行记录）+ soLines（订单行）+ sourceOrder（来源销售订单）
- `POST /api/sales-outs/{id}/partial-ship` — body `{lines:[{expectedLineId, productId, warehouseId, batchNo, serialNo, qty, unitPrice}]}`，按应发行累计校验，自动回写订单 SHIPPING/COMPLETED
- `POST /api/sales-outs/{id}/cancel-partial` — 按已发货执行行恢复库存
- `POST /api/sales-outs/{id}/cancel-full` — 整单作废，全部库存回滚
- `POST /api/sales-outs/{id}/red-cancel` — 红字冲销

##### v3.7.8 销售出库子单（发货批次）模型
- `POST /api/sales-outs/{id}/batches` — 创建一张 DRAFT 发货子单（code = 父单号-序号）
- `PUT  /api/sales-out-batches/{bid}` — 保存子单明细（整单覆盖）。body `{lines:[{expectedLineId, shipLineNo, productId, warehouseId, qty, stockBatchId, batchNo, serialNo, unitPrice}]}`
- `POST /api/sales-out-batches/{bid}/confirm` — 确认发货：扣减 QUALIFIED 合格库存（批次合并/序列号逐件）、写库存流水、置序列号 shipped_at、累加应发行 shipped_qty、回写父单与源订单状态
- `POST /api/sales-out-batches/{bid}/cancel` — 取消本次（仅 DRAFT，不影响库存）
- `POST /api/sales-outs/{id}/cancel-remaining` — 取消剩余待发（未发数置 cancelled_qty、取消 DRAFT 子单、父单 COMPLETED/CANCELLED、回写源订单）
- 批次/序列号约束：必须来自该仓该物料的在库合格库存（`GET /api/inventory/available-batches`、`/available-serials`），后端校验存在且数量足够；序列号产品每行 qty=1 且必选在库序列号
- 状态：父单 DRAFT/APPROVED -> PARTIAL_SHIPPED -> COMPLETED；源订单 APPROVED -> SHIPPING -> COMPLETED；销售订单仅在未发货时可取消


#### 📦 采购入库 (RK)
- `GET /api/receipts`
- `GET /api/receipts/{id}/detail`
- `POST /api/receipts/{id}/execute` — **手工录入**批次 → 增 PENDING
- `POST /api/receipts/{id}/cancel-draft`

#### 🏥 手术植入报台
- `GET/POST /api/surgery-reports`

#### 📊 库存
- `GET /api/inventory` — 分页 + join 产品/仓库/经销商
- `GET /api/inventory/available-lots?productId&warehouseId` — 可选批次
- `POST /api/inventory/query` — v3.8.1 库存汇总查询（JSON：productCodes 必填多值、warehouseId 选填；按物料汇总 totalQty，不展开批次/序列号/状态）
- `GET/POST /api/inventory-adjustments` — 库存调整
- `GET/POST /api/stock-moves` — 库存移动

#### 🔍 Lookup（下拉数据源）
- `GET /api/lookups/dealers?keyword&limit`
- `GET /api/lookups/products?keyword&limit&dealerId` ⭐ v3.4.5 支持授权过滤
- `GET /api/lookups/hospitals` `/warehouses` `/categories` `/regions` `/contracts` `/orders`

#### 📈 仪表盘
- `GET /api/dashboard/kpi` — 8 KPI
- `GET /api/dashboard/inventory-pie`
- `GET /api/dashboard/sales-trend` — 12 月趋势
- `GET /api/dashboard/order-funnel`
- `GET /api/dashboard/top-dealers`
- `GET /api/dashboard/top-hospitals`
- `GET /api/dashboard/activity-7d`

#### 📋 业务报表（v3.4.5 字段丰富）
- `GET /api/reports/sales-ranking` — 编码/级别/订单均值/审批数/最近下单
- `GET /api/reports/product-top10` — 编码/规格/单位/订单数/经销商数/均价
- `GET /api/reports/inventory-turnover` — 合格/待检/不合格/批次数/周转天数
- `GET /api/reports/surgery-stats` — 编码/级别/经销商数/医生数/平均植入/最近手术
- `GET /api/reports/receivables` — 编码/级别/账龄 30/60/90/最早未收
- `GET /api/reports/overview` — 概览
- `GET /api/reports/order-trace` — v3.6.0 订单追溯（单号/类型/经销商/下单/审核/出库/收货/状态/金额/产品数）

#### 📊 通用规范
所有列表 API 支持：
- `?page=1&size=20` 分页
- `?sort=field,desc` 排序（驼峰属性）
- `?keyword=xxx` 关键字搜索
- 返回结构 `{code, message, data:{total, page, size, list}}`


---

### v3.7.6 变更 (2026-07-26)

#### 单号规则 (返回 code 字段)
| 业务 | v3.7.5 前 | v3.7.6 后 |
|------|-----------|-----------|
| 收货入库 | `RK-YYYYMMDD-N` | `GR-YYYYMMDD-N` |
| 收货子单 | `RK-*-M` | `GR-*-M` |
| 销售出库 | `CK-*` / `SO-timestamp` | `GI-YYYYMMDD-N` |
| 采购退入库 | `RRK-*` | `GRR-*` |
| 销退出库 | `RCK-*` | `GIR-*` |

#### /api/purchase-orders list 响应字段新增
- `auditUserName` (string): 审核人姓名, users JOIN po.approved_by
- `auditAt` (string): 审核时间 (对应 po.approved_at)

#### /api/purchase-orders/{id}/cancel 副作用
- 级联 `UPDATE receipts SET status='CANCELLED'` where source_po_id = {id} AND status IN (DRAFT/RECEIVING/PARTIAL_RECEIVED/APPROVED)
- 级联 `UPDATE receipt_batches SET status='CANCELLED', cancel_reason='源 PO 已取消'` for DRAFT 子单

#### /api/receipt-batches/{bid}/confirm 副作用
- 更新 receipt_batches.confirmed_at = now(), confirmed_by = current user
- 更新 receipts.received_at, receipts.status (可能 COMPLETED)
- 更新 purchase_orders.status (可能 COMPLETED, completed_at = now())

#### /api/operation-log/list/receipt/{receiptId}
- businessId 已由 aspect 统一为 receiptId (从 result.receiptId 提取)
- 一次收货完整流程可查到: CREATE / UPDATE 更新明细 / UPDATE 确认收货 (或 UPDATE 取消本次)


#### 🔁 销退/采退订单（v3.8.1）
- `GET/POST /api/sales-returns` — 销退订单列表/创建（is_red=true，单号 RS）
- `GET/PUT/DELETE /api/sales-returns/{id}` — 详情/编辑(仅草稿)/删除(仅草稿)
- `POST /api/sales-returns/{id}/submit|approve|reject|cancel` — 状态机；approve 自动生成 RGR 入库草稿
- `GET /api/sales-returns/shipped-outs?orderId&dealerId` — 已发货出库单下拉
- `GET /api/sales-returns/shipped-outs/{salesOutId}/lines` — 带可退明细与可退数量
- `GET/POST /api/purchase-returns` — 采退订单列表/创建（单号 RP）
- `GET/PUT/DELETE /api/purchase-returns/{id}` — 详情/编辑/删除
- `POST /api/purchase-returns/{id}/submit|approve|reject|cancel` — 状态机；approve 自动生成 RGI 出库草稿
- 下游 RGR 走 `/api/receipts` 收货；RGI 走 `/api/sales-outs` 发货。


#### v4.2.0 (2026-08-06) - 内部传输接口（与对外 OpenAPI 并列）

> 内部传输接口：JWT 鉴权、调用方已登录 DMS 租户；与 `docs/06_API设计/DMS对外开放接口文档.md` 的 HMAC 对外接口为两套独立通道。`/transfer` 端点统一返回 `{ code, message, data: { id, code, orderType, status, amount } }`，成功时 `code=0`、`data.code` 即业务单号，失败时 `code!=0`、`message` 即失败原因。

| 方法 | 路径 | 说明 | 鉴权 | 业务单号（日志 biz_key） |
|------|------|------|------|--------------------------|
| POST | `/api/orders/transfer` | 销售订单传输。请求体复用 `OrderCreateRequest`（必填 `dealerId` + `lines`）。事务内同步落单 + 授权校验 + 促销计算 + 状态机 DRAFT。 | JWT | `data.code`（`SO-YYYYMMDD-#####`） |
| POST | `/api/purchase-orders/transfer` | 采购订单传输。请求体 Map（必填 `supplierId` / `warehouseId` / `lines`）。事务内同步落单 + 写明细 + 写状态历史。 | JWT | `data.code`（`PO-YYYYMMDD-#####`） |
| GET  | `/api/inventory` | 库存查询。`dealerId/productId/warehouseId/batchNo/serialNo/keyword/stockStatus/page/size/sort`。join 产品/仓库/经销商返回丰富字段。 | JWT | `warehouseId-productId` 或首条 `data.list[0].id` |

**调用示例（销售订单传输）**
```bash
curl -X POST http://host:8080/api/orders/transfer   -H "Authorization: Bearer ${JWT}"   -H "Content-Type: application/json"   -d '{
    "dealerId": 12,
    "orderType": "NORMAL",
    "expectedDate": "2026-08-15",
    "remark": "上游单号 X123",
    "lines": [
      { "productId": 101, "qty": 5, "unitPrice": 199.00, "taxRate": 0.13 }
    ]
  }'
```

**成功响应**
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": 1024,
    "code": "SO-20260806-00001",
    "orderType": "NORMAL",
    "status": "DRAFT",
    "amount": 995.00
  }
}
```

**失败响应**（如经销商不存在）
```json
{
  "code": 40401,
  "message": "dealerId=12 不存在",
  "data": null
}
```

#### v4.2.0 接口调用日志（api_call_log）字段扩展

| 字段 | 类型 | 说明 |
|------|------|------|
| `biz_key` | VARCHAR(64) | 业务单号：SO-/PO- 订单编号，库存查询时为 `warehouseId-productId` |
| `biz_action` | VARCHAR(64) | 业务动作标签：`inventory.query` / `order.transfer.sales` / `order.transfer.purchase` |

**新增索引**：
- `idx_api_call_log_biz_key`（按业务单号检索）
- `idx_api_call_log_biz_action (biz_action, started_at DESC)`（按动作筛选 + 时间倒序）
- `idx_api_call_log_path_time (path, started_at DESC)`（按路径 + 时间倒序）

**后台查询**（仅 admin）：`GET /api/admin/api-call-logs?bizKey=SO-20260806-00001` 或 `?bizAction=order.transfer.sales&startTime=2026-08-06`


---

### v3.10.0 (2026-08-09) 审批流接口

所有接口前缀 `/api/approval`，均需登录态。分页参数统一 `page`、`size`，返回 `{ code, data:{ total, page, size, list } }`。

#### 模板配置
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/approval/templates | 模板列表，可按 businessType/status/keyword 过滤 |
| GET | /api/approval/templates/{id} | 模板详情（含节点、审批人、抄送） |
| POST | /api/approval/templates | 新建草稿 |
| PUT | /api/approval/templates/{id} | 更新草稿 |
| POST | /api/approval/templates/{id}/publish | 发布 |
| POST | /api/approval/templates/{id}/disable | 停用 |
| POST | /api/approval/templates/{id}/new-version | 基于该版本创建新版本草稿 |

模板保存体 TemplateSaveRequest：`businessType, code, name, priority, rejectPolicy, conditionConfig{logic,rules[]}, timeoutHours, remindIntervalHours, maxRemindCount, description, nodes[{nodeOrder,name,approveMode,allowTransfer,allowAddSign,timeoutHours,assignees[{assigneeType,refId,displayName}],ccs[]}], finishCcs[]`。

#### 审批操作
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/approval/instances/start | 发起审批（内部/业务调用，传 businessType/businessId/businessCode/title/businessSnapshot） |
| GET | /api/approval/tasks/my-todo | 我的待办 |
| GET | /api/approval/tasks/my-done | 我已处理 |
| GET | /api/approval/instances/my-submitted | 我发起的 |
| GET | /api/approval/cc/my | 抄送我的 |
| GET | /api/approval/instances/{id} | 实例详情（instance + tasks + records） |
| GET | /api/approval/instances/by-business | 按 businessType+businessId 查最新实例 |
| POST | /api/approval/instances/{id}/withdraw | 发起人撤回（body: {comment}） |
| POST | /api/approval/tasks/{id}/approve | 同意（body: {comment}） |
| POST | /api/approval/tasks/{id}/reject | 驳回（body: {comment}） |
| POST | /api/approval/tasks/{id}/transfer | 转办（body: {targetUserId, comment}） |
| POST | /api/approval/tasks/{id}/add-sign | 加签（body: {targetUserId, signType: BEFORE/AFTER, comment}） |

#### 管理员（需 approval:admin）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/approval/admin/instances?status= | 全部实例 |
| POST | /api/approval/admin/tasks/{id}/reassign | 改派（body: {targetUserId, reason}） |
| POST | /api/approval/admin/instances/{id}/terminate | 终止（body: {reason}） |

#### 委托
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/approval/delegations | 委托列表 |
| POST | /api/approval/delegations | 新建委托（delegatorId, delegateeId, startsAt, endsAt, reason） |
| POST | /api/approval/delegations/{id}/disable | 停用委托 |

#### 业务接入约定
业务单据提交时调用 `/instances/start`，传入 `businessSnapshot`（至少含 `finalAmount/orderType` 等条件字段）。审批结果通过 `ApprovalBusinessCallback` 回调：`supports(businessType)` 判定，`onApproved/onReturned/onRejected/onWithdrawn/onTerminated` 回写业务状态。无匹配模板或命中 AUTO_APPROVE 模板时自动通过并触发 onApproved。
### 合同模块接口（v3.11.0 重构）

> 原 `/api/contract-applications` 已移除（合并为 `/api/contracts`）；`/api/contracts/actions/export` 于 v3.11.1 恢复为合同工作台导出（xlsx，16 列）。

#### 合同
- `GET /api/contracts` 列表（参数 page/size/status/keyword/dealerId/category）
- `GET /api/contracts/actions/export` 导出当前筛选结果为 xlsx（16 列，v3.11.1）
- `POST /api/contracts` 新建草稿（body: name/category/applicationType/dealerId/vendorParty/dealerParty/validFrom/validTo/targetAmount/signedAmount/templateId/formData...）
- `GET /api/contracts/{id}` 详情（含模板字段定义、附件、审批轮次）
- `PUT /api/contracts/{id}` 更新（仅 draft/rejected）
- `DELETE /api/contracts/{id}` 删除（仅 draft）
- `POST /api/contracts/{id}/submit` 提交审批（挂模板则回填 Word 生成成稿，启动审批流）
- `POST /api/contracts/{id}/withdraw` 撤回审批
- `POST /api/contracts/{id}/attachments?fileId=&fileName=&sizeBytes=&category=` 上传附件记录
- `DELETE /api/contracts/{id}/attachments/{attachmentId}` 删除附件
- 成稿/附件下载：`GET /api/files/{fileId}/download`

#### 合同模板
- `GET /api/contract-templates` 列表（page/size/category/status/keyword）
- `POST /api/contract-templates` 新建草稿
- `GET /api/contract-templates/{id}` 详情
- `PUT /api/contract-templates/{id}` 更新（仅 draft）
- `POST /api/contract-templates/{id}/publish` 发布
- `POST /api/contract-templates/{id}/new-version` 基于已有版本创建新版本草稿
- `POST /api/contract-templates/{id}/disable` 停用
- `DELETE /api/contract-templates/{id}` 删除（非 published）
- `GET /api/contract-templates/match?category=` 按分类匹配当前已发布模板
- `POST /api/contract-templates/upload-and-parse`（multipart file）上传 Word 并识别字段，返回 `{fileId, originalName, fields[]}`
- `POST /api/contract-templates/parse-docx`（multipart file）仅解析字段，不落库


---

## 二、DMS 对外开放接口


## DMS 对外开放接口文档（Open API）

**版本**: v3.8.3
**最后更新**: 2026-08-02
**适用范围**: 外部系统（ERP/WMS/HR/UDI/CA/第三方平台）调用 DMS 创建单据

---

### 1. 概述

DMS 对外开放一组 RESTful 接口，供外部系统以 **HMAC-SHA256 签名**方式调用，无需用户名密码登录。当前开放：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建销售订单 | POST | `/open/api/sales-orders` | 在 DMS 中创建一张销售订单（草稿） |
| 创建采购订单 | POST | `/open/api/purchase-orders` | 在 DMS 中创建一张采购订单（草稿） |

未来新增接口将沿用同一套鉴权与签名规则。

#### 1.1 环境地址

> 注意：浏览器访问的 DMS UI 位于 `/dms/`、`/dms/admin/`、`/dms/mobile/login`；开放 API 仍以站点根路径为 Base URL，不加 `/dms`。

| 环境 | Base URL |
|------|----------|
| 测试环境 | `http://43.128.145.141` |
| 生产环境 | 由实施方提供 |

#### 1.2 数据格式
- 请求与响应均为 `application/json; charset=UTF-8`。
- 日期格式：`yyyy-MM-dd`（如 `2026-08-10`）。
- 金额/数量：数字，保留两位小数，不加千分位。
- 主数据统一使用 **编码（code）** 传参，而非内部 ID（产品也可传 productId 作为备选）。

---

### 2. 鉴权与签名

#### 2.1 接入凭据
每个对接系统由 DMS 管理员分配一对凭据（存于 `open_app` 表）：

- `appKey`：应用标识，明文传输，放在请求头。
- `appSecret`：应用密钥，**绝不传输**，仅用于客户端本地计算签名。

> 测试环境默认应用：appKey = `dms-demo-app`，appSecret = `8c39b1f7e2a44d6b9f0a1c2d3e4f5a6b`（仅测试用，生产请重置）。

#### 2.2 请求头
每个请求必须携带以下请求头：

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-App-Key` | 是 | 应用标识 |
| `X-Timestamp` | 是 | 当前毫秒时间戳（UTC），与服务器偏差不得超过 ±5 分钟 |
| `X-Nonce` | 是 | 随机字符串，建议 UUID，用于防重放 |
| `X-Signature` | 是 | 按下方规则计算的 HMAC-SHA256 签名（小写 hex） |
| `Content-Type` | 是 | 固定 `application/json` |

#### 2.3 签名算法

**待签名字符串 signString**（字段间用换行符 `\n` 连接）：

```
HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + X-Timestamp + "\n" + X-Nonce + "\n" + sha256Hex(body)
```

- `HTTP_METHOD`：大写，如 `POST`。
- `REQUEST_PATH`：路径部分（不含域名与 query），如 `/open/api/sales-orders`。
- `sha256Hex(body)`：请求体原始字节的 SHA-256 摘要（小写 hex）。GET 等无 body 时为空字符串的摘要。

**签名**：

```
signature = lower( HMAC_SHA256(appSecret, signString) )
```

#### 2.4 签名示例（伪代码）

**Python**
```python
import hashlib, hmac, time, uuid, json, requests

APP_KEY = "dms-demo-app"
APP_SECRET = "8c39b1f7e2a44d6b9f0a1c2d3e4f5a6b"
BASE = "http://43.128.145.141"

def call(method, path, body_obj):
    body = json.dumps(body_obj, ensure_ascii=False)
    ts = str(int(time.time() * 1000))
    nonce = uuid.uuid4().hex
    body_hash = hashlib.sha256(body.encode()).hexdigest()
    sign_string = f"{method}\n{path}\n{ts}\n{nonce}\n{body_hash}"
    signature = hmac.new(APP_SECRET.encode(), sign_string.encode(), hashlib.sha256).hexdigest()
    headers = {
        "X-App-Key": APP_KEY, "X-Timestamp": ts, "X-Nonce": nonce,
        "X-Signature": signature, "Content-Type": "application/json"
    }
    return requests.request(method, BASE + path, headers=headers, data=body.encode())
```

**Java**
```java
String bodyHash = sha256Hex(body.getBytes(StandardCharsets.UTF_8));
String signString = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
String signature = HexFormat.of().formatHex(mac.doFinal(signString.getBytes(StandardCharsets.UTF_8)));
```

**JavaScript (Node.js)**
```javascript
const crypto = require('crypto');
const bodyHash = crypto.createHash('sha256').update(body).digest('hex');
const signString = `${method}\n${path}\n${timestamp}\n${nonce}\n${bodyHash}`;
const signature = crypto.createHmac('sha256', appSecret).update(signString).digest('hex');
```

#### 2.5 鉴权失败响应
HTTP 状态码 401/403，响应体：

```json
{ "code": 40501, "message": "签名校验失败", "requestId": "" }
```

常见原因：缺少鉴权头、时间戳超差、appKey 无效、应用被禁用、来源 IP 不在白名单、签名计算错误（注意 body 必须与发送的字节完全一致，建议用同一份序列化结果计算 hash 与发送）。

---

### 3. 统一响应结构

所有接口返回统一信封：

```json
{
  "code": 0,
  "message": "OK",
  "data": { },
  "requestId": "bbd9b85ca4be408584852533f068034d"
}
```

- `code = 0` 表示业务成功；非 0 表示业务失败（HTTP 仍可能为 200）。
- `message`：结果描述。
- `data`：业务数据。
- `requestId`：链路追踪 ID，排查问题时请提供。

常用错误码：

| code | 含义 |
|------|------|
| 0 | 成功 |
| 40001 | 参数校验失败（如必填项为空、明细为空） |
| 40401 | 主数据不存在（经销商/供应商/仓库/产品编码错误） |
| 40501 | 鉴权失败（HTTP 401） |
| 40300 | 无权限/应用禁用/IP 受限（HTTP 403） |
| 50000 | 系统内部错误 |

---

### 4. 接口详情

#### 4.1 创建销售订单

`POST /open/api/sales-orders`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| dealerCode | string | 是 | 经销商编码 |
| warehouseCode | string | 是 | 发货仓库编码 |
| expectedDate | string | 否 | 预计发货日期 yyyy-MM-dd |
| orderType | string | 否 | 订单类型，默认 `NORMAL` |
| dealerName | string | 否 | 经销商名称快照 |
| remark | string | 否 | 备注 |
| extra | object/string | 否 | 扩展字段，存为 JSON |
| lines | array | 是 | 明细行，至少一行 |

**lines[]**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productCode | string | 是* | 产品编码（与 productId 二选一） |
| productId | number | 否 | 产品内部 ID（备选） |
| qty | number | 是 | 数量 |
| unitPrice | number | 是 | 单价（不含税） |
| taxRate | number | 否 | 税率，默认 0.13 |
| isGift | boolean | 否 | 是否赠品 |

**请求示例**
```json
{
  "dealerCode": "D00001",
  "warehouseCode": "WH-MAIN",
  "expectedDate": "2026-08-10",
  "remark": "外部ERP推送",
  "lines": [
    { "productCode": "PROD-000012", "qty": 5, "unitPrice": 100, "taxRate": 0.13 }
  ]
}
```

**响应 data**
```json
{ "id": 772, "code": "SO-20260802-00002", "status": "DRAFT" }
```

说明：创建后订单为 `DRAFT`（草稿）状态，由 DMS 内部后续提交、审批；审批通过后按既有规则自动生成销售出库单。金额合计由 DMS 按 `qty * unitPrice` 汇总。

---

#### 4.2 创建采购订单

`POST /open/api/purchase-orders`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| supplierCode | string | 是 | 供应商编码 |
| warehouseCode | string | 是 | 收货仓库编码 |
| expectedDate | string | 否 | 预计到货日期 yyyy-MM-dd |
| orderType | string | 否 | 订单类型，默认 `NORMAL` |
| supplierName | string | 否 | 供应商名称快照 |
| remark | string | 否 | 备注 |
| extra | object/string | 否 | 扩展字段，存为 JSON |
| lines | array | 是 | 明细行，至少一行 |

**lines[]**：同销售订单（productCode/productId、qty、unitPrice、taxRate）。

**请求示例**
```json
{
  "supplierCode": "SUP-0030",
  "warehouseCode": "WH-MAIN",
  "expectedDate": "2026-08-12",
  "remark": "外部ERP推送",
  "lines": [
    { "productCode": "PROD-000016", "qty": 3, "unitPrice": 50 }
  ]
}
```

**响应 data**
```json
{ "id": 77, "code": "PO-20260802-00002", "status": "DRAFT" }
```

---

### 5. 完整调用示例（curl）

```bash
BODY='{"dealerCode":"D00001","warehouseCode":"WH-MAIN","expectedDate":"2026-08-10","lines":[{"productCode":"PROD-000012","qty":5,"unitPrice":100}]}'
TS=$(date +%s%3N)
NONCE=$(cat /proc/sys/kernel/random/uuid)
HASH=$(printf '%s' "$BODY" | sha256sum | awk '{print $1}')
SIGN_STR=$(printf 'POST\n/open/api/sales-orders\n%s\n%s\n%s' "$TS" "$NONCE" "$HASH")
SIG=$(printf '%s' "$SIGN_STR" | openssl dgst -sha256 -hmac "8c39b1f7e2a44d6b9f0a1c2d3e4f5a6b" | awk '{print $2}')

curl -X POST http://43.128.145.141/open/api/sales-orders \
  -H "Content-Type: application/json" \
  -H "X-App-Key: dms-demo-app" \
  -H "X-Timestamp: $TS" \
  -H "X-Nonce: $NONCE" \
  -H "X-Signature: $SIG" \
  -d "$BODY"
```

---

### 6. 调用日志与排错

- 所有 `/open/api/**` 调用均记录在 DMS 的 **接口调用日志**（`api_call_log`，direction=IN），可在 DMS 后台「用户与权限 → 接口调用日志」查看，含请求体、响应体、状态码、耗时、appKey、系统标识。
- 签名失败时没有 requestId，请重点核对：body 字节一致性、path 是否含 query（不应含）、时间戳是否为毫秒、换行符是否为 `\n`。
- 时间戳偏差超过 5 分钟会被拒绝，请校准调用方服务器时钟（NTP）。

### 7. 安全建议

- appSecret 仅在调用方服务端保存，切勿下发到前端/客户端。
- 生产环境通过后台重置默认 appSecret，并按需配置来源 IP 白名单（`open_app.allowed_ips`）。
- 全程使用 HTTPS。
- 请妥善保存 requestId，便于双方对账与问题定位。


---

## 三、订单传输接口


## 订单传输接口文档

**版本**: v4.2.0
**最后更新**: 2026-08-06
**适用范围**: DMS 同租户系统（经销商/厂家内部系统）通过 JWT 鉴权推送销售订单 / 采购订单

---

### 1. 概述

DMS 提供 3 个写入/读取型的"传输接口"作为对内/对外统一通道：
- 库存查询（只读）
- 销售订单传输（写入）
- 采购订单传输（写入）

| 接口 | 方法 | 路径 | 鉴权 | 同步 | 业务单号 | 失败原因 |
|------|------|------|------|------|----------|----------|
| 库存查询       | GET  | `/api/inventory`               | JWT | 是 | `warehouseId-productId` 或首条记录 id | `message` 字段 |
| 销售订单传输   | POST | `/api/orders/transfer`         | JWT | 是 | `data.code`（`SO-YYYYMMDD-#####`） | `message` 字段 |
| 采购订单传输   | POST | `/api/purchase-orders/transfer`| JWT | 是 | `data.code`（`PO-YYYYMMDD-#####`） | `message` 字段 |

成功响应：HTTP 200，`code=0`，`data.code` 即新单号。  
失败响应：HTTP 200（业务失败），`code` 为 `ErrorCode` 业务错误码，`message` 即失败原因。

---

### 2. 销售订单传输

#### 2.1 请求

`POST /api/orders/transfer`，Body = `OrderCreateRequest`（与 `POST /api/orders` 一致）：

```json
{
  "dealerId": 12,
  "orderType": "NORMAL",
  "expectedDate": "2026-08-15",
  "remark": "上游单号 X123",
  "lines": [
    { "productId": 101, "qty": 5, "unitPrice": 199.00, "taxRate": 0.13 }
  ]
}
```

字段要求：
- `dealerId`（必填）：经销商内部 ID
- `lines`（必填、非空）：明细数组
- `orderType`、`expectedDate`、`remark`、`shipAddressId`、`shipSnapshot`、`refOrderId`、`isRed`（可选）

#### 2.2 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": 1024,
    "code": "SO-20260806-00001",
    "orderType": "NORMAL",
    "status": "DRAFT",
    "amount": 995.00
  }
}
```

#### 2.3 失败响应

| 错误码 | 说明 | 触发示例 |
|--------|------|----------|
| 40002 | 参数缺失 | `dealerId` 为 null、明细为空 |
| 40401 | 经销商不存在 | `dealerId=99` |
| 40006 | 业务规则校验失败 | 授权 `SALES_TO_HOSPITAL` 失败、促销 GIFT/BUNDLE 未启用 |
| 50000 | 系统内部错误 | 异常兜底 |

```json
{
  "code": 40401,
  "message": "dealerId=12 不存在或不可用",
  "data": null
}
```

---

### 3. 采购订单传输

#### 3.1 请求

`POST /api/purchase-orders/transfer`，Body = Map：

```json
{
  "supplierId": 7,
  "warehouseId": 3,
  "orderType": "NORMAL",
  "expectedDate": "2026-08-15",
  "remark": "上游单号 P001",
  "lines": [
    { "productId": 101, "qty": 10, "unitPrice": 88.50, "taxRate": 0.13 }
  ]
}
```

字段要求：
- `supplierId`（必填）：供应商内部 ID
- `warehouseId`（必填）：仓库内部 ID
- `lines`（必填、非空）：明细数组，每行需 `productId` + `qty`
- `orderType`、`expectedDate`、`remark`、`supplierName`、`isRed`、`extra`（可选）

#### 3.2 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": 502,
    "code": "PO-20260806-00001",
    "orderType": "NORMAL",
    "status": "DRAFT",
    "amount": 885.00
  }
}
```

#### 3.3 失败响应

| 错误码 | 说明 | 触发示例 |
|--------|------|----------|
| 40002 | 参数缺失 | `supplierId` 为 null、明细行 `productId/qty` 为空 |
| 50000 | 系统内部错误 | 异常兜底 |

---

### 4. 库存查询

#### 4.1 请求

`GET /api/inventory`，Query 参数：

| 参数 | 类型 | 说明 |
|------|------|------|
| `dealerId` | Long | 经销商 ID |
| `productId` | Long | 产品 ID |
| `warehouseId` | Long | 仓库 ID |
| `batchNo` | String | 批号（模糊匹配） |
| `serialNo` | String | 序列号（模糊匹配） |
| `keyword` | String | 关键字（按产品编码/名称模糊） |
| `stockStatus` | String | 库存状态（`NORMAL/QUARANTINE/...`） |
| `page` | int | 页码（默认 1） |
| `size` | int | 每页条数（默认 20） |
| `sort` | String | 排序字段（默认按 `updated_at` 倒序） |

#### 4.2 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 23,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 9001,
        "warehouseId": 3,
        "warehouseName": "上海中心仓",
        "warehouseCode": "WH-SH01",
        "productId": 101,
        "productCode": "SKU-001",
        "productName": "示例产品",
        "productSpec": "10mg*30片",
        "productUnit": "盒",
        "isSerialManaged": true,
        "dealerId": 12,
        "dealerName": "示例经销商",
        "batchNo": "B20260801",
        "serialNo": null,
        "qty": 50.0,
        "stockStatus": "NORMAL",
        "expDate": "2028-08-01",
        "prodDate": "2026-08-01",
        "createdAt": "2026-08-01T10:00:00+08:00",
        "updatedAt": "2026-08-06T09:30:00+08:00"
      }
    ]
  }
}
```

---

### 5. 接口调用日志（api_call_log）

3 个接口的调用自动记录到 `api_call_log` 表。除通用字段（`tenant_id` / `user_id` / `request_id` / `started_at` / `spent_ms` / `status_code` / `biz_code` / `success` / `request_body` / `response_body`）外，v4.2.0 新增两个专用字段：

| 字段 | 类型 | 含义 | 取值 |
|------|------|------|------|
| `biz_key`    | VARCHAR(64) | 业务单号 | `data.code`（成功时）/ `message` 前 32 字符（失败时）/ 库存查询=`warehouseId-productId` 或首条 id |
| `biz_action` | VARCHAR(64) | 业务动作标签 | `inventory.query` / `order.transfer.sales` / `order.transfer.purchase` |

#### 5.1 后台检索

```
GET /api/admin/api-call-logs?bizKey=SO-20260806-00001
GET /api/admin/api-call-logs?bizAction=order.transfer.sales&startTime=2026-08-06
GET /api/admin/api-call-logs?path=/api/orders/transfer&statusCode=200
```

#### 5.2 索引

- `idx_api_call_log_biz_key` (单列)
- `idx_api_call_log_biz_action (biz_action, started_at DESC)`
- `idx_api_call_log_path_time (path, started_at DESC)`


#### 5.3 inventory biz_key 取值优先级

`ApiCallLogFilter#deriveBizTags` 对 `GET /api/inventory` 的 `biz_key` 抽取规则，按以下顺序生效：

1. `warehouseId` + `productId` 两个 query 参数拼成 `warehouseId-productId`（如 `1-1`）；任一缺失时该位用 `*` 补齐。
2. 1 未提供时，回退到响应体 `data.list[0].id`。
3. 仍取不到时，回退到 `message` 前 32 字符。

早期版本直接走到第 3 步，导致 `biz_key` 出现 `OK` 之类噪音；v4.2.0 重打包已修复并部署测试环境。

#### 5.4 端到端验证记录（阿里云测试环境 43.128.145.141）

| 用例 | 路径 | 关键响应 | `api_call_log.biz_key` | `api_call_log.biz_action` |
|------|------|----------|------------------------|---------------------------|
| 库存查询 | `GET /api/inventory?warehouseId=1&productId=1&page=1&size=3` | HTTP 200，`data.total=7`，`data.page=1`，`data.size=3` | `1-1` | `inventory.query` |
| 销售订单传输 | `POST /api/orders/transfer` | `code=0`，`data.id=1220`，`data.code=SO-20260807-00002`，`data.status=DRAFT`，`data.amount=100.00` | `SO-20260807-00002` | `order.transfer.sales` |
| 采购订单传输 | `POST /api/purchase-orders/transfer` | `code=0`，`data.id=82`，`data.code=PO-20260807-00001`，`data.status=DRAFT`，`data.amount=80.00` | `PO-20260807-00001` | `order.transfer.purchase` |

---

### 6. 与 OpenAPI 对外接口的差异

| 维度 | 内部传输（本文档） | OpenAPI 对外（`docs/06_API设计/DMS对外开放接口文档.md`） |
|------|--------------------|--------------------------------------------------------|
| 鉴权 | JWT（登录 DMS）    | HMAC-SHA256（appKey + 签名）                            |
| 路径 | `/api/orders/transfer` 等 | `/open/api/sales-orders` 等                       |
| 适用 | 内部系统（厂家、经销商 BI/ERP） | 第三方平台（ERP/WMS/HR/UDI/CA） |
| 业务逻辑 | 复用现有 Service（带授权/促销/状态机） | 直接 SQL 写入（DRAFT 状态） |

---

### 7. 错误码速查

| HTTP 状态 | 业务 code | 含义 |
|-----------|-----------|------|
| 200 | 0 | 成功 |
| 200 | 40001 | 参数校验失败（缺失/类型错误） |
| 200 | 40002 | 缺少必要参数 |
| 200 | 40006 | 业务规则校验失败 |
| 200 | 40401 | 资源不存在（经销商/供应商/仓库/产品） |
| 200 | 50000 | 系统内部错误（兜底） |
| 401 | 40101 | 未登录或凭证失效 |
| 403 | 40301 | 无操作权限 |

---

### 8. 部署

- 后端：`mvn -o -DskipTests package` → `target/dms-backend.jar` → Docker 容器 `dms-test-backend`。
- 数据库迁移：Flyway 自动执行 `V60__api_call_log_transfer_fields.sql`（仅新增列与索引，不影响存量数据）。
- 阿里云测试环境（43.128.145.141）已就位。
- 阿里云生产环境已上线（v3.12.4，2026-08-16）：http://8.133.193.238/dms/ ，统一 80 端口 + `/dms/` 子路径，历史 8080/8081 端口拓扑已废弃。

#### 8.1 v4.2.0 实际部署记录（2026-08-07 00:03 UTC+8）

- 新 jar md5：`E4008E5DCA62267E4C062DB41E43B288`（115,388,111 字节）
- 部署流程：本地 `mvn package` → SFTP 上传 `/tmp/dms-backend-new.jar` → `docker cp` → `docker restart`
- 健康检查：`GET /actuator/health` 轮询 180s 内返回 `{"status":"UP"}`
- E2E 三接口（admin/Sh123456，default 租户）全部 200，详见 5.4。


---
