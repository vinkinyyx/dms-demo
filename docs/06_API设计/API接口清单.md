# REST API 接口清单

> 版本 **V3.0**（含 v2.0 + v3.0 新增 API）
> 风格：RESTful；协议 HTTPS；序列化 JSON；鉴权 Bearer JWT
> Base URL：`https://{host}/api`
> **阿里云演示地址**：`http://<YOUR_SERVER_IP>/api`
> 通用响应包裹：`{code, message, data, request_id}`
> 通用分页参数：`?page=1&size=20&sort=field,asc`
> 租户上下文：JWT payload 含 tenant_id；超管切租户可用 `X-Tenant-Id` header

**V3.0 新增 API 见文末附录。**


## 通用错误码

| Code | 含义 |
|---|---|
| 0 | 成功 |
| 40001 | 参数缺失 |
| 40002 | 参数格式错误 |
| 40003 | 业务校验失败 |
| 40006 | 附件病毒风险 |
| 40101 | 账号或密码错误 |
| 40102 | 账号已锁定 |
| 40103 | 租户不可用 |
| 40301 | 无权限 |
| 40401 | 资源不存在 |
| 40901 | 状态不允许当前操作 |
| 40902 | 并发冲突（version 不匹配）|
| 42901 | 请求过频 |
| 50000 | 服务器错误 |

---

## V1 决策变更概要

> 以下为 V1 阶段（D-24 ~ D-41 决策）对本接口清单的关键变更：
>
> - **删除** SSO 相关接口：`POST /auth/sso/verify` 从 V1 范围内移除（保留至 V2 评估）。
> - **新增** 微信扫码登录接口 4 个：`POST /auth/wechat/qrcode`、`POST /auth/wechat/callback`、`POST /auth/wechat/bind`、`POST /auth/wechat/unbind`。
> - **删除** 邮件/短信外部发送接口：`POST /integrations/mail/send`、`POST /integrations/sms/send` 不再作为独立外部接口暴露。
> - **新增** 群机器人 Webhook 推送：`POST /integrations/wechat-bot/push`（企微群机器人）、`POST /integrations/feishu-bot/push`（飞书群机器人），承接原邮件/短信的通知触达。
> - **促销 API 保留**，但 `promo_type` 参数在 V1 仅接受 `MOQ` 与 `FULL_REDUCTION`；`GIFT` / `BUNDLE` 请求会被拒绝并返回 `40003`。

---

## 1. 认证与账户

| Method | Path | 说明 |
|---|---|---|
| POST | /auth/login | 登录（返回 access/refresh Token）|
| POST | /auth/logout | 登出（吊销 Token）|
| POST | /auth/refresh | 刷新 access token |
| POST | /auth/forgot-password | 发送重置密码邮件 |
| POST | /auth/reset-password | 用 token 重置密码 |
| POST | /auth/change-password | 已登录用户改密（首登强制）|
| GET  | /auth/me | 获取当前用户信息（roles/permissions/tenant）|
| POST | /auth/wechat/qrcode | 生成微信登录二维码（返回 scene 与 qr_url）|
| POST | /auth/wechat/callback | 微信扫码回调（openid → user 匹配或跳绑定）|
| POST | /auth/wechat/bind | 绑定 DMS 账号（首次扫码后填账号密码完成绑定）|
| POST | /auth/wechat/unbind | 解绑微信 |

## 2. 工作台

| Method | Path | 说明 |
|---|---|---|
| GET | /home/dashboard | 首屏聚合数据 |
| GET | /home/todos | 待办列表 |
| GET | /home/announcements | 公告列表 |
| GET | /notifications?is_read= | 消息列表 |
| POST | /notifications/{id}/read | 标记已读 |
| POST | /notifications/read-all | 全部已读 |

## 3. 租户 & 系统参数

