# DMS 登录信息手册

**更新时间**: 2026-08-04
**适用范围**: 测试 (8083) / 生产 (8081) / 平台后台 (admin-vue)
**数据来源**: 真实数据库 + 实际登录验证 (paramiko 脚本探测)

---

## 0. 核心规则（必读）

1. **统一初始密码** = `Sh123456`（来自 V51/V52 数据库 hash 验证）。
2. **tenant 隔离**：业务租户（厂家/经销商）走 `/api/auth/login`，平台管理员走 `/api/admin/auth/login`，**两套账号体系不互通**。
3. **首次登录强制改密**（`must_change_password=true` 触发的 V52 规则）。
4. **平台 admin 没有 captcha**，业务登录无 captcha，登录端点都是 `POST` + JSON。
5. **生产 nginx 当前不放行 `/api/admin/`**（403），平台 admin 只能从公网 `:8080` 直连（未对外暴露），或在测试环境 `:8082` 直连后端调。

---

## 1. 环境总览

| 环境 | 标识 | 阿里云服务器 | 数据库 |
|------|------|-------------|--------|
| 测试 | 8083 / 8082 | 8.133.193.238 | dms_test (PG) |
| 生产 | 8081 / 8080 | 8.133.193.238 | dms (PG) |
| 平台后台 (生产) | admin-vue @ 8081/admin/ | 8.133.193.238 | platform_admin_users |

---

## 2. 业务登录（PC 端 / 移动端 共用）

### 2.1 登录端点

| 用途 | Method | URL | Content-Type |
|------|--------|-----|--------------|
| 业务登录 | POST | `http://<host>:<port>/api/auth/login` | application/json |
| 业务刷新 token | POST | `http://<host>:<port>/api/auth/refresh` | application/json |
| 业务当前用户 | GET | `http://<host>:<port>/api/auth/me` (需 Bearer) | - |

### 2.2 登录请求体

```json
{
  "tenantCode": "default",
  "username": "admin",
  "password": "Sh123456"
}
```

### 2.3 真实可登录账号（已实测）

| 账号 | 密码 | tenantCode | 用途 | 验证状态 |
|------|------|-----------|------|---------|
| **admin** | `Sh123456` | `default` | 超级管理员（V7） | 8081/8083 都 OK |
| **vendor02** | `Sh123456` | `default` | 厂家用户（V7 generate_series 2~10） | 8083 OK |
| **dealer02** | `Sh123456` | `default` | 经销商用户（V7 generate_series 1~10） | 8083 OK |

> 备注：`vendor01` / `dealer01` 的密码不在 `Sh123456` 列，V7 老用户 hash 不可破解（建议忘记密码流程重置，或查 `dms.users.password_hash` 确认实际策略）。

### 2.4 平台租户账号（V52）

| 账号 | 密码 | 租户 | 备注 |
|------|------|------|------|
| `mfr_a_admin` | `Sh123456` | MFR_A（厂家A） | tenant_admin，首次登录强制改密 |
| `mfr_b_admin` | `Sh123456` | MFR_B（厂家B） | tenant_admin |
| `dealer_a1_admin` | `Sh123456` | DEALER_A1 | tenant_admin |
| `dealer_a2_admin` | `Sh123456` | DEALER_A2 | tenant_admin |
| `dealer_b1_admin` | `Sh123456` | DEALER_B1 | tenant_admin |

登录时 `tenantCode` 用对应租户的 `code`（如 `MFR_A` / `DEALER_A1`）。

### 2.5 业务登录入口

| 入口 | 测试 | 生产 |
|------|------|------|
| PC 端 (Vue 业务前台) | http://8.133.193.238:8083/ | http://8.133.193.238:8081/ |
| 移动端 (H5) | http://8.133.193.238:8083/mobile/login | http://8.133.193.238:8081/mobile/login |

---

## 3. 平台后台登录（admin-vue）

### 3.1 登录端点

| 用途 | Method | URL | Content-Type |
|------|--------|-----|--------------|
| 平台登录 | POST | `http://<host>:<port>/api/admin/auth/login` | application/json |
| 平台当前用户 | GET | `http://<host>:<port>/api/admin/auth/me` (需 Bearer) | - |
| 平台改密 | POST | `http://<host>:<port>/api/admin/auth/change-password` | application/json |
| 平台退出 | POST | `http://<host>:<port>/api/admin/auth/logout` | application/json |
| 平台刷新 token | POST | `http://<host>:<port>/api/admin/auth/refresh` | application/json |

