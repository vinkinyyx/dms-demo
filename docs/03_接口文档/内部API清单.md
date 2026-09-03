# DMS 内部 API 清单（控制器→基路径对照）

> 版本：v4.6.1（Flyway V146）｜ 整理日期：2026-09-02
> 数据来源：对 `backend/src/main/java/com/dms/**/*Controller.java` 的全量扫描（118 个 `@RestController`/`@Controller` 文件，其中含 1 个 `@ControllerAdvice` 全局异常处理）。
> 维护方式：后端新增/调整控制器基路径时，同步更新本清单对应行；本清单只列"控制器 → 基路径/鉴权区"，字段级接口细节以 Swagger（`/actuator` 暴露后）与各 Controller 源码为准。

## 1. 鉴权分区

| 前缀 | 鉴权方式 | 过滤器 | 使用者 |
|---|---|---|---|
| `/api/**`（不含 `/api/admin/**`） | 业务端 JWT（租户 + 用户） | JwtAuthFilter | PC 工作台、移动 H5、供应商门户 |
| `/api/admin/**` | 平台后台 JWT（平台管理员） | AdminJwtFilter | 平台后台 admin-vue |
| `/open/api/**` | HMAC-SHA256 机器凭证（AppKey/AppSecret） | OpenApiAuthFilter | 外部 ERP / 经销商系统对接 |
| `/actuator/**`、静态资源 | 放行/独立管控 | — | 运维 |

统一响应壳：`ApiResponse{code, message, data}`，`code=0` 成功；业务错误码见 `GlobalExceptionHandler`（`common/` 包）。

## 2. 认证 / 注册 / 当前用户

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| AuthController | auth | `/api/auth` | 业务端登录/登出/改密（含 admin 账号） |
| CustomerRegisterController | auth | `/api/auth` | H5 登录附属端点（注册入口跳转） |
| CustomerRegistrationController | user.registration | `/api/customer-registrations` | 经销商准入自助注册（H5 `/dms/mobile/register`） |
| AdminAuthController | adminauth | `/api/admin/auth` | 平台后台登录/登出 |
| MyPermissionsController | rbac | `/api/me` | 当前用户权限码（`GET /api/me/permissions`，data 为字符串数组）、个人信息 |

## 3. 用户 / 权限 / 组织

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| UserController | user | `/api/users` | 租户内用户 CRUD、重置密码、启停用 |
| RbacController | rbac | `/api` | 角色/策略/资源管理（`/api/roles`、`/api/strategies`、`/api/resources` 等） |
| SalesOrgController | org | `/api/sales-org` | 销售组织 |
| SalesPositionController | org | `/api/sales-positions` | 销售岗位（岗位-用户/岗位-经销商绑定） |

## 4. 审批 / 授权

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| ApprovalController | approval | `/api/approval` | 审批实例查询、审批操作（同意/驳回/转办） |
| ApprovalTemplateController | approval | `/api/approval/templates` | 审批模板与节点配置 |
| ApprovalDelegationController | approval | `/api/approval/delegations` | 审批委托 |
| AuthorizationCheckController | authz | `/api/authorizations` | 授权校验（业务侧鉴权查询） |
| AuthorizationController | authz | 方法级全路径（见 §10） | 授权台账 CRUD、临时授权、导出 |

## 5. 租户 / 平台后台管理

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| TenantController | tenant | `/api/tenants` | 租户查询（业务端可见范围） |
| TenantFeatureController | tenant | `/api/tenant/features` | 租户功能开关（进销存等） |
| AdminTenantController | tenant.admin | `/api/admin/tenants` | 平台侧租户 CRUD、开户（TenantRoleProvisioner） |
| AdminTenantAdminController | tenant.admin | `/api/admin/tenant-admins` | 租户管理员账号管理 |
| RoleTemplateController | platform.rbac | `/api/admin/role-templates` | 平台角色模板 |
| PlatformDictController | platform.dict | `/api/admin/dicts` | 平台字典管理 |
| AdminMenuController | platform.config | `/api/admin/menus` | 平台菜单配置 |
| AdminUiConfigController | platform.config | `/api/admin`（方法级 `/api/admin/ui-configs` 等） | 平台页面/筛选配置 |
| AdminButtonConfigController | platform.config | `/api/admin`（方法级 `/api/admin/button-configs` 等） | 平台按钮码配置 |
| AdminMailConfigController | system | `/api/admin/mail-config` | 邮件配置 + 定时邮件开关（v4.6.1 `/switches`） |
| AdminApiLogController | platform.apilog | `/api/admin/logs` | API 调用日志（含请求/响应报文文件下载） |
| AdminPlatformAuditController | platform.audit | `/api/admin/logs` | 平台审计日志 |
| OpLogAdminController | operationlog | `/api/admin/op-logs` | 平台侧操作日志 |

