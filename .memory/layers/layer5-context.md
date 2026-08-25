# Layer 5: 当前上下文（临时）

> 会话级临时记忆。最近刷新：2026-08-25（v4.2.9 全量问题排查修复已同步仓库、部署测试环境并发布生产）

---

## 当前版本状态（v4.2.9，2026-08-25）

| 项目 | 状态 |
|------|------|
| 当前交付基线 | v4.2.9（PATCH，42 文件 +1633/-417；代码已同步回仓库，测试环境与生产环境均已部署） |
| 上一基线 | v4.2.8（2026-08-25，RBAC + 部署脚本 + 移动端 UX） |
| Flyway 迁移 | 已到 V120（V120 修正 V83 资源 API path：sales-positions / contract-templates / tenant-ui） |
| 测试环境 | http://43.128.145.141/dms/ 健康 UP（actuator/health）；三端可访问，营销页图片已补全 |
| 业务前台 | http://43.128.145.141/dms/ （admin / Sh123456，租户 default；sys_admin / Dms@123456 为厂商超管） |
| 移动端 H5 | http://43.128.145.141/dms/mobile/login |
| 平台后台 | http://43.128.145.141/dms/admin/ （admin / Sh123456，token 与前台隔离） |
| 正式环境 | http://8.133.193.238/dms/（**v4.2.9，2026-08-25 22:16 已部署**；后台 /dms/admin/，移动 /dms/mobile/login，统一 80 网关 webgate；部署目录 /opt/dms/prod/，账号 root，容器 dms-prod-{backend,postgres,redis,minio}，profile docker-test；生产报告 docs/02_设计/prod-deploy-report-v4.2.9.md） |
| 记忆体系 | v2.0，五层结构（rules/conventions/lessons/decisions/context），2026-08-16 全面更新 |

### 必备 Skill（2026-08-24 安装/创建）
- `dms-project`：DMS 项目主入口 Skill，固化技术栈、UI 不变量、测试深度、记忆索引和反模式。
- `dms-requirement-intake`：阶段 A 需求准入 Skill，强制 R1/R2、影响范围、边界、验收标准、关键假设和 DMS 业务追问。
- `dms-ux-functional-audit`：阶段 C UI/UX/业务功能审计 Skill，覆盖布局、按钮、表单、表格、权限、状态、业务价值、反向路径和证据要求。
- `qa-skills`：~/.codex/skills/qa-skills/（来源 neonwatty/qa-skills），用于 UX rubric、adversarial、移动端、多用户、性能、安全和独立验证。
- `playwright` / `screenshot`：官方 Skill，用于真实浏览器自动化、截图和网络/UI 断言。
- `computer-use` / `review-loop`：继续用于真实点击验证和交付前代码审查闭环。
- 参考但未启用：`~/.codex/skill-evaluation/superpowers/`、`~/.codex/skill-evaluation/sdd/`。
- 强制执行时机见 AGENTS.md 第 10 节、Layer1 §0.6。

### 技术栈
- 前端：Vue3 + Vite + Element Plus + Pinia + Vue Router（前台 frontend-vue / 后台 admin-vue / 移动 H5 三套）
- 后端：Spring Boot 3.x + Java 17 + Spring Data JPA/Hibernate 为主（MyBatis-Plus 并存）；Flyway 脚本已到 V112（测试/容器 profile enabled=true，默认 profile enabled=false）
- 存储：PostgreSQL（测试容器 postgres:16-alpine，库 dms_test，多租户 tenant_code 隔离）+ Redis 7 + MinIO
- 部署：Docker Compose（nginx/backend/postgres/redis/minio），测试环境对外 80 端口
- 自动化：pytest 五层套件（automation_test/）+ Playwright E2E（tools/p2-e2e/）
- Session 超时：8 小时

### v3.12.4 关键更新（2026-08-15）
- 审批邮件异步化：采购订单提交耗时从 51.3s 降至 0.67s
- EmailLog 实体对齐 V95 迁移（retries/durationMs 字段）
- 全量 548 passed / 0 failed / 0 errors
- 三端 E2E 全通过

### v3.12.3 P2 交付结论（2026-08-14）
- 三端构建全通过：backend mvn package、frontend-vue build、admin-vue build
- 自动化执行 564 条断言/用例级检查，0 失败
- 关键修复：收货单 supplier_name 别名、移动端扫码中文乱码、MFA/TOTP、登录限流、库存盘点、报表订阅、效期预警、序列号追溯、防重复提交、主数据批量删除、日志清理任务
- 定时任务：审批提醒 09:00、通知清理 03:00、日志清理 03:15、合同检查 00:05、报表订阅 08:00；DB 备份每日 02:30（保留14天）

