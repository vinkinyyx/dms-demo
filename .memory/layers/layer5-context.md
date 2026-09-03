> 会话级临时记忆。最近刷新：2026-09-03 晚（**v4.6.2 已部署测试环境 dms-dev 并验证**）。Flyway V147 迁移成功（authorizations.status 扩 VARCHAR(32)、115 租户播种 order.authz.enforce=false、2 索引）。集成测试 AuthorizationServiceIntegrationTest 12/12 通过（开关两态/产品线/SALES_TO_HOSPITAL 终端/排他/续约/终止通过+驳回/缺参/开关一致性）。测试驱动修复 2 真 bug：①status VARCHAR(16) 装不下 terminate_pending(17) → V147 扩 32；②**开关写后读不到**——租户配置读缓存 key 带 tenant: 前缀、写后失效却用全局 key，缓存未失效，新增 SystemSettingService.evictTenant() 按租户 key 失效。铁律9 GATE 真实浏览器 8 入口全 200/302、宣传页 title 正确、授权页/合同页 DOM 完整、0 console 红错/0 5xx。API 实测：开关翻转读写一致；开→未授权 check 返回 authorized=false「产品所属产品线未授权」，关→放行；产品线/终端选择器、区域子树过滤(regionId=1→8 家)正常；跨经销商排他冲突拦截且报错带已授权经销商名；pending_approval 终止被门禁拒。测试授权 id=43 已软删清理。自动化脚本 automation_test/v462_authz_gate.cjs。**注意**：该脚本自动填表登录未成功（表单结构选择器问题），5 个 API 401 为脚本侧假阴性，API 已用真实 token 手工验证通过；生产未动。
> 会话级临时记忆。最近刷新：2026-09-03（**v4.6.2 授权模块产品线化重构 + 授权-下单挂钩开关 + 合同/授权终止续约**，Flyway V147，未部署）。要点：①R1 授权-下单租户级开关 `order.authz.enforce`（system_settings scope=tenant，默认 false=解耦），SystemSettingService 扩展租户读写（缓存 key 带租户），AuthorizationService.isOrderAuthzEnforced 统一判定；关闭→下单/出库跳过授权校验，开启→无授权拦截。接口 GET/POST /api/authorizations/order-enforce，授权列表页顶部开关。②R2 授权=厂家→经销商，维度 经销商+产品线(product_lines CSV 多选)+终端医院(terminal_ids CSV 多选)+有效期；**修复 check() 通配误放行**：原 product_id=null 恒 true 短路分类校验，现按订单产品 product_line_id 命中 product_lines，ORDER 只校验产品线、SALES_TO_HOSPITAL 加校验终端；DTO Line 加 productLineId。③R3 医院批量选：GET /api/authorizations/terminals(regions 递归子树+hospitals.region_id，支持按省筛选+整省全选)、/product-lines。④R4 排他：同医院×同产品线时间段重叠不得授权给不同经销商，assertNoOverlap 查 pending_approval/active/not_started 跨经销商集合交集预检，冲突报已授权经销商名；同经销商续约放行。⑤R5/R6/R7：授权终止 POST /{id}/terminate（AUTHORIZATION_TERMINATE 审批，通过 terminated，驳回恢复 active/not_started）；授权续约 POST /{id}/renew（复制维度+新有效期走审批 source=renew）；合同终止 POST /api/contracts/{id}/terminate（CONTRACT_TERMINATE，通过 terminated 驳回恢复 effective）。回调成状态唯一落库点，Controller 不再 markApproved/markRejected 双写，回调补 @Transactional、revision action 语义修正。⑥R8 越权修复：ContractService.get(id) 与授权 getOwned 加租户校验；授权删除限草稿/驳回；下单/出库授权报错带产品编码名称；新增 AuthorizationExpiryTask(每日00:10 active 过期置 expired)。⑦前端 views/authorization/ 三页+api.js+路由 /authorizations*+菜单接通；合同详情加发起终止。验证：mvn -o compile 通过、frontend npm run build 通过。**未做**：TempAuthorization 半成品未动；V147 未在测试环境执行；铁律9 GATE 与授权开关两态 5 维验证待跑。
# Layer 5: 当前上下文（临时）
> 会话级临时记忆。最近刷新：2026-09-01（**v4.6.0 统一测试框架重构完成**，不改业务代码、无 Flyway）。要点：①后端删零引用 H2，PG 集成统一用已在用的 zonky 嵌入式 PostgreSQL14（免 Docker），新增 Testcontainers 仅起真实 Redis7 的 `*IT`（`backend/src/test/java/com/dms/it/`，failsafe 跑，无 Docker 时 assumeTrue 自动跳过，CI 用 `BACKEND_IT=1` 真实执行），补「登录限流/token 黑名单 Redis 长期 mock」盲区；pom 加 surefire/failsafe 3.2.5 + jacoco 0.8.12。②前端新增唯一金额工具 `frontend-vue/src/utils/money.js`（元/分整数、`apportion` 按行分摊守恒不超额不负数）+ `money.spec.js` 9 用例（D1~D8），vitest.config 加 utils 覆盖率门禁 60%。③黑盒统一 **Playwright Test**：`tests/` 五 project（api/pc/admin/mobile/gate），`helpers/env.js`（地址账号+铁律9八入口唯一来源）、`helpers/db.js`（pg 回读，PGHOST 未配则 skip）、`fixtures/test-fixtures.js`（authedApi/authedPage+Console/Network 错误守卫）；铁律9 部署 GATE 固化为 `tests/gate/deploy-gate.spec.js`；Python api_smoke 与散装 .cjs 退出主编排（文件留档）。④性能 k6 `tests/performance/smoke-load.js`（p95<800ms），安全 ZAP `tests/security/README.md`。⑤根 package.json 脚本统一（test:gate/api/ui/e2e/backend:it/frontend:cov/perf/security），`tools/run-tests.js` 重排 L1静态→L2后端→L3前端→L4 Playwright（`BACKEND_IT=1`/`SKIP_E2E=1`/`--module` 透传）。验证：`mvn test-compile` 通过、vitest 9 用例通过、`playwright --list` 收集 19 用例/5 project。**本机无 Docker**：真实 Redis IT、ZAP、k6 需在有 Docker/装 k6 的 CI/部署机跑；L4 Playwright 需被测环境可达（默认 https://dms-dev.mysolmed.com）。文档(铁律11)：CHANGELOG v4.6.0、`docs/02_设计/test-strategy.md` §9-11、layer3 L79、layer4 D37。
> 会话级临时记忆。最近刷新：2026-09-01（**v4.5.7 验收补丁已部署测试环境**，2026-09-01 23:04 前端-only，备份 /opt/dms/backups/frontend-dms-20260901-230428，未动 nginx/后端/admin）。用户验收三点：①首页「常用功能」图标不好看→`MHome.vue` 改内联线性 SVG（ICONS map：surgery 剪贴板心搏/orders 单据/approvals 勾选框/dashboard 折线图/messages 气泡/profile 用户，v-html 渲染），`.m-quick-ic` 44px+svg 23px（app.scss）；②入口精简 8→6，删「扫码收货」「库存查询」（页面文件保留）；③报台填批序信息不能扫码→新增 `mobile/components/BarcodeScanner.vue`（底部弹窗 78%，getUserMedia(facingMode:environment)+BarcodeDetector 循环，关闭释放 tracks，手动输入框常驻，emit scanned），`MSurgeryReportCreate.vue` 批号/序列号均挂扫码按钮（原仅序列号可扫）+ onScanned 回填 line.batchNo。**关键限制：getUserMedia 仅安全上下文(HTTPS/localhost)可用，测试站纯 HTTP 真机无法调摄像头**；组件检测 navigator.mediaDevices 缺失即显示「当前环境无法调用摄像头…请手动输入或改用 HTTPS」降级提示；真机扫码需配 nginx TLS（铁律10 本轮未动 nginx）。铁律9 GATE 通过：8 入口+health 全 200、0 console error、0 API 4xx/5xx；localhost 扫码弹窗 video 元素存在+手动回填 OK；HTTP 环境降级提示+手动回填 OK（tmp-changes/verify_done2.py、verify_deployed.py，截图 tmp-changes/deploy-gate-shots/）。文档(铁律11)：CHANGELOG v4.5.7 补丁段、本 layer5、layer3 新增 getUserMedia 安全上下文教训。
> 会话级临时记忆。最近刷新：2026-09-01（**v4.5.7 移动端 H5 简约重设计已部署测试环境（2026-09-01 22:08，前端-only：frontend-vue VITE_BASE=/dms/ 构建产物 tar 上传解压至 /opt/dms/test/frontend/dms，保留 admin/ 子目录，未动 nginx/后端；服务器备份 /opt/dms/backups/frontend-dms-20260901-220838；新 bundle index-CRlupef7.js）。铁律9 GATE 真实浏览器通过**：root→/dms/login、/dms/、/dms/admin/、/dms/mobile/login、/dms/mobile/register、/brochure/（domcontentloaded，networkidle 因常驻动画超时属正常）、/brochure/mobile.html、/brochure/print.html 全 200，/actuator/health UP；DEALER_D1 登录后 8 个移动页（home/orders/surgery-reports/approvals/messages/profile/dashboard/smart-order）Console 0 红错、API 0 4xx/5xx，截图 tmp-changes/deploy-gate-shots/。，纯前端无 Flyway）。背景：用户反馈移动端「太臃肿」要求简约直观。改动 14 文件（+280/-204）：①`app.scss` 新增移动端统一类（`.m-hero` 渐变头部+safe-top、`.m-sec-title`、`.m-primary-actions/.m-primary-btn` 双主操作、`.m-stats` 概览数字、`.m-quick-grid` 单色蓝底快捷入口替代彩虹色、`.m-list-card` 白卡列表、`.m-amt/.m-sub`），全部引用既有 token 不引新依赖；②`MHome.vue` 重写为「头部问候+铃铛消息入口(van-badge→/mobile/messages) → 2 主操作(智能下单/下销售订单) → 本月概览 2 数字(点击→/mobile/dashboard) → 8 单色常用功能 → 最近订单」，删彩虹配色/SurgeryIcon/今日业绩冗余块；③`MLayout.vue` tabbar 6→5（首页/订单/报台/审批/我的），移除消息 tab（MMessages 页保留，铃铛可达），仅留 approvalBadge；④列表页 MOrders/MSurgeryReports/MApprovals/MMessages 统一 pull-refresh+m-list-card 去硬编码色；⑤详情/我的/业绩 MOrderDetail/MApprovalDetail/MProfile/MDashboard 硬编码色→token（--dms-divider-color/--dms-gray-200/400/--dms-text-1/--dms-bg-container），MProfile 渐变头部+safe-top；⑥表单 MOrderCreate/MSmartOrder/ReceiptConfirm 轻量归一（头像 #52c41a→var(--dms-blue-300)）。语义色保留：促销橙/代金券红/扫码黑/登录页深色渐变未动。验证：`npm run build` exit 0；Playwright（393×852，账号 DEALER_D1/dealer_d1_admin/Sh123456，vite dev 代理 VITE_API_TARGET=http://dms-dev.mysolmed.com）审计 11 页（home/orders/surgery-reports/approvals/messages/profile/dashboard/smart-order/orders/create/scan-receive/scan-inventory）DOM 正常、**Console 0 红错、API 0 4xx/5xx**，截图在 `tmp-changes/mobile-shots/`，审计脚本 `tmp-changes/mobile_audit.py`，原文件备份 `tmp-changes/*.bak`。**待办**：部署测试环境需用户明确指令；部署后按铁律9 重跑浏览器 GATE（/dms/mobile/login、/dms/mobile/register 等 8 入口）。CHANGELOG 已记 v4.5.7。

