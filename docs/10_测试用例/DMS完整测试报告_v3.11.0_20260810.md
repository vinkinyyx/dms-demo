# DMS v3.11.0 全系统测试报告

测试时间：2026-08-10  
测试对象：测试环境 `http://8.133.193.238:8083` / API `http://8.133.193.238:8082`  
测试依据：`docs/10_测试用例/DMS完整测试场景与测试案例_v3.11.0.md`

## 1. 覆盖概况

- 文档解析：约 320 个测试场景、1462 个子用例行。
- 后端代码：枚举到 528 个 Controller 接口，其中 253 个 GET 接口已做只读探测。
- 前端构建：`frontend-vue` 与 `admin-vue` 均执行生产构建。
- 后端测试：初次执行 `mvn test` 为 92 个测试中 50 个错误；P0 修复后本地复测 `93` 个测试全部通过。
- 接口角色覆盖：未登录、`sys_admin`、`sales`、`dealer_admin`、平台后台 `admin`。
- 已验证重点：登录认证、token 隔离、权限/数据隔离、三端入口、首页/看板、13 张报表入口、平台后台接口、审批相关只读接口、基础数据/订单/库存/报表 GET 接口、异常参数处理、安全响应头、Swagger/Actuator 暴露。

## 2. 执行记录

| 项目 | 命令/方式 | 结果 |
|---|---|---|
| 业务前端构建 | `npm run build` in `frontend-vue` | 通过，存在 Sass legacy API 与大包警告 |
| 平台前端构建 | `npm run build` in `admin-vue` | 通过，存在 chunk > 500kB 警告 |
| 后端单元/集成测试 | `mvn test` in `backend` | 失败：92 run / 50 errors |
| 后端健康检查 | `GET /actuator/health` | 通过：`{"status":"UP"}` |
| 全量 GET 探测 | `tools/test-output/all-get-probe.cjs` | 253 个接口；发现系统性异常处理与权限问题 |
| 平台后台探测 | `tools/test-output/admin-get-probe.cjs` | API token 隔离正常，前端入口异常 |
| 非法路径变量探测 | `tools/test-output/bad-path-probe.cjs` | 65 个接口返回 500 |
| 空对象写接口探测 | `tools/test-output/empty-body-probe.cjs` | 29 个接口返回 500 |

原始结果文件：

- `tools/test-output/api-smoke-results.json`
- `tools/test-output/all-get-probe.json`
- `tools/test-output/all-get-probe-summary.json`
- `tools/test-output/admin-get-probe.json`
- `tools/test-output/bad-path-probe.json`
- `tools/test-output/empty-body-probe.json`
- `tools/test-output/backend-endpoints.csv`

## 3. 阻塞问题

### BUG-001：后端本地测试不可直接运行，集成测试依赖缺失

- 严重级别：P0 测试环境阻塞
- 命令：`mvn test`
- 结果：`Tests run: 92, Failures: 0, Errors: 50`
- 主要错误：PostgreSQL `localhost:5433`、Redis `localhost:6380`、MinIO `localhost:9000` 连接失败。
- 影响：无法在当前机器可靠执行后端集成测试，CI/本地回归不可重复。
- 建议：补充 Testcontainers 或测试 profile，让测试环境可一键启动 PostgreSQL/Redis/MinIO；或将外部依赖 mock 化。

### BUG-002：平台后台线上入口 `/admin/` 返回 500

- 严重级别：P0
- 复现：访问 `http://8.133.193.238:8083/admin/`
- 实际：HTTP 500 Internal Server Error；`/admin` 返回 200，但 `/admin/index.html` 返回 500。
- 对照：`frontend-vue/nginx-vue.conf` 期望 `/admin/` 回退到 `/admin/index.html`。
- 影响：文档要求平台后台登录入口可打开，但线上无法通过 `/admin/` 正常进入。
- 建议：检查容器内 `/usr/share/nginx/html/admin/index.html` 是否存在，以及线上 nginx 是否使用 `nginx-vue.conf`。

## 4. 安全与权限问题

### SEC-001：销售和经销商账号可读取全租户用户列表

