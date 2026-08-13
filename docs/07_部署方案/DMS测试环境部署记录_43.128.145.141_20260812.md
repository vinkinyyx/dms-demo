# DMS 测试环境部署记录（43.128.145.141）

**部署时间**: 2026-08-12  
**服务器**: 43.128.145.141  
**SSH 用户**: ubuntu  
**部署目录**: `/opt/dms/test`  
**编排方式**: Docker Compose  
**环境用途**: 最新 DMS 测试环境，独立于 `8.133.193.238` 旧测试环境

---

## 1. 访问入口

| 入口 | 地址 | 说明 |
|---|---|---|
| 业务前台登录页 | http://43.128.145.141/login | 厂家/经销商租户用户登录（PC） |
| 移动端 H5 登录页 | http://43.128.145.141/mobile/login | 移动端 H5 登录入口，适合手机浏览器访问 |
| 移动端首页 | http://43.128.145.141/mobile/home | 移动端首页/待办入口 |
| 业务工作台 | http://43.128.145.141/ | 登录后进入 PC 工作台 |
| 平台后台 | http://43.128.145.141/admin/ | 平台管理员登录入口 |
| 健康检查 | http://43.128.145.141/actuator/health | 返回 `{"status":"UP"}` 表示服务正常 |

说明：测试环境统一通过标准 80 端口访问（Nginx `80 -> 80` 容器），前端静态资源和后端 API 反向代理统一入口，无需单独暴露后端端口。（历史曾使用 8083 端口，已切换至 80。）

---

## 2. 登录账号

### 2.1 业务前台

| 租户编码 | 用户名 | 密码 | 角色 | 说明 |
|---|---|---|---|---|
| `default` | `sys_admin` | `Dms@123456` | 系统管理员（SYS_ADMIN） | 厂家厂商超级管理员，拥有完整业务菜单、按钮、审批、日志等权限 |

业务前台也可以不填租户编码，系统会按用户名自动匹配默认演示租户。移动端 H5 与业务前台共用同一登录接口、同一套账号和 token。

移动端已随本次 `frontend-vue/dist` 一并发布，已验证：

- `GET /mobile/login` 返回 `200`。
- `GET /mobile/home` 返回 `200`，未登录时会由前端路由跳转到 `/mobile/login`。
- 移动端登录页已预填租户 `default`、账号 `sys_admin`、密码 `Dms@123456`。

### 2.2 平台后台

| 用户名 | 密码 | 说明 |
|---|---|---|
| `admin` | `Sh123456` | 平台超级管理员 |

平台后台入口为 `/admin/`，不需要租户编码，token 与业务前台不互通。

---

## 3. 容器与端口

容器均位于 `/opt/dms/test`，使用 Compose 项目 `test` 和 Docker 网络 `dms-test`。

| 容器名 | 服务 | 镜像 | 对外端口 | 说明 |
|---|---|---|---|---|
| `dms-test-nginx` | `nginx-test` | `nginx:1.25-alpine` | `80 -> 80` | 前端静态资源 + API 反向代理 |
| `dms-test-backend` | `backend-test` | `eclipse-temurin:17-jre-alpine` | 仅内网 | Spring Boot 后端，容器内 `8080` |
| `dms-test-postgres` | `postgres-test` | `postgres:16-alpine` | 仅内网 | PostgreSQL，库名 `dms_test` |
| `dms-test-redis` | `redis-test` | `redis:7-alpine` | 仅内网 | Redis 缓存 |
| `dms-test-minio` | `minio-test` | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | 仅内网 | 对象存储 |

### 3.1 公网防火墙

服务器 UFW 已放行：

- `22/tcp`：SSH
- `443/tcp`：预留 HTTPS
- `80/tcp`：DMS 测试环境入口（标准 HTTP）

PostgreSQL、Redis、MinIO、后端端口均不应对公网开放，仅通过 Docker 内网访问。

---

## 4. 中间件连接信息

| 服务 | 主机 | 端口 | 库名/桶 | 账号 | 密码 |
|---|---|---|---|---|---|
| PostgreSQL | `postgres-test` | `5432` | `dms_test` | `dms` | `dms123456` |
| Redis | `redis-test` | `6379` | DB `0` | 无 | 无 |
| MinIO API | `http://minio-test:9000` | `9000` | bucket `dms` | `minioadmin` | `minioadmin123` |

这些地址用于后端容器环境变量，不建议直接暴露到公网。

---

## 5. 后端关键配置

后端使用 Spring Profile：`docker-test`。

关键环境变量：

| 变量 | 值 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker-test` |
| `DB_HOST` | `postgres-test` |
| `DB_NAME` | `dms_test` |
| `SPRING_DATA_REDIS_HOST` | `redis-test` |
| `MINIO_ENDPOINT` | `http://minio-test:9000` |
| `APP_BASE_URL` | `http://43.128.145.141` |
| `MAIL_ENABLED` | `false` |
| `SEED_ENABLED` | `true` |
| `JAVA_OPTS` | `-Xms256m -Xmx768m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai` |