> 会话级临时记忆。最近刷新：2026-08-31（**v4.5.4 已发布生产并完成本地/测试/生产三环境深度清理**）。生产（8.133.193.238）发布：Flyway V140/V141/V142 全部 success、cross_tenant_doc_links 已建，后端 dms-prod-backend healthy、/actuator/health UP、/dms/ 200；发布前备份 /opt/dms/backups/dms-prod-pre-v454-20260831-214628.dump + app-prod-20260831-214701.jar + frontend-prod-20260831-220245（回滚三件套，清理后**保留**）。**生产清理**：删 8/27 v432、8/30 v447 旧 DB 备份 + app-prod-20260830-202629.jar + frontend-prod-20260830-202629，备份目录 243M→121M；truncate dms-prod-backend/postgres/redis/minio 四容器 json 日志；**未动任何业务数据**。**测试环境清理**：E2E 协同单据全删（台账 cross_tenant_doc_links id30-33；采退 RP-20260831-00001/00002 id266/267、厂家红字销退 SO-20260831-00001/00002 id306/307、红字出库 RGI id140/141+批次103/104+batch_lines、厂家红字入库 RGR id284/285+receipt_lines、红字库存流水 inventory_transactions id702/703、approval_instances 139/140 及 approval_records、26 条 E2E op_log），**种子库存 BATCH-CR-001（inventory id4202，经销商 A1 产品26）保留作演示**；Flyway V140-142 完好、协同表结构在、台账归零。文件层：备份目录 2.5G→366M（仅留最新2 jar app-20260831-213902/212345、frontend-dms-20260831-165944、2 个 SQL dump），truncate 五容器日志，/tmp 无残留。**本地清理**：backend/target 删除、%TEMP% 下全部 dms_* 部署/E2E 脚本与历史 dms*/dmspdf* 临时目录清零（0 残留）、automation_test 3 个 v424 旧 .log 删除。清理后两环境 /actuator/health 均 UP、前端 /dms/ 200。
> 会话级临时记忆。最近刷新：2026-08-31（**v4.5.4 跨租户反向退货协同已部署测试环境并端到端验证通过** http://dms-dev.mysolmed.com/dms/，后端 app.jar 备份戳 20260831-212345，纯后端无 Flyway，仅 force-recreate backend-test，未动 nginx/前端）。**铁律9**：/dms/ /dms/admin/ /dms/mobile/login /actuator/health 全 200。**端到端（DEALER_A1 dealer_a1_admin/Dms@123456 ↔ MFR_A mfr_a1_admin/Dms@123456，真实 API+DB 回读）**：A1 造库存（仓25、对码产品26=厂家25、批次 BATCH-CR-001 合格50，inventory id 4202）→ POST /purchase-returns 建采退 RP-20260831-00001(id266,供应商=平台厂家9,仓25,产品26退5) → POST submit（PURCHASE_RETURN 审批模板**自动审批** autoApproved=true → APPROVED → PurchaseReturnApprovalCallback 自动生成红字出库 RGI-20260831-00001 id140 DRAFT）→ 路径C 同步生成厂家红字销退草稿 SO-20260831-00001(id306, orders.is_red=true DRAFT, 行产品=厂家25数量5, customer_po_code=RP-...) 台账 link_type=PR_TO_RED_SALES_ORDER(id30)，采退 vendor_order_code 回写；→ 红字出库发货：POST /api/sales-outs/140/batches（**返回无 id 字段，只有 code/seq，需查 sales_out_batches 表取 id=103**）→ PUT /api/sales-out-batches/103（lines[{expectedLineId=302,productId=26,warehouseId=25,qty=5,batchNo=BATCH-CR-001}]）→ POST confirm → 出库 COMPLETED、A1 库存 50→45（deductBatch isRed 扣减）、路径D 生成厂家红字销退入库 RGR-20260831-00001(id284, receipts.is_red=true receipt_type=SALES_RETURN ref_doc_type=sales_return ref_doc_id=306 status=PENDING dealer_id=18 warehouse_id=26, 行 product=25 批次 BATCH-CR-001 透传 expected_qty=5)，台账 link_type=RED_OUT_TO_RED_RECEIPT(id31)。**四张单据终态**：经销商采退 COMPLETED / 厂家红字销退 DRAFT(待厂家人工审批) / 经销商红字出库 COMPLETED / 厂家红字入库 PENDING(待厂家收货确认)。单测 CrossTenantCollabIntegrationTest 17/17 通过。**坑/备注**：①createBatch 响应 data 只有 {code,seq,status} 无 id，自动化需按 code 查 sales_out_batches.id；②红字出库发货走 SalesOutBatchService.confirmBatch（partialShip 明确"红字单不允许再发货"），路径D 钩子挂这里；③E2E 单据保留未清理（证据，台账 id30/31，单据 266/306/140/284）；④厂家红字入库确认后按既有红字销退入库流程 +PENDING 待检（收到退回重新质检）。接口文档 docs/03_接口文档/cross-tenant-collab-api.md（含正向A/B+反向C/D）。**生产未发布**。
> 会话级临时记忆。最近刷新：2026-08-31（**v4.5.4 跨租户反向退货协同已完成编码+单测，本地 17/17 通过，尚未部署测试环境**）。在 v4.5.0 正向（采购→销售、出库→收货）基础上补齐退货方向，逐表对称：**路径C** 经销商采退单（purchase_orders.is_red=true，供应商=平台厂家）提交 → 厂家红字销退订单草稿（orders.is_red=true, DRAFT, 金额0，对码转厂家产品），回写 purchase_orders.vendor_order_code，台账 link_type=**PR_TO_RED_SALES_ORDER**；**路径D** 经销商红字销售出库（采退审批后 AutoDocGenerator 生成的 RGI，sales_outs.is_red=true）批次发货 confirmBatch → 厂家红字销退入库单（receipts.is_red=true, receipt_type=SALES_RETURN, ref_doc_type=sales_return, ref_doc_id=红字销退单, status=PENDING），产品转厂家产品/批次序列号透传，台账 link_type=**RED_OUT_TO_RED_RECEIPT**，按"出库单+发货执行行 outLineId"幂等。钩子：PurchaseReturnController.submit（采退提交）、SalesOutBatchService.confirmBatch（红字发货，is_red 分流，收集 ShippedLine）。**关键决策**：厂家销退落 orders.is_red 红字销退单而非 rma_orders——因为只有红字销售订单审批后才经 AutoDocGenerator.createSalesOutForOrder 生成红字出库单(GIR)并走发货流程；rma_orders 审批即直接回写库存、无"发货"环节，无法承接用户要求的"红字出库单发货后回调"。红字库存冲销仍由各租户既有红字流程负责（经销商红字出库 deductBatch(isRed) 扣任意状态库存、厂家红字入库确认 +PENDING 待检=收到退回重新质检），跨租户层只建对方红字单据不重复记库存。**无 Flyway**（复用 orders.is_red/receipts.is_red/cross_tenant_doc_links 现有列）。测试：CrossTenantCollabIntegrationTest 新增6反向用例（转单/幂等/对码阻断、红字入库生成/幂等/无路径C台账静默跳过）共17/17。**待办**：部署测试环境后铁律9浏览器+端到端验证（default↔DEALER_D1 或 MFR_A↔DEALER_A1 跑 采退→厂家销退→红字出库发货→厂家红字入库）；接口文档已更新 docs/03_接口文档/cross-tenant-collab-api.md（补3B反向章节+触发点+幂等+边界）；CHANGELOG v4.5.4。**生产未发布**。
> 会话级临时记忆。最近刷新：2026-08-30（**v4.5.1 PATCH 已部署测试环境** http://dms-dev.mysolmed.com/dms/，备份戳 20260830-234851，**Flyway V141 已 apply**）。修复"sys_admin(default 厂家)产品对码页为空"：根因不是前端/接口 Bug，而是 v4.5.0 跨租户演示数据(MFR_A↔DEALER_A1/A2、MYSR/JXA/COLLAB 对码)全建在专用演示厂家 **MFR_A** 租户，而 sys_admin 登录的 **default 厂家没有归属它的经销商租户、也没有任何 product_mappings**；产品对码页/`/api/product-mappings` 与 `/api/my-dealer-tenants` 按设计做租户隔离(manufacturer_tenant_id=当前厂家、owner_manufacturer_id=当前厂家)，所以 default 正确返回 0 条。修复=Flyway **V141__default_mfr_dealer_demo.sql**（仿 V52 种子，幂等）：为 default 厂家新建归属它的经销商租户 **DEALER_D1**（id 22222222-1111-0000-0000-000000000001、owner=default，含 dealer 主数据 D-D1、tenant_dealer_bindings、管理员 **dealer_d1_admin/Sh123456 首登强制改密**、DEALER 角色/菜单/策略/数据权限）+ 5 个经销商产品 DD1-P001..P005 + **5 条对码**（PRD-T001/T003/T005、PRD-S001/S002 ↔ DD1-P001..P005，box/换算率1，S002↔P005 为 disabled 演示停用）。验证：default/sys_admin `/api/product-mappings` total=5（中文产品名/租户名齐全）、my-dealer-tenants=1；Playwright `/dms/product-mappings` 渲染 5 行无红错无 5xx（截图 automation_test/v4-browser-results/v451-product-mappings-default.png）；MFR_A 仍 15 条不受影响；铁律9 health UP。**注意**：default 的对码物料 PRD-* 目前仅主数据、无库存/销售价，若要用 default↔DEALER_D1 跑跨租户下单→出库→收货全链路，需补 default 仓库库存+销售价（端到端仍用 MFR_A↔DEALER_A1 的 COLLAB-* 已验证）。文档(铁律11)：CHANGELOG v4.5.1、登录手册加 dealer_d1_admin、本 layer5；V141 纯演示数据种子无表结构变更。**生产未发布**（生产仍 V139/v4.4.7）。

> 会话级临时记忆。最近刷新：2026-08-30（**v4.5.0 MINOR 已部署测试环境并端到端验证通过 + 铁律11 文档全部补齐** http://dms-dev.mysolmed.com/dms/，备份戳 20260830-222106，**Flyway V140 已 apply**：①厂家进销存菜单开关持久化（MANUFACTURER 租户 modules_enabled.inventoryEnabled/purchaseEnabled=false），sys_admin 左侧仅留 销售订单/销退/销售出库/寄售库存，dealer 用户不受限；②厂家↔经销商跨租户协同——经销商采购单(供应商=平台厂家 suppliers.manufacturer_tenant_id)提交→厂家草稿销售单(对码转码/customer_po_code/回写 purchase_orders.vendor_order_code)；厂家 partialShip 出库→经销商待收货 receipt(不带价、带批次序列号、source_po_id)，无采购单则自动补建 APPROVED 采购单(路径B)；新增 cross_tenant_doc_links 台账(sales_out_id/po_id 唯一→幂等)；对码缺失阻断整笔回滚；com.dms.collab.CrossTenantCollabService 挂 PurchaseOrderService.submit 与 SalesOutService.partialShip；前端采购单列表加"厂家销售单号"列。坑：Hibernate 原生 SQL 不能用 '{}'::jsonb（:: 与命名参数冲突），用 CAST('{}' AS jsonb)。**文档(铁律11)已全部同步**：需求汇总 §9.1/§14A/§31.1+版本头v2.2、业务功能设计 v4.5.0 章节、数据库设计 V140 章节、API设计 v4.5.0 章节、**新增接口文档 docs/03_接口文档/cross-tenant-collab-api.md**、文档索引登记接口文档、运维部署 Flyway 版本更新到 V140；CHANGELOG v4.5.0；layer3 加 L76(开关持久化)+L77(环境配置先查文档勿凭记忆)、layer4 加跨租户决策。**教训 L77**：测试库名 dms_test/生产 dms、容器 dms-test-postgres、账号 dms/dms123456 都早写在 docs/02_设计/运维部署.md 与 docs/DMS登录信息手册.md，本次一度误用本地 application-local.yml 的 dms 去连测试库——环境事实先查文档/docker inspect。**生产尚未发布**（测试 V140、生产 V139，V140 含表结构变更，发生产需部署+DB迁移）；本期不做取消/红冲跨租户。端到端 MFR_A↔DEALER_A1：PO-...0001→SO-...00005→RK-...0001；路径B PO-...0002+RK-...0002；铁律9 八入口+health 全 200）
> 会话级临时记忆。最近刷新：2026-08-30（**v4.4.7 已发布生产** http://8.133.193.238/dms/ 20:27，v4.4.5+v4.4.6+v4.4.7 三批 PATCH 一并上线；无 Flyway 迁移（本地=生产=V139）；DB 备份 dms-db-pre-v447-prod-20260830-202615.sql.gz；铁律9 八入口+health 全 200、容器全 healthy；旧 jar 备份 /opt/dms/backups/app-prod-20260830-202629.jar 可回滚；生产已清理过期备份+截断大日志，根分区 9.7G/40G）
> 上一刷：2026-08-30（v4.4.7 PATCH：百分比折扣语义颠倒修复——手动行/整单折扣 PERCENT 从误按"减免比例"(98→减98%)改为中文"折数=实付比例"(98折→付98%/减2%)，V4Money.signedDiscount；移动端摘要显示 9.8 折；PC/移动同一后端一次修复；全局折扣/促销 rate 不受影响；无 Flyway 变更；铁律11 文档同步）
> 上一刷：2026-08-30（v4.4.6 PATCH：移动端"很慢/刷不出来"根因——前端令牌刷新误打 /auth/refresh 应为 /api/auth/refresh（404 致 token 过期后整页挂起），已修并加移动端登录跳转；op_log varchar 超长 @PrePersist 截断；无 Flyway 变更；铁律11 文档同步）
> 上一刷：2026-08-29（工程化：引入 Ponytail 极简工程模式——项目级 skill ponytail/ponytail-review/ponytail-debt + layer1【铁律12 极简优先】；不改业务代码/不加依赖/不动部署；无 Flyway 变更；铁律11 文档同批更新）
> 再上刷：2026-08-29（v4.4.5 PATCH：智能下单 BOM 组件价回退/禁静默0、客户产品代金券列表 5个/批分页、数量快捷条、移动审批按 assignee 门禁；无 Flyway 变更）
> 再上刷：2026-08-29（v4.4.4 PATCH：移动端「智能下单」对话式向导上线测试环境 + 修复零金额订单 BOM 子件未归零；新增铁律11 文档及时更新；无 Flyway 变更）
---