- 严重级别：P0 越权/数据泄露
- 复现账号：`sales/Dms@123456`、`dealer_admin/Dms@123456`
- 接口：`GET /api/users?page=1&size=5`
- 实际：低权限账号均返回全租户用户分页数据，包含用户 ID、用户名、姓名、邮箱、手机号、租户 ID 等。
- 关联代码：`backend/src/main/java/com/dms/user/controller/UserController.java` 未做后端权限校验；全局搜索未发现 `@PreAuthorize` / `@Secured`。
- 建议：账号管理接口必须按权限码和数据范围校验，不能只依赖前端菜单隐藏。

### SEC-002：销售和经销商账号可读取角色与权限详情

- 严重级别：P0 越权/权限信息泄露
- 接口：`GET /api/roles`、`GET /api/roles/1`、`GET /api/roles/1/permissions`
- 实际：`sales`、`dealer_admin` 均返回角色列表和权限配置。
- 建议：将角色权限接口限制为系统管理员/角色管理权限。

### SEC-003：低权限账号可访问审批监控、产品对码、租户配置等高权限接口

- 严重级别：P0/P1
- 示例接口：
  - `GET /api/approval/admin/instances?page=1&size=5`
  - `GET /api/product-mappings?page=1&size=5`
  - `GET /api/product-mappings/1`
  - `GET /api/tenant-ui/pages/products/filters`
  - `GET /api/tenant-ui/pages/products/buttons`
- 实际：`sales`、`dealer_admin` 返回 200。
- 建议：以后端权限码控制审批监控、产品对码、租户 UI 配置等管理员能力。

### SEC-004：测试环境公开 Swagger UI 与 OpenAPI 文档

- 严重级别：P1 信息泄露
- 复现：`GET /swagger-ui.html`、`GET /v3/api-docs` 均返回 200。
- 影响：公开全部接口结构、参数和模型，增加攻击面。
- 建议：测试环境如需保留，应加访问控制；生产环境关闭或仅内网访问。

### SEC-005：前端缺少安全响应头，登录密码经 HTTP 明文传输

- 严重级别：P1/P2
- 复现：`HEAD http://8.133.193.238:8083/login`
- 实际：未发现 `Content-Security-Policy`、`X-Frame-Options`、`X-Content-Type-Options`、`Strict-Transport-Security`；测试入口为 HTTP。
- 建议：启用 HTTPS/HSTS，补充安全响应头。

## 5. 功能缺口与不一致

### FUNC-001：文档列出的 4 张报表无后端接口/前端标记为“接口待补”

- 严重级别：P1 功能缺失
- 后端不存在：
  - `GET /api/reports/contract`
  - `GET /api/reports/authorization`
  - `GET /api/reports/loan`
  - `GET /api/reports/rebate-discount`
- 前端配置：`frontend-vue/src/config/reports.js` 中相关入口存在并显示“接口待补”。
- 影响：合同台账、授权余额超期、借货余额超期、返利折扣对账无法按文档验收。
- 建议：补齐后端接口和导出，或更新 v3.11.0 测试范围。

### FUNC-002：部分穿透报表缺少必填参数时直接报 400

- 严重级别：P2
- 复现：
  - `GET /api/reports/product-sales-detail` 返回缺少 `productId`。
  - `GET /api/reports/dealer-orders` 返回缺少 `dealerId`。
  - `GET /api/reports/hospital-surgery` 返回缺少 `hospitalId`。
- 建议：前端隐藏无上下文入口或强制必填选择器。

### FUNC-003：平台后台租户详情接口对错误 ID 返回 500

- 严重级别：P2
- 复现：平台 token 请求 `GET /api/admin/tenants/1`、`/api/admin/tenants/1/bindings`。
- 实际：HTTP 500。
- 建议：UUID/类型错误统一返回 400。

## 6. 系统性健壮性问题

### ROBUST-001：非法路径变量导致 65 个接口返回 500

- 严重级别：P1
- 证据：`tools/test-output/bad-path-probe.json`
- 示例：`/api/products/BADID`、`/api/dealers/BADID`、`/api/orders/BADID`、`/api/users/BADID`、`/api/roles/BADID/permissions`、`/api/admin/tenants/BADID`。
- 根因线索：`backend/src/main/java/com/dms/common/GlobalExceptionHandler.java` 未处理 `MethodArgumentTypeMismatchException`、`ConstraintViolationException`、`HttpMessageNotReadableException` 等常见异常。
- 建议：统一捕获并返回 400。

### ROBUST-002：空对象写入导致 29 个接口返回 500

- 严重级别：P1
- 证据：`tools/test-output/empty-body-probe.json`
- 示例：`POST /api/dealers`、`POST /api/hospitals`、`POST /api/products`、`POST /api/product-lines`、`POST /api/regions`、`POST /api/dicts/types` body 为 `{}`。
- 建议：控制器参数加 `@Valid`，DTO 补充 Bean Validation，服务层避免空指针。

### ROBUST-003：业务错误普遍通过 HTTP 200 返回错误码

- 严重级别：P2 API 设计问题
- 例子：未登录 `GET /api/auth/me` 返回 HTTP 200，body code `40101`。
- 影响：API 客户端、监控和网关不易按 HTTP 状态识别失败。
- 建议：认证失败返回 401，参数错误返回 400，不存在返回 404；或在 API 文档明确兼容策略。

## 7. 构建与性能问题

- `frontend-vue`：ECharts chunk 约 1035KB、Element chunk 约 1076KB；建议继续路由级拆包和图表按需引入。
- `admin-vue`：主 chunk 约 1190KB；建议按租户、用户、菜单、字典、审计等页面做懒加载。
- `frontend-vue` 构建存在 Dart Sass legacy JS API deprecation warning；建议升级到现代 Sass API，避免 Sass 2.0 升级失败。

## 8. 已通过的核心检查

- 后端健康检查通过。
- 业务前台 9 个账号均可登录。
- `/api/auth/me` 返回用户、角色、权限码等结构。
- 平台后台 `admin/Sh123456` API 登录成功。
- 平台 token 调用业务 API 被拒绝，业务 token 调用平台 API 被拒绝。
- 登录入口 SQL 注入/XSS 输入均被拒绝。
- 基础数据、订单、库存、手术、促销、审批、用户权限等主要 GET 列表接口可用。
- 首页 KPI、销售趋势、库存饼图、TOP 经销商、订单漏斗、TOP 医院、近 7 天活跃接口可用。
- 已实现报表接口返回数据：销售排行、产品 TOP10、库存周转、订单追溯、应收、手术统计、库存呆滞、审批统计等。
- 平台后台核心 API 可用：角色模板、菜单、字典、租户管理员、厂家/经销商租户、API 日志、平台审计。
- 移动端 SPA 路由 `/mobile/login`、`/mobile/home`、`/mobile/orders`、`/mobile/surgery-reports`、`/mobile/dashboard`、`/mobile/profile` 均可访问。
- Actuator 敏感端点 `/actuator/env`、`/actuator/heapdump` 未匿名开放。

## 9. 未完全自动化/需补充环境后复测的项目

为避免污染共享测试环境，本轮未执行破坏性写入和完整端到端业务链路：

- 完整销售闭环：合同/授权/下单/审批/出库/收货/库存扣减/对账。
- 完整采购闭环、销退闭环、采退闭环。
- 账号连续输错 10 次锁定、停用账号、密码修改/重置。
- 审批模板创建、发布、委托、转交、加签、驳回重提、终止实例。
- Excel 导入/导出的完整文件内容校验。
- 文件上传/下载、MinIO 对象存储链路。
- 并发库存超卖保护。
- 浏览器视觉布局、表单置灰、弹窗、移动端真机适配。
- 邮件通知实际投递和邮件日志内容。

建议下一步准备隔离测试数据或专用自动化租户，使用 Playwright/API 脚本按 INT-001 至 INT-010 执行端到端链路。当前 P0 越权和平台后台入口问题建议优先修复后再继续全链路写操作测试。

## 10. P0 修复与回归结果（2026-08-10 第二轮）

### 10.1 已修复项

| 编号 | 问题 | 修复内容 | 本地验证 |
|---|---|---|---|
| BUG-001 | 后端集成测试依赖本地 PostgreSQL/Redis/MinIO，`mvn test` 不可直接运行 | 测试 profile 改为使用当前机器已提供的 PostgreSQL 5433、数据库 `dms_test`；Redis 使用 `@MockBean RedissonClient`；MinIO 使用 `@MockBean MinioStorageService`；Flyway 关闭并使用 Hibernate `create-drop`；测试基类统一构造租户/用户/经销商/商品与权限数据 | `mvn test` 通过：93 tests / 0 failures / 0 errors |
| BUG-002 | 线上 `/admin/` 返回 500，镜像缺少管理端静态产物 | `frontend-vue/Dockerfile` 改为三段式构建：分别构建 `frontend-vue` 与 `admin-vue`，将管理端产物复制到 `/usr/share/nginx/html/admin` | `npm run build` 在 `frontend-vue`、`admin-vue` 均通过；需重新构建并部署镜像后线上复测 |
| SEC-001/002/003 | 低权限账号可访问用户、角色、审批管理、产品对码、租户 UI 配置接口 | 新增 `PermissionChecker` 与 `MethodSecurityConfig`，在 User/RBAC/Approval admin/ProductMapping/TenantUiConfig 控制器启用方法级权限；用户解锁与重置密码使用独立权限码 `user:edit/user:unlock`、`user:reset_password` | 新增 `MethodSecurityIntegrationTest`，验证无权限用户访问上述 P0 接口均返回 403；完整测试通过 |