| Method | Path | 说明 |
|---|---|---|
| GET | /tenants | 租户列表（超管）|
| POST | /tenants | 新建租户 |
| PUT | /tenants/{id} | 修改租户 |
| POST | /tenants/{id}/modules | 启用/禁用模块 |
| GET | /system/settings | 参数列表 |
| PUT | /system/settings/{key} | 更新参数 |
| GET | /system/cache | 缓存状态 |
| POST | /system/cache/sync | 手工触发同步 |
| GET | /system/mock-toggle | Mock 开关 |
| PUT | /system/mock-toggle | 切换 Mock/真实 |

## 4. 用户 / 权限

| Method | Path | 说明 |
|---|---|---|
| GET | /users | 用户列表 |
| POST | /users | 新建用户 |
| GET | /users/{id} | 详情 |
| PUT | /users/{id} | 编辑 |
| POST | /users/{id}/unlock | 解锁 |
| POST | /users/{id}/reset-password | 重置密码（邮件下发）|
| POST | /users/import | 批量导入 |
| GET | /users/export | 异步导出 |
| GET | /orgs | 组织树 |
| POST | /orgs | 新建节点 |
| PUT | /orgs/{id} | 编辑 |
| GET | /resources | 资源树 |
| POST | /resources | 新建 |
| GET | /strategies | 策略列表 |
| POST | /strategies | 新建 |
| GET | /roles | 角色列表 |
| POST | /roles | 新建 |
| POST | /roles/{id}/strategies | 绑定策略 |
| POST | /users/{id}/roles | 分配角色 |
| POST | /users/{id}/data-scope | 设置数据权限 |
| GET | /audit-logs | 审计日志（含前值/后值下钻）|

## 5. 字典 / 流程 / 菜单

| Method | Path | 说明 |
|---|---|---|
| GET | /dicts/types | 字典类型列表 |
| GET | /dicts/{type_code}/items | 字典项 |
| POST | /dicts/{type_code}/items | 新增字典项 |
| GET | /workflows | 流程列表 |
| POST | /workflows | 新建流程 |
| PUT | /workflows/{id} | 编辑 |
| POST | /workflows/{id}/publish | 发布新版本 |
| GET | /menus | 菜单树 |
| POST | /menus | 新建菜单 |

## 6. 主数据

| Method | Path | 说明 |
|---|---|---|
| GET | /products | 产品列表（分页/筛选）|
| POST | /products | 新建产品 |
| PUT | /products/{id} | 编辑 |
| POST | /products/{id}/deactivate | 停用（引用检查）|
| POST | /products/import | 批量导入 |
| GET | /product-categories | 分类树 |
| POST | /product-categories | 新建 |
| GET | /price-lists | 价格列表 |
| POST | /price-lists | 新建价格 |
| GET | /hospitals | 终端列表 |
| POST | /hospitals | 新建 |
| GET | /regions | 区域树 |
| POST | /regions | 新建 |
| GET | /dealers | 经销商列表 |
| POST | /dealers | 新建（首次导入）|
| PUT | /dealers/{id} | 编辑 |
| GET | /dealers/{id} | 详情 |
| GET | /dealers/{id}/addresses | 收货地址 |
| POST | /dealers/{id}/addresses | 新增地址 |
| GET | /warehouses | 仓库列表 |
| POST | /warehouses | 新建仓库 |
| POST | /warehouses/{id}/deactivate | 停用 |

## 7. 合同

| Method | Path | 说明 |
|---|---|---|
| GET | /contract-applications | 合同申请列表 |
| POST | /contract-applications | 新建 |
| GET | /contract-applications/{id} | 详情（含变更对照）|
| PUT | /contract-applications/{id} | 编辑（草稿）|
| POST | /contract-applications/{id}/submit | 提交 |
| POST | /contract-applications/{id}/cancel | 撤回 |
| POST | /contract-applications/batch-extend | 批量延展 |
| POST | /contract-applications/batch-update | 批量更新 |
| GET | /contracts | 已生效合同 |
| GET | /contracts/{id} | 详情 |
| POST | /contracts/{id}/sign/dealer | 经销商签章 |
| POST | /contracts/{id}/sign/vendor | 厂商签章 |
| POST | /contracts/{id}/upload-scan | 上传纸质扫描件替代 |
| GET | /contracts/{id}/pdf | 下载 PDF |
| GET | /contract-templates | 模板列表 |
| POST | /contract-templates | 新建模板 |

