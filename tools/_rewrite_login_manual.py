from pathlib import Path
p = Path(r'D:\Workspace\TRAE\DMS\docs\DMS登录信息手册.md')
s = '''# DMS 登录信息手册

**更新时间**: 2026-08-08  
**当前版本**: v3.8.12  
**适用范围**: 测试环境（8083/8082）与生产环境（8081/8080）

---

## 1. 环境总览

| 环境 | 前端入口 | 后端 API | 数据库 | 用途 |
|---|---|---|---|---|
| 测试 | `http://8.133.193.238:8083` | `http://8.133.193.238:8082` | `dms_test` | 需求开发、功能验证、回归测试 |
| 生产 | `http://8.133.193.238:8081` | `http://8.133.193.238:8080` | `dms` | 正式演示与生产使用 |

生产数据库已迁移到 Flyway `V71`，共 `126` 张表；测试环境与生产环境均支持业务前台、移动端 H5、平台后台登录。

---

## 2. 登录入口

### 2.1 生产环境

| 入口 | 地址 | 说明 |
|---|---|---|
| 业务前台登录页 | http://8.133.193.238:8081/login | 厂家/经销商租户用户登录 |
| 业务工作台 | http://8.133.193.238:8081/ | 登录后进入业务 PC 工作台 |
| 移动端 H5 登录 | http://8.133.193.238:8081/mobile/login | 移动端订单、报台、我的等功能 |
| 平台后台 | http://8.133.193.238:8081/admin/ | 平台管理员登录入口 |
| 后端健康检查 | http://8.133.193.238:8080/actuator/health | 返回 `{"status":"UP"}` 表示后端正常 |
| Swagger API | http://8.133.193.238:8080/swagger-ui.html | 后端接口文档 |

### 2.2 测试环境

| 入口 | 地址 | 说明 |
|---|---|---|
| 业务前台登录页 | http://8.133.193.238:8083/login | 测试租户用户登录 |
| 业务工作台 | http://8.133.193.238:8083/ | 测试 PC 工作台 |
| 移动端 H5 登录 | http://8.133.193.238:8083/mobile/login | 测试移动端 |
| 平台后台 | http://8.133.193.238:8083/admin/ | 测试平台后台 |
| 后端健康检查 | http://8.133.193.238:8082/actuator/health | 测试后端健康检查 |
| Swagger API | http://8.133.193.238:8082/swagger-ui.html | 测试接口文档 |

---

## 3. 默认账号

### 3.1 业务前台

| 用户名 | 密码 | 租户编码 | 角色/用途 |
|---|---|---|---|
| `admin` | `Sh123456` | `default` | 默认厂家超级管理员 |
| `vendor02` | `Sh123456` | `default` | 厂家用户 |
| `dealer02` | `Sh123456` | `default` | 经销商用户 |

业务登录请求：

```http
POST /api/auth/login
Content-Type: application/json

{
  "tenantCode": "default",
  "username": "admin",
  "password": "Sh123456"
}
```

### 3.2 平台后台

| 用户名 | 密码 | 用途 |
|---|---|---|
| `admin` | `Sh123456` | 平台超级管理员 |

平台登录请求：

```http
POST /api/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "Sh123456"
}
```

平台登录不需要 `tenantCode`。业务前台 token 与平台后台 token 不互通。

### 3.3 平台租户管理员

| 用户名 | 密码 | 租户编码 | 说明 |
|---|---|---|---|
| `mfr_a_admin` | `Sh123456` | `MFR_A` | 厂家 A 管理员，首次登录可能要求改密 |
| `mfr_b_admin` | `Sh123456` | `MFR_B` | 厂家 B 管理员 |
| `dealer_a1_admin` | `Sh123456` | `DEALER_A1` | 经销商 A1 管理员 |
| `dealer_a2_admin` | `Sh123456` | `DEALER_A2` | 经销商 A2 管理员 |
| `dealer_b1_admin` | `Sh123456` | `DEALER_B1` | 经销商 B1 管理员，首次登录可能要求改密 |

---

## 4. 基础服务地址

| 服务 | 测试 | 生产 | 账号/备注 |
|---|---|---|---|
| PostgreSQL | `8.133.193.238:5433` | `8.133.193.238:5432` | 用户 `dms` / 密码 `dms123456`；测试库 `dms_test`，生产库 `dms` |
| Redis | 容器内网 `6379` | 容器内网 `6379` | 无密码 |
| MinIO API | http://8.133.193.238:9002/ | http://8.133.193.238:9000/ | `minioadmin` / `minioadmin` |
| MinIO 控制台 | http://8.133.193.238:9003/ | http://8.133.193.238:9001/ | 同上 |

---

## 5. 已验证入口

2026-08-08 生产冒烟通过：

- `GET /`：业务前台静态入口返回 `200`
- `GET /admin/`：平台后台静态入口返回 `200`
- `POST /api/auth/login`：业务登录返回 `200`
- `GET /api/auth/me`：业务当前用户返回 `200`
- `POST /api/admin/auth/login`：平台登录返回 `200`
- `GET /api/admin/auth/me`：平台当前用户返回 `200`
- `GET /api/admin/tenants/stats`：平台租户统计返回 `200`
- `GET /api/admin/tenant-admins`：租户管理员列表返回 `200`
- `GET /api/admin/dicts/types`：平台字典类型返回 `200`

---

## 6. 常见问题

### 6.1 登录返回 401

- 业务前台确认 `tenantCode` 是否正确。
- 平台后台不要传 `tenantCode`。
- 默认密码统一为 `Sh123456`，注意大小写。
- 若账号被标记首次登录改密，先调用改密接口或按页面提示修改密码。

### 6.2 页面打开但接口 502

通常是后端容器重启窗口或前端容器缓存了旧后端 IP。处理顺序：

1. 检查 `http://8.133.193.238:8080/actuator/health` 是否为 `UP`。
2. 重启前端容器：`docker restart dms-frontend-vue`。
3. 再次访问 `http://8.133.193.238:8081/login`。

### 6.3 平台后台路径说明

平台后台页面入口统一使用 `/admin/`，不要使用旧文档中的 `/admin/login`。未登录时由前端路由和平台登录接口处理认证。
'''
p.write_text(s, encoding='utf-8', newline='\n')
print('rewrote login manual')