### 3.2 登录请求体

```json
{
  "username": "admin",
  "password": "Sh123456"
}
```

> 无 `tenantCode`，无 `captcha`。

### 3.3 平台账号（V51）

| 账号 | 密码 | 角色 |
|------|------|------|
| **admin** | `Sh123456` | 平台超级管理员（默认） |

### 3.4 平台入口

| 入口 | 测试 | 生产 |
|------|------|------|
| 管理员后台 (admin-vue) | http://8.133.193.238:8083/admin/ | http://8.133.193.238:8081/admin/ |
| 平台 API 直连 (绕过 nginx) | http://8.133.193.238:8082/api/admin/auth/login | http://8.133.193.238:8080/api/admin/auth/login |

> 生产 8081 的 nginx 暂不转发 `/api/admin/**`（403），平台后台页面本身可用（因为 admin-vue 走的是 `/admin/` 静态资源 + 同源 cookie），但**纯 API 调用**平台后端需走 `8080` 直连。**测试 8083 的 nginx 同样只放行了 `/api/` 和 `/api/auth/`，平台 API 需走 `8082` 直连**。

---

## 4. MinIO / 其他基础服务

| 服务 | 测试 | 生产 | 默认账号 |
|------|------|------|---------|
| MinIO API | http://8.133.193.238:9000/ | http://8.133.193.238:9000/ | `minioadmin` / `minioadmin` |
| MinIO 控制台 | http://8.133.193.238:9001/ | http://8.133.193.238:9001/ | 同上 |
| PostgreSQL 直连 | `8.133.193.238:5432` | `8.133.193.238:5432` | `dms` / `dms123456`（生产 dms 库）；`dms` / `dms123456`（测试 dms_test 库） |
| Redis 直连 | 仅 6379 阿里云内网 | 仅 6379 阿里云内网 | 无密码 |

---

## 5. 常见登录问题排查

### 5.1 登录后跳到强制改密页

V52 规则：首次登录后 `must_change_password=true` 的账号会被强制定向到改密页。
- 业务改密：`PUT /api/auth/password` 或对应前端页
- 平台改密：`POST /api/admin/auth/change-password`

### 5.2 401 "用户名或密码错误"

- 确认 `tenantCode` 与账号所在租户一致（admin / vendor02 / dealer02 用 `default`；平台 admin 不需要 tenantCode）
- 确认密码是 `Sh123456`（区分大小写）
- 老用户 (vendor01 / dealer01) 密码不在已知清单，需重置

### 5.3 403 跨域

- 当前已开启 CORS（业务域白名单），浏览器无需额外设置
- 如果从 Postman / curl 拿 403，多半是 nginx 路径不转发（如 `/api/admin/` 在生产 8081 被前端静态资源拦截）

### 5.4 登录后接口 401

- Token 过期：默认 accessToken 有效期 8 小时（V52 之前的 `iat` + 28800s）
- 跨租户访问：业务 token 只在所属租户内有效

---

## 6. SSH 与服务器访问

| 项 | 值 |
|----|---|
| 服务器 | `8.133.193.238` |
| SSH 端口 | `22` |
| SSH 用户 | `root` |
| SSH 密码 | `Welcomeyyx0616` |
| 测试部署目录 | `/opt/dms/dms-test` |
| 生产部署目录 | `/opt/dms` |
| 镜像备份目录 | `/opt/dms/frontend-vue` 下 tar 镜像 + docker tag backup-* |

---

## 7. 密码策略备忘

| 角色 | 默认密码 | 强制改密 | 来源 |
|------|---------|---------|------|
| 业务超级管理员 admin (V7) | `Sh123456` | 否（must_change_password=false） | V7 seed |
| 业务 vendor01~10 / dealer01~10 (V7) | **未知**（非 Sh123456） | 否 | V7 seed（hash 未破解） |
| 平台 admin (V51) | `Sh123456` | 否 | V51 |
| 平台租户管理员 (V52) | `Sh123456` | **是** | V52 |

> V7 用户的 hash 是经典 bcrypt `$2a$10$` 形式，但本机 bcrypt 库不能直接解出原文（怀疑实现差异），如需使用请走"忘记密码"流程重置，或用 `admin/Sh123456` 代替。