## 6. 主数据

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| DealerController | masterdata | `/api/dealers` | 经销商 CRUD、引用检查 |
| DealerAddressController | masterdata | `/api/dealer-addresses` | 经销商收货地址 |
| DealerContactController | masterdata | `/api/dealer-contacts` | 经销商联系人（v4.x V124） |
| DealerGlobalDiscountController | masterdata | `/api/dealer-global-discounts` | 经销商全局折扣 |
| ProductController | masterdata | `/api/products` | 产品（器械）CRUD |
| ProductCategoryController | masterdata | `/api/product-categories` | 产品分类 |
| ProductLineController | masterdata | `/api/product-lines` | 产品线 |
| ProductPriceController | masterdata | `/api/product-prices` | 产品价格表 |
| ProductGlobalDiscountController | masterdata | `/api/product-global-discounts` | 产品全局折扣 |
| ProductBundleController | masterdata | `/api/product-bundles` | 产品组合包 |
| WarehouseController | masterdata | `/api/warehouses` | 仓库 |
| SupplierController | masterdata | `/api/suppliers` | 供应商 |
| HospitalController | masterdata | `/api/hospitals` | 医院（终端客户） |
| RegionController | masterdata | `/api/regions` | 区域 |
| ReferenceCheckController | masterdata | `/api/reference-check` | 删除前引用检查（被引用返回 40904） |

## 7. 订单 / 收发单据

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| OrderController | order | `/api/orders` | 销售订单（厂家视角）CRUD + 提交 |
| OrderMetaController | order | `/api/orders` | 订单元数据（单号/枚举/下拉） |
| OrderBatchController | order | `/api/orders` | 订单批量操作 |
| SalesOrderController | order | `/api/sales-orders` | 销售订单（经销商视角/协同） |
| PurchaseOrderController | order | `/api/purchase-orders` | 采购订单（经销商向厂家下单） |
| SalesReturnController | order | `/api/sales-returns` | 销退单 |
| PurchaseReturnController | order | `/api/purchase-returns` | 采购退货单 |
| SalesOutController | sales | `/api/sales-outs` | 销售出库单 |
| SalesOutOpsController | sales | `/api/sales-out-ops` | 出库操作（拣选/发货等） |
| DistributionShipmentController | sales | `/api/distribution-shipments` | 配送发货单 |
| ReceiptController | inventory | `/api/receipts` | 收货单 |

## 8. 库存

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| InventoryController | inventory | `/api/inventory` | 库存查询/台账 |
| ExpiryAlertController | inventory | `/api/inventory` | 近效期预警 |
| InventoryStatusController | inventory | `/api/inventory-status` | 库存状态（合格/不合格等） |
| InventorySummaryController | inventory | `/api/inventory-summary` | 库存汇总 |
| InventoryAdjustmentController | inventory | `/api/inventory-adjustments` | 库存调整单 |
| InventoryAdjOpsController | inventory | `/api/inventory-adj-ops` | 调整操作 |
| StockMoveController | inventory | `/api/stock-moves` | 移库单 |
| StocktakeController | inventory | `/api/stocktakes` | 盘点单 |
| TraceabilityController | inventory | `/api/traceability` | 序列号/批号追溯 |

## 9. RMA / 合同 / 发票 / 促销 / 代金券 / 寄售

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| RmaOrderController | rma | `/api/rma-orders` | 不良事件/RMA 单 |
| RmaOrderPortalController | rma | `/api/rma/orders` | RMA 门户端 |
| RmaAuthorizationController | rma | `/api/rma-authorizations` | RMA 授权 |
| ContractController | contract | `/api/contracts` | 合同申请/合同台账 |
| ContractPriceController | contract | `/api/contracts/{contractId}/prices` | 合同价格行 |
| ContractTemplateController | contract | `/api/contract-templates` | 合同模板 |
| SalesInvoiceController | invoice | `/api/sales-invoices` | 销售发票 |
| PurchaseInvoiceController | invoice | `/api/purchase-invoices` | 采购发票 |
| PromotionController | promotion | `/api/promotions` | 促销活动 |
| CustomerVoucherController | voucher | `/api/customer-vouchers` | 客户代金券模板/发放/核销 |
| V4Controller | v4 | `/api/v4` | v4 计价引擎（`/api/v4/calc/preview`） |
| ConsignmentController | consignment | `/api/consignment` | 寄售台账/补货/开票扣减 |
| DealerCreditController | consignment | `/api/dealer-credit` | 经销商资信 |