### 已知缺口与后续排期
- P1 排期约 180-250 人日
- 平台后台覆盖率偏低、17 个"模块尚未迁移"前端页
- 移动端审批 Tab
- NF-08 密钥外置轮换（SMTP 授权码等硬编码需外置）
- 生产推送需用户明确指令（遵守 Layer 1 部署铁律）

### docs 文档结构（2026-08-14 整理后）
- 根：README.md、文档索引.md、项目设计文档.md、AI开发文档.md、DMS登录信息手册.md
- 01_需求/DMS需求文档_汇总版.md（唯一需求主文件，v2.0 模块版，16篇+附录）
- 02_设计/：业务功能、数据库、API、审批流、平台后台、UI、运维部署（各一份）+ schema_export SQL
- 03_测试/：测试场景与案例、测试步骤 + 每次独立报告（最新 v3.12.4）
- 04_接管运维/：README、七天接管计划、启动/部署/排障/验收/全景图/需求变更 共8份
- release/：发布基线与发布说明

### 服务器信息
- 新测试服务器：43.128.145.141，SSH ubuntu/Welcomeyyx0616，sudo 免密；测试库登录 `sudo docker exec -it dms-test-postgres psql -U dms -d dms_test`
- 生产服务器：8.133.193.238，SSH root/Welcomeyyx0616，部署路径 /opt/dms/prod（compose v1.29.2），后端仅 127.0.0.1:18080，对外走 webgate 80 端口
- 部署脚本：scripts/deploy_test.py（DMS_DEPLOY_PASSWORD 环境变量）
- 极速部署：.trae/skills/dms-deploy/（deploy-fast.ps1 + remote/*.sh）

## v4.2.1 生产部署备注（2026-08-22）

- 生产主机 8.133.193.238（root），与测试 43.128.145.141（ubuntu）**用户/密码体系不同**，需分开记录凭据；生产库登录先查找带前缀的 `*_dms-prod-postgres` 容器再 `psql -U dms -d dms`。
- 部署目录 /opt/dms/prod/，compose /opt/dms/prod/docker-compose.yml，容器前缀 dms-prod-。
- 部署脚本：仓库 scripts/deploy_prod.py（scripts/deploy_test.py 仅适用于测试环境）。
- 公共域名 /dms/（PC）、/dms/admin/（admin）、/dms/mobile/login（H5）；webgate 容器统一 80 入口，/dms/ 路径 alias 到 /opt/dms/prod/frontend。
- 旧版 v3.12.4 实际 Flyway 已到 V109；本次 4.2.1 升级仅应用 V110/V111/V112 三条幂等迁移。
- 生产后端 profile 仍为 docker-test（沿用历史），与容器内 postgres/redis/minio 主机名配套。
- 本次部署因 /opt/dms/prod/docker-compose.yml 中的 docker-compose v1.29 与 Docker 29.x 不兼容，**降级为 docker start/docker restart**：先启动退出的依赖容器，再 docker restart dms-prod-backend；webgate 转发 502 时 docker exec webgate nginx -s reload 解决。
- 本次生产发布归档：
eleases/dms-4.2.1-prod-deploy-20260822-231940/。

## v4.2.9 生产发布备注（2026-08-25）

- 生产脚本：`scripts/deploy_prod.py`；部署时后端 jar 为 `backend/target/dms-backend.jar`，PC 前端使用 `VITE_BASE=/dms/`，admin 使用 `/dms/admin/`。
- 部署前备份：`/opt/dms/backups/dms-db-pre-v429-20260825-221506.sql.gz`、`/opt/dms/backups/app-prod-20260825-221518.jar`、`/opt/dms/backups/frontend-prod-20260825-221518/`。
- Flyway：V120 在生产执行成功；生产 `resources.path` 已为前端路由形态，因此 V120 的 UPDATE 匹配 0 行，无数据变更。
- 兼容问题：生产 docker-compose v1.29.2 对 backend `--force-recreate` 报 `KeyError: 'ContainerConfig'`，且会停止 postgres/redis/minio；修复方式为 `docker start` 三个依赖容器，等待 healthy 后 `docker rm -f dms-prod-backend`，再执行 `docker-compose -f /opt/dms/prod/docker-compose.yml up -d --no-deps backend`。
- 验证：`/actuator/health` 返回 UP；PC/admin/mobile 入口 200；登录态核心订单 API 200；Playwright 生产 UI 冒烟 9/9 PASS，无 Console 错误和 5xx。
- 清理：删除 8 月 22/24 的旧 jar、旧前端目录和完整旧发布目录；保留 `221518` 与 `021234` 两套回滚备份；Docker 无停止容器、无悬空镜像、构建缓存为 0；根分区 11G/28% → 9.9G/27%。