## 8. 审批

| Method | Path | 说明 |
|---|---|---|
| GET | /approval-tasks | 我的待办 |
| GET | /approval-tasks/{id} | 详情 |
| POST | /approval-tasks/{id}/action | 通过/拒绝/转交/加签 body:{action, comment, next_assignee?} |
| GET | /open/approval?token=xxx | 邮件 Token 免登入口 |
| GET | /approval-history?ref_type=&ref_id= | 审批历史 |

## 9. 授权

| Method | Path | 说明 |
|---|---|---|
| GET | /authorizations | 授权列表 |
| POST | /authorizations/check | 校验（批量输入 lines，返回失败明细）|
| GET | /temp-authorizations | 临时授权列表 |
| POST | /temp-authorizations | 申请临时授权 |

## 10. 订单

| Method | Path | 说明 |
|---|---|---|
| GET | /orders | 列表（分页/筛选）|
| POST | /orders | 新建订单（触发促销引擎）|
| GET | /orders/{id} | 详情 |
| PUT | /orders/{id} | 编辑（草稿）|
| POST | /orders/{id}/submit | 提交 |
| POST | /orders/{id}/cancel | 撤销（草稿/未审批）|
| POST | /orders/{id}/split | 拆单（客服）|
| POST | /orders/{id}/reback | 退回修改（客服）|
| POST | /orders/import | 批量导入（Excel）|
| POST | /orders/export | 异步导出 |
| GET | /orders/{id}/status-history | 状态历史 |

## 11. 收货

| Method | Path | 说明 |
|---|---|---|
| GET | /receipts | 列表 |
| GET | /receipts/{id} | 详情 |
| POST | /receipts/{id}/confirm | 确认收货（可扫码）|
| POST | /receipts/{id}/cancel | 异常撤销 |

## 12. 库存 / 移库 / 借货 / 调整 / 盘点 / 流向

| Method | Path | 说明 |
|---|---|---|
| GET | /inventory | 明细查询 |
| GET | /inventory/summary | 汇总查询 |
| GET | /inventory/alerts | 临期/呆滞预警 |
| POST | /stock-moves | 新建移库 |
| POST | /stock-moves/{id}/cancel | 撤销（反向移库）|
| POST | /loans | 借货出库 |
| POST | /loans/{id}/confirm-receive | 借入方确认 |
| POST | /loans/{id}/return | 归还 |
| POST | /inventory-adjustments | 库存调整 |
| POST | /inventory-adjustments/{id}/submit | 提交（增加类走审批）|
| GET | /stocktakes | 盘点列表 |
| POST | /stocktakes/upload | 上传盘点表 |
| GET | /stocktakes/{id}/diff | 差异报告 |
| GET | /product-trace | 流向追踪 params: product_id, batch_no, serial_no |
| POST | /product-trace/report-udi | UDI 上报（Mock）|

## 13. 销售 & 分销

| Method | Path | 说明 |
|---|---|---|
| GET | /sales-outs | 销售出库列表 |
| POST | /sales-outs | 新建（报台）|
| POST | /sales-outs/{id}/red-cancel | 红字冲销 |
| POST | /distribution-shipments | 分销出库 |
| POST | /distribution-shipments/{id}/cancel | 撤销 |

## 14. 退换货 (RMA)

| Method | Path | 说明 |
|---|---|---|
| GET | /rma-authorizations | RMA 授权列表 |
| POST | /rma-authorizations | 新建授权 |
| POST | /rma-authorizations/{id}/deactivate | 停用 |
| GET | /rma-orders | 退换货单列表 |
| POST | /rma-orders | 新建 |
| POST | /rma-orders/{id}/submit | 提交 |
| POST | /rma-orders/{id}/complete | 厂商收货确认 |

## 15. 发票

| Method | Path | 说明 |
|---|---|---|
| GET | /purchase-invoices | 采购发票列表 |
| POST | /purchase-invoices | 上传 |
| POST | /purchase-invoices/batch | 批量上传（Excel + ZIP）|
| GET | /sales-invoices | 销售发票列表 |
| POST | /sales-invoices | 上传 |