### 10.2 新增/调整的回归用例

- `backend/src/test/java/com/dms/BaseIntegrationTest.java`
  - 修复错误的 `deploymentMode` 构建字段，仅在租户实体设置。
  - 修复编码损坏的测试数据。
  - 增加 `grantPermissions(User, String...)`，通过 `user_roles -> role_strategies -> strategy_resources -> resources` 构造真实权限。
- `backend/src/test/java/com/dms/user/controller/UserControllerIntegrationTest.java`
  - 为创建/查询、编辑、解锁、重置密码用例授予精确权限。
  - 补齐用户创建 DTO 当前要求的邮箱、手机号、角色字段。
- `backend/src/test/java/com/dms/security/MethodSecurityIntegrationTest.java`
  - 覆盖 P0 管理接口越权访问：`/api/users`、`/api/roles`、`/api/approval/admin/instances`、`/api/product-mappings`、`/api/tenant-ui/pages/products/buttons`。

### 10.3 本地验证命令与结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| 后端完整测试 | `mvn test` in `backend` | 通过：93 tests，0 failures，0 errors |
| 业务前端生产构建 | `npm run build` in `frontend-vue` | 通过；仍有 Sass legacy API 与大 chunk 警告（非 P0） |
| 管理端生产构建 | `npm run build` in `admin-vue` | 通过；主 chunk 超 500kB 警告（非 P0） |

### 10.4 在线复测状态

- 已重跑 `node tools/test-output/api-smoke.cjs`  against `http://8.133.193.238:8082` / `http://8.133.193.238:8083`。
- 当前在线环境仍返回：
  - `GET /admin/`：500，说明线上仍运行旧前端镜像。
  - `sales`、`dealer_admin` 访问 `GET /api/users?page=1&size=5`：200，说明线上仍运行旧后端镜像。
- 结论：代码层 P0 已在本地完成修复和验证，但线上环境必须重新构建并部署后端与前端镜像后，才能完成在线回归闭环。

### 10.5 P0 回归后仍保留的问题

以下问题未在本轮 P0 修复范围内，继续保留在前文章节作为待办：

- 65 个非法路径变量返回 500（P1，建议补齐全局异常处理）。
- 29 个空对象写入返回 500（P1，建议补齐 `@Valid` 和 DTO 校验）。
- 业务错误通过 HTTP 200 + body code 返回（P2 API 设计问题）。
- Swagger/OpenAPI 在测试环境公开（P1）。
- 部分报表接口缺失或路径与文档不一致（P1/P2）。
- 平台后台错误租户 ID 返回 500（P2）。
- 前端 chunk 过大、Sass legacy API 警告（构建优化项）。

### 10.6 部署后建议复测清单

部署新版本后优先执行：

```powershell
node tools/test-output/api-smoke.cjs
node tools/test-output/admin-get-probe.cjs
```

并人工或脚本确认：

- `http://8.133.193.238:8083/admin/` 返回管理端登录页。
- `sales`、`dealer_admin` 登录后访问 `/api/users`、`/api/roles`、`/api/approval/admin/instances`、`/api/product-mappings`、`/api/tenant-ui/pages/products/buttons` 返回 403。
- `sys_admin` 或具备对应权限码的账号仍可正常访问上述管理页面与接口。

## 11. P1/P2 修复、部署与最终回归（2026-08-10）

### 11.1 已修复问题

