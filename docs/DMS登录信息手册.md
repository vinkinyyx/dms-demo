# DMS 登录信息手册

**更新时间**: 2026-08-15
**当前版本**: v3.12.4
**适用范围**: 测试环境（43.128.145.141；旧测试 43.128.145.141/8082）与生产环境（8081/8080）

---

## 1. 环境总览

| 环境 | 前端入口 | 后端 API | 数据库 | 用途 |
|---|---|---|---|---|
| 生产 | `http://8.133.193.238/dms/` | Nginx 同源 `/api`、`/auth`、`/actuator` 反代后端容器 | `dms` | 正式生产环境（v3.12.4，Docker Compose 部署，2026-08-16 上线） |
| 新测试 | `http://43.128.145.141` | Nginx 同源 `/api` 反代内网 `8080` | `dms_test` | 最新 DMS 测试环境，Docker Compose 部署 |
| 旧测试 | `http://43.128.145.141` | `http://43.128.145.141` | `dms_test` | 历史测试环境 |

测试库已迁移到 Flyway V77。v3.11.0 合同模块重构：原「合同申请+合同」合并为「合同工作台」，新增「合同模板」菜单。测试环境与生产环境均支持业务前台、移动端 H5、平台后台三种登录方式。

---

## 2. 登录入口

### 2.1 生产环境（v3.12.4，2026-08-16 上线）

| 入口 | 地址 | 说明 |
|---|---|---|
| 业务前台/PC 工作台 | http://8.133.193.238/dms/ | 厂家/经销商租户用户登录与工作台 |
| 移动端 H5 | http://8.133.193.238/dms/mobile/login | 手机浏览器访问，订单/报台/审批/我的 |
| 平台后台 | http://8.133.193.238/dms/admin/ | 平台管理员登录入口 |
| 后端健康检查 | http://8.133.193.238/actuator/health | 返回 `{"status":"UP"}` 表示后端正常 |
| 产品宣传手册 | http://8.133.193.238/ | 根路径为宣传手册站，与 DMS 并存 |

**生产登录账号**：
- 业务前台（租户 `default`）：`admin` / `Sh123456`（厂商超管）；演示账号 `mfr_a_admin` / `Sh123456`（租户 `MFR_A`）
- 平台后台：`admin` / `Sh123456`

**生产服务器与部署信息**：
- SSH：`ssh root@8.133.193.238`，密码 `Welcomeyyx0616`
- 部署目录：`/opt/dms/prod`（`docker-compose.yml` + `backend/app.jar` + `frontend/`）
- 容器：`dms-prod-backend`、`dms-prod-postgres`、`dms-prod-redis`、`dms-prod-minio`
- 统一入口：宿主机已有 `webgate`（nginx:80）容器，DMS 挂在 `/dms/`，API 走根路径 `/api`、`/auth`、`/actuator`
- 配置文件：`/opt/webgate/nginx.conf`（DMS 静态文件挂载 `/opt/dms/prod/frontend:/usr/share/nginx/dms:ro`，并加入 `dms-prod` 网络反代后端）
- 数据库/缓存：容器内网络，不对外暴露端口；后端仅映射 `127.0.0.1:18080`
- 运维命令：`cd /opt/dms/prod && docker-compose ps`、`docker-compose logs -f backend`、`docker-compose up -d`

### 2.2 测试环境

#### 新测试环境（43.128.145.141，当前推荐）

| 入口 | 地址 | 说明 |
|---|---|---|
| 业务前台登录页 | http://43.128.145.141/login | PC 登录页，租户 `default`，账号 `sys_admin`，密码 `Dms@123456` |
| 移动端 H5 登录页 | http://43.128.145.141/mobile/login | 手机浏览器访问，账号与 PC 业务前台相同 |
| 业务工作台 | http://43.128.145.141/ | 最新测试 PC 工作台 |
| 平台后台 | http://43.128.145.141/admin/ | 账号 `admin`，密码 `Sh123456` |
| 健康检查 | http://43.128.145.141/actuator/health | 返回 `{"status":"UP"}` 表示正常 |

**服务器登录与部署信息**：

- SSH：`ssh ubuntu@43.128.145.141`
- 密码：`Welcomeyyx0616`（敏感信息，仅限内部部署使用，请勿外传或提交到公开仓库）
- 提权：`sudo`（该账号可直接执行 Docker 和部署相关命令）
- Docker 容器：
  - `dms-test-nginx`：Nginx 静态入口，映射 `0.0.0.0:8083->80/tcp`
  - `dms-test-backend`：后端 API 容器
  - `dms-test-postgres`：PostgreSQL
  - `dms-test-redis`：Redis
  - `dms-test-minio`：MinIO