## 16. 促销

> **V1 仅支持 promo_type = MOQ / FULL_REDUCTION，GIFT / BUNDLE 请求会被拒绝返回 40003**

| Method | Path | 说明 |
|---|---|---|
| GET | /promotions | 促销列表 |
| POST | /promotions | 新建（走审批）|
| GET | /promotions/{id} | 详情 |
| PUT | /promotions/{id} | 编辑（走审批）|
| POST | /promotions/{id}/pause | 暂停 |
| POST | /promotions/{id}/resume | 恢复 |
| POST | /promotions/{id}/copy | 复制新单 |
| POST | /promotions/preview | 预览（下单页面用）body: draft order → 返回命中规则 |

## 17. 报表与画像

| Method | Path | 说明 |
|---|---|---|
| POST | /reports/{type}/query | 通用查询（type ∈ contract/order/inventory/sales/authorization/loan/invoice/rebate/discount/return）|
| POST | /reports/{type}/export | 异步导出 |
| GET | /dashboard | 综合看板 |
| GET | /dealer-profile/{dealer_id} | 画像入口（默认基本信息）|
| GET | /dealer-profile/{dealer_id}/{tab} | tab ∈ basic/kpi/achievement/rebate/contracts/inventory |
| POST | /rebate/recalc | 手工触发返利重算 |

## 18. 异步任务 & 文件

| Method | Path | 说明 |
|---|---|---|
| GET | /jobs/{id} | 任务状态 |
| GET | /jobs/{id}/stream | SSE 进度流 |
| POST | /files | 上传文件 |
| GET | /files/{id} | 下载/预览 |

## 19. 外部接口（Mock 层）

| Method | Path | 说明 |
|---|---|---|
| POST | /integrations/erp/sync/products | ERP → DMS 产品同步（通用 REST，不绑定厂商）|
| POST | /integrations/erp/orders | 推送订单到 ERP（通用 REST，不绑定厂商）|
| POST | /integrations/wms/receive-callback | WMS 回执 |
| POST | /integrations/hr/sync | HR 同步 |
| POST | /integrations/sso/verify | SSO 验证 |
| POST | /integrations/ca/sign | 电子签章（Mock 按 e签宝 API 契约）|
| POST | /integrations/regulator/udi | UDI 上报 |
| POST | /integrations/wechat-bot/push | 企微群机器人推送 Webhook |
| POST | /integrations/feishu-bot/push | 飞书群机器人推送 Webhook |

## 20. H5 移动端专用（复用主 API，仅列差异）

- H5 与 PC 端 API 完全相同；
- 扫码类：H5 前端本地调用 `getUserMedia` 识别到条码/QR → 调用 `/inventory` 或 `/receipts/{id}/confirm` 或 `/sales-outs` 等已有接口；
- H5 登录接口同 `/auth/login`，前端参数 `device_type=h5`。

---

## 关键请求/响应示例

### POST /orders （新建订单）
Request：
```json
{
  "order_type": "PURCHASE",
  "dealer_id": 12,
  "ship_address_id": 34,
  "lines": [
    { "product_id": 100, "qty": 20 },
    { "product_id": 105, "qty": 10 }
  ],
  "remark": "急"
}
```
Response：
```json
{
  "code": 0, "message": "ok",
  "data": {
    "order_id": 1001, "code": "ORD-20260718-000123",
    "amount_incl_tax": 12800.00,
    "discount_amount": 500.00,
    "final_amount": 12300.00,
    "promotions_hit": [
      {"promotion_id":5,"rule_type":"FULL_REDUCTION","discount":500.00}
    ],
    "warnings": ["PROD-005 起订量 10，当前 8"]
  }
}
```

### POST /authorizations/check
```json
{
  "dealer_id": 12,
  "auth_type": "SALES_TO_HOSPITAL",
  "at_time": "2026-07-18T10:00:00+08:00",
  "lines": [
    {"product_id": 100, "terminal_id": 50},
    {"product_id": 101, "terminal_id": 51}
  ]
}
```
Response 授权失败：
```json
{
  "code": 40003, "message": "授权校验失败",
  "data": {
    "failed": [
      {"product_id":101,"terminal_id":51,"reason":"授权已过期"}
    ]
  }
}
```

