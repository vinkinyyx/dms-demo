# API 设计

**版本**：v1.0  
**日期**：2026-08-02

---

## 1. 接口分层

| 层级 | 前缀 | 认证 | 说明 |
|---|---|---|---|
| 平台后台 | `/api/admin/**` | 后台 token | 平台管理员使用 |
| 业务前台 | `/api/**` | 业务 token | 厂家和经销商租户使用 |
| 对外开放接口 | `/api/open/**` | AppKey/签名 | 后续外部系统接入 |

平台后台和业务前台 token 不能互换。平台接口统一经过后台鉴权过滤器；业务接口统一经过租户鉴权和数据权限过滤器。

---

## 2. 平台后台认证

### 登录

`POST /api/admin/auth/login`

请求：

```json
{
  "username": "admin",
  "password": "string",
  "captcha": "string"
}
```

响应：

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 28800,
  "user": {
    "id": 1,
    "username": "admin",
    "name": "平台管理员"
  }
}
```

### 退出

`POST /api/admin/auth/logout`

### 当前用户

`GET /api/admin/auth/me`

### 修改密码

`POST /api/admin/auth/change-password`

---

## 3. 租户管理 API

### 厂家租户

- `GET /api/admin/tenants/manufacturers`：厂家租户分页列表。
- `POST /api/admin/tenants/manufacturers`：创建厂家租户。
- `GET /api/admin/tenants/manufacturers/{id}`：厂家租户详情。
- `POST /api/admin/tenants/{id}/enable`：启用。
- `POST /api/admin/tenants/{id}/disable`：停用。

创建厂家请求：

```json
{
  "code": "MFR_A",
  "name": "厂家A",
  "contactName": "张三",
  "contactPhone": "13800000000",
  "adminUsername": "mfr_admin",
  "adminPassword": "初始化密码",
  "adminName": "厂家管理员"
}
```

### 经销商租户

- `GET /api/admin/tenants/dealers`：经销商租户分页列表。
- `POST /api/admin/tenants/dealers`：创建经销商租户并绑定 dealer。
- `GET /api/admin/tenants/dealers/{id}`：详情。
- `GET /api/admin/tenants/{id}/bindings`：绑定信息。
- `POST /api/admin/tenants/{id}/enable`：启用。
- `POST /api/admin/tenants/{id}/disable`：停用。

创建经销商请求：

```json
{
  "manufacturerTenantId": "uuid",
  "dealerId": 1001,
  "code": "DEALER_A",
  "name": "经销商A",
  "contactName": "李四",
  "contactPhone": "13900000000",
  "adminUsername": "dealer_admin",
  "adminPassword": "初始化密码",
  "adminName": "经销商管理员"
}
```

后端必须校验 dealer 属于该厂家且未被启用中租户绑定。

---

## 4. 租户管理员 API

- `POST /api/admin/tenant-admins`：创建租户管理员。
- `POST /api/admin/tenant-admins/{id}/disable`：停用租户管理员。
- `POST /api/admin/tenant-admins/{id}/reset-password`：重置密码。

每租户只能有一个启用中的租户管理员。重置密码后必须强制修改密码。

---

## 5. 默认角色与权限模板 API

- `GET /api/admin/role-templates`
- `POST /api/admin/role-templates`
- `PUT /api/admin/role-templates/{id}`
- `GET /api/admin/role-templates/{id}/permissions`
- `PUT /api/admin/role-templates/{id}/permissions`
- `GET /api/admin/resources?tenantType=MANUFACTURER`

默认角色模板字段：

- 编码
- 名称
- 租户类型
- 数据权限类型
- 菜单/功能权限点
- 状态

租户开通时从模板复制到租户内 `roles` 和相关权限表。

---

## 6. 菜单和 UI 配置 API

### 平台菜单

- `GET /api/admin/menus`
- `POST /api/admin/menus`
- `PUT /api/admin/menus/{id}`
- `POST /api/admin/menus/{id}/enable`
- `POST /api/admin/menus/{id}/disable`

### 页面字段配置

- `GET /api/admin/page-configs?pageKey=&tenantType=`
- `PUT /api/admin/page-configs`
- `POST /api/admin/page-configs/refresh-cache`

### 筛选配置

- `GET /api/admin/filter-configs?pageKey=&tenantType=`
- `PUT /api/admin/filter-configs`

### 前台读取

- `GET /api/menus`
- `GET /api/ui-configs/pages/{pageKey}`
- `GET /api/filter-configs/pages/{pageKey}`

前台接口只能读取当前租户类型对应的启用配置。

---

## 7. 字典 API

平台后台：

- `GET /api/admin/dicts/types`
- `POST /api/admin/dicts/types`
- `PUT /api/admin/dicts/types/{id}`
- `GET /api/admin/dicts/types/{code}/items`
- `POST /api/admin/dicts/types/{code}/items`
- `PUT /api/admin/dicts/items/{id}`
- `POST /api/admin/dicts/items/{id}/enable`
- `POST /api/admin/dicts/items/{id}/disable`
- `POST /api/admin/dicts/refresh-cache`

业务前台：

- `GET /api/dicts/types`
- `GET /api/dicts/types/{code}/items`

业务前台只读，不提供新增、修改、删除。

---

## 8. 产品对码 API

产品对码属于厂家租户前台接口：

- `GET /api/product-mappings`：分页查询。
- `GET /api/product-mappings/{id}`：详情。
- `POST /api/product-mappings`：手工新增。
- `PUT /api/product-mappings/{id}`：编辑。
- `POST /api/product-mappings/{id}/enable`：启用。
- `POST /api/product-mappings/{id}/disable`：停用。
- `GET /api/product-mappings/template`：下载模板。
- `POST /api/product-mappings/import/preview`：上传并预览。
- `POST /api/product-mappings/import/confirm`：确认导入。
- `GET /api/product-mappings/import-batches/{id}/errors`：下载错误报告。
- `GET /api/my-dealer-tenants`：厂家查看归属自己的经销商租户只读列表。

所有接口必须强制：

- 当前租户类型为厂家。
- 经销商租户 `owner_manufacturer_id = currentTenantId`。
- 厂家产品属于当前租户。
- 经销商产品属于所选经销商租户。

---

## 9. 日志 API

平台后台：

- `GET /api/admin/logs/operations`
- `GET /api/admin/logs/logins`
- `GET /api/admin/logs/api`
- `GET /api/admin/logs/exceptions`
- `GET /api/admin/logs/slow`
- `GET /api/admin/logs/platform-audits`
- `GET /api/admin/logs/api/{id}/request-file`
- `GET /api/admin/logs/api/{id}/response-file`

查询参数：

- `tenantId`
- `ownerManufacturerId`
- `userId`
- `path`
- `method`
- `statusCode`
- `success`
- `requestId`
- `traceId`
- `slow`
- `startTime`
- `endTime`
- `page`
- `size`

日志文件下载必须鉴权并记录下载审计。

---

## 10. 租户内用户和角色 API

业务前台保留：

- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{id}`
- `POST /api/users/{id}/disable`
- `POST /api/users/{id}/reset-password`
- `GET /api/roles`
- `POST /api/roles`
- `PUT /api/roles/{id}`
- `POST /api/roles/{id}/permissions`
- `GET /api/roles/{id}/permissions`

这些接口只能由租户管理员或有权限的用户访问，且只能操作当前租户数据。

---

## 11. 统一响应和错误码

统一使用现有响应结构。新增建议错误码：

| 错误码 | HTTP 状态 | 说明 |
|---|---|---|
| `TENANT_DISABLED` | 403 | 租户已停用 |
| `TENANT_NOT_FOUND` | 404 | 租户不存在 |
| `DEALER_ALREADY_BOUND` | 409 | dealer 已绑定 |
| `TENANT_ADMIN_EXISTS` | 409 | 租户管理员已存在 |
| `PRODUCT_MAPPING_CONFLICT` | 409 | 产品对码冲突 |
| `INVALID_MANUFACTURER_SCOPE` | 403 | 厂家越权访问其他厂家数据 |
| `PLATFORM_AUTH_REQUIRED` | 401 | 需要平台后台登录 |
| `UI_CONFIG_INVALID` | 400 | 页面配置不合法 |
