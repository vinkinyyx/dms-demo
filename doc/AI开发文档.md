# AI 开发文档（快照指针）

> 本文件为跨会话快速加载入口。**本项目文档主目录为 `docs/`**，完整 AI 上下文快照维护在：
>
> **👉 [../docs/AI开发文档.md](../docs/AI开发文档.md)**（技术栈、目录结构、编码规范、模块信息、第三方依赖、历次修改记录、踩坑点、本地启动配置）

## 当前版本速览（2026-08-31）

- 当前版本：**v4.5.2**（v4.5.0 = 厂家进销存开关持久化 + 厂家↔经销商跨租户订单协同；v4.5.1 = 协同双层自动化回归补齐（端到端 56 例 + 集成 11 例）+ 分批/幂等/幽灵库存缺陷修复；**v4.5.2 = 接入 GitHub Actions CI 流水线（.github/workflows/ci.yml）+ 全量测试基线修复 12 例，后端 147/147 全绿、前端 vitest 36 例 + build 通过**；v4.5.x 未部署测试环境生产链路（仅 CI/测试代码变更，无 Flyway/接口变更）；**生产环境最新已推送 v4.4.7（2026-08-30），v4.5.x 推送待用户明确指令**）
- Flyway：**V121–V142**（V140 = v4.5.0 跨租户协同：suppliers.manufacturer_tenant_id / purchase_orders.vendor_order_code / orders.customer_po_code / cross_tenant_doc_links 台账 + 厂家进销存开关固化；V141 = 演示数据迁移补回本地；V142 = collab 部分发货多链路索引 ux→ix 非唯一；Current=142）
- 技术栈：Spring Boot 3.2 + Java 17 + JPA/Hibernate + Flyway + PostgreSQL 14 + Redis 7 + MinIO；Vue 3 + Vite 5 + Element Plus（PC）+ Vant 4（H5）+ Pinia；Docker Compose（backend/nginx/postgres/redis/minio 5 容器）；后端集成测试 zonky embedded-postgres 2.0.7（内嵌 PG 14.10）
- 测试环境：**http://dms-dev.mysolmed.com**（域名 → 43.128.145.141，裸域名 302→`/dms/`；IP 直连同样可用；ubuntu / Welcomeyyx0616；浏览器登录 sys_admin / Dms@123456，业务前台 admin / Sh123456，平台后台 admin / Sh123456）
- 生产环境：http://8.133.193.238/dms/

## 今日（2026-08-31）变更要点

- **v4.5.2（接入 GitHub Actions CI 流水线 + 全量测试基线修复）**：①新建 `.github/workflows/ci.yml`——push/PR 到 main 触发，双并行 job：`backend-test`（ubuntu + JDK17 temurin + maven 缓存，backend 目录 `mvn -B verify`，失败也上传 surefire-reports）、`frontend-build`（Node 20 + npm 缓存，`npm ci` → vitest 单测 → vite build，失败也上传 dist）；CI 无需 Docker/PG/Redis（内嵌 PG14 + Redis Mock）。②全量 `mvn test` 暴露并修复 12 例基线失败：11 例旧 Controller 集成测试造用户未授权致 403（补 `grantPermissions` 显式权限码；注意 `"*"` 是字面码非通配、`isNotIn(404,5xx)` 断言不含 403 会假性通过）+ 1 例 V4CalculatorTest 百分比折数造数过时（v4.4.7 折数语义下填 20=减80%，改填 80=减20%=折扣额 200）。③验证：后端 **Tests run 147, Failures 0, Errors 0**；前端经测试服务器（node v20.20.2，与 CI 等价）实测 npm ci 299 包、vitest **36/36**、vite build 成功（28.51s）。④**CI 激活需 git commit + push 工作流文件到 GitHub**（vinkinyyx/dms-demo main）；本次仅测试代码 + CI 配置，无 Flyway/接口/生产逻辑变更，未部署任何环境。最新踩坑点：第 55–57 条。
- **v4.5.1（跨租户协同自动化回归补齐 + 缺陷修复）**：v4.5.0 首版仅手工测试，本次补齐双层自动化并修 5 项缺陷。①**端到端脚本** `automation_test/v450_collab.py`（接口调用 + DB 回读，TAG 标记种子数据并自清理，覆盖 10 个缺口：路径A/B 正常链路、重复幂等、未对码/未绑定阻断、分批累计、路径A台账复用、未绑定静默跳过）**56/56 通过**；②**后端集成测试** `CrossTenantCollabIntegrationTest`（zonky 内嵌 PG14 + Flyway 全量，TenantContext 切双租户直调 CrossTenantCollabService）路径A 5 例 + 路径B 6 例 **11/11 通过**，既有 Inventory 7/Order 7/SalesReturn 2/Chain 4 共 20 例回归全绿，**合计 31/31**；③修复：二次部分发货整笔跳过（改 outLineId 行级幂等）、分批重复补 PO（linkRowBySalesOrder/linkRowBySalesOut 先复用 + upsertPoLineQty 累计 5+3=8）、purchase_order_lines 无效列 updated_at、收货单缺 dealer_id/warehouse_id 幽灵库存（自动补建 DEALER-SELF 主体 + COLLAB-DEFAULT-WH 默认仓）、测试基建撞 V136 NOT NULL（工厂 + 6 处 Dealer.builder() 补 4 字段）；④Flyway V141/V142。详见 CHANGELOG v4.5.1 与测试报告 §8。
- 最新踩坑点：第 52–54 条（**Flyway 新增 NOT NULL 列后 Hibernate 显式插 null 不走 DEFAULT，所有 builder 造数点必须显式赋值**；跨租户联动幂等键必须行级、补单先复用后新建、下游单据外键带全防幽灵库存；embedded-postgres 集成测试范式与 surefire 报告读取）。