### POST /promotions/preview
```json
{
  "dealer_id":12,
  "lines":[{"product_id":100,"qty":50}]
}
```
返回同 `/orders` 中的 `promotions_hit / discount_amount / warnings`。

### POST /auth/wechat/callback
Request：
```json
{"code":"WX_CODE_xxx","state":"random_state"}
```
Response 已绑定：
```json
{"code":0,"data":{"access_token":"...","refresh_token":"...","user":{"user_id":123,"name":"张三","tenant_id":1}}}
```
Response 未绑定：
```json
{"code":40103,"data":{"openid":"o_xxx","need_bind":true,"bind_token":"tmp_xxx"}}
```

### POST /approval-tasks/{id}/action
```json
{ "action": "PASS", "comment": "同意" }
```

---

## OpenAPI 拆分建议

按模块拆分为多个 `openapi/{module}.yaml`：
- `auth.yaml`, `home.yaml`, `tenant.yaml`, `user.yaml`
- `contract.yaml`, `authorization.yaml`, `order.yaml`
- `inventory.yaml`, `sales.yaml`, `invoice.yaml`, `rma.yaml`
- `promotion.yaml`, `report.yaml`, `integration.yaml`
- 通过 `$ref` 引用共享 schema（`components/schemas/{Order,Product,...}.yaml`）

—— END ——


---

## 附录 · V2.0 新增 API

### 合同签章
```
POST   /api/contracts/{id}/send-sign-code       发送短信 Token
POST   /api/contracts/{id}/sign                 校验 Token 并签章
GET    /api/contracts/{id}/print-view           打印视图（HTML，可 Ctrl+P）
POST   /api/contracts/{id}/archive-to-erp       归档到 ERP Mock
```

### 合同申请扩展
```
GET    /api/contract-applications/{id}/diff             变更前后对照
POST   /api/contract-applications/renew-from/{cid}      续约一键复制
POST   /api/contract-applications/batch-extend          批量延展
```

### UDI 追溯
```
GET    /api/traceability/by-serial?serialNo=X          按序列号追溯
GET    /api/traceability/by-batch?batchNo=X            按批次追溯
```

### 主数据引用检查
```
GET    /api/reference-check/{resource}/{id}    停用前查引用（product/dealer/hospital/warehouse）
```

### 订单批量与异步
```
POST   /api/orders/batch-import                批量导入
POST   /api/orders/export-async                异步导出（返回 taskId）
GET    /api/orders/export-tasks/{taskId}       查询任务状态
GET    /api/orders/export-tasks/{taskId}/download   下载 CSV
GET    /api/orders/export-tasks                任务列表
```

### 综合看板
```
GET    /api/dashboard/overview                 KPI + 分布 + Top + 库存告警
GET    /api/dashboard/inventory-stats          库存 3 卡（总量/临期/呆滞）
GET    /api/dashboard/todos                    首页待办列表
```

### 邮件 Token 审批
```
POST   /api/system-ops/approval-tokens/generate       生成 Token
GET    /api/system-ops/approval-tokens/{token}/approve 免登录审批
```

### 系统运维
```
POST   /api/system-ops/check-timeouts          手动触发超时检查
GET    /api/system-ops/cache/status            Redis 状态
POST   /api/system-ops/cache/flush             清缓存
POST   /api/system-ops/users/batch-import      批量导入用户
POST   /api/system-ops/invoices/batch-import   批量导入发票
GET    /api/system-ops/my-data-scope           查询当前用户数据权限
GET    /api/system-ops/rbac/matrix             RBAC 权限矩阵
GET    /api/system-ops/workflows               流程列表
GET    /api/system-ops/workflows/{id}/nodes    流程节点
GET    /api/system-ops/seed-status             SEED_ENABLED 状态
```

