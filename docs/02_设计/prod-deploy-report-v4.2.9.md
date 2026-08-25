# DMS v4.2.9 生产部署报告（2026-08-25）

## 发布范围
- 版本：v4.2.9 PATCH
- 生产入口：PC `http://8.133.193.238/dms/`，后台 `http://8.133.193.238/dms/admin/`，移动端 `http://8.133.193.238/dms/mobile/login`
- 部署目录：`/opt/dms/prod/`
- 运行容器：`dms-prod-backend`、`dms-prod-postgres`、`dms-prod-redis`、`dms-prod-minio`、`webgate`；未触碰 `brochure` 与 `ai-knowledge`

## 发布产物
- 后端：`backend/target/dms-backend.jar`
- PC 前端：`frontend-vue/dist`，构建 base 为 `/dms/`
- 平台后台：`admin-vue/dist`，部署到 `/dms/admin/`
- 数据库迁移：V120 已在生产执行成功

## 备份与回滚
- 数据库备份：`/opt/dms/backups/dms-db-pre-v429-20260825-221506.sql.gz`
- 旧后端备份：`/opt/dms/backups/app-prod-20260825-221518.jar`
- 旧前端备份：`/opt/dms/backups/frontend-prod-20260825-221518/`
- 上一版额外备份：`/opt/dms/backups/app-prod-20260825-021234.jar`、`/opt/dms/backups/frontend-prod-20260825-021234/`

应用层回滚步骤：

```bash
cp /opt/dms/backups/app-prod-20260825-221518.jar /opt/dms/prod/backend/app.jar
rm -rf /opt/dms/prod/frontend/*
cp -a /opt/dms/backups/frontend-prod-20260825-221518/. /opt/dms/prod/frontend/
docker rm -f dms-prod-backend
cd /opt/dms/prod && docker-compose up -d --no-deps backend
```

V120 在生产匹配 0 行，属于幂等无数据变更；通常无需数据库回滚。

## 部署处理
- `scripts/deploy_prod.py` 自动备份旧 jar/旧前端并上传新产物。
- 生产 `docker-compose v1.29.2` 在 `--force-recreate backend` 时触发 `KeyError: 'ContainerConfig'`，并停止 postgres/redis/minio。
- 已按生产兼容方式修复：先 `docker start` postgres/redis/minio，等待 healthy；再删除旧 `dms-prod-backend` 容器，使用 `docker-compose -f /opt/dms/prod/docker-compose.yml up -d --no-deps backend` 重建后端。
- 后端启动后健康检查通过，Flyway V120 记录为 `success=t`。

## 验证结果
- `http://8.133.193.238/actuator/health`：200，返回 `{"status":"UP"}`
- `/dms/`、`/dms/admin/`、`/dms/mobile/login`：均 200
- 登录态核心 API：登录成功，`/api/sales-orders?page=1&size=5` 返回 200，经销商关键字搜索回归正常
- Playwright 生产 UI 冒烟：9/9 PASS
- Console：PC、后台、移动端均无错误
- 网络：无 5xx；未登录访问受保护 API 返回 401 为预期行为

## 生产清理
- 删除旧发布文件：`app-prod-20260824-001203.jar`、`frontend-prod-20260824-001203/`、`frontend-prod-20260822-230530/`、`frontend-prod-20260822-230424/`、`prod-20260822-065316/`
- 执行 `docker builder prune -af`、`docker container prune -f`、`docker image prune -f`
- 清理后无停止容器、无悬空镜像、无可回收构建缓存
- 根分区从 11G/28% 降至 9.9G/27%

## 注意事项
- 浏览器可能缓存旧前端资源，发布后请使用 `Ctrl+Shift+R` 强制刷新。
- 生产 Compose v1.29.2 与 Docker 29.x 的兼容性问题仍存在；后续发布后端建议继续采用“删除旧 backend 容器 + `up -d --no-deps backend`”的方式，避免直接 `--force-recreate`。