## 2026-08-28 变更要点

- **v4.4.2（品牌与域名）**：①全站替换 MySolMed 品牌 logo（藏青 #0B2545「m」标 + 青色圆点）——frontend-vue 与 admin-vue 的 `DmsLogo.vue` 改为 `<img>` 方案（`src/assets/brand/logo-mark.png` / `logo-mark-white.png`，inverse 白标用于深色侧边栏/登录页），两项目 index.html 增加 favicon-16/32/48 + apple-touch-icon、theme-color 改 #0B2545、标题改「MySolMed DMS …」；覆盖 PC 登录页/工作台首页/侧边栏、移动 H5 登录页、平台后台登录页与布局共 7 个使用点。②测试环境启用域名 `dms-dev.mysolmed.com`（DNS 已解析到 43.128.145.141；nginx `server_name` 早已含该域名）；根路径 `/` 由"返回宣传手册 landing"改为 **302 → `/dms/`**（测试环境直达 DMS），宣传手册保留在 `/brochure/`；后端 `APP_BASE_URL` 改为 `http://dms-dev.mysolmed.com/dms`（审批邮件链接）。③**nginx 变更管控规则落档**（见 AGENTS.md）：禁随意调整，改动须备份→最小 diff→`nginx -t`→reload/重启→容器内 `nginx -T` 取证；注意 **bind-mount 下 `sed -i` 换 inode 后必须 restart 容器**（reload 不生效，本次实测踩坑）。
- 验证：铁律 9 真实浏览器全过——裸域名 → 302 → `/dms/home` 工作台；PC 首页 2 logo、移动 H5 登录页 1 logo、后台 1 logo 均 `naturalWidth=512`；`/api/auth/login` 200；全入口 curl 200；Console 无 error。

- **v4.4.0（R1–R7，MINOR 寄售业务闭环）**：寄售补货/开票/红冲订单类型与寄售开关、寄售台账 consignment_stock + movements 五类流水（REPLENISH_IN/INVOICE_LOCK/INVOICE_DEDUCT/INVOICE_RELEASE/REPLENISH_OUT）、经销商资信模块 dealer_credit_profiles、进销存报表精细化、资源选择器；Flyway V135–V137（含 RMA/INVOICE_ORDER 审批模板）。
- **v4.4.1（PATCH，BUG-01~04 + 红字补货 + 拣选交互方案 A）**：①BUG-01 补货→寄售入库链路三缺口修复（红字补货建单 SOR/validateReplenishRed、ux_sales_serial 改部分唯一索引豁免红字行、SalesOutService 钩子拼写 `"REPLENISH"`→`"REPLENISHMENT"`）；②BUG-02 供应商门户菜单 inventoryOnly 补全；③BUG-03 CrudView 浮动 Promise 补 catch；④BUG-04 官网 landing 图片 404 修复；⑤开票寄售库存拣选弹窗（OrderCreate.vue：经销商 ResourcePicker 门禁、整行勾选、序列号限 1、实时汇总、整单替换、el-tag 回显）；Flyway V138–V139。
- 验证：E2E scripts/e2e_invoice_consignment.py **23/23 全绿**（开票闭环 4 场景 + 红字补货 9 项检查）；铁律 9 真实浏览器门禁全 PASS（/、/brochure/、/dms/、登录→工作台、寄售台账列表、H5 供应商、订单新增→拣选弹窗全链路，弹窗实测勾选 B2608-B2 应付 ¥470.40）；测试库存 seed_consignment.py 9 行台账。
- 最新踩坑点：第 48–51 条（红冲共用序列号须改部分唯一索引且所有 INSERT 绑定 is_red、字符串字面量分支须与枚举逐字核对、红单重建行后回调须重取行 ID、integrated_code_mode Exec V8 沙箱范式）。

## 新会话加载顺序

1. 本文件 → `docs/AI开发文档.md`（完整上下文）
2. `AGENTS.md` + `.trae/rules/project_rules.md`（铁律与部署规则）
3. `docs/项目设计文档.md`（架构与版本变更记录）
4. `docs/文档索引.md`（全部文档导航）