## 10. 方法级全路径端点（无类级 @RequestMapping 的控制器）

以下控制器未声明类级 `@RequestMapping`，每个方法自带完整路径：

**execution 执行层（单据详情/审批执行/操作日志兼容视图）**

| 控制器 | 端点 |
|---|---|
| BizDocListController | `GET /api/sales-outs`、`GET /api/receipts`（列表兼容别名） |
| BizDocDetailController | `GET /api/sales-outs/{id}/detail`（兼 `/api/sales-outs/{id}`）、`GET /api/receipts/{id}/detail`（兼 `/api/receipts/{id}`）、`GET /api/orders/{id}/detail`、`GET /api/purchase-orders/{id}/detail` |
| OrderApprovalExecutionController | `POST /api/orders-approval/{id}/approve`、`POST /api/purchase-orders-approval/{id}/approve`、`POST /api/sales-outs/{id}/execute`、`POST /api/sales-outs/{id}/cancel-draft`、`POST /api/receipts/{id}/execute`、`POST /api/receipts/{id}/cancel-draft` |
| ExecutionOperationLogController | `GET /api/operation-logs` |

**批次（收货/出库分批执行）**

| 控制器 | 端点 |
|---|---|
| ReceiptBatchController | `POST /api/receipts/{id}/batches`、`PUT /api/receipt-batches/{bid}`、`POST /api/receipt-batches/{bid}/confirm`、`/cancel`、`POST /api/receipts/{id}/cancel-remaining` |
| SalesOutBatchController | `POST /api/sales-outs/{id}/batches`、`PUT /api/sales-out-batches/{bid}`、`POST /api/sales-out-batches/{bid}/confirm`、`/cancel`、`POST /api/sales-outs/{id}/cancel-remaining` |
| InventoryListController | `GET /api/stock-moves`、`GET /api/inventory-adjustments`（列表兼容别名） |

**授权台账**

| 控制器 | 端点 |
|---|---|
| AuthorizationController | `GET/POST /api/authorizations`、`POST /api/authorizations/check`、`GET /api/authorizations/actions/export`、`DELETE /api/authorizations/{id}`、`POST /api/temp-authorizations` |
| AuthorizationController（v4.6.2 授权产品线化） | `GET/POST /api/authorizations/order-enforce`（授权-下单挂钩租户开关读写）、`GET /api/authorizations/terminals?regionId=&keyword=`（终端医院按区域/关键词批量选择）、`GET /api/authorizations/product-lines`（产品线选项）、`POST /api/authorizations/{id}/renew`（授权续约）、`POST /api/authorizations/{id}/terminate`（授权终止，AUTHORIZATION_TERMINATE 审批） |
| ContractController（v4.6.2） | `POST /api/contracts/{id}/terminate`（合同终止，CONTRACT_TERMINATE 审批；通过置 terminated、驳回恢复 effective） |

**促销预览/杂项补充**

| 控制器 | 端点 |
|---|---|
| PromotionPreviewController | `POST /api/promotions/preview` |
| MiscSupplementController | `POST /api/promotions/{id}/submit|approve|reject`、`GET /api/rebates/calculate`、`GET/POST /api/loans`、`GET /api/system-ops/seed-status`、`GET /api/reports/{reportKey}/export-csv` |

## 11. 报表 / 首页 / 通知

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| ReportController | report | `/api/reports` | 报表查询（进销存/订单追踪等，CTE 化 SQL） |
| BusinessReportController | report | `/api/reports` | 业务报表（orderTrace 等） |
| ReportSubscriptionController | report.subscription | `/api/report-subscriptions` | 报表订阅（每日 08:00 CSV 邮件） |
| DealerProfileController | report | `/api/dealer-profile` | 经销商画像 |
| DashboardController | report | `/api/dashboard` | 业务端首页看板 |
| DashboardController | system | `/api/dashboard` | 系统看板 |
| HomeController | home | `/api/home` | 首页聚合数据 |
| NotificationController | notification | `/api/notifications` | 站内通知 |
| EmailLogController | notification | `/api/email-logs` | 邮件发送日志 |

