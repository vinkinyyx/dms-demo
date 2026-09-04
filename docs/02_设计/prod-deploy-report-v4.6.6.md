# DMS v4.6.6 生产部署报告（2026-09-05）

## 发布范围
- 版本：v4.6.6（含 v4.6.3 后端审批流补齐 + 系统开关管理员页、v4.6.4/v4.6.5/v4.6.6 全部 UI 改造），从生产 v4.6.1 一次性升级。
- 生产入口：PC `http://8.133.193.238/dms/`，平台后台 `http://8.133.193.238/dms/admin/`，移动端 `http://8.133.193.238/dms/mobile/login`，经销商注册 `http://8.133.193.238/dms/mobile/register`，健康检查 `http://8.133.193.238/actuator/health`。
- 部署目录：`/opt/dms/prod/`；运行容器：`dms-prod-backend`、`a3493e36ecba_dms-prod-postgres`、`ab71c6fdb795_dms-prod-redis`、`c3b77202c384_dms-prod-minio`、`webgate`；未触碰 `brochure`、`ai-knowledge`。

## 发布产物
- 后端：`backend/target/dms-backend.jar`（117 MB，含 Flyway V147/V148）。
- PC 前端：`frontend-vue/dist`，构建 base `/dms/`。
- 平台后台：`admin-vue/dist`，构建 base `/dms/admin/`，部署到 `/opt/dms/prod/frontend/admin/`。
- 数据库迁移：生产由 V146 → **V147、V148**，日志确认 `Successfully applied 2 migrations ... now at version v148`。
  - V147：授权-下单挂钩租户开关（默认 false=解耦）+ 授权 status 列扩容 VARCHAR(32)。
  - V148：审批流 SUBMITTER 节点 + 6 类业务模板 + 节点补挂提交人审批人（幂等播种）。

## 备份与回滚
- 数据库备份：`/opt/dms/backups/dms-prod-pre-v466-20260904-235301.sql.gz`（1.36 MB，52928 行）。
- 旧后端/前端备份（部署脚本自动）：`/opt/dms/backups/app-prod-20260904-235319.jar`、`/opt/dms/backups/frontend-prod-20260904-235319/`。
- 本版本发布包归档：`/opt/dms/backups/release-v4.6.6-20260905/`（dms-backend-v4.6.6.jar、frontend-business-v4.6.6.tar.gz、frontend-admin-v4.6.6.tar.gz、nginx-webgate.conf）；本地同名包 `releases/dms-v4.6.6-20260905/`。

回滚步骤（应用层，DB 已含 V147/V148 幂等播种，通常无需回退数据库）：

```bash
cp /opt/dms/backups/app-prod-20260904-235319.jar /opt/dms/prod/backend/app.jar
find /opt/dms/prod/frontend -mindepth 1 -maxdepth 1 ! -name admin -exec rm -rf {} +
cp -a /opt/dms/backups/frontend-prod-20260904-235319/. /opt/dms/prod/frontend/
docker restart dms-prod-backend webgate
```

## Nginx 变更（webgate，铁律10）
- webgate 容器 nginx 配置（bind-mount `/opt/webgate/nginx.conf`）新增平台后台深链 fallback：
  ```nginx
  location /dms/admin/ {
      alias /usr/share/nginx/dms/admin/;
      try_files $uri $uri/ /dms/admin/index.html;
  }
  ```
  修复 `/dms/admin/login` 等后台深链直访被 `/dms/` 的 try_files 回退到业务前端 index 而出现业务 SPA 404 的问题。
- 变更前备份：`/opt/webgate/nginx.conf.bak.20260905-000333`、`nginx.conf.bak.admin.20260905-000724`；容器内 `nginx -t` 通过后 `docker restart webgate` 生效。

## 关键坑位（本次新增）
- **webgate bind-mount 前端整层替换后容器持有旧 inode**：`deploy_prod.py` 用 `find ... -exec rm -rf` + 重新解压替换 `/opt/dms/prod/frontend`，目录内容换新 inode，而 webgate 以 bind-mount 挂该目录、长连接/目录项缓存仍指向旧目录，导致公网仍返回旧 index（`Last-Modified` 停在 08-31）。处理：**前端整层替换后必须 `docker restart webgate`**（与测试环境铁律10 第 3 条同理）。已验证重启后容器 `stat` inode 与宿主一致、公网返回新 hash。

## 发布后验证（真实浏览器，2026-09-05）
- 铁律9 八入口全部 200：`/`、`/dms/`、`/dms/admin/`、`/dms/mobile/login`、`/dms/mobile/register`、`/brochure/`、`/brochure/mobile.html`、`/brochure/print.html`（宣传三页 title 正确）；`/actuator/health` UP。
- 业务前端 sys_admin/default 登录成功；工作台 TOP5 为排行榜且 5 家全名（合肥德医/天津金邦/青岛海诺/成都川医/苏州康宁）；月亮按钮仅切菜单底色（切换后 `data-sider=dark`、`data-mode` 恒 light、内容区 `.main` 背景恒 `rgb(245,247,250)`）。
- 平台后台：`/dms/admin/login` 深链与 `/dms/admin/` 入口均可登录，首页总览 10 卡片、侧栏浅色。
- 移动端首页：渐变头 + 6 个彩色功能宫格正常。
- Console 0 红色错误、Network 0 个 5xx。
