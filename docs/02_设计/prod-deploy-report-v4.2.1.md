# DMS v4.2.1 生产部署报告（2026-08-22）

## 主机与目录
- **生产主机**：`8.133.193.238`，账号 `root`，密码与测试不同（独立管理）
- **部署目录**：`/opt/dms/prod/`
- **Docker Compose**：`/opt/dms/prod/docker-compose.yml`
- **共享备份**：`/opt/dms/backups/`
- **Nginx 配置（webgate 容器）**：`/opt/webgate/nginx.conf` → bind 到容器 `/etc/nginx/conf.d/default.conf`
- **DMS 自己的 nginx 备用配置**：`/opt/dms/prod/nginx-dms.conf`（历史遗留，已不直接使用，但保留以便切换）

## 容器
| 名称 | 镜像 | 用途 |
|------|------|------|
| `dms-prod-backend` | `eclipse-temurin:17-jre-alpine` | Spring Boot 后端，8080，映射宿主 127.0.0.1:18080 |
| `dms-prod-postgres` | `postgres:16-alpine` | 库 `dms`，与后端同 dms-prod 网络 |
| `dms-prod-redis` | `redis:7-alpine` | appendonly，dms-prod 网络 |
| `dms-prod-minio` | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | 桶 `dms`，dms-prod 网络 |
| `webgate` | `nginx:1.25-alpine` | 统一 80 网关；bind `/opt/webgate/nginx.conf` 与 `/opt/dms/prod/frontend` |

> 容器实际名带 Compose v1.29 前缀（如 `a3493e36ecba_dms-prod-postgres`），但 compose `container_name` 字段仍写 `dms-prod-postgres`；通过别名 `postgres` / `redis` / `minio` / `backend` / `dms-prod-backend` 在网络内解析。

## 网络
- 业务网络：`dms-prod`（bridge, subnet 172.19.0.0/16, gateway 172.19.0.1）
- webgate 同时挂在 `dms-prod` 与 `web-net`（172.18.0.0/16，与 brochure/ai-knowledge 共享）
- webgate 通过容器名 `backend` 解析到 dms-prod-backend（172.19.0.2）

## 公网路径
- 业务前台 PC：`http://8.133.193.238/dms/`（前端 dist base `VITE_BASE=/dms/`，index.html 资源全用 `/dms/...` 前缀）
- 平台后台：`http://8.133.193.238/dms/admin/`（admin dist base `VITE_BASE=/dms/admin/`）
- 移动端 H5：`http://8.133.193.238/dms/mobile/login`
- 后端 API：`http://8.133.193.238/api/...`、`/auth/...`、`/actuator/...`（由 webgate 转发到 backend:8080）
- 健康检查：`http://8.133.193.238/actuator/health`（webgate → backend）

## 与测试环境差异
| 项 | 测试 43.128.145.141 | 生产 8.133.193.238 |
|----|---------------------|---------------------|
| 账号 | ubuntu | root |
| 部署目录 | /opt/dms/test/ | /opt/dms/prod/ |
| 容器名 | dms-test-* | dms-prod-* |
| compose | docker compose v2 OK | 仅 docker-compose v1.29（与 Docker 29 不兼容，部署改用 docker restart） |
| 公共域根 | / | /dms/ |
| admin base | /admin/ | /dms/admin/ |
| PC 前端 base | / | /dms/ |
| webgate | 单一 nginx 容器即 webgate | 独立 webgate 容器（dms 自己不带 nginx） |

## 4.2.1 部署流程（已验证）
1. SSH 到生产 root@8.133.193.238，确认部署目录结构与容器名
2. 备份当前 jar/前端：`/opt/dms/backups/app-prod-<stamp>.jar` + `frontend-prod-<stamp>/`
3. 上传新 jar（111 MB）+ frontend.tar.gz + admin.tar.gz
4. 解压 frontend 到 `/opt/dms/prod/frontend`，admin 到 `/opt/dms/prod/frontend/admin`
5. **不用 docker-compose**（v1.29 + Docker 29 兼容性问题），改用：
   - `docker start <exited>` 启动可能停掉的依赖容器
   - `docker restart dms-prod-backend` 重启后端，触发 Flyway 应用 V110-V112
   - `docker exec webgate nginx -s reload` 强制 webgate 重新解析 upstream（避免缓存 502）
6. 验证公网 health + 三端 200 + API 真实登录返回 token
7. 远程清理：保留最近 3 份 jar/前端备份；truncate 5 容器 JSON 日志；删 /tmp；`docker image prune -f`
8. 本地归档：`releases/dms-4.2.1-prod-deploy-<stamp>/`

## 跨版本升级（本次实测）
- 部署前 Flyway：`V109 success`（V105-V109 已 7/7 凌晨 07:07 应用）
- 部署后 Flyway：`V112 success`（V110/V111/V112 在 23:10 自动应用）
- V110：email_logs.duration_ms BIGINT、product_prices.price_scope 默认值 'SALE'
- V111：4 张表 period_yyyymm CHAR→VARCHAR
- V112：product_prices.price_scope 历史脏值兜底
- 全部 success=true，business 关键表 200 OK

## 部署后回归
- 公网 `/actuator/health` = 200
- 公网 `/dms/` = 200，`/dms/admin/` = 200，`/dms/mobile/login` = 200
- `POST /api/auth/login` 返回 `{"code":0,"data":{"accessToken":"..."}}` 200
- 业务模块冒烟（order/sales-return/purchase/inventory）：**43/43 PASS**
- 用户实际操作日志（23:18-23:19）：dashboard / orders / approval / my-todo / permissions / notifications 全部 200

## 回滚（紧急）
```bash
ssh root@8.133.193.238
# 1. 还原 jar
sudo cp /opt/dms/backups/app-prod-230530.jar /opt/dms/prod/backend/app.jar
# 2. 还原前端
sudo rm -rf /opt/dms/prod/frontend/*
sudo cp -a /opt/dms/backups/frontend-prod-230530/* /opt/dms/prod/frontend/
# 3. 重启后端
sudo docker restart dms-prod-backend
# 4. reload webgate（如 502）
sudo docker exec webgate nginx -s reload
```
> Flyway V110-V112 已应用，回滚 jar 不会回滚 schema（schema 迁移是单向）。如需回滚 schema 必须手动编写反向迁移，不在本脚本范围。

## 已知安全事项
- 生产 `JWT_SECRET`、`DB_PASSWORD`、`MINIO_SECRET_KEY` 等敏感信息以明文保存在 `/opt/dms/prod/.env`（模式 666）
- 生产与测试使用相同密码（`Welcomeyyx0616`）但不同账号（root / ubuntu），存在横向风险
- 建议：尽快给生产改独立密码 + 把 .env 权限收紧到 600