- 宿主机静态目录：`/opt/dms/test/frontend`
- 平台后台静态目录：`/opt/dms/test/frontend/admin`
- Nginx 容器内静态目录：`/usr/share/nginx/html`（只读挂载自宿主机目录）
- Nginx 配置：`/opt/dms/test/nginx/nginx.conf`
- UI 发布前备份目录：`/opt/dms/backups/`

部署明细见 `docs/07_部署方案/DMS测试环境部署记录_43.128.145.141_20260812.md`。

#### 统一测试环境（43.128.145.141）

| 入口 | 地址 | 说明 |
|---|---|---|
| 业务前台登录页 | http://43.128.145.141/login | 测试租户用户登录 |
| 业务工作台 | http://43.128.145.141/ | 测试 PC 工作台 |
| 移动端 H5 登录 | http://43.128.145.141/mobile/login | 测试移动端 |
| 平台后台 | http://43.128.145.141/admin/ | 测试平台后台 |
| 后端健康检查 | http://43.128.145.141/actuator/health | 测试后端健康检查 |
| Swagger API | http://43.128.145.141/swagger-ui.html | 测试接口文档 |

---

## 3. 三种登录方式

系统提供三类登录入口，账号体系和 token 不互通。

### 3.1 业务前台登录（PC + H5 共用）

- 地址：`/login`（PC）、`/mobile/login`（H5）
- 接口：`POST /api/auth/login`
- 请求体：

```json
{ "tenantCode": "可选，留空则按用户名全局匹配", "username": "admin", "password": "Sh123456" }
```

- 返回 `data.accessToken`，业务前台和移动端 H5 使用同一套 token。
- 登录成功后可调用 `GET /api/auth/me` 获取当前用户与权限码。

### 3.2 平台后台登录

- 地址：`/admin/`
- 接口：`POST /api/admin/auth/login`
- 请求体：

```json
{ "username": "admin", "password": "Sh123456" }
```

- 平台登录**不需要** `tenantCode`，管理的是跨租户的平台数据。
- 平台 token 与业务前台 token 不互通，请勿混用。

### 3.3 移动端 H5

- 与业务前台共用同一登录接口和账号，入口为 `/mobile/login`。
- 适合销售/经销商在手机上做订单、报台、审批等操作。

---

## 4. 默认账号

### 4.1 业务前台

| 用户名 | 密码 | 租户编码 | 角色/用途 |
|---|---|---|---|
| `sys_admin` | `Dms@123456` | `default` 或留空 | 新测试环境默认厂家超级管理员（系统管理员角色） |
| `admin` | `Sh123456` | 留空或 `default` | 旧测试环境默认厂家超级管理员（系统管理员角色） |
| `vendor02` | `Sh123456` | `default` | 厂家用户 |
| `dealer02` | `Sh123456` | `default` | 经销商用户 |

### 4.2 平台后台

| 用户名 | 密码 | 用途 |
|---|---|---|
| `admin` | `Sh123456` | 平台超级管理员 |

### 4.3 平台租户管理员（多租户演示）

| 用户名 | 密码 | 租户编码 | 说明 |
|---|---|---|---|
| `mfr_a_admin` | `Sh123456` | `MFR_A` | 厂家 A 管理员 |
| `mfr_b_admin` | `Sh123456` | `MFR_B` | 厂家 B 管理员 |
| `dealer_a1_admin` | `Sh123456` | `DEALER_A1` | 经销商 A1 管理员 |
| `dealer_a2_admin` | `Sh123456` | `DEALER_A2` | 经销商 A2 管理员 |
| `dealer_b1_admin` | `Sh123456` | `DEALER_B1` | 经销商 B1 管理员 |

---

## 5. 测试账号（2026-08-09 重新生成，可直接使用）

以下账号均位于测试租户 `11111111-1111-1111-1111-111111111111`（租户编码留空即可登录），统一密码 **`Dms@123456`**，首次登录**无需**改密。每个账号对应一个角色，覆盖主要权限场景，资料齐全。

| 用户名 | 密码 | 姓名 | 角色 | 用户类型 | 邮箱 | 手机号 | 说明 |
|---|---|---|---|---|---|---|---|
| `sys_admin` | `Dms@123456` | 林管理员 | 系统管理员（SYS_ADMIN） | 厂商 | vinkinyu@163.com | 13800000001 | 全部权限，用于配置/审批流/监控 |
| `sales_mgr` | `Dms@123456` | 赵销售经理 | 销售经理（SALES_MGR） | 厂商 | vinkinyu@163.com | 13800000002 | 销售主管视角、审批待办 |
| `sales` | `Dms@123456` | 孙销售员 | 销售（SALES） | 厂商 | vinkinyu@163.com | 13800000003 | 一线销售，创建订单/合同/授权 |
| `cs` | `Dms@123456` | 周客服 | 客服（CS） | 厂商 | vinkinyu@163.com | 13800000004 | 客服/售后视角 |
| `biz` | `Dms@123456` | 吴商务 | 商务（BIZ） | 厂商 | vinkinyu@163.com | 13800000005 | 合同/订单商务处理 |
| `fin` | `Dms@123456` | 郑财务 | 财务（FIN） | 厂商 | vinkinyu@163.com | 13800000006 | 发票/结算/审批节点 |
| `contract` | `Dms@123456` | 王合同专员 | 合同专员（CONTRACT_SPEC） | 厂商 | vinkinyu@163.com | 13800000007 | 合同起草与归档 |
| `dealer_admin` | `Dms@123456` | 李经销商 | 经销商管理员（DEALER_ADMIN） | 经销商 | vinkinyu@163.com | 13800000008 | 绑定经销商“杭州济民医药器械有限公司（D00003）” |