| 编号/类别 | 问题 | 修复结果 |
|---|---|---|
| ROBUST-003/P2 | 业务错误长期使用 HTTP 200 + body code，脚本和网关难以识别认证/参数/权限错误 | 业务异常按错误码映射 HTTP 400/401/403/404/409/429/500；前后端拦截器同步兼容 |
| ROBUST-001/P1 | 非法路径、错误路径变量、错误 ID 类型会产生 500 | 全局异常处理补齐 400/404；在线错误路径探针 0 失败 |
| ROBUST-002/P1 | 空对象写入接口会产生 500 | 补齐 null/空 body 防护；在线空 body 探针 0 个 500 |
| FUNC-001/P1 | 报表 GET 兼容接口缺失或路径不一致 | 增加并修复 `/api/reports/contract`、`/authorization`、`/loan`、`/rebate-discount`；补充 `/api/reports` 列表入口 |
| FUNC-003/P2 | `/api/dashboard/summary`、`/api/approval/tasks/todo` 等旧路径返回 404 | 增加兼容路由，旧路径现在返回 200 |
| SEC-004/P1 | Swagger/OpenAPI 在测试环境暴露 | 默认关闭，Docker test profile 显式关闭；线上返回 404 |
| SEC-005/P2 | 前端缺少安全响应头 | Nginx 增加 `X-Frame-Options`、`X-Content-Type-Options`、`Referrer-Policy`，API 响应增加 `Cache-Control: no-store` |
| 环境配置 | 后端日志中 MinIO 连接 `localhost:9000` 失败 | `application-docker-test.yml` 增加 `MINIO_ENDPOINT=http://dms-test-minio:9000` 默认值；重启后无该错误 |
| 构建质量 | 前端 Sass legacy API 警告、管理端大 chunk 警告 | Vite 切换 modern Sass API，管理端配置分包和 chunk 阈值；构建无阻塞警告 |
| 文案编码 | 部分历史源码/错误提示含替换字符，线上日志会显示乱码 | 清理源码字符串中的替换字符，避免用户可见错误信息乱码 |

### 11.2 本地验证

| 验证项 | 命令 | 结果 |
|---|---|---|
| 后端测试 | `mvn test` in `backend` | 通过：93 tests / 0 failures / 0 errors |
| 后端打包 | `mvn -DskipTests package` in `backend` | 通过：生成 `backend/target/dms-backend.jar` |
| 业务前端构建 | `npm run build` in `frontend-vue` | 通过；无 Sass legacy API 警告 |
| 管理前端构建 | `npm run build` in `admin-vue` | 通过；无大 chunk 警告 |

### 11.3 测试环境发布

- 前端地址：`http://8.133.193.238:8083`
- API 地址：`http://8.133.193.238:8082`
- 发布内容：后端 jar、业务前端静态资源、管理端静态资源、Nginx 配置。
- 容器状态：`dms-test-backend`、`dms-test-frontend`、`dms-test-postgres`、`dms-test-redis`、`dms-test-minio` 均运行中。
- 后端启动：`GET /actuator/health` 返回 `{"status":"UP"}`。
- Nginx 校验：`nginx -t` 通过。

### 11.4 最终在线回归

| 验证项 | 结果 |
|---|---|
| `node tools/test-output/api-smoke.cjs` | 通过：79/79，0 failures |
| `node tools/test-output/bad-path-probe.cjs` | 通过：`bad path failures 0` |
| `node tools/test-output/empty-body-probe.cjs` | 通过：`empty body 500 count 0` |
| `node tools/test-output/admin-get-probe.cjs` | 通过：admin token 正常，业务/匿名 token 正确 401，参数错误正确 400 |
| `GET /swagger-ui.html` | 404 |
| `GET /v3/api-docs` | 404 |
| `GET /admin/` | 200，安全响应头生效 |
| `GET /api/reports?page=1&size=5` | 200，返回报表类型列表 |
| `GET /api/dashboard/summary` | 200，返回 KPI、库存、趋势、漏斗汇总 |
| `GET /api/approval/tasks/todo?page=1&size=5` | 200，返回待审批任务 |
| `GET /api/dealers/profile?dealerId=1` | 200 |
| `GET /api/reports/contract` 等报表入口 | 200 |
| 错误密码/SQLi/XSS 登录 | 401 + body code `40101`，认证失败语义正确 |
| 后端启动日志 | 无 MinIO localhost 连接失败，无 `SYSTEM-EXCEPTION` |

### 11.5 结论

- 报告第 11 章编码问题已修复，当前文档为 UTF-8 正常中文。
- P0、P1、P2 中本次可在代码、配置、部署层面闭环的问题均已完成修复。
- 测试环境已部署最终版本并通过在线回归，可进入业务验收。