## 当前版本状态（v4.4.0，2026-08-28）

| 项目 | 状态 |
|------|------|
| 当前交付基线 | **v4.3.2**（v4.3.0 MINOR 9 需求 R1–R9 + v4.3.1 PATCH 4 BUG + v4.3.2 PATCH 3 修复：R1 销退 RMA 单接入审批流、R2 有价/0金额退货联动、R3 销售订单布局+送货地址必填；Flyway V135；2026-08-27 测试已部署验证，生产待发布；前端 package.json=4.3.0） |
| 上一基线 | v4.2.9（2026-08-26 旧生产基线；v4.3.1 已于 2026-08-27 推生产） |
| Flyway 迁移 | 已到 **V135**（V121–V133 = v4.3.0 功能包；V134 = rma_order_lines.serial_no；V135 = RMA_ORDER 审批模板播种） |
| 测试环境 | http://43.128.145.141/dms/ 健康 UP；**前端 VITE_BASE=/dms/，Nginx 已切换为 alias + try_files（业务 SPA+H5 走 /dms/，后台走 /dms/admin/），与生产一致**；旧 302 重定向方案已废弃；铁律9五入口浏览器首检已通过 |
| 业务前台 | http://43.128.145.141/dms/ （admin / Sh123456，租户 default；sys_admin / Dms@123456 为厂商超管） |
| 移动端 H5 | http://43.128.145.141/dms/mobile/login |
| 平台后台 | http://43.128.145.141/dms/admin/ （admin / Sh123456，token 与前台隔离） |
| 正式环境 | http://8.133.193.238/dms/（**v4.4.7，2026-08-30 20:27 已部署**，含 v4.4.5/v4.4.6/v4.4.7 三批 PATCH；Flyway 至 V139，本次**无 DB 迁移**（本地=生产=V139，纯应用层升级，历史订单价格快照不重算）；webgate(nginx) 直供 /dms/ 与 /dms/admin/，前端 VITE_BASE=/dms/；发布前 DB 备份 /opt/dms/backups/dms-db-pre-v447-prod-20260830-202615.sql.gz，旧 jar 备份 /opt/dms/backups/app-prod-20260830-202629.jar 可回滚；铁律9 八入口（/、/dms/、/dms/admin/、/dms/mobile/login、/dms/mobile/register、/brochure/、/brochure/mobile.html、/brochure/print.html）+ /actuator/health 全 200，backend/postgres/redis/minio 四容器 healthy；登录、/api/auth/refresh（400 可达非 404）、/api/sales-orders、/api/approval-instances 均正常，preview 98 折计价正确） |
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
- 新测试服务器：43.128.145.141，SSH ubuntu/<SSH密码见部署运维负责人/环境变量DMS_DEPLOY_PASSWORD>，sudo 免密；测试库登录 `sudo docker exec -it dms-test-postgres psql -U dms -d dms_test`
- 生产服务器：8.133.193.238，SSH root/<SSH密码见部署运维负责人/环境变量DMS_DEPLOY_PASSWORD>，部署路径 /opt/dms/prod（compose v1.29.2），后端仅 127.0.0.1:18080，对外走 webgate 80 端口
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

