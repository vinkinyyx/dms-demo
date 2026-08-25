# DMS 生产环境部署说明（v3.12.4，2026-08-16）

## 架构

服务器 `8.133.193.238`（Ubuntu 22.04，2C1.6G，Docker + docker-compose v1）。

- 统一 80 入口由宿主机已有 `webgate`（nginx:1.25-alpine）容器承担。
  - `/` → brochure（产品宣传手册，原有）
  - `/ai/` → ai-knowledge（原有）
  - `/dms/` → DMS 业务前台 + 移动端 H5 静态文件
  - `/dms/admin/` → DMS 平台后台静态文件
  - `/api/`、`/auth/`、`/actuator/` → 反代到 `dms-prod-backend:8080`
- DMS 栈（docker-compose，目录 `/opt/dms/prod`，网络 `dms-prod`）：
  - `dms-prod-backend`（eclipse-temurin:17-jre-alpine，仅映射 127.0.0.1:18080）
  - PostgreSQL 容器名可能带 Compose 前缀（如 `a3493e36ecba_dms-prod-postgres`，库 `dms`）
  - `dms-prod-redis`（redis:7-alpine，AOF）
  - `dms-prod-minio`（minio，不对外暴露）
- webgate 额外挂载 `/opt/dms/prod/frontend:/usr/share/nginx/dms:ro` 并加入 `dms-prod` 网络。

## 访问地址

| 入口 | 地址 |
|---|---|
| 业务前台/PC | http://8.133.193.238/dms/ |
| 移动端 H5 | http://8.133.193.238/dms/mobile/login |
| 平台后台 | http://8.133.193.238/dms/admin/ |
| 健康检查 | http://8.133.193.238/actuator/health |

## 账号

- 业务前台（租户 `default`）：`admin` / `Sh123456`；演示 `mfr_a_admin` / `Sh123456`（租户 `MFR_A`）
- 平台后台：`admin` / `Sh123456`
- SSH：`root@8.133.193.238` / 见 `docs/DMS登录信息手册.md`

## 首次/更新部署

在仓库根目录准备好 `backend/target/dms-backend.jar`、`frontend-vue/dist`（base=/dms/）、`admin-vue/dist`（base=/dms/admin/），然后上传到 `/opt/dms/prod`：

```bash
cd /opt/dms/prod
# 编辑 .env（参考 .env.example，设置 DB_PASSWORD/MINIO_SECRET_KEY/JWT_SECRET/MAIL_PASSWORD）
docker-compose up -d postgres redis minio
docker-compose up -d backend
# webgate 增加挂载与网络后（见下）重启
docker restart webgate
```

webgate 运行命令（含 DMS 挂载与网络）：

```bash
docker run -d --name webgate --restart unless-stopped --network web-net \
  -p 80:80 \
  -v /opt/webgate/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  -v /opt/dms/prod/frontend:/usr/share/nginx/dms:ro \
  nginx:1.25-alpine
docker network connect dms-prod webgate
```

## 运维

```bash
cd /opt/dms/prod
docker-compose ps
docker-compose logs -f backend
docker-compose restart backend
# 数据库登录（容器名可能带 Compose 前缀）
c=$(docker ps --format '{{.Names}}' | grep 'dms-prod-postgres' | head -1)
docker exec -it "$c" psql -U dms -d dms

# 数据库备份
docker exec "$c" pg_dump -U dms dms > dms_$(date +%F).sql
```

## 注意

- 会话有效期：access token 8 小时（`ACCESS_TOKEN_TTL` 默认 28800000ms），refresh 7 天。
- DB/Redis/MinIO 不对外暴露端口；后端仅监听 127.0.0.1:18080。
- 机器内存有限，后端 JVM 限制 `-Xmx640m`。
- `.env` 含密钥，不入库；仓库仅提供 `.env.example`。