使用说明：

- 角色权限在「用户与权限 → 角色权限」里配置；改完角色后对应用户需**重新登录**生效。
- 一个账号同时只能有一个角色；在「账号管理 → 编辑 → 角色」中分配。
- 审批流测试时，可让 `sales` 发起单据，用 `sales_mgr`/`fin`/`sys_admin` 审批，触发站内信 + 163 邮件通知。
- 所有测试账号邮箱已统一为 `vinkinyu@163.com`（2026-08-12 更新），方便真实接收审批与测试邮件；用户名仍保留以便区分账号。

---

## 6. 基础服务地址

| 服务 | 测试（43.128.145.141） | 生产（8.133.193.238） | 账号/备注 |
|---|---|---|---|
| PostgreSQL | 仅容器内网 `5432` | 仅容器内网 `5432` | 生产用户/库 `dms`，密码见服务器 `/opt/dms/prod/.env`（`DB_PASSWORD`，不对外暴露、不入库）；备份用 `docker exec dms-prod-postgres pg_dump -U dms dms` |
| Redis | 仅容器内网 `6379` | 仅容器内网 `6379` | AOF 持久化，无对外端口 |
| MinIO API | 仅容器内网 `9000` | 仅容器内网 `9000` | 访问密钥见 `.env`（`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`），控制台 `9001` 同样不对外暴露 |
| MinIO 控制台 | 仅容器内网 `9001` | 仅容器内网 `9001` | 同上 |
| 后端 | 仅容器内 `8080` | 仅监听 `127.0.0.1:18080` | 经网关反代，健康检查 `/actuator/health` |
| SMTP（163） | `smtp.163.com:465` SSL | 同左 | 发件人 `vinkinyu@163.com`，授权码用 `.env` 的 `MAIL_PASSWORD` 注入，不写死在配置中 |

> v3.12.4 起两套环境的 DB/Redis/MinIO/后端端口均不对外暴露，只能通过容器网络或 SSH 隧道访问；历史直连端口（5432/5433/9000-9003/8080/8082）拓扑已废弃。

---

## 7. 已验证入口

2026-08-09 测试环境冒烟通过：

- `GET /`：业务前台静态入口返回 `200`
- `GET /admin/`：平台后台静态入口返回 `200`
- `POST /api/auth/login`：业务登录返回 `200`
- `GET /api/auth/me`：业务当前用户返回 `200`
- `POST /api/admin/auth/login`：平台登录返回 `200`
- `GET /actuator/health`：返回 `{"status":"UP"}`
- 账号管理查看抽屉显示「角色=财务」「用户类型=厂商」，不再出现 raw `roleId`/`vendor`；列表/编辑/查看三处一致。
- 8 个新测试账号（`sys_admin`/`sales_mgr`/`sales`/`cs`/`biz`/`fin`/`contract`/`dealer_admin`）均能以 `Dms@123456` 登录。

---

## 8. 常见问题

### 8.1 登录返回 401

- 业务前台确认 `tenantCode` 是否正确（测试租户可留空）。
- 平台后台不要传 `tenantCode`。
- 默认管理员密码为 `Sh123456`，测试演示账号密码为 `Dms@123456`，注意大小写。
- 若账号被锁定（连续输错 9 次锁定 30 分钟），可由管理员在「账号管理 → 更多 → 解锁」处理。

### 8.2 页面打开但接口 502

通常是后端容器重启窗口或前端缓存了旧后端 IP。处理顺序：

1. 检查后端健康检查地址是否为 `UP`。
2. 重启前端容器：`docker restart dms-test-frontend`（测试）或 `docker restart dms-frontend-vue`（生产）。
3. 浏览器 Ctrl+Shift+R 强刷。

### 8.3 平台后台路径说明

平台后台页面入口统一使用 `/admin/`，未登录时由前端路由和平台登录接口处理认证。

### 8.4 收不到审批邮件

- 系统通过 SMTP 客户端发件，163 网页“已发送”里看不到属于正常现象。
- 在「用户与权限 → 邮件发送日志」查看每封邮件的 `SUCCESS/FAILED`、收件人、错误信息；
  也可调用 `POST /api/email-logs/test`（body `{"to":"你的邮箱"}`）发测试邮件。