## 12. 系统配置 / UI 配置 / 日志 / 其他

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| SystemAdminController | system | `/api/system` | 系统参数 |
| SystemOpsController | system | `/api/system-ops` | 运维操作（种子/重建索引等） |
| MenuConfigController | system | `/api/menu-configs` | 租户菜单配置 |
| FormConfigController | system | `/api/form-configs` | 表单配置 |
| DictCrudController | system | `/api/dicts` | 字典类型/明细 CRUD |
| LookupController | system | `/api/lookups` | 通用下拉查找 |
| IntegrationController | integration | `/api/integration` | 集成参数（system_settings scope=system） |
| MenuReadController | platform.config | `/api/menus` | 业务端菜单读取 |
| ButtonConfigReadController | platform.config | `/api/button-configs` | 按钮码读取 |
| UiConfigReadController | platform.config | `/api`（方法级 `/api/ui-configs` 等） | 页面/筛选配置读取 |
| TenantUiConfigController | platform.config | `/api/tenant-ui` | 租户 UI 配置 |
| PlatformPageLayoutController | platform.config | `/api/ui/layout` | 页面布局 |
| OperationLogController | controller | `/api/operation-log` | 单据操作日志（`/list/{bizType}/{id}`） |
| OpLogQueryController | operationlog | `/api/operation-logs/fullchain` | 全链路操作日志 |
| ApiCallLogController | apilog | `/api/api-call-logs`、`/api/admin/api-call-logs` | API 调用日志（双区） |
| FileController | common | `/api/files` | MinIO 文件上传/下载 |
| AsyncTaskController | asynctask | `/api/async-tasks` | 异步任务状态 |
| SurgeryReportController | surgery | `/api/surgery-reports` | 手术跟台报告 |
| ProductMappingController | platform.mapping | `/api/product-mappings` | 跨租户物料对码 |
| MyDealerTenantController | platform.mapping | `/api/my-dealer-tenants` | 当前用户关联的经销商租户 |

## 13. 开放接口（/open/api，HMAC 机器凭证）

| 控制器 | 包 | 基路径 | 说明 |
|---|---|---|---|
| OpenOrderController | openapi | `/open/api` | 厂家 ERP 对接（订单/出库回传旧版） |
| OpenErpOutboundController | openapi | `/open/api/erp` | 厂家 ERP 出库回调（V96 erp_outbound_callbacks） |
| OpenCollabController | openapi | `/open/api/collab` | 平台外经销商报文协同（v4.5.5）：`POST /purchase-orders/submit`、`POST /purchase-returns/submit`；出站 SHIP_NOTICE / RED_SHIP_NOTICE webhook 由 DMS 主动推送 |

鉴权细节（四头 `X-App-Key/X-Timestamp/X-Nonce/X-Signature`，±5 分钟时钟窗）与报文格式见 `docs/03_接口文档/跨租户订单协同接口文档_v4.5.5.docx`。

## 14. 兼容别名层（CompatAliasController）

`compat/CompatAliasController` 无类级注解，为旧前端/历史集成提供路径别名，**新代码不要使用**，应调用 §2–§13 的正式端点：

- 审批旧路径：`GET/POST /api/approval-flows`、`GET /api/approval-instances[/{id}][/{id}/summary]`、`GET/POST /api/approval-delegates`、`GET /api/approval-monitors`
- 平台旧路径：`GET/POST/PUT/DELETE /api/admin/tenants`、`GET /api/admin/users`、`/api/admin/dict-types`（CRUD）、`/api/admin/dict-items`、`/api/admin/audit-logs`、`/api/admin/login-logs`、`/api/admin/tenant-dealer-bindings`
- 业务旧路径：`GET /api/login-logs`、`GET /api/dict-items`

## 15. 对外/专项接口文档索引

| 文档 | 覆盖范围 |
|---|---|
| `跨租户订单协同接口文档_v4.5.5.docx` | 平台外经销商 HMAC 报文接口（4 个） |
| `跨租户订单协同接口文档_v4.5.4.docx` | 同 SaaS 内跨租户协同（路径 A–D）的接口说明 |
| `cross-tenant-collab-api.md` | 同 SaaS 跨租户协同 API（v4.5.0–v4.5.4） |
| `openapi-erp.md` | 厂家 ERP 对接（/open/api/erp） |