数据库由 Flyway 自动迁移。本次部署后已包含 V83：补齐 `sys_admin` 菜单权限别名并修复新旧权限码兼容问题。

---

## 6. 常用运维命令

以下命令均在服务器上执行：

```bash
cd /opt/dms/test

# 查看容器状态
sudo docker compose ps

# 查看后端日志
sudo docker compose logs -f backend-test

# 查看 Nginx 日志
sudo docker compose logs -f nginx-test

# 重启全部服务
sudo docker compose restart

# 只重启后端
sudo docker compose restart backend-test

# 停止测试环境
sudo docker compose down

# 启动测试环境
sudo docker compose up -d
```

更新后端 jar：

```bash
cd /opt/dms/test
# 替换 backend/app.jar 后执行
sudo docker compose up -d --force-recreate backend-test
```

更新前端静态文件：

```bash
cd /opt/dms/test
# 替换 frontend 目录内容后执行
sudo docker compose restart nginx-test
```

---

## 7. 已验证结果（2026-08-12）

- 业务前台 `/login` 返回 `200`。
- 移动端 H5 `/mobile/login` 返回 `200`，移动端受保护路由 `/mobile/home` 返回 `200`。
- 平台后台 `/admin/` 返回 `200`。
- 健康检查 `/actuator/health` 返回 `{"status":"UP"}`。
- 业务账号 `default / sys_admin / Dms@123456` 登录成功，角色为“系统管理员”。
- 平台账号 `admin / Sh123456` 登录成功。
- Flyway 迁移成功，最新包含 `V83__sys_admin_menu_aliases`。
- `sys_admin` 具备基础数据、合同、销售订单、采购订单、库存、审批、日志等完整菜单与按钮权限。
- 业务前台页面已预填测试账号：租户 `default`、账号 `sys_admin`、密码 `Dms@123456`。

---

## 8. P1 迭代部署记录（2026-08-13）

**版本**: P1 Sprint（B1-B8）
**入口变更**: Nginx 对外端口由 `8083 -> 80` 切换为标准 `80 -> 80`，统一访问地址 `http://43.128.145.141/`

### 8.1 新增功能

| 条目 | 内容 |
|---|---|
| B1 统一日志中心 | `/api/operation-logs/fullchain` 操作日志分页/详情/业务历史；业务前台「日志中心」整合登录/操作/接口/邮件 4 个 Tab；业务对象操作历史抽屉组件 |
| B2 序列号追溯 | PC「序列号追溯」页面，支持序列号/批次查询与出入库时间线（复用已有 `/api/traceability` 接口） |
| B3 异步导入导出 | `@Async` 线程池 + `async_task` 表（V86）；`/api/async-tasks` 列表/详情/下载；合同异步导出示例；「导入导出任务」页 |
| B4 打印模板 | `/print/:type/:id` A4 打印页，覆盖销售订单/销售出库单/合同；详情页新增打印按钮 |
| B5 附件预览 | `/api/files/{id}/preview` inline 预览端点；`FilePreview` 组件（图片/PDF/视频/下载兜底）；合同附件接入 |
| B6 批次效期预警 | `/api/inventory/expiry-alerts` + `/expiry-summary`；「效期预警」页；首页预警横幅 |
| B7 状态规范统一 | `StateView` 组件（空/加载/错误/403/404/500）；`/error/:code` 错误页；请求拦截器增加 404/500 处理；修复 request.js 中文乱码 |
| B8 慢查询+监控 | V87 复合索引（orders/purchase_orders/approval_instances/email_logs/login_logs/api_call_log/inventory/notifications/async_task）；Actuator 暴露 prometheus + health probes；`scripts/backup_db.sh` / `restore_db.sh` 备份恢复脚本 |

### 8.2 部署要点

- 后端 jar 挂载 `/opt/dms/test/backend/app.jar`，容器 `dms-test-backend`；`APP_BASE_URL` 已改为 `http://43.128.145.141`
- Nginx 容器 `dms-test-nginx` 端口映射 `80:80`（原 8083 不再使用）
- Flyway 自动迁移到 V87（V86 async_task 表、V87 慢查询索引）
- Actuator：`/actuator/health`、`/actuator/prometheus`（已在 SecurityConfig 放行）
- 备份：建议 crontab `30 2 * * * /opt/dms/scripts/backup_db.sh`，默认保留 14 天

### 8.3 验证结果

- 后端 `mvn package`、业务前台 / 平台后台 `npm run build` 全部通过
- 全量自动化回归：**541 passed, 39 skipped, 22 xfailed, 23 xpassed, 0 failed**（`automation_test/`）
- 后端健康检查 `GET /actuator/health` → `{"status":"UP"}`
- Prometheus 指标 `GET /actuator/prometheus` → 200，含 jvm/http 指标
