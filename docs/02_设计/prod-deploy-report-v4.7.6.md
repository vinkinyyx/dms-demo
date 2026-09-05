# DMS v4.7.6 生产部署报告（2026-09-05）

## 发布范围
- 版本：v4.7.6（含 v4.7.2 藏青琥珀换肤+登录页重构、v4.7.3 三端可访问性/数字排版、v4.7.4 P1 收尾+宣传册域名分流、v4.7.5 宣传手册修订、v4.7.6 移动 H5 交互修复），**纯前端**发布。
- 生产入口：PC `http://8.133.193.238/dms/`，平台后台 `http://8.133.193.238/dms/admin/`，移动端 `http://8.133.193.238/dms/mobile/login`，注册 `http://8.133.193.238/dms/mobile/register`，健康检查 `http://8.133.193.238/actuator/health`。
- 宣传手册：生产为**根路径**（webgate `location /` 反代到 `brochure` 容器）：PC `http://8.133.193.238/`，打印 `http://8.133.193.238/print.html`，场景 `http://8.133.193.238/scenarios.html`，移动 `http://8.133.193.238/mobile.html`（brochure 容器 root `/usr/share/nginx/html`，宿主 `/opt/brochure/html`）。注意生产**没有** `/brochure/` 前缀（与测试环境 nginx server 不同）。
- 无后端/Flyway 变更：后端 jar 与数据库维持 v4.6.6 基线（Flyway **V148**），未重启 `dms-prod-backend`。

## 部署产物与步骤
- PC 业务前端：`frontend-vue/dist`，构建 `VITE_BASE=/dms/`，替换 `/opt/dms/prod/frontend`（保留 `admin/` 子目录），webgate bind-mount 该目录到 `/usr/share/nginx/dms`。
- 平台后台：**未重建**，沿用既有 `/opt/dms/prod/frontend/admin/`。
- 宣传手册：本地 `DMS产品宣传手册/pages/*.html` + `assets/`（37 张，排除 _ 备份）打包，清空镜像到 `/opt/brochure/html`。
- 整层替换后 `docker restart webgate brochure`（bind-mount inode 缓存，见 v4.6.6 坑位）。

## 备份与回滚
- 业务前端：`/opt/dms/backups/frontend-prod-20260905-145834`
- 宣传手册：`/opt/dms/backups/brochure-prod-20260905-145834`
- 回滚（应用层）：
  ```bash
  find /opt/dms/prod/frontend -mindepth 1 -maxdepth 1 ! -name admin -exec rm -rf {} +
  cp -a /opt/dms/backups/frontend-prod-20260905-145834/. /opt/dms/prod/frontend/
  find /opt/brochure/html -mindepth 1 -exec rm -rf {} +
  cp -a /opt/dms/backups/brochure-prod-20260905-145834/. /opt/brochure/html/
  docker restart webgate brochure
  ```

## 发布后验证（真实浏览器 Playwright，IP 直连）
- 铁律9 入口全部 200、Console 无红错、无 5xx：根 `/`（宣传 PC 首页，含移动审批+phone-sm）、`/print.html`、`/scenarios.html`、`/mobile.html`、`/dms/`（PC 登录）、`/dms/admin/`（后台登录）、`/dms/mobile/login`、`/dms/mobile/register`。
- 移动 H5 登录后：首页无搜索栏、无「已认证」；6 个常用功能图标均有效（消息中心信封 envelop-o）；「我的」页消息中心信封图标正常；最近业务点「查看」正确进入订单详情（不再落首页）；消息中心页签仅 全部/未读/审批，点卡片弹详情弹层；审批同意后待办 6→5、底部角标与首页 KPI 即时同步。
- 宣传册根路径 title 区分正确（print/scenarios/mobile 各自 title，非回退 PC 首页）；角色权限页为有内容的 `20-pc-roles-new.png`（非空白 22）。

## 清理
- 生产 `/tmp` 部署临时包已删；`/opt/dms/backups` 保留 12 份近期备份（510MB，磁盘 28%）。
- 测试环境旧备份裁剪到 50 份/1.6GB（删除 0831-0903 旧归档），`/tmp` 临时包清理，nginx conf 备份只留最新 1 份。
- 本地临时脚本/暂存包/junction/宣传册 _ 备份目录已清理。
