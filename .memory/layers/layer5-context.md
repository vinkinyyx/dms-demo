# Layer 5: 当前上下文（临时）

> 会话级临时记忆。最近刷新：2026-08-27（v4.3.0 MINOR 功能包 R1–R9 + v4.3.1 PATCH 走查修复，已部署测试环境，镜像 tag v433；变更包 dms-changes-2026-08-27-full.tar.gz 已同步回仓库）

---

## 当前版本状态（v4.3.1，2026-08-27）

| 项目 | 状态 |
|------|------|
| 当前交付基线 | **v4.3.1**（测试环境后端镜像/产物 tag **v433**；上午 v4.3.0 MINOR 9 需求 R1–R9 + 下午 v4.3.1 PATCH 4 BUG 走查修复；前端 frontend-vue/package.json=4.3.0） |
| 上一基线 | v4.2.9（2026-08-26 生产在跑版本；MINOR 未推生产） |
| Flyway 迁移 | 已到 **V134**（V121–V133 = v4.3.0 功能包；V134 = rma_order_lines.serial_no） |
| 测试环境 | http://43.128.145.141/dms/ 健康 UP；**前端 vite base=/，Nginx 对 /dms/* 做 302 外部重定向到 /*（地址栏会变 / 为预期）**；铁律9要求逐 URL 浏览器首检 |
| 业务前台 | http://43.128.145.141/dms/ （admin / Sh123456，租户 default；sys_admin / Dms@123456 为厂商超管） |
| 移动端 H5 | http://43.128.145.141/dms/mobile/login |
| 平台后台 | http://43.128.145.141/dms/admin/ （admin / Sh123456，token 与前台隔离） |
| 正式环境 | http://8.133.193.238/dms/（**仍为 v4.2.9**；v4.3.x 未推生产，需用户明确指令） |
| 记忆体系 | v2.0，五层结构（rules/conventions/lessons/decisions/context） |

### v4.3.0 MINOR 功能包（R1–R9，2026-08-27，Flyway V121–V133）
- **R1 销退单关联多张出库单**：rma_order_outbound_refs / rma_order_lines 关系化，按来源出库单行锁定可退量（已退+在途+本次），跨单/跨经销商不可挪用；审批后按来源行回写库存。
- **R2 客户多联系人/多地址**：dealer_contacts、dealer_addresses（V124），默认联系人/地址；下单选地址并写快照；历史 dealers 单联系人回填默认联系人。
- **R3 V4 计价引擎**：定价优先级链 合同价 → 客户基础价/全局价 → 产品全局折扣 → 促销 → 行手动折扣 → 客户全局折扣 → 整单手动折扣；模式 NORMAL/FIXED_PRICE（一口价）/ZERO_ORDER（整单0）/VOUCHER（代金券）；POST /api/v4/calc/preview 四模式；提交后端重算落库、价格快照（V128），重开不依赖实时查价。
- **R4 促销增强**：新增 QTY_DISCOUNT（满N件打折/固定单价）、QTY_REDUCE（满N件减额）、GIFT 满赠（V131）；同 SKU 同时段唯一、拆多行拦截、命中行禁行手动折扣/禁0金额；order_promotion_hits 落库 + 文案。
- **R5 代金券**：customer_vouchers + customer_voucher_usage（V127）；厂家批量发放，一单一张、整单层抵扣不摊行（防退货套现）；面值>原价合计/过期/停用/他人券拦截；ISSUED→USED（下单占用）→审批通过核销/审批拒绝释放。
- **R6 产品全局折扣** product_global_discounts（V126，PC 维护页，时段不重叠、只减）。
- **R7 客户全局折扣** dealer_global_discounts（V126，整单层只减、按行折后金额占比摊回行）。
- **R8 合同价格** contract_prices（V126）：合同工作台价格明细 Tab，计价最高优先级。
- **R9 客户自助注册/自助下单**：customer_registrations 审批流（V129）+ H5 注册页 + 客户角色 RBAC（V130/V133，后端强制 dealer_id 数据隔离，越权 403）；H5 自助下单全流程。
- 数量整型（V123，销售订单 qty 与 BOM 子件用量改 INTEGER）；后端新增模块 v4(V4PriceEngine)、voucher、user/registration、authz(SalesScopeService)、contract(ContractPrice)、masterdata(Contact/Address/GlobalDiscount)、rma(Line/Ref/Portal)。

### v4.3.1 PATCH 走查修复（2026-08-27，Flyway V134）
- **BUG-A（Critical）代金券审批拒绝不返还**：SalesOrderApprovalCallback/SalesReturnApprovalCallback 拒绝/退回/撤回分支补 oucherService.release(businessId)，券 USED→ISSUED；SalesReturnApprovalCallback 注入 V4OrderService 用 @Lazy 解循环依赖，新增 backend/lombok.config（lombok.copyableAnnotations+=@Lazy）。
- **BUG-B（高）销退新建页返工**：字段顺序定稿 经销商 → 发货仓库 → 发货单 → 退货原因；外键全部 el-select 远程搜索（禁自由文本）；未选经销商/仓库时「选择出库单」按钮 disabled 门禁；出库单弹窗恢复批号 batchNo/序列号 serialNo/仓库筛选并接后端参数；发货单按发货仓库过滤 + 同仓库校验。
- **BUG-C（中）销退行 serialNo 回显**：RmaOrderLine 增 serialNo + V134 + 前端回显。
- **BUG-D（高）销售订单重开回显**：OrderCreate.makeLine 兼容 p.productId ?? p.id ?? null；MOrderCreate/ResourcePicker 同步；dealerName 回显，修复 SO-20260827-00003 重开经销商为空/价格报错。
- 验证：测试环境部署 v433，TRAE-browseruse 真实浏览器铁律9 URL 首检（/、/dms/、/dms/admin/、/dms/mobile/login、/actuator/health）+ 4 BUG 复测全过；详见 docs/03_测试/测试报告_v4.3.0_20260827.md。

### v4.3.x 规则沉淀（已入 AGENTS.md / project_rules.md）
- **铁律9（防回归规则七）**：部署后首检必须用真实浏览器逐条验证文档中所有用户入口 URL（/、/dms/、/dms/admin/、/dms/mobile/login、/actuator/health），DOM refs≥20、Console 无红错、Network 无 5xx；禁止只凭容器 Running / health UP / API 200 宣称部署成功。
- **页面重写/改造功能对照规则**：重写前必须盘点旧页面全部筛选/列/按钮/选择器/弹窗字段，禁止功能减法；外键引用一律 el-select/资源弹窗，禁止自由文本；业务前置条件用按钮 disabled 门禁；后端支持的筛选参数前端必须挂筛选框；验收必须覆盖回显页。
- **前端部署路径契约**：VITE_BASE=/ 则 /dms/* 必须 302 外部重定向到 /*（非内部 rewrite，否则 Vue Router base 不匹配跳 /error/404）；VITE_BASE=/dms/ 则文件放 dms 子目录 + alias 直供；两方案不可混用。
- 报表 SQL 禁令：禁止嵌套相关子查询，必须 WITH CTE + LEFT JOIN。
- 打包：Windows 给 Linux 的压缩包用 	ar -a -cf（正斜杠路径），勿用 Compress-Archive（反斜杠毁目录）。

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
- 后端：Spring Boot 3.x + Java 17 + Spring Data JPA/Hibernate 为主（MyBatis-Plus 并存）；Flyway 脚本已到 V134（测试/容器 profile enabled=true，默认 profile enabled=false）
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