### 集成 Mock
```
GET    /api/integration/config                 集成配置列表
POST   /api/integration/config/{system}/mode   切换 mock/real
POST   /api/integration/erp/sync               ERP 同步
POST   /api/integration/wms/receive-confirm    WMS 收货回执
GET    /api/integration/hr/employees           HR 员工
POST   /api/integration/hr/sync                HR 同步
POST   /api/integration/udi/report             UDI 上报
```

### 促销审批
```
POST   /api/promotions/{id}/submit             提交促销
POST   /api/promotions/{id}/approve            审批通过
POST   /api/promotions/{id}/reject             驳回
```

### 返利与报表
```
GET    /api/rebates/calculate?dealerId=X       返利分段计算
GET    /api/reports/{reportKey}/export-csv     Excel 导出（5 类）
GET    /api/loans                              借货单列表
POST   /api/loans                              创建借货单
```

### 收货撤销
```
POST   /api/receipts/{id}/cancel               收货撤销
```

---

## 附录 · V3.0 新增 API

### 采购订单（完整状态机）
```
GET    /api/purchase-orders                     采购单列表（可过滤 status / supplierId）
GET    /api/purchase-orders/{id}                采购单详情（含明细 + allowedActions）
POST   /api/purchase-orders                     创建采购单
PUT    /api/purchase-orders/{id}                编辑（仅 DRAFT）
POST   /api/purchase-orders/{id}/submit         提交审批
POST   /api/purchase-orders/{id}/approve        审批通过
POST   /api/purchase-orders/{id}/reject         驳回
POST   /api/purchase-orders/{id}/cancel         取消
POST   /api/purchase-orders/{id}/receive        📦 收货入库（联动库存）
```

#### 收货入库请求示例
```json
POST /api/purchase-orders/123/receive
Body（可空，为空则整单收货）：
{
  "lines": [
    { "lineId": 1, "productId": 100, "qty": 30 }
  ]
}
```

#### 详情返回（含 allowedActions）
```json
{
  "code": 0,
  "data": {
    "id": 123,
    "code": "PO-1752834567890",
    "orderType": "NORMAL",
    "supplierId": 1,
    "warehouseId": 2,
    "status": "APPROVED",
    "lines": [
      {"seq":1, "productCode":"P001", "productName":"骨科螺钉", "qty":50, "receivedQty":0, "unitPrice":80, "subtotal":4000}
    ],
    "allowedActions": [
      {"key":"receive", "label":"📦 收货入库", "type":"success", "method":"POST", "path":"/receive"}
    ]
  }
}
```

### 销售订单元数据
```
GET    /api/orders/{id}/allowed-actions        按 ID 查允许操作
GET    /api/orders/actions-for-status?status=X 按状态查允许操作
```

### 低代码字段配置
```
GET    /api/form-configs/forms                 可配置的表单列表
GET    /api/form-configs/{formKey}             查询字段配置
POST   /api/form-configs/{formKey}/upsert      批量新增/更新字段
DELETE /api/form-configs/{id}                  删除字段配置
```

#### 字段配置返回示例
```json
[
  { "id":1, "formKey":"order", "fieldKey":"orderType", "fieldLabel":"订单类型",
    "fieldType":"select", "isNative":true, "required":true,
    "showInList":true, "showInForm":true, "showInDetail":true,
    "group":"订单信息", "sortOrder":10 }
]
```

### 数据字典 CRUD
```
GET    /api/dicts/types                        字典分类列表
POST   /api/dicts/types                        新增字典分类
GET    /api/dicts/{typeCode}/items             字典条目列表
POST   /api/dicts/{typeCode}/items             新增字典条目
PUT    /api/dicts/items/{id}                   更新条目
DELETE /api/dicts/items/{id}                   删除条目
```

### 库存实时查询
```
GET    /api/inventory-summary/by-product/{id}          产品实时库存
                                                       返回：totalQty/expiringQty/staleQty + 按仓库明细
GET    /api/inventory-summary/dealer-overview/{id}     经销商概览
                                                       返回：基本信息 + 本月订单 + 累计 + 授信额度 + 最近 5 笔
```


