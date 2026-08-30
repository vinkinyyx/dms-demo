## v4.4.7 (2026-08-30) - 修复百分比折扣语义颠倒：98折/90折被错误算成"减98%/减90%"

> PATCH 版本（计价缺陷修复，PC + 移动端同时受益）。用户在移动端智能下单第二行填行折扣 98（提示"9折填90"，即 98=98折=原价×0.98），系统却按"减 98%、只付 2%"计价，导致订单金额严重偏低。

### 修复
- **【Critical】手动百分比折扣（行折扣、整单折扣）语义颠倒**：`backend/.../v4/V4Money.signedDiscount()` 对 PERCENT 类型一律 `amt = base × (value/100)` 当作"减免比例"——填 98 就减 98%、只付 2%。但前端（PC `OrderCreate.vue` 与移动端 `MSmartOrder.vue`）一致采用中文"折数"语义：**填的是实付比例**（90=9折=付90%、98=98折=付98%），提示文案明确写"按百分比折扣，9折填90"。
  - 实证（SO-20260830-00001 第 2 行 PRD-T006，4×6200=24800，填 98）：修复前 `lineDiscountAmount=-24304`（减 98%，仅剩 496）；正确应为减 2%=496、实付 24304。
  - 修复：折扣方向（REDUCE）下 PERCENT 减免额 = `base ×（1 − value/100）`（98→减2%、90→减10%、100→不减）；加价方向（ADD/高开）保持原语义（factor 即加价比例，98 不用作 ADD）。value>1 按 0~100 折数折算、value≤1 按小数系数，两种入参形态都兼容。
  - 影响范围：手动**行折扣**与**整单折扣**（均经 `V4Money.signedDiscount`），PC 端与移动端走同一后端，一次修复两端生效。产品全局折扣、客户全局折扣、促销满件打折走独立 `rate`（减免比例）路径，**不在此方法内、不受影响**（本就语义正确）。
- **移动端折扣展示消歧**：`MSmartOrder.vue` 确认摘要里行折扣/整单折扣原显示 `98%`（易被读成"减98%"），改为中文折数 `9.8 折`（90→`9 折`）；金额折扣显示 `减 ¥x`。提示输入文案"9折填90"本就正确，未改。

### 验证（测试环境 http://dms-dev.mysolmed.com/dms，/api/sales-orders/preview）
- 行折扣 98（10×标准额28000）：`lineDiscountTotal=560`=减 2%（修复前为 27440 量级的错误减免）；行折扣 90：`lineDiscountTotal=2800`=减 10%；填 100：减免 0、金额等同无折扣。
- 整单折扣 98：`headerDiscountTotal=548.8`=减 2%（作用于行折后总额 27440）；整单 90：2744=减 10%。
- 固定金额折扣（AMOUNT 减1000）：`lineDiscountTotal=1000`，不受影响。
- 真实浏览器智能下单端到端：选 PRD-T001 ×10 → 行折扣填 98 → 确认摘要显示"行折扣：9.8 折"、应付金额正确，Console 无红错、无 5xx（末尾取消，不产生脏单）。
- 分页/数量回归 `v446_paging.js` 15/15；铁律9 八入口 + health 全 200。
- 说明：历史已提交订单（如 SO-20260830-00001）价格为快照、不会自动重算；修复对**新下订单**生效。

### 生产发布（2026-08-30 20:27，http://8.133.193.238/dms/）
- v4.4.5 + v4.4.6 + v4.4.7 三批 PATCH 一并推送到生产（用户明确指令），生产后端容器 `dms-prod-backend` 于 2026-08-30 20:27 重启，镜像内 `/app/app.jar` 为本次新构建（117,092,641 字节，20:26）；旧版本 jar 备份至 `/opt/dms/backups/app-prod-20260830-202629.jar` 供回滚。
- **无 DB 迁移**：本地 Flyway 最高 V139 = 生产 V139，本次纯应用层升级，不涉及表结构/历史单据改价（历史订单价格快照不重算）。
- 发布前已备份生产数据库：`/opt/dms/backups/dms-db-pre-v447-prod-20260830-202615.sql.gz`（1.3M）。
- 铁律9 生产入口首检全过：`/`、`/dms/`、`/dms/admin/`、`/dms/mobile/login`、`/dms/mobile/register`、`/brochure/`、`/brochure/mobile.html`、`/brochure/print.html` 全部 HTTP 200（裸根 302→/dms/），`/actuator/health` 200 UP；4 个容器（backend/postgres/redis/minio）均 healthy。
- 功能冒烟（生产真实接口）：`admin/Sh123456` 登录成功（token 300 字符）；`POST /api/auth/refresh` 返回 400（端点可达、非 404，验证 v4.4.6 令牌刷新路径修复已上线）；前端构建产物含 `api/auth/refresh`；`GET /api/sales-orders`、`GET /api/approval-instances` 均 200；移动端智能下单 preview 行折扣 98 折减免 2%、整单 98 折减免 2% 计算正确，Console 无红错、Network 无 5xx。
- 生产清理：删除过期备份 jar/前端目录、截断 >2M 的 docker json 日志；保留当前版本产物与两份 DB dump；备份目录由 241M 降至约 122M，根分区 9.7G/40G。
## v4.4.6 (2026-08-30) - 修复移动端"很慢/页面刷不出来"：令牌刷新路径错误 + op_log 超长写入失败

> PATCH 版本（线上性能/可用性缺陷修复）。用户反馈移动端整体变慢、搜产品和页面都加载不出。诊断后端接口本身很快（80–145ms）、无长慢查询，根因在前端令牌刷新与操作日志写入两处。

### 修复
- **【Critical·主因】移动端登录态失效后整页刷不出/请求一直转圈**：access token 过期（约 18 分钟）后，前端 `frontend-vue/src/utils/request.js` 的 `doRefresh()` 向 **`/auth/refresh`** 发起刷新，但本系统 API 统一带 `/api` 前缀，Nginx 只把 `/api/`、`/auth/` 反代到后端，而后端刷新接口实际是 **`/api/auth/refresh`**——`/auth/refresh` 在后端没有映射，落到 `GlobalExceptionHandler` 返回 404（"No static resource auth/refresh"）。结果：access token 一过期，所有业务请求 401 → 刷新又 404 → 清空登录态/请求挂起，表现为"页面刷不出来、搜产品转圈"。
  - 修复：`doRefresh()` 改为 `POST /api/auth/refresh`；并新增 `redirectToLogin()`——登录过期跳转按当前路由/设备区分，移动端跳 `/mobile/login`、PC 跳 `/login`（原先写死 `/login`，移动端会跳到不存在的 PC 登录页）。
  - 验证：登录后人为把 access token 改成失效串，再进移动订单页，前端自动发起 1 次 `POST /api/auth/refresh` 拿到新令牌、重放 `/api/sales-orders` 成功，**保持登录且列表正常渲染**，Console 无红错。
- **【次因】操作日志 op_log 频繁写入失败刷错误日志/DB**：长 Java 方法签名、长 path、长 User-Agent、长 remark 超过 varchar(255/16/8/64) 时，PostgreSQL 抛 `value too long for type character varying(255)`，整条操作日志 INSERT 失败（`op_log DB persist failed`）。虽为异步、不阻塞业务请求，但持续产生错误日志和无效 DB 写入。
  - 修复 `backend/.../operationlog/entity/OpLogEntry.java`：新增 `@PrePersist enforceLengths()`，落库前按各列长度安全截断（method/path/user_agent/remark≤255、http_method≤8、layer/action≤16、request_id/username/ip/biz_id≤64 等），超长追加 "..."；日志不再写失败。部署后持续制造业务流量，`value too long` 与 `op_log DB persist failed` 均为 0。

### 诊断结论（为什么"感觉很慢"）
- 服务器健康、无资源瓶颈：主机 load 低（空闲 85–98%），PostgreSQL 无长查询/锁（11 连接仅 1 active、0 waiting），各业务 API 实测 57–351ms；主机内存偏紧（3.6G、有约 380MB swap）是潜在隐患但非本次主因。
- 真正的用户可见现象来自令牌刷新 404：token 过期后页面无法静默续期，请求失败/挂起、被踢登录，体验即"很慢、刷不出来"。
- `actuator/health` 本身 ~1.4s（DB+Redis+MinIO 探针对内存偏紧主机较慢），但该端点仅运维用、前端不调用，不影响移动端。

### 验证（测试环境 http://dms-dev.mysolmed.com/dms）
- 令牌刷新：`/api/auth/refresh` 用有效 refresh token 返回新 access token（旧 `/auth/refresh` 仍 404，前端已不再调用）；浏览器实测失效 access token 下自动刷新 + 重放成功、不掉登录。
- op_log：部署后产生多笔业务流量，后端无 `value too long`、无 `op_log DB persist failed`，容器 healthy。
- 铁律9 部署后首检：`/`(302→/dms/)、`/dms/`、`/dms/admin/`、`/dms/mobile/login`、`/dms/mobile/register`、`/brochure/`、`/brochure/mobile.html`、`/brochure/print.html` 全 200，业务页 80–148ms，`/actuator/health` UP。
- 移动端深度冒烟 `smoke-test.cjs --target=mobile`：17/17 通过，Console 无红错、无 5xx。
## 工程化（2026-08-29）- 引入 Ponytail 极简工程模式（项目级 skill + 铁律12）

> 非业务版本：不改动任何业务代码、不新增运行时依赖、不动部署/Nginx。把开源 [DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail)（MIT，"像最懒的资深开发一样写代码：最好的代码是从没写过的代码"）按 DMS 规则体系改编融入。

### 新增
- **项目级 skill `.codex/skills/`（随仓库版本控制，团队共享）**：
  - `ponytail/`：常驻极简模式。极简阶梯——①YAGNI 这事真要做吗 ②先复用代码库已有（`CrudView.vue`/`ListPageLayout.vue`/`usePageLayout.js`、`frontend-vue/src/api/` 封装、`v-has` 指令、已装依赖）③标准库 ④平台原生 ⑤已装依赖 ⑥一行 ⑦才写最少代码；修 Bug 修根因（grep 全部调用方、在共享层修一次）；删除优先于新增、最短可工作 diff；有意砍的有上限角用 `ponytail:` 注释标记。
  - `ponytail-review/`：只查过度工程的 diff 审查（delete/reuse/stdlib/native/yagni/shrink 标签），并把 DMS 高频复用点（CrudView/统一 api/v-has）列为 `reuse:` 检查项；明确不许把 DMS 硬约定（CrudView、铁律9/10/11、五维测试）当冗余删。
  - `ponytail-debt/`：把全仓库 `ponytail:` 注释收台账，追加到 `.memory/layers/layer3-lessons.md` 末尾附录（遵守 §0.7 不建散文件）。
  - 刻意**不**引入上游的 hooks/MCP/多平台插件脚手架与 `ponytail-gain`（对 DMS 是多余依赖/营销看板，正是 ponytail 自身反对的）。
- **【铁律12：极简优先 / Ponytail】** 写入 `.memory/layers/layer1-rules.md` 部署铁律表，使极简原则不依赖 skill 被触发也常驻生效。

### 取舍
- **优先级**：ponytail 管"怎么写代码最简"；DMS 铁律9（真实浏览器首检）、铁律10（Nginx 管控）、铁律11（文档同批更新）、五维测试、审批回滚测试是更高优先级硬约束，冲突时以 DMS 铁律为准，绝不为求简删减验证/安全/错误处理/文档。
- 极简的是**实现**，不是**理解**：阶段 A 需求确认、旧功能盘点（Gap6）不省。

## v4.4.5 (2026-08-29) - 移动端智能下单：BOM 组件价回退（非零金额）、选择列表分页、数量快填、移动审批越权门禁

> PATCH 版本（移动端对话向导体验优化 + 后端计价缺陷修复 + 移动审批越权修复）。基于用户对 v4.4.4 智能下单的 4 项反馈逐条修复。

### 修复
- **【Critical·问题1】销售订单中 BOM 组合品的组件（子件）未维护 BOM 专属组件价时静默按 0 计价，导致整套组合品近乎免费**：订单 SO-20260829-00003 中 BOM 母件 PRD-T005（股骨髓内钉系统）的子件 PRD-T003/T004（锁定接骨螺钉）因没有配 BOM_COMPONENT 专属价，`V4PricingService.salesPrice(BOM_COMPONENT)` 找不到组件价时直接 `zeroPrice` 返回 0，且不报错。
  - 修复 `backend/.../v4/V4PricingService.java`：BOM 组件价查找顺序为「经销商组件价 → 全局组件价(0L) → 全局组件价(null)」，全部未命中时**回退到该组件的单品销售价**（DEALER STANDALONE → GLOBAL 0L → GLOBAL null），仍取不到价则抛 `BusinessException`（含产品编码+名称+所属 BOM 母件提示），**禁止再静默返回 0**。
  - 实测修复后：PRD-T005 ×1（含子件 T003×4、T004×2）preview 返回子件 720 + 440 = finalAmount 1160（母件行 0 为正确口径——BOM 母件不独立计价，价值由子件承载）。
  - 说明：修复前已创建的历史订单 SO-20260829-00003 仍保留旧的 0 金额快照（订单提交后价格已落快照，不会自动重算）；新下单的 BOM 组合品计价正确。
- **【问题3·越权】移动端审批详情对非指派人也显示「同意/驳回」按钮，点击后后端 403 `only assignee can process task`**：审批节点配置了多名指派人（含超级管理员、林管理员及一批测试用户），`MApprovalDetail.vue` 的 `myPendingTasks` 只按 `status==='PENDING'` 过滤，未校验当前登录人是否为该任务 assignee，导致非指派人看到按钮、点击报 403。
  - 修复 `frontend-vue/.../mobile/MApprovalDetail.vue`：引入 `useUserStore`，`myPendingTasks` 改为 `t.status==='PENDING' && (t.assigneeId==null || Number(t.assigneeId)===Number(userStore.user.id))`，与 PC 端 `ApprovalDetailDrawer.vue` 口径一致；非指派人不再渲染操作按钮（后端 `/api/approval` 仍有 assignee 校验兜底，前后端双重门禁）。

### 优化
- **【问题2】客户/产品/代金券选择列表分页展示**：原产品搜索只列前 10 个、客户列前 30 个，超出部分无法触达。`MSmartOrder.vue` 新增统一分页机制：每批显示 **5** 个（`PAGE_SIZE=5`），选项编号全局连续（第二批从 6 开始），列表底部提供「‹ 上一批」「下一批 ›（还有 N 个）」导航按钮；标题实时显示「第 x/y 批 · 共 N 个」。
  - 产品搜索 `limit` 由 20 提升到 100，覆盖更多搜索结果；客户（经销商）、代金券列表同样改为 5 个/批分页；「进入下一步/不使用券」等非数据操作按钮以 `_pin` 固定在分页按钮之前，翻页不丢失。
- **【问题4】数量步快捷填写**：数量输入步新增 `.qty-bar` 快捷条 —— 「−/+」步进按钮、常用数量 [1, 5, 10, 20, 50, 100] 一键点选、大号当前数量显示、「确定 N 件」主按钮；同时保留原输入框可直接键盘输入，两者双向同步。

### 验证（测试环境 http://dms-dev.mysolmed.com/dms）
- 铁律9 部署后首检：根 `/` 302→/dms/、`/dms/`、`/dms/admin/`、`/dms/mobile/login`、`/dms/mobile/register`、`/brochure/`、`/brochure/mobile.html`、`/brochure/print.html` 全部 200/302 且 title 各异无静默回退，`/actuator/health` UP。
- 分页/数量 `automation_test/v446_paging.js`（真实移动浏览器）：**15/15 通过** —— 客户 12 个分 3 批、首批 ≤5 个、翻到第 2 批有上一批、翻回第 1 批；产品搜 PRD 命中 24 个分 5 批、第二批编号从 6 全局连续；数量步进 10→11、常用数量点选、确定后进入第五步；走到第九步确认摘要后取消；全程 Console 无红错、Network 无 5xx。
- 计价：PRD-T005 BOM 组合 preview 子件计价 720+440=1160（非 0）；母件行 0 为 BOM 正确口径。
- 移动审批门禁：非指派人 sales（id=32）my-todo 不含该单、详情页**不显示**同意/驳回按钮（不再 403）；指派人 admin（id=1）正常显示按钮。
- 深度冒烟回归 `tools/smoke-test.cjs`（PC+Admin+Mobile）：**272/272 通过**。
## v4.4.4 (2026-08-29) - 移动端「智能下单」对话式向导 + 零金额订单 BOM 子件计价修复

> PATCH 版本（移动端新增功能 + 后端计价缺陷修复）。移动端新增对话式（点选为主）下单向导，并修复补货/样品等零金额订单在 BOM 组合品下子件仍计价、导致整单金额不为 0 的问题。

### 新增
- **移动端「智能下单」对话式向导** `MSmartOrder.vue`（路由 `/mobile/smart-order`，移动端首页快捷入口第一个）：以聊天气泡 + 选项按钮点选为主、关键词/数量/折扣值等少量输入为辅，引导用户九步完成下单。
  - 第一步选订单类型：销售订单 / 补货订单 / 样品订单（移动端仅允许实物出库三类，点选）；
  - 第二步选客户（经销商）：列出可下单客户编码+名称，经销商账户自动锁定；补货订单对未开通寄售库存的客户即时拦截重选；
  - 第三步搜产品：支持产品编码/名称/型号/规格关键词搜索（走 `/api/lookups/products`，带客户授权过滤），点选结果；无结果可换词重搜；
  - 第四步填数量：仅接受正整数，非法输入原地重问；
  - 第五步行折扣（仅销售订单）：无 / 百分比 / 固定金额；补货、样品自动跳过（后端规则：这两类强制 0 金额、无折扣）；
  - 样品订单在数量后追加「申请样品原因」必填（后端强制）；
  - 第六步是否继续加产品：是回第三步，否进入下一步；样品单品自动跳过；
  - 第七/八步整单优惠（仅销售订单）：无 / 整单折扣（金额·百分比）/ 整单一口价 / 代金券（拉取该客户可用券点选）；补货、样品直接跳到确认；
  - 第九步确认：后端 `/api/sales-orders/preview` 重算后展示订单摘要（类型/客户/各行产品数量折扣/整单优惠/促销文案/应付金额/送货地址），选择「确认提交」（创建+提交进审批）、「保存草稿」或「取消」；
  - 全程右上角「↺ 重新开始」常驻，任意自由输入步输入 `0` 清空进度回到第一步；超范围/非法输入原地提示重输，不产生脏数据；
  - 外键一律显示编码+名称，错误信息透传后端业务报错（含产品编码+名称）。

### 修复
- **【Critical】零金额订单（补货 REPLENISHMENT / 样品 SAMPLE / 整单0）含 BOM 组合品时整单金额不为 0**：`V4PriceEngine.expandBom` 展开 BOM 时未把母件行的 `lineZero` 标志透传给子件行，母件金额归 0 但子件（CHILD）仍按销售价计价，导致 `preview/创建` 返回的整单 `finalAmount` 为子件金额之和（实测 PRD-B001 补货 2 件 finalAmount=382.20，应为 0）。
  - 修复：`expandBom` 增加 `parentLineZero` 透参，母件为零金额行时子件行同步 `setLineZero(true)` 并清空行折扣，复用既有 `zeroLine()` 汇总逻辑；修复后 BOM 补货/样品单整单 `finalAmount=0`。

### 验证（测试环境 http://dms-dev.mysolmed.com/dms）
- 铁律9 部署后首检：根 `/` 302→/dms/、`/dms/`、`/dms/admin/`、`/dms/mobile/login`、`/dms/mobile/register`、`/brochure/`、`/brochure/mobile.html`、`/brochure/print.html`、`/actuator/health` 全部 200/302，宣传 3 页 title 各异无静默回退；域名形态分流 12/12 通过。
- 智能下单全流程 Playwright 真实浏览器验证：销售订单（多产品+行折扣9折+整单95折→应付¥137.20）提交后回读状态 `PENDING_APPROVAL`；销售草稿保存成功；代金券路径选券→摘要含券；整单一口价→应付=一口价；补货订单跳过所有折扣步且 BOM 单整单 ¥0.00；样品订单问申请原因、单品、¥0.00；输入 0 重启、非法数量/金额拦截均正确；全程 Console 无红错、Network 无 5xx。
- 深度冒烟回归 `tools/smoke-test.cjs`（PC+Admin+Mobile）：272/272 通过。

## v4.4.3 (2026-08-28) - 移动端列表页永久"加载中"修复（van-list 首次加载）

> PATCH 版本（纯前端缺陷修复，无后端逻辑变更）。全面测试中发现并修复移动 H5 核心列表无法加载数据的问题。

### 修复
- **【Critical】移动端销售订单/审批/消息列表永久"加载中"**：`MOrders.vue`、`MApprovals.vue`、`MMessages.vue` 的 `<van-list>` 在该移动布局下首次 `@load` 事件未稳定触发（列表容器初始高度极小、immediate-check 未判定触底），且加载处理函数开头的守卫 `if (loading.value) return` 与 Vant 行为冲突——Vant 触发 load 前已通过 v-model 将 loading 置 true，守卫直接 return，既不发请求也不复位 loading，导致页面一直转圈、数据不渲染。
  - 移除与 van-list 冲突的 `loading` 早返回守卫，改用独立的 `inFlight` 标志防重入；
  - 三个列表页统一新增 `onMounted(() => 首拉)` 主动首次加载兜底，不再仅依赖 van-list 的 immediate-check。
- 对照页 `MSurgeryReports.vue`（无该守卫）本就正常，未改动。

### 测试脚本/工具修复（消除假阴性，非产品代码）
- `tools/smoke-test.cjs`：新增 `--target=pc/admin/mobile/all` 分段参数与按段超时预算，修复原 8 分钟总超时导致 admin/mobile 段从未执行的问题。
- `automation_test/v440_browser_deep.js`：菜单可见性改用全菜单项文本（折叠态 innerText 取空导致假阴性）；订单类型下拉真实展开浮层再断言；菜单显隐改为读取 `/api/tenant/features` 按开关状态动态判定。
- `automation_test/v440_browser_gate.js`：G9 菜单检测同款修复 + 进销存菜单按租户开关动态判定。

### 部署运维
- 部署时后端首次启动报 Flyway V135 校验和不匹配（V135 迁移在数据库已应用后内容随 v4.4.0 包更新过）：按 `flyway repair` 等价方式对齐 `flyway_schema_history` 中 V135 校验和（454037780 → 55081890），不重放迁移、不动业务数据，后端正常启动 UP。
- 服务器清理：删除 /home/ubuntu 部署残留与 /tmp 临时文件；/opt/dms/backups 旧 jar/前端备份由 2.3G 精简至约 201M（保留最新 app.jar、最新 frontend、最新 DB dump、dms-changes 归档）。

### 验证（测试环境 http://43.128.145.141/dms）
- 铁律9/10 浏览器首检 GATE：**22/22 PASS**（8 入口 + PC/admin/移动登录 + 核心列表 + 菜单）。
- 移动端回归：销售订单列表发 `GET /api/sales-orders?page=1&size=20` 渲染 20 卡片、审批列表渲染 RMA 待办、消息列表渲染审批待办通知；永久"加载中"消失，Console 无红错、无 5xx。
- `v440_browser_deep.js`：**12/12 PASS**；`smoke-test --target=mobile`：**17/17 PASS**。
- v4.4.1 红字补货红冲端到端 `scripts/e2e_invoice_consignment.py`：**0 失败**（SOR→提交→审批→RED 回调→台账 on_hand-1→REPLENISH_OUT→红字 GIR is_red=true，红字行同序列号豁免）。
- 代金券计价矩阵：券模式实付=原价-券扣减、多产品分摊不超面值、USED 券被拒、券与整单折扣互斥、补货全单 0 金额、开票不可用券，均通过。

## v4.4.2 (2026-08-28) - 全站 MySolMed 品牌 logo、测试环境域名 dms-dev.mysolmed.com、Nginx 变更管控

> PATCH 版本（品牌与运维设施，无业务逻辑变更）。测试环境启用域名、全站替换品牌 logo，并固化 Nginx 配置基线。

### 品牌
- **全站换 MySolMed logo**：PC 业务工作台登录页/页头、平台后台、移动端 H5（登录/注册/工作台）统一替换为 MySolMed 品牌标识（藏青 #0B2545 + 青色 #00B4D8），品牌资源取自 `DMS产品宣传手册/mysolmed-brand/assets`。

### 域名与 Nginx
- **测试环境域名启用**：`dms-dev.mysolmed.com` → 43.128.145.141（A 记录解析）；`server_name dms-dev.mysolmed.com _;`，域名与 IP 直连行为一致。
- **裸域名根路径 302 → `/dms/`**：打开 `http://dms-dev.mysolmed.com/` 直达 DMS 系统登录页/工作台，不再落到产品宣传页；宣传手册保留在 `/brochure/`（PC `/brochure/`、移动 `/brochure/mobile.html`、打印 `/brochure/print.html`，移动/打印页与 index.html 平级，**无 /brochure/pages/ 子目录**）。
- 后端 `APP_BASE_URL=http://dms-dev.mysolmed.com/dms`（审批邮件链接等绝对地址）。
- 配置备份：服务器 `/opt/dms/test/nginx/nginx.conf.bak.domain-20260828164741`。

### 规则与文档
- **新增【铁律10：Nginx 变更管控】**（AGENTS.md Gap 7 + project_rules.md 同源）：nginx 配置非必要不调整；变更前必备份、容器内 `nginx -t`、bind-mount 改完必须 `docker restart dms-test-nginx`（reload 可能因 inode 变化不生效）、`nginx -T` 取证实际生效配置、再按铁律9 真实浏览器终验全部入口；警惕 try_files 静默回退产生"假 200"。
- 铁律9 部署后首检必检 URL 扩为 8 条：`/`、`/dms/`、`/dms/admin/`、`/dms/mobile/login`、`/dms/mobile/register`（经销商准入）、`/brochure/`、`/brochure/mobile.html`、`/brochure/print.html` + `/actuator/health`。
- 登录信息手册/README/运维部署/需求文档/AI开发文档/automation_test README 全部域名化，补全经销商准入链接与宣传 3 链接。

### 验证
- 7 个用户入口经 TRAE-browseruse 真实浏览器逐一验证通过（PC 工作台登录态直达 /dms/home、移动登录、平台后台、经销商注册页、宣传 PC/移动/打印），页面 logo/标题正确、图片零破损、Console 无红色错误。

## v4.4.0 (2026-08-28) - 寄售库存/补货开票订单/样品订单/经销商资信账期

> MINOR 版本。新增寄售（代销）业务闭环与经销商资信管理，订单类型扩展。

### 新增
- **订单类型扩展**：销售订单(SALES)、补货订单(REPLENISHMENT，寄售)、开票订单(INVOICE，寄售)、样品订单(SAMPLE)、定制订单(CUSTOM，保留)。
  - 补货订单：仅寄售开启经销商可下；所有产品按 0 金额，禁用折扣/代金券/一口价/满减满赠；发货后厂家库存扣减（沿用销售出库）并计入该经销商寄售库存台账。
  - 开票订单：仅寄售开启经销商可下；针对该经销商寄售库存开票；按合同价/客户价/全局折扣重新计价，可用产品促销/经销商折扣/代金券外的部分金额折扣与整单/行折扣，**不参与满减满赠，不可用代金券/一口价/0金额**；新增结算终端（终端医院）选择。提交即预占寄售库存，审批通过实扣，拒绝/退回/撤回释放。
  - 样品订单：仅一个单品、0 金额、禁用折扣促销，必填申请样品原因。
- **寄售库存**：新增 `consignment_stock` 台账（经销商+产品SKU+批号+序列号维度，含在库/锁定/标准价）与 `consignment_stock_movements` 流水（补货入库/开票预占/实扣/释放）；寄售金额按产品标准价汇总。新增 `ConsignmentService`、`InvoiceOrderApprovalCallback`、`/api/consignment/available` 接口与「寄售库存」页面。
- **经销商资信与账期**：经销商主数据新增寄售开关/寄售额度/信用额度/账期/结算方式/信用等级；新增 `dealer_credit_profiles` 运行期占用表（信用占用、寄售占用）与「经销商资信与账期」页面 `/api/dealer-credit`。超额/超账期触发审批（不硬拦截）。
- 销售出库发货在源订单为补货订单时自动回写寄售库存。

### 调整
- **进销存开关仅约束厂家用户**：租户关闭进销存后，厂家用户不可见采购/采退/收货入库/库存管理/库存调整等菜单与接口；经销商用户仍可使用库存相关菜单（销售订单/销退/销售出库始终保留）。
- 销退单审批摘要（RMA_ORDER）补充退货类型/原因/关联出库单/退货数量金额与明细行。

### 联调修复（2026-08-28 测试环境实测）
- 开票订单端到端「寄售库存选择器」：OrderCreate 开票时改为从 `/api/consignment/available` 弹窗选择该经销商寄售库存（产品+批号+序列号维度，含可用量/标准价/仓库），明细携带批号/序列号落库；新增 Flyway `V137__v440_invoice_order_approval_template.sql` 播种开票订单审批模板，提交进入审批中心。
- 修复开票预览 500：产品名 SQL 误引用 products 不存在的 `name` 列（应为 `name_cn`），同时使错误信息显示产品编码+名称而非数字 ID。
- 修复开票落库 500：order_lines INSERT 增加 batch_no/serial_no 列后参数错位导致 line_zero 落 null 违反 NOT NULL，重排绑定顺序（?38 batch_no / ?39 serial_no / ?40 line_zero）。
- 修复开票提交 `Unknown alias warehouse_id`：回调/提交从 order_lines 读取不存在的 warehouse_id，改为以寄售台账仓库为准。
- 修复 dealer_credit_profiles 不落数：寄售金额汇总 INSERT 未绑定参数且 ON CONFLICT 无法推断部分唯一索引，补绑参并指定 `(tenant_id,dealer_id) WHERE deleted_at IS NULL`。
- 前端：开票/补货/样品/定制订单锁定计价方式为普通折扣（禁用代金券/一口价/0金额）；修复开票产品选择器缺 resource 导致的 `/api/lookups/undefined` 404 与寄售表 row-key 告警。

### 数据迁移
- Flyway `V136__v440_consignment_credit.sql`：经销商新增寄售/资信字段、寄售台账与流水表、资信档案表、订单结算终端/样品原因字段、资信模块资源播种。

## v4.3.2 (2026-08-27) - 销退审批流接入、有价/0金额退货联动、销售订单布局与送货地址必填

> PATCH 版本。v4.3.1 上线后用户反馈 4 项问题，全部修复并部署测试环境（真实浏览器端到端验证通过）。

### 修复
- **R1 销退单提交后不进审批（Critical）**：v4.3 多出库销退走 `RmaOrderService`（rma_orders 表，`POST /api/rma/orders`），`create()`/`submit()` 仅置 `SUBMITTED` 未调用 ApprovalService，导致单据卡在「已提交」、审批中心无待办。现统一接入审批流：有审批模板→`PENDING_APPROVAL`（审批中心可见待办），无模板自动通过→`COMPLETED`；新增 `RmaOrderApprovalCallback`（审批通过→complete，驳回/退回/撤回→释放 sales_out_lines 锁定量并回 DRAFT）；`RmaOrderPortalController` 新增 `/api/rma/orders/unified/{uid}/submit|approve|reject|cancel`，兼容 RMA 新单（r{id}）与 legacy 红字单（l{id}）；新增 Flyway `V135__rma_order_approval_template.sql` 播种 RMA_ORDER 审批模板。前端销退列表/详情状态文案修正：SUBMITTED=待审批、PENDING_APPROVAL=审批中、REJECTED=已驳回，列表与详情状态一致。
- **R2 有价/0金额产品退货筛选联动**：销退新建页「发货仓库」后新增「退货类型」单选（默认「有价产品退货」，可选「0金额产品退货」）。后端 `SalesReturnController/Service` 的 shipped-outs/shipped-out-lines 增加 `amountType` 参数：PAID 仅带 unit_price>0 产品，ZERO 仅带 0 金额产品；选择「0金额产品退货」时退货原因禁用「常规退货（NORMAL）」，前端保存再拦截一次；明细 payload 带 rmaType（RETURN/ZERO_RETURN）。
- **R3 销售订单新建页布局 + 送货地址必填**：栅格重排为第一行「经销商 + 订单类型」、第二行「送货地址 + 期望日期」，两字段均完整可见；送货地址加红星必填（rules 增加 shipAddressId required），未选地址提交被拦截；下单日期保持默认当天不变。
- **R4 文档**：`.memory/layers/layer1-rules.md` 历史遗留 `????` 乱码段落（v4.0.0 规则）重写为中文。

### 部署/运维
- 测试环境 Nginx 由旧的「/dms/* 302 → /*」（对应 VITE_BASE=/）方案切换为生产同款「alias + try_files」（对应 VITE_BASE=/dms/），修复前端按 /dms/ 构建后业务 SPA 被重定向到宣传站、用户入口 500/白屏的问题；业务 SPA + 移动 H5 走 `/dms/` alias，平台后台走 `/dms/admin/` alias。

### 验证
- 后端打包、前端（VITE_BASE=/dms/ 与 /dms/admin/）构建通过；部署测试环境后真实浏览器铁律9 首检：`/`、`/dms/`、`/dms/login`、`/dms/admin/`、`/dms/mobile/login`、`/actuator/health` 全 200，登录→工作台→菜单展开→列表数据加载正常，Console 无红错、无 5xx。
- R1：RMA-20260827-00008 提交→PENDING_APPROVAL→审批中心待办→同意→COMPLETED；RMA-00009 驳回→回 DRAFT 且可退量恢复；遗留 4 张卡死 SUBMITTED 单据重新进入审批流（软删除单自动排除）。
- R2：退货类型单选默认「有价」，切「0金额」常规退货原因消失；API 实测 PAID 带出 6 张出库单、ZERO 带出 0 张。
- R3：新建页两列布局 + 送货地址红星必填，截图存 `automation_test/v4-browser-results/`。

## v4.3.1 (2026-08-27) - 销退单返工、代金券审批返还、销售订单重开回显 BUG 修复

> PATCH 版本。v4.3.0 上线后真实浏览器/业务走查发现 4 个 BUG，全部修复并已部署测试环境（v433）、浏览器端到端验证通过。完整文件清单见 `CHANGES-2026-08-27.md`。

### 修复
- **销退订单（SalesReturnEdit / SalesReturnService / SalesReturnController）**
  - 字段顺序与操作门禁返工：新建页改为「先选经销商 → 选发货仓库 → 再选发货单 → 选退货原因」；未选经销商/仓库时「选择出库单」按钮 disabled + tooltip，杜绝自由文本录入经销商
  - 出库单弹窗恢复并强化筛选：经销商只读展示（el-tag）、仓库、批号（batchNo）、序列号（serialNo）全部接回后端查询参数，实测筛选结果集收敛
  - 发货单按发货仓库过滤 + 同仓库校验：仅允许选择与所选发货仓库一致的出库单
  - `RmaOrderService.enrichOrders` 补仓库信息、`RmaOrderLine` 增加 serialNo 字段；新增 Flyway `V134__rma_order_lines_serial_no.sql`
- **代金券审批拒绝未返还（Critical）**：销售订单/销退单使用代金券后提交审批，若审批拒绝（onRejected/onReturned/onCanceled），券状态停留在 used 不返还。`SalesOrderApprovalCallback` / `SalesReturnApprovalCallback` 审批回滚路径补 `voucherService.release(businessId)`，券从 USED 恢复为 ISSUED；`SalesReturnApprovalCallback` 注入 V4OrderService 加 `@Lazy` 解决循环依赖，新增 `backend/lombok.config`（`lombok.copyableAnnotations+=@Lazy`）
- **销售订单重开经销商为空/价格报错（SO-20260827-00003）**：`OrderCreate.vue` makeLine 产品 ID 兼容 `p.productId ?? p.id ?? null`，重开行明细正确回填产品与价格；dealerName 回显，不再因取不到 partnerId 导致价格查询失败
- 移动端 `MOrderCreate.vue`、资源选择器 `ResourcePicker.vue`（displayValue）同步修复

### 验证
- 后端打包、前端构建通过；部署 v433；真实浏览器走查：登录 → 销退新建（经销商→仓库→出库单筛选→退货原因）→ 销售订单重开回显 → 代金券审批拒绝返还，全部通过；Flyway V134 执行成功。

### 流程规则沉淀（AGENTS.md / project_rules.md）
- 新增「页面重写/改造功能对照规则」：重写前必须盘点旧页面全部筛选/列/按钮/选择器，禁止功能减法；外键引用一律 el-select/资源弹窗禁止自由文本；后端支持的筛选参数前端必须有入口
- 新增「前端部署路径与文档 URL 一致性铁律（铁律9）」：部署后首检必须用真实浏览器逐条验证文档中所有用户入口 URL（/dms/、/dms/admin/、/dms/mobile/login），VITE_BASE 与 Nginx 路径必须一致

## v4.3.0 (2026-08-27) - 订单计价体系升级、促销扩展、客户代金券、全局折扣、客户自助注册下单、多出库销退

> MINOR 版本，基线 v4.2.9。需求见 `docs/01_需求/v4.3.0/DMS_v4.3.0_需求规格.md`，设计见 `docs/02_设计/v4.3.0/总体设计.md`、`docs/02_设计/v4.3.0/订单折扣与促销规则说明书.md`，测试见 `docs/03_测试/v4.3.0/DMS_v4.3.0_测试报告.md`。前端版本号升至 4.3.0（`frontend-vue/package.json`）。已部署测试环境，三端冒烟 0 FAIL（PC 224 项 / 平台后台 / Mobile 17 项），计价 A–L 折扣场景集全部通过。

### 新增功能（R1–R9）
- **R1 一张销退单关联同经销商多张出库单**：销退可一次关联同一经销商多张销售出库单；可退量按来源出库单行维度锁定（已退+在途+本次），跨单不可挪用；混经销商拦截；审批后按来源行回写库存，退货价按 EA 快照；销退列表统一展示新 RMA 单与历史红字单（unified）
- **R2 客户多联系人/多收货地址**：新增 `dealer_contacts`、`dealer_addresses`（V124），支持默认联系人/默认地址；PC + H5 下单选地址并写地址快照；历史 dealers 单联系人字段回填一条默认联系人兼容
- **R3 整单一口价 / 整单 0 金额 / 行 0 金额**：一口价差额按折后行金额占比摊到各行与 EA；行 0 金额权重 0，低开/高开均不分摊；一口价、整单 0、代金券三者互斥
- **R4 促销扩展**：新增满 N 件打折（百分比/固定单价）、满 N 件减固定金额、满赠保留（V131）；同 SKU 同时段只命中一种促销，冲突拦截并列清单；命中行禁行手动折扣、禁 0 金额；同 SKU 拆多行拦截；命中落库 `order_promotion_hits` 并在订单上方展示文案；退货跌破门槛赠品按 0 金额退回
- **R5 客户代金券**：厂家按客户/范围批量发放，含面值、最低消费、适用范围（全部/指定 SKU/品类）、有效期（V127）；一单限用一张，整单层抵扣绝不摊行（防退货套现）；面值大于原价合计/过期/停用/他人券拦截；与一口价、整单 0、所有折扣互斥；整单未出库作废返还券，部分退货/已出库不退
- **R6 产品全局折扣**：独立 PC 维护页，按时间段生效、历史留痕、同产品时段不可重叠（V126）；下单取客户价后乘折扣，未生效/过期不应用
- **R7 客户全局折扣**：独立 PC 维护页，按时间段生效；下单在最终价格上打折，按各行折后金额占比摊回每行，摊后任一行 < 0 拦截
- **R8 折扣加价方向（高开）**：行折扣与整单折扣均支持向下减/向上加，百分比与金额两种形式；加价无上限；自动折扣（产品/客户/促销）只减，加价仅手动（V132）
- **R9 客户账号/自助注册/移动端自助下单**：客户自助注册（PC + 独立 H5 注册页，V129），审核通过自动创建客户账号 + 主数据（dealer+联系人+地址），登录名为手机号；客户角色 RBAC（V130/V133），数据范围后端强制按 dealer_id 隔离，越权 403/空；H5 自助下单全流程（选地址、选品、自动折扣、行/整单折扣、一口价、代金券、促销文案、金额实时刷新）

### 计价引擎
- 新增 V4PriceEngine 统一计价口径：含税、正整数数量（V123，销售订单 qty 与 BOM 子件用量改整型）、2 位小数、中间高精度、尾差吸收到金额最大行
- 行价格优先级：基础客户价（合同价 > 客户价 > 全局价）→ 产品全局折扣 → 产品促销折扣 → 行手动折扣；整单折扣：客户全局折扣 → 整单手动折扣；先行后整单，整单增减按行折后总价比例摊到行再摊到 EA；0 金额行/赠品行权重 0
- 提交时后端重算落库，不信任前端金额；拦截信息含行号/SKU/原因

### 当日修复（批次 B，2026-08-27 下午）
- 代金券审批拒绝后未返还：`SalesOrderApprovalCallback` / `SalesReturnApprovalCallback` 补返还逻辑
- 销退单返工：新增页加发货仓库过滤、字段顺序调整为 经销商→发货仓库→发货单→退货原因；外键字段禁自由文本改选择器；恢复出库单弹窗批号/序列号筛选（`SalesReturnController/Service`、`RmaOrderService`、`SalesReturnEdit.vue`、`ResourcePicker.vue`）
- `OrderCreate` / `MOrderCreate` 价格回显修复：`p.productId ?? p.id ?? null`、dealerName 回显
- V134：`rma_order_lines` 增加 `serial_no` 字段

### 测试期缺陷修复（详见测试报告）
- 促销 `rule_detail` jsonb 以 String 存储未解析导致促销静默失效；满件打折文案错误；RMA unified 列表/404；注册租户/地址违约

### 数据库迁移
- V123–V134：integer_quantities、customer_contacts_addresses、rma_multi_outbound、global_discounts_contract_price、customer_vouchers、order_pricing_fields、customer_self_registration、customer_role_rbac、promo_types、line_discount_direction、v430_rbac_resources、rma_order_lines_serial_no（V121/V122 为 v4.2.9 RBAC 迁移）

### 验证
- 三端真实浏览器冒烟 0 FAIL；价格引擎 A–L 折扣场景全 PASS；R1 多出库销退、R9 客户自助注册全链路 PASS；F1–F5 核心流无回归

## v4.2.9 (2026-08-26) - RBAC 越权修复、三端 UI/UX 一致性、移动端经销商锁定、平台后台补全

> PATCH 版本。四端并行审计（PC/移动/平台后台/9 角色权限矩阵）后修复，已部署测试环境并通过 272 项深度冒烟 + 针对性回归。

### 安全修复（Critical）
- **RBAC 业务角色越权**：`PermissionQueryService` 将 `api.auth`（登录登出公开接口）错误派生为 `auth:create/edit/delete`，已将 `auth` 加入排除列表
- 新增 `V121__revoke_business_role_admin_perms.sql`：回收非管理员业务角色的 `approval:admin/manage`、`auth:*`、`user/role:*`、`menu.user/role/tenant`、`tenant_ui_config:*`、`api.user/role/tenant`；经销商角色额外回收 `promotion:*`/`product_price:*`
- 新增 `V122__grant_business_roles_price_promo_view.sql`：给非经销商业务角色补授 `promotion:view/search`、`product_price:view/search`（下单计价需要）
- `PermissionChecker.canAdminApprovals()` 收紧为仅校验 `approval:admin`/`approval:manage`
- 9 角色 API 矩阵验证全部符合预期

### PC 端
- 修复平台布局筛选参数与后端不一致：销售订单经销商 `dealer→dealerId`、创建/业务日期范围、采购供应商、仓库、产品分类、接口日志状态/时间等统一映射；`CrudView` 与 `ListPageLayout` 的查询/导出共用映射，合同列表补创建日期后端过滤
- `CrudView.vue` 新增按钮绑定 `createPermission` 权限码，无权限不再渲染「新增」
- `modules.js` 补全 20+ 模块 `createPermission`；orders/sales-returns/purchase-orders 配置 `keywordFields`，搜索框正确提示可搜经销商
- `router/index.js` 新增 `beforeEach` 权限守卫，越权 URL 跳转 403
- 采购订单后端补 `keyword` 参数支持按供应商名搜索；合同工作台重置过滤空参数

### 移动端
- `MOrderCreate.vue`：dealer_admin 经销商自动锁定（readonly 不可点击），回填编码+名称
- 经销商 picker 空值校验，未选择时 Toast 提示

### 平台后台
- 新增「首页总览」统计页；根路由不再直接重定向
- 经销商租户「所属厂家」显示名称而非 UUID，新建表单改为厂家下拉
- 新增「报表总览」入口，修复子路径跳转
- 11 页面统一查询/重置/分页/必填校验/二次确认/枚举中文/表格规范

### 验证
- 后端打包、三端前端构建均通过；深度冒烟 272/272，0 Console/网络错误

## v4.2.9 (2026-08-25) - 全量问题排查修复：后端 500、PC/移动统一、查询项生效、部署与营销页图片

> PATCH 版本（42 个文件，+1633 / -417）。本批变更已部署测试环境和生产环境并通过真实浏览器验证，代码已同步回仓库以保持一致。完整变更清单见 `DMS-changes-2026-08-25/清单.md`。

### 修复
- **后端列表/详情 500 错误**
  - 销售订单列表按经销商关键字搜索 500：`SalesOrderService` COUNT 查询缺少 JOIN dealers，补全关联
  - 合同列表 500：PostgreSQL TIMESTAMPTZ 列经 Hibernate 返回 `Instant`，强转 `OffsetDateTime` 报错；改用统一 `DateFmt.fmt()` 格式化（`ContractService` 等）
  - 经销商画像、授权、采购/销退、岗位、促销、跟台报告、用户等列表/详情的查询条件与字段映射修复（`DealerProfileService`、`AuthorizationService`、`PurchaseOrderService`、`SalesReturnService`、`SalesPositionService`、`PromotionService`、`SurgeryReportService`、`UserService` 及对应 Controller）
- **PC 端各列表查询项生效**：经销商、产品、状态、日期等搜索条件全部接入后端查询（`BizDocListController`、`InventoryListController`、`PurchaseReturnController` 等）
- **PC 与移动端统一**
  - 移动端销售订单去除对授权的依赖，补齐订单类型、BOM 结构、折扣填写
  - 移动端审批报错修复（`MApprovalDetail`、`MApprovals`）
  - `MOrderCreate` / `MOrderDetail` 大规模重构：计价、BOM、促销、可退量逻辑对齐 PC
  - 移动首页/看板/登录/消息/扫码收货/盘点/手术报台等页面同步修复（`MHome`、`MDashboard`、`MLogin`、`MMessages`、`MReceiveScan`、`MInventoryScan`、`MSurgeryReportCreate/Detail`、`ReceiptConfirm`）
- **前端基础设施**：`CrudView` 通用列表组件、`dict.js` 字典、`reports.js` 报表配置、`api/order.js` 调整；`Admin.vue`、`ContractWorkspace.vue` 同步
- **数据库迁移**：新增 `V120__fix_v83_resource_api_paths.sql`，修正 V83 写入的资源 path 与 Controller `@RequestMapping` 不一致（`position:view` → `/api/sales-positions/**`、`contract_template:manage` → `/api/contract-templates/**`、`tenant_ui_config:view` → `/api/tenant-ui/**`）

### 部署与基础设施（测试环境，非仓库文件）
- 修复 `dms-test-nginx` 容器缺失前端 bind mount 导致营销页/移动 H5/平台后台三端无法访问，重建容器
- 补全营销页图片：从 `brochure-test/assets/` 复制 41 张 PNG 到 `/opt/dms/test/frontend/assets/`，修复 34 张图片全部 404
- 清理本地临时脚本与测试服务器临时文件、旧容器（`brochure-test`）、旧镜像及 builder 缓存（磁盘 41% → 39%）

### 生产发布与清理（2026-08-25）
- 已发布到正式环境 `http://8.133.193.238/dms/`；后台 `http://8.133.193.238/dms/admin/`，移动端 `http://8.133.193.238/dms/mobile/login`
- 部署前备份：数据库 `/opt/dms/backups/dms-db-pre-v429-20260825-221506.sql.gz`；旧应用 `/opt/dms/backups/app-prod-20260825-221518.jar`；旧前端 `/opt/dms/backups/frontend-prod-20260825-221518/`
- Flyway V120 在生产执行成功；生产数据中该迁移 UPDATE 匹配 0 行，为幂等无副作用
- 处理生产 docker-compose v1.29.2 与 Docker 29.x 的 `KeyError: 'ContainerConfig'` 兼容问题：启动 postgres/redis/minio 后，移除旧 backend 容器并用 `docker-compose ... up -d --no-deps backend` 重建
- 生产清理：删除 8 月 22/24 的旧发布包与完整旧目录，保留本次回滚包、上一版备份和数据库备份；执行 Docker builder/container/image prune，无停止容器、无悬空镜像、无可回收构建缓存
- 清理结果：根分区从 11G/28% 降至 9.9G/27%；运行中 DMS、webgate、brochure、ai-knowledge 容器均保持正常

### 验证
- 后端 API：35/35 通过（登录、列表、详情、删除等核心接口）
- 三端真实浏览器验证：营销页 / 移动 H5 / 平台后台均可打开、登录，Console 与网络无错误
- 营销页 34 张图片全部 `complete=true`、无 broken
- 后端容器 healthy

### 清理
- 删除旧变更快照目录 `DMS-changes-2026-08-24/`（44 个文件，此前已从磁盘删除，本次提交记录删除）
- 按 `DMS-changes-2026-08-25/清单.md` 第四节清理本地临时文件：根目录 `zip_fe.ps1`、`sync_release.ps1`；`tools/` 下 check_lookups*.sh、inspect_data*.sh、patch_build_deploy*.sh、rebuild.sh、redeploy.sh、deploy_fe.sh、verify_fe.sh、extract_fe.py、probe-mobile-*.png、audit-mobile-deep.cjs.bak 共 20 个
## v4.2.8 (2026-08-25) - 演示前 RBAC 修复、部署脚本修复、移动端 UX 收尾

### 修复
- **部署脚本致命错误** `scripts/deploy_test.py`：前端构建 base 路径错误（业务前台需 `VITE_BASE=/dms/`，admin 为 `/dms/admin/`），且解压目标路径与 nginx alias 不一致，导致部署后整站 500。重写脚本：前端解压到 `/opt/dms/test/frontend/dms/`，后台到 `dms/admin/`，landing 页保留到根
- **RBAC 权限码不匹配** 业务角色被授予 `products:view`（带 s），而 `ProductController` 的 `@PreAuthorize` 要求 `product:view`（不带 s），导致销售/客服/商务/财务/合同无法查看产品；V114 还过度回收了促销查看权限。新增 V116/V117/V118 迁移，统一补授 `product:view/search`、`promotion:view/search`、`product_price:view/search` 给业务角色
- **经销商越权查看价格/促销** 多角色共享同一组策略，DB 层无法隔离；在 `PermissionChecker` 新增 `isDealer()` 方法，`ProductPriceController`/`PromotionController` 类级别加 `and !@perm.isDealer()` 条件，经销商类角色（DEALER_ADMIN/SERVICE/SALES）返回 403
- **`ProductPriceController` 缺类级权限注解** 原本只有方法级 `@PreAuthorize`，list/detail 完全无保护，补上类级 `product_price:view/search`
- **移动端业绩入口跳 PC 路由** `MDashboard.vue` 的 `goReport()` 跳 `/reports?key=...`（PC 路由），在移动端显示异常；改为 Toast 提示"详细报表请在 PC 端查看"
- **移动端表单只读 picker 长文本截断** `MSurgeryReportCreate.vue` 根元素缺少 scoped class，`:deep()` 样式完全不生效；补 class 后收窄 label（60px）、缩小字号（11px）、ellipsis 截断；`MOrderCreate.vue` 同步处理

### 验证
- 三端部署至 http://43.128.145.141/ ，健康检查 UP，landing/pc/admin/mobile 均 200
- 后台管理审计 12/12 PASS（厂家租户/经销商租户/租户管理员/角色模板/平台菜单/页面配置/全局字典/接口日志/审计日志/报表总览）
- RBAC 矩阵 9 角色 × 8 端点全部正确：非管理员 users/roles 返回 403；dealer_admin promotions/product-prices 返回 403；业务角色 products/promotions/product-prices/sales-orders/dealers/dashboard 均 200
- PC 业务流 29/29 PASS（报表 9 项/岗位业绩/经销商画像/合同/销退/邮件日志/手术报台/促销/BOM/审批模板 SALES_ORDER_ENABLED）
- PC UX 14 PASS / 3 WARN（均为非缺陷：筛选 popover、导出直接下载、经销商详情为独立页面）/ 0 FAIL，Console 与网络无错误
- 移动端深度审计 4 PASS、0 major/critical、2 minor（只读 picker 在 393px 窄屏 ellipsis 截断，不影响功能）
## v4.2.7 (2026-08-24) - Skill 驱动的需求准入与深度 QA 流程增强
> 流程增强（不升应用版本）。针对用户反馈“测试浮于表面、UI/UX/功能价值未真正验证、需求理解不准”，安装并固化 QA/浏览器 Skill，同时把 DMS 项目方法论凝练为专属 Skill。

### 新增/固化
- 安装 `qa-skills`、官方 `playwright`、官方 `screenshot` 到 `~/.codex/skills/`
- 创建 DMS 专属 Skill：`dms-project`、`dms-requirement-intake`、`dms-ux-functional-audit`
- AGENTS.md 第 10 节扩展为完整 Skill 工作流：阶段 A 强制需求准入，阶段 C 强制 UI/UX/业务功能审计
- `.memory/layers/layer1-rules.md`、`layer4-decisions.md`、`layer5-context.md`、`index.md`、`requirement-closure.md` 同步写入新流程
- 拉取 `obra/superpowers` 与 `genkovich/sdd` 到 `~/.codex/skill-evaluation/` 作为参考，未全量启用

### 验证
- 已校验 8 个必备 Skill 均存在 `SKILL.md`、frontmatter 完整、无 `U+FFFD` 乱码
- 已校验 `qa-skills` 核心 UX/adversarial/mobile/validation/playwright 文件存在
- 已校验 AGENTS 与 `.memory` 更新文件均包含新 Skill 流程且无新增乱码
- 本次未改业务代码、未部署、未运行浏览器冒烟；后续首个业务任务将按新流程执行真实浏览器验证

## v4.2.7 (2026-08-23) - 日期时间筛选支持时分秒 + 选完结束日期弹层不消失 + 宽表筛选漏斗可点击
> PATCH 版本。针对 v4.2.6 部署后用户反馈：1) 日期时间筛选控件只能选日期、不能选时间；2) 选完结束日期后控件自动消失，无法筛选；另修复产品等宽表页面筛选漏斗被固定操作列遮挡而无法点击的问题。

### 修复
- **Q1 时间筛选支持选时分秒** `CrudView.vue` + `modules.js`：筛选弹层 `el-date-picker` 支持 `type: datetime/datetimerange`（值格式 `YYYY-MM-DD HH:mm:ss`），全部列表 `createdAt/updatedAt` 列由 `date` 升级为 `datetime`；弹层宽度按 datetime 动态适配（400/800）；后端 `SpecUtil` 新增 `rangeBound/hasTime/parseLocalDateTime/parseOffsetDateTime`，订单相关 service/controller 的 `createdAtFrom/To`、`updatedAtFrom/To` 支持带时间（To 带时间用 `<=`，纯日期用 `<`）
- **Q2 选完结束日期弹层自动消失** `CrudView.vue`：popover 改为受控模式（去掉 `@update:visible`），仅外部 mousedown（不在弹层与触发图标内）才关闭，鼠标移入日期时间面板/应用按钮不再消失
- **Q3 宽表筛选漏斗被固定操作列遮挡** `CrudView.vue`：表格总列宽超出容器出现横向滚动时，Element Plus 固定右列会悬浮覆盖最后一列（创建/更新时间）表头的筛选漏斗导致无法点击；现依据 `el-table--scrollable-x` 自动取消操作列固定，恢复漏斗可点击；不溢出时仍保留固定
- **本地持久化（沿用）** 列表分页/排序/筛选状态按用户账号 + 模块维度本地保存，操作后返回不丢失

### 验证
- `npm run build`、`mvn -q -DskipTests compile|package` 通过；部署测试环境 http://43.128.145.141（备份 stamp `20260823-155510`，健康检查 200）
- `tools/verify_v427.cjs` headless 自验 **11/11**：Q1 datetime 面板含时间选择、值带时分秒、请求带 createdAtFrom/To / Q2 选完结束日期弹层仍打开、移入应用按钮不消失 / Q3 产品列表时间筛选漏斗可点、面板含时间、请求带参数 / 回归经销商下拉+dealerId、最终金额数字范围 / Console 干净 / 无 5xx
- 截图：`automation_test/v4-browser-results/verify-v427-*/`

### 文件改动
- `frontend-vue/src/components/CrudView.vue`（受控 popover + datetime 模板 + 动态宽度 + 宽表取消固定列）
- `frontend-vue/src/config/modules.js`（约 40 处 createdAt/updatedAt 筛选 → datetime）
- `backend/src/main/java/com/dms/common/util/SpecUtil.java`（rangeBound/hasTime/parseLocalDateTime/parseOffsetDateTime）
- `backend/src/main/java/com/dms/order/service/SalesOrderService.java`、`SalesReturnService.java`、`PurchaseOrderService.java`、`PurchaseReturnController.java`
- `tools/verify_v427.cjs`（新增）

## v4.2.6 (2026-08-23) - 日期筛选面板不消失 + 销售订单经销商/金额筛选修复
> PATCH 版本。针对 v4.2.5 部署后用户反馈的 3 个问题：1) 日期筛选面板鼠标移入即消失；2) 销售订单经销商筛选无效（销退正常）；3) 销售订单最终金额列无数字范围筛选。

### 修复
- **Q1 日期筛选浮层消失** `CrudView.vue`：筛选 popover 内 `el-date-picker`（单日期 / daterange）加 `:teleported="false"`，日期面板渲染进 popover DOM，鼠标移入面板不再被 popover 当作外部点击关闭；popover 宽度按列类型动态适配（date 340 / daterange 640），`.crud-filter-popover` 允许面板溢出展示
- **Q2 销售订单经销商筛选无效** `modules.js`：经销商列由 `select + remote`（原发送 `dealerName` 参数、后端不接收）改为 `resource + paramKey: dealerId`，与销退订单一致；应用后请求真实携带 `dealerId` 且结果过滤生效
- **Q3 销售订单最终金额无范围筛选** 前端 `modules.js` 给 `finalAmount` 列加 `filter: { type: 'number', range: true }`；后端 `SalesOrderController / SalesOrderService` 新增 `finalAmountFrom / finalAmountTo` 参数与 `o.final_amount >= / <=` 过滤条件（模式与销退订单一致）

### 验证
- `mvn -q -DskipTests compile`、`mvn package -DskipTests`、`npm run build` 全部通过
- 部署到测试环境：http://43.128.145.141（备份 stamp `20260823-151526`，健康检查 200）
- `tools/verify_v426.cjs` headless 自验 **12/12**：Q1 日期面板渲染在 popover 内且鼠标移入不消失 / Q2 经销商下拉 12 个选项 + 应用后请求带 `dealerId` / Q3 最终金额列出现数字范围输入框 + 请求带 `finalAmountFrom/To` / Console 干净 / 无 5xx
- 截图：`automation_test/v4-browser-results/verify-v426-*/`

### 文件改动
- `frontend-vue/src/components/CrudView.vue`（date-picker teleported + popover 动态宽度）
- `frontend-vue/src/config/modules.js`（orders 经销商 resource 下拉 + finalAmount 数字范围）
- `backend/src/main/java/com/dms/order/controller/SalesOrderController.java`
- `backend/src/main/java/com/dms/order/service/SalesOrderService.java`
- `tools/verify_v426.cjs`（新增）

## v4.2.5 (2026-08-23) - 筛选下拉真实可用 + 订单更新时间有数据 + 范围过滤后端落地
> PATCH 版本。针对 v4.2.4 部署后仍存在的问题：resource 下拉空白、销售/销退"更新时间"列无数据、日期/金额范围在订单类页面不生效。

### 修复
- **Q2.3 resource 下拉空白（真实修复）** `frontend-vue/src/components/CrudView.vue`：`selectFilterOptions()` 增加 `filter.resource` 解析（原只处理 `filter.remote`），`ensureRemoteFilterOptions()` 同步收集 resource 键并扩充端点映射（dealers/suppliers/warehouses/hospitals/products/product-lines/contracts），打开筛选 popover 时懒加载；产品价格"经销商/供应商"、销退"经销商/收货仓库"等 resource 下拉现在有选项可选
- **Q3 更新时间列有数据** 销售订单/销退/采购/采退列表 SQL 补查 `updated_at` 并映射 `updatedAt`（此前前端自动注入列但数据为空）
- **Q2.6 日期范围在订单类页面真实生效** SalesOrder/SalesReturn/PurchaseOrder/PurchaseReturn/ProductPrice 列表接口新增 `xxxFrom/xxxTo` 范围参数（`>=`/`<` 次日 0 点），支持 createdAt/updatedAt/validFrom/validTo 范围筛选
- **Q2.5 金额/数量范围在订单类页面真实生效** 销售/销退/采购/采退 `finalAmount`、采购 `totalAmount`、库存 `qty` 支持 `From/To` 范围过滤；对应模块列升级 `filter.range: true`
- **物料/仓库/供应商可下拉** 库存查询"物料/仓库"、采购/采退"供应商/仓库"列由文本/编号输入升级为 resource 下拉（参数与后端一致：productId/warehouseId/supplierId）
- **排序参数** 订单类列表接口支持 `sort=updatedAt,desc`（前端默认按更新时间倒序）

### 验证
- `mvn package -DskipTests` 通过、`npm run build` 通过
- 部署到测试环境：http://43.128.145.141
- `tools/verify_v425.cjs` headless 自验：Q1 BOM子件销售价净化 / Q2.3 经销商下拉有选项 / Q2.6 日期范围请求带 From/To（区域+销售订单）/ Q2.5 levelFrom/To / Q3 更新时间列+数据非空 / Console 与网络 5xx 干净

### 文件改动
- `frontend-vue/src/components/CrudView.vue`、`frontend-vue/src/config/modules.js`
- `backend/.../SalesOrderController|Service.java`、`SalesReturnController|Service.java`、`PurchaseOrderController|Service.java`、`PurchaseReturnController.java`、`ProductPriceController|Service.java`、`InventoryController.java`
- `tools/verify_v425.cjs`

## v4.2.4 (2026-08-23) - 列表筛选 UX 大改 + 后端范围过滤 + 价格详情净化 + 订单列补齐
> PATCH 版本。基于 4.2.3 部署后用户反馈，3 个未解决问题 + 范围过滤后端支持全部修复。

### 新增
- **后端 `SpecUtil` 支持 `keyFrom` / `keyTo` 范围过滤**：数值字段（BigDecimal/Long/Integer/...）生成 `>=` / `<=`，日期/时间字段（LocalDate/LocalDateTime/OffsetDateTime/Date）生成 `>=` / `<`（To 用次日 0 点实现 `<`），与原字符串 ILIKE 完全兼容
- `frontend-vue/src/config/modules.js`：`createdAt` / `updatedAt` 全部改为 `filter: { type: 'date', range: true }`，区域管理 `level` 改为 `filter: { type: 'number', range: true }`，至少 1 个数值范围演示点

### 改进
- **问题 1** `frontend-vue/src/views/ResourceDetail.vue`：`groupedFields` computed 过滤 `f.type === 'component-prices'`，详情页"价格信息"分组不再渲染原始 JSON；底部"BOM子件价格"表保持 `moduleKey === 'product-prices' && componentPrices.length` 守卫
- **问题 2.1** `CrudView.vue` 筛选 popover：el-select 加 `:teleported="false"`，dropdown 浮层渲染在 popover DOM 内，下拉可正常选择
- **问题 2.2** 文本筛选 placeholder 改为"模糊搜索（支持部分匹配）"；后端 `SpecUtil` 字符串字段走 ILIKE，文本已支持部分匹配
- **问题 2.3** 物料/经销商/供应商等远程资源下拉：popover 内统一走 `selectFilterOptions()` 解析 `remote`/`resource`/`dict`，数据正确加载
- **问题 2.4** 工具栏筛选控件宽度统一 200px，按钮 padding 6px 14px，漏斗 icon 16px
- **问题 2.5** popover 内数字字段支持 `filter.range: true` → 双 `el-input-number` From/To
- **问题 2.6** popover 内日期字段支持 `filter.range: true` → `el-date-picker type=daterange`，前端 `fetchData` 发送 `key+From` / `key+To` 查询参数，后端 `SpecUtil` 接收并生成对应谓词
- **问题 3** `modules.js` 中 `orders` / `sales-returns` 已在 CrudView 通用机制下显示创建/更新时间两列（v4.2.3 已实现）

### 验证
- `mvn -DskipTests compile` 通过（SpecUtil 重写后无编译错误）
- `npm run build` 通过（前端 chunk 已部署）
- 部署到测试环境：http://43.128.145.141
- `tools/verify_v424.cjs` headless 自验（11 项）：Q1 BOM子件销售价净化 ✓ / Q2.1 popover select ✓ / Q2.2 模糊搜索 placeholder ✓ / Q2.4 工具栏宽度 ≥190px ✓ / Q2.5 数字 range ✓ / Q2.6 日期 range + 后端 `createdAtFrom/createdAtTo` 接收 ✓ / Q3 销售/销退订单 更新时间列 ✓ / Console 干净 / Network 5xx 干净
- 截图保存到 `automation_test/v4-browser-results/verify-v424-*/`

### 文件改动
- `backend/src/main/java/com/dms/common/util/SpecUtil.java`（重写：增加 range 谓词构造 + `Range` 谓词去重）
- `frontend-vue/src/components/CrudView.vue`（v4.2.3 已完成：popover 改写 + range 控件 + 宽度样式）
- `frontend-vue/src/views/ResourceDetail.vue`（v4.2.3 已完成：`groupedFields` 过滤）
- `frontend-vue/src/config/modules.js`（本次：`createdAt/updatedAt` 改 range；区域 `level` 改 range）
- `tools/verify_v424.cjs`（升级 R2，验证范围请求实际带 From/To）

## v4.2.3 (2026-08-23) - 列表页通用调整 + 产品价格只读页优化
> PATCH 版本。针对 4.2.1 部署后用户反馈，调整全站列表页通用细节 6 项 + 产品价格只读页 2 项。

### 新增
- `frontend-vue/src/components/CrudView.vue`：新增「列设置」弹框（基于 `sortablejs ^1.15.7`），用户可拖拽调整列表列顺序
- `frontend-vue/src/components/CrudView.vue`：新增 `currentAccountKey()` 工具函数，从 `dms_user` localStorage 读 `username/account/loginName` 派生账号维度键
- `frontend-vue/src/components/CrudView.vue`：自动注入 `创建时间` / `更新时间` 两列（无需在每个模块 config 中声明）
- `frontend-vue/src/views/ResourceDetail.vue`：新增 `groupedFields` computed，按 form field 的 `group` 分组渲染只读页

### 改进
- **R1.1** 工具栏顺序严格遵守 AGENTS.md §3.1：`查询 → 重置 → [spacer] → 导入 → 导出 → 新增 → 列设置 → 业务按钮`，按钮 `size=small` 统一对齐
- **R1.2** 分页状态本地持久化：`page` / `size` / `sortField` / `sortOrder` 在用户编辑/查看/删除后回退列表时自动还原；key 格式 `dms:listState:${account}:${config.key}`，按账号隔离
- **R1.3** 列表筛选 popover 内部控件（select / date-picker / input）统一加 `@click.stop`，避免点击控件时 popover 立即关闭导致选不上
- **R1.4** 列表列可拖拽排序，按账号本地持久化：key 格式 `dms:colOrder:${account}:${config.key}`，「列设置」弹框内提供「重置默认」按钮
- **R1.5** 编号 / 产品编码 / SKU 等字段不再渲染成 `<el-link>`，避免与行内「查看」按钮重复跳转；保留 `c.link` 显式声明可自定义
- **R1.6** 全部列表页末尾自动追加 `创建时间` / `更新时间` 两列，默认按 `updatedAt desc` 排序（已在原 CrudView 实现上确保不被覆盖）
- **R2.1** BOM 产品价格只读页按 form field `group` 分组渲染，价格信息分 2 列展示、其余组 3 列；BOM_HEADER 的 inclPrice/exclPrice 显示「见子件价格」；BOM 子件价格表守卫 `moduleKey === 'product-prices' && componentPrices.length`
- **R2.2** 「失效」/「启用」按钮原地更新 `detail.value.status`，不再 `load()` 或 `location.reload()`；按钮权限通过 `v-has="'product-price:deactivate'|'product-price:activate'"` 控制

### 验证
- `npm run build` 通过（CrudView / ResourceDetail 编译无错）
- `sortablejs ^1.15.7` 已在 `package.json` 依赖中，未新增第三方包
- 全站列表页通用生效（无 module config 改动）
- 后端无改动；现有 `ProductPriceController#deactivate/activate` 接口已存在

### 文件改动
- `frontend-vue/src/components/CrudView.vue`（修改）
- `frontend-vue/src/views/ResourceDetail.vue`（修改）
- 备份：上述两文件同目录的 `.bak.pre-v4.2.1-listpolish` / `.bak.pre-v4.2.1-detail`

## v4.2.2 (2026-08-23) - 测试分层策略（PATCH 范围测试）
> PATCH 版本。优化发布期测试效率：小版本发布不再跑全量回归，只测改动涉及的流程。

### 新增
- docs/02_设计/test-strategy.md：测试分层策略（PATCH/MINOR/MAJOR 三层） + 5 条核心业务流 F1-F5 基线 + 手动 scope 契约
- 	ools/scope-map.json：5 条核心流 + 13 个业务模块映射（UI 路径 + API + DB 表 + E2E spec）
- 	ools/test-scope.cjs：范围测试调度器（dry-run / --module= / --scope= / --include= / --core-flows= 四种组合）

### 改进
- AGENTS.md §5.0：新增“测试分层（按发布版本）”章节：
  - PATCH：	ools/test-scope.cjs 范围测试（只跑改动涉及的模块 / 流）
  - MINOR：	ools/smoke-test.cjs 全量员工测试
  - MAJOR：MINOR + 兼容性 + 性能
- 5 条核心流基线 F1-F5 必跑：下单→审批→出库 的发货流等
- 5 次元测试结构：UI / API / DB 表 / 业务规则 / 异常分支

### 契约
- 每次 PATCH 补丁前 Codex 必须反问涉及的业务流（不允许自动推断）
- 用户不明确时禁止自动推断 scope；必须明确指定 模块 / 流号 / include 后才跑测试

## v4.2.1 (2026-08-22) - 冒烟脚本 element-plus overlay 适配 / 覆盖层残留重置
> PATCH 版本。修复 v4.2.0 部署后冒烟测试中 3 个页面 "create btn" 失败，与 4.2.0 业务代码无关，纯工具脚本缺陷。
> **2026-08-22 23:19 已部署到生产** http://8.133.193.238/dms/（root@8.133.193.238，部署目录 /opt/dms/prod/，Flyway V109→V112 已自动应用，业务冒烟 43/43 PASS）。详见 `docs/02_设计/prod-deploy-report-v4.2.1.md`。
### 修复
- **`forceCloseOverlays` 选不中新版 element-plus 的 overlay 容器**：v2.4+ 的 el-dialog / el-drawer / el-message-box 不再以 `__wrapper` 为外层（`.el-dialog__wrapper` / `.el-drawer__wrapper` 等已废弃），而是统一挂在 `.el-overlay` 容器下。原脚本只匹配老 wrapper，导致 row action 触发的 drawer 关不掉，残留遮挡后续 "新增" 按钮 click。新版选择器同时覆盖 `.el-dialog__wrapper:visible, .el-drawer__wrapper:visible, .el-message-box__wrapper:visible, .el-overlay-dialog:visible`（旧）与 `.el-overlay:visible .el-dialog, .el-overlay:visible .el-drawer, .el-overlay:visible .el-message-box`（新），并加 `.van-popup/.van-dialog/.van-action-sheet` 兼容移动端。`force:true` 强制点 closeBtn，失败时降级到蒙层 `mouse.click(8,8)` / `Escape`，最多 8 轮。
- **`processOnePage` row action 与 create 按钮之间不再共享 DOM 状态**：`clickFirstRowAction` 后增加 `page.goto(BASE + p.path)` 重访当前路径（DOMContentLoaded + sleep 800ms），清掉 drawer / overlay / 路由过渡动画与可能的防连点锁，再做 `clickCreateButton`。彻底消除"row action drawer 已视觉关闭但 element-plus 内部状态残留"导致的 "clicked no dialog" 误报。
- **`clickCreateButton` 选择器收紧**：原循环用 `container.locator(".el-button--primary, .el-button--success, .el-button")` 深度搜索整张卡片/工具区，会把表格内行内按钮（编辑/发布/更多）和主题切换 chip 都纳入候选，依赖内层文本正则命中。新版先在工具区可见容器（`.el-card .toolbar, .toolbar, .crud-toolbar, .list-toolbar, .page-header, .el-card`）内按文本找 "新建/新增/创建/添加/Create/Add" 按钮，找不到再 fallback 到全页可见 `button/a`（排除 `.el-table__body-wrapper` 内），排除"更多"按钮防止误点。click 用 `force:true` 跳过 actionability check。

### 验证
- `--module=approval`：**25/25 PASS**（含原 3 个失败项中的 `PC-approval-templates`）。
- `--module=admin`：**42/42 PASS**（含 `ADM-manufacturers`、`ADM-dealers`）。
- `--module=tenants`：**12/12 PASS**。
- 全量 PC 段（221 项）0 FAIL，无回归。
- 前端 vitest **36/36** 通过；后端 134/134 仍绿（未触及）。
- 测试环境 actuator/health 200，无停机。

### 部署
- 仅工具脚本变更，无需重启后端；理论上无需重新部署（脚本是测试环境外的 node 进程直接执行）。
- 如需对齐发布版本：发布包 v4.2.1 含 `dms-backend.jar`（与 4.2.0 同）+ PC/admin dist（与 4.2.0 同）+ `tools/smoke-test.cjs` 修复版。
## v4.2.0 (2026-08-22) - 胖 Controller 下沉 / Schema 漂移修复 / 测试加固

> MINOR 版本，已由用户明确授权升级。包含 fat controller 重构、email_logs/price_scope 等 schema 漂移修复、Supplier 主键对齐，以及下单→审批→出库→库存扣减主链路相关测试加固。

### 重构（胖 Controller 瘦身，行为保持）
- 将业务逻辑从 Controller 下沉到 Service，Controller 只做参数接收与委托：
  - `SalesReturnController` → `SalesReturnService`（+ `SalesReturnLineSupport`）
  - `PurchaseOrderController` → `PurchaseOrderService`
  - `SalesOrderController` → `SalesOrderService`
  - `SalesPositionController` → `SalesPositionService`（`PositionResolver` 由 `org.controller` 迁至 `org.service`）
  - `BusinessReportController` → `BusinessReportService`
  - `BizDocDetailController` → `BizDocDetailService`
  - `SurgeryReportController` → `SurgeryReportService`
  - `OrderApprovalExecutionController` → `OrderApprovalExecutionService`
  - `ProductPriceController` → `ProductPriceService`
  - `CompatAliasController` → `CompatAliasService`
- 抽出可复用组件：`SqlValueSupport`（SQL 空值/类型安全）、`ApprovalResponseSupport`（审批响应统一）、`ActionButtonSupport`（行内动作按钮状态）。
- 所有 HTTP 路径与接口契约保持不变；后端 134 测试全绿保证回归。

### 修复
- **下单 500（折扣字段为 NULL）**：`OrderLine` 折扣字段设为 nullable=false，`OrderService` 默认 `ZERO`，避免折扣空值导致下单异常。
- **email_logs.duration_ms 类型漂移**：V95 误建为 INT，实体为 Long，V110 对齐为 BIGINT（Hibernate validate 不再报 wrong column type）。
- **product_prices.price_scope 默认值漂移**：默认值仍为旧 'SALES'，不显式指定的新写入落回旧值导致计价漏匹配；V110 改为 'SALE'，V112 幂等归一化历史脏值（'SALES'/'GLOBAL'/'DEALER' → 'SALE'，'PUR'/'SUPPLIER' → 'PURCHASE'）。
- **period_yyyymm 列类型漂移**：stocktakes / rebate_previews / rebate_settlements / dealer_kpi_snapshots 由 CHAR(6) 改 VARCHAR(6)，消除尾补空格导致的比较/唯一约束不一致（V111）。
- **Supplier 主键类型对齐**：Supplier UUID→Long，对齐真实 BIGSERIAL 主键及 Repository。
- **PermissionQueryService**：修复乱码注释（不影响逻辑）。

### 测试
- 后端测试由 130 增至 **134**，0 failures / 0 errors / 0 skipped。
- 新增：`SalesReturnLineSupportTest`(10)、`ApprovalResponseSupportTest`(3)、`ActionButtonSupportTest`(1)、`SqlValueSupportTest`(3)，以及下单/审批/出库/促销/BOM 链路相关测试（`com.dms.chain`、`com.dms.core`、`V4CalculatorPromotionTest`、`SalesReturnLockingIntegrationTest` 等）。
- 前端 vitest **36/36** 通过（auth/format/has 指令/dict）。
- 修复 `tools/smoke-test.cjs` 长会话挂起：增加步骤级 `withTimeout`、`forceCloseOverlays` 兜底关闭 dialog/drawer/messagebox、全局 8 分钟进程级超时；PC/Admin 单页 60s、Mobile 30s 上限。dashboard/order 模块实测 30s 内干净退出。

### 部署
- 测试环境：http://43.128.145.141/
- 新增 Flyway 迁移 V110/V111/V112（均幂等），部署前需确认测试库 `flyway_schema_history` 无同版本记录，详见 `docs/02_设计/reviews/v4.2.0-deploy-conflict-report.md`。
- 回滚备份：`releases/v4.1.7-backup-20260822-205600/`。
## v4.1.7 (2026-08-22) - 销退选单带出实物赠品（口径同步）

### 修复
- **销退「选择发货单」漏带赠品行**：v4.1.6 把实物赠品（`is_gift=true`）放进了 `sales_out_lines`，但 `SalesReturnService.shippedOutLines()` 仍沿用「跳过赠品 + 用 final_amount 推算单价」的旧逻辑，结果选择发货单接口只回 4 行而不是 5 行，赠品行在 UI 上凭空消失。赠品是物理件，必须与付费行一样可被选择退货。
- **去掉赠品跳过**：移除 `if (lineIsGift || "PARENT".equals(lineLevel)) continue;` 中对赠品的过滤，只保留对 BOM 母件（`PARENT`、无实物）的过滤。
- **单价改用出库行 `unit_price`**：原代码 `lineSupport.toBd(l.get("final_amount")).divide(shipped, 4, HALF_UP)` 在赠品 `final_amount=0` 时计算失真；改为直接读 `unit_price`（赠品 = 0.00，精确）。

### 工程 / 测试
- 新增 `SalesReturnLockingIntegrationTest.shippedOutLines_includesGiftLine`：直接造一个 COMPLETED 的 sales-out（1 行付费 + 1 行赠品），调 `SalesReturnService.shippedOutLines()`，断言返回 2 行且赠品行 `qty=2`、`unitPrice=0`、`returnableQty=2`。
- 测试中显式 `TenantContext.setTenantId(tenant.getId())` 模拟 TenantInterceptor 行为——这是直接调 service 层（非 controller 路径）所必需的。
- 销退锁测试 + 赠品带出测试 全绿（2/2）。

### 验证（测试环境）
- 出库单 `GI-20260822-00012` 含 5 行（含 1 行实物赠品 PRD-J004 单价 0）。
- 销退「选择发货单」接口确认返回 5 行（PRD-J004 数量 5、单价 0、可退 5）。
- 用户在销退新建页选 GI-20260822-00012 应能带出 5 行明细，赠品行可正常参与退货。

## v4.1.6 (2026-08-22) - 实物赠品应当出库（一致性修复）

### 修复
- **实物赠品被错误地不出库**：v4.1.3 / v4.1.4 在出库生成逻辑里把 `is_gift=true` 的订单行一并跳过。但骨科植入物类促销赠品（接骨螺钉/钢板等）本身就是实物，必须随订单一起出库；"不参与总价/不参与折扣/不出库"是错误的过度修正。
- **`V4ErpService.simulateShip` / `receiveOutbound` / `AutoDocGenerator.createSalesOutForOrder`**：去掉对 `is_gift` 的无差别跳过；只保留对 BOM 母件（`line_level=PARENT`，无实物）的跳过。赠品行按 0 元正常进入 `sales_out_lines` 和 CONFIRMED `sales_out_batch_lines`。
- **`V4ErpService.refreshOrderStatus` 订单完成判定**：移除 `AND is_gift=false` 过滤，赠品（实物）出库后订单应能 COMPLETED；与出库侧"赠品要发"口径一致。

### 工程 / 测试
- `SalesOrderApprovalOutboundChainTest.orderWithGiftLine_completesAfterShipment_notPartial`：恢复并加强——下单→提交→注入赠品 order_line→simulateShip→回读订单 COMPLETED，**并断言赠品行出现在 sales_out_lines**。
- 新增 `physicalGift_isShippedBySimulateShip`：断言赠品同时在 `sales_out_lines` 和 `sales_out_batch_lines`（CONFIRMED 批次子单）里。
- 链式测试 4/4 全绿；测试环境冒烟 272/272 通过。

### 验证（测试环境）
- 建促销：买 PRD-J002 ≥1 赠 PRD-J004 ×2；预览带 `gift=true` 的 PRD-J004 行。
- 下单 → 自动审批 → simulateShip → GI-20260822-00011：含 2 行（PRD-J002 实物 2 个单价 400 + PRD-J004 实物赠品 2 个单价 0），batch 全部 CONFIRMED，订单 COMPLETED。

## v4.1.5 (2026-08-22) - 销退可退数量提交即锁定 / 驳回不双重释放

### 修复
- **销退提交后可退数量未正确扣减（草稿仍显示全额可退）**：前端 `SalesReturnEdit.returnableQty` 只减了已退数（returnedQty），漏减其他审批中销退单的占用锁（lockedQty/otherLockedQty），导致一张销退单提交后，另一张在它之前建好的草稿仍显示并尝试提交全额。现可退数量 = 已发 - 已退 - 其他单占用锁；加载草稿时把超过当前可退量的陈旧数量自动收敛到可退上限。
- **销退详情把本单自己的锁也减掉**：详情接口原来用出库行总 `return_locked_qty` 算可退，已提交单自己持有的锁也被减掉，自己的行显示可退 0。现新增 `other_locked_qty`（总锁 - 当前单各自行占用），只扣其他单的锁；前端优先使用 `otherLockedQty`。
- **驳回销退导致锁被双重释放**：`SalesReturnService.reject()` 在 `approvalService.rejectBusiness()`（其回调 `onRejected` 已释放锁）之后又显式调一次 `lockReturnLines(unlock=true)`，把 `return_locked_qty` 重复扣减，可退数量错乱。移除冗余释放，锁只由审批回调释放一次（通过/驳回/撤回/退回各一次）。
- **提交前逐行预检可退量**：`submit` 新增 `checkReturnableBeforeSubmit`，按出库行聚合并回读当前可退量，超额时返回含产品编码/名称、当前可退、本单需退的业务提示，而非模糊的「可能已被其他销退单锁定」。
- **审批模板条件匹配健壮性**：`ApprovalService.matchSingleRule` 在 snapshot 为 null 时直接放行，避免无快照/无条件模板触发 NPE 500。

### 工程 / 测试
- 新增 `SalesReturnLockingIntegrationTest`：同一出库行两张销退单，第一张提交加锁（6）、第二张超额创建被拒（40001）且锁不变、第二张在剩余额度内可提交（锁=10）、驳回第一张只释放自身 6（锁=4，不双重释放、不为负）。
- 覆盖审批中（MANUAL 模板 PENDING_APPROVAL）持锁、驳回释放、超额拦截全链路；销退锁测试 + 出库链测试全绿。

## v4.1.4 (2026-08-22) - 含赠品订单出库后状态修复

### 修复
- **含促销赠品的订单全部出库后仍显示「部分发货」**：`V4ErpService.refreshOrderStatus` 回算订单状态时只排除了 BOM 母件（`line_level<>PARENT`），没有排除促销赠品（`is_gift=true`）。赠品是 0 元非实物行、从不生成出库，于是被当成「未发完」，导致订单停在 PARTIAL_OUTBOUND 并提示母件/赠品数量未发。现查询同时过滤 `COALESCE(is_gift,false)=false`，只按实物行判定是否发完；与出库/销退过滤口径一致。
- 历史数据已在测试库幂等校正（只统计非母件非赠品实物行），受影响的 SO-20260822-00010 等 3 张订单已纠正为 COMPLETED。

### 工程 / 测试
- `SalesOrderApprovalOutboundChainTest` 新增回归用例 `orderWithGiftLine_completesAfterShipment_notPartial`：下单→提交自动审批→注入赠品 order_line→simulateShip→回读订单状态必须为 COMPLETED，不被赠品拖成 PARTIAL_OUTBOUND（3 个链式测试全绿）。

## v4.1.3 (2026-08-22) - 出库明细/行号、单据页缓存、满减周期

### 修复
- **ERP/模拟出库未回写发货子单（出库单详情无明细、无批序号/行号）**：`V4ErpService.receiveOutbound` 原来只写 `sales_out_lines`，不写 `sales_out_batches`/`sales_out_batch_lines`，导致销售出库详情页「发货子单」为空、销退选单无法带出行号。现 ERP 回写的已完成出库单会创建一张 CONFIRMED 发货子单，并为每个出库行写入 `batch_lines`（含 expected_line_seq/ship_line_no、batch_no、serial_no、unit_price）；出库行 `seq` 从 1 递增。
- **ERP/模拟出库把促销赠品也出库**：`receiveOutbound`/`simulateShip` 行循环新增 `is_gift` 跳过，与 `AutoDocGenerator` 保持一致（赠品 0 元无实物，不出库/不可退）。
- **销退新建残留上一单内容 / 离开销退页后弹「销退订单不存在」**：根因是 layout 的 `<keep-alive>` 缓存了单据编辑组件，`onMounted` 不再触发、被缓存实例的异步/定时器仍在请求旧 id。现给所有单据编辑/新建路由加 `meta.noCache`，layout 对这些页禁用 keep-alive；`SalesReturnEdit` 卸载时清理 `dealerTimer`。
- **满A减钱缺少「减免周期（仅一次/每满N循环）」**：前端 `modules.js` 周期/每满N列对 GIFT 和 FULL_REDUCTION 都显示；后端 `PromotionService.replaceRules` 对满减也校验 cycle（ONCE/EVERY_N）及 everyN>0；`V4Calculator.applyReduction` 按周期计算减免次数，循环减免 = 单次减免 × 次数（封顶不超过命中行折后金额）。

### 工程 / 测试
- `LinesEditor.colTitle` 支持函数型列标题。
- 路由 noCache 覆盖：合同/合同模板、销售/采购订单、收货入库、库存移动、销售出库、销退/采退新建与编辑。

## v4.1.2 (2026-08-22) - 促销规则保存校验 / 满赠周期语义 / 计价 price_scope 根因修复

### 修复
- **满A减钱保存误报「赠品SKU不能为空」**：`CrudView` 明细行必填校验原来只遍历 required 列、不看列显隐，导致满A减钱时隐藏的「赠品SKU/赠品数量」列仍被校验。现改为用 `{...formData, ...row}` 上下文执行 `showIf/showWhen`，隐藏列跳过；`modules.js` 给「命中SKU/命中产品层次/每满N数量」补上受显隐控制的 required。
- **满赠「赠送周期」语义修正**：`V4Calculator.applyGift` 现把 `thresholdQty` 作为起赠门槛 A、`everyN` 作为循环步长。仅赠一次：`hit>=A` 赠 1；每满N循环：`1 + floor((hit-A)/everyN)`（旧实现用 `floor(hit/threshold)` 把 A 当除数，与「每满N」列语义冲突）。
- **促销校验补强**：`PromotionService.replaceRules` 增加 `targetType` 枚举校验（SKU/LINE）、GIFT 的 `cycle` 校验（ONCE/EVERY_N）、EVERY_N 时校验 `everyN>0`；满A减钱不要求赠品字段。
- **计价「没有维护有效单品销售价格」根因修复**：重置演示数据时灌入的 `product_prices.price_scope` 是旧值 `SALES`，而计价查询按 `SALE` 过滤，导致有价格却解析为空。新增 Flyway `V112__normalize_price_scope_legacy.sql` 幂等归一化历史脏值；重新生成的 `ortho_demo_data_reset.sql` 已统一为 `SALE`。

### 工程 / 测试
- 新增 E2E `automation_test/e2e/specs/14-promo-target-cycle.spec.js`：覆盖满A减钱选SKU/产品层次保存、ONCE/EVERY_N 周期持久化、负向校验、preview 端到端计价（满减 5600→5100、EVERY_N 赠 3、产品线隔离、ONCE+EVERY_N 叠加 4、低于门槛不赠）、浏览器实点列表/新建弹窗与 Console 错误。
- `V4CalculatorPromotionTest` 新增「门槛A vs 每满N步长」用例，锁定新语义防止回归（共 7 个单测全绿）。
- 修复测试环境平台后台白屏：admin 构建默认用生产基路径 `/dms/admin/`，但测试以 `/admin/` 提供，资源 404。`deploy_test.py` 增加资源前缀校验，测试环境须用 `VITE_BASE=/admin/` 构建；冒烟测试 238→272 全绿。

## UNRELEASED - 测试体系补强 / 版本与文档卫生

### 新增
- 新增 SalesOrderApprovalOutboundChainTest（2 个深度集成测试）：用真实 Spring 栈 + 嵌入式 PG，完整走「下单 DRAFT → 提交自动审批 APPROVED（无模板 AUTO_APPROVED，回读 approval_instances）→ 出库 COMPLETED → 库存 100 扣到 95 + 写 SALES_OUT 事务」，并回读订单状态/金额/库存数量；反向用例验证库存不足返回 400/40006 且库存不变。
- 后端测试可零外部依赖运行：新增嵌入式 PostgreSQL（io.zonky:embedded-postgres）+ JUnit LauncherSessionListener，测试 profile 启用 Flyway 真实 schema（ddl-auto=none），无需 Docker 或本地安装数据库。
- 修复 BaseIntegrationTest：补全 Redisson RRateLimiter mock，登录限流器不再 NPE，大量原本无法在本地运行的集成测试恢复通过。
- 新增 V4CalculatorPromotionTest（6 个单元测试）：覆盖 MOQ 满赠、EVERY_N 每 N 赠、整单满减按行分摊、BOM 母件不计总价/不参与促销、数量非法、赠品幂等等 BOM+促销+下单核心计价规则。
- 新增 CoreDomainEndpointTest（7 个参数化集成测试）：覆盖销售订单、销售出库、库存移动、审批、采购订单、收货、销退 7 条核心链路的端点存在性与鉴权（匿名 401/403，登录后不 404/5xx）。
- 前端引入 Vitest + @vue/test-utils + jsdom 测试体系，新增 36 个单元/组件测试：时间格式化（format）、状态/枚举中文映射（dict）、token/权限本地存储（auth）、v-has 权限指令 DOM 移除（has-directive）。
- 新增根脚本 test:backend 与 test:frontend。

### 文档
- 新增三份评审报告：docs/02_设计/reviews/01-fat-controllers.md、02-orm-boundary.md、03-flyway-governance.md。
- 新增 .memory/requirement-closure.md 需求执行闭环流程，并在 AGENTS.md 阶段 B 强制引用，解决"提到的问题没执行"。
- 同步版本元数据到 v4.1.1（package.json、frontend-vue/package.json、AGENTS.md、layer5-context.md）；修复 README v3.8.10 历史乱码与失效文档链接。

### 重构（第四轮）：Service 重复逻辑收敛
- 新增 ApprovalResponseSupport：统一 submit/approve/reject 后返回的 id/newStatus/approvalInstanceId/autoApproved 结构，SalesOrder/PurchaseOrder/SalesReturn 共用。
- 新增 ActionButtonSupport：统一 allowedActions 按钮描述结构，消除三个订单类 Service 的 action() 重复实现。
- 新增 SqlValueSupport：统一语义一致的宽松 toLong/toBdZero/strOr 转换；SalesOrder 与 OrderApprovalExecution 已接入。PurchaseOrder 的严格 ID 校验、SurgeryReport 的 null BigDecimal 语义不同，保留原实现以避免行为漂移。
- 新增 ApprovalResponseSupportTest、ActionButtonSupportTest、SqlValueSupportTest，共 7 个单元测试；后端测试总数提升到 130。
- 对测试环境执行分段深度冒烟：采购、销退、销售订单、价格、审批、报表、销售岗位、手术报台、收货以及后台菜单/字典/API日志/审计日志/角色模板/经销商/厂商全部通过；一次性全量跑会在后台多页连续切换后挂起，单独分页均通过，判定为脚本长会话稳定性问题。

### 重构（第三轮）：全量胖 Controller 下沉
- 把评审报告中全部 10 个胖 Controller（SalesReturn/PurchaseOrder/SalesPosition/BusinessReport/SalesOrder/BizDocDetail/SurgeryReport/OrderApprovalExecution/ProductPrice/CompatAlias）的业务逻辑整体下沉到对应 Service，Controller 只保留路由注解 + 参数委托；Controller 上的 @Transactional 全部移到 Service。
- 逻辑/SQL/请求响应结构保持不变，仅搬运归属；OperationLog 切面切点已覆盖 service 包，操作日志继续生效。
- PositionResolver 从 controller 包移到 org.service 包以配合下沉。
- 回归：后端 mvn test 123 个测试全绿。

### 修复（第二轮）
- **胖 Controller 重构试点**：从 SalesReturnController 下沉纯行逻辑到 SalesReturnLineSupport（行解析/数量聚合/单价与金额计算），Controller 瘦身为委托调用；新增 SalesReturnLineSupportTest 10 个毫秒级单测守护。
- **修复订单创建真实 bug**：order_lines.line_discount_value/line_discount_amount/header_discount_amount 为 NOT NULL，但 OrderService 在无折扣时插入 null 导致 500，补全默认 ZERO；实体列约束对齐为 nullable=false。
- **修复供应商实体 schema 漂移**：Supplier 实体错误声明 UUID id（实际表为 BIGSERIAL），周边采购/收货/退货均用 Long；对齐为 Long id + IdType.AUTO、level/status 改 String、移除不存在的 attrs 列；同步 Repository/Service/ReferenceCheckService 类型。
- **修复 schema 漂移**：V110 对齐 email_logs.duration_ms 为 BIGINT、product_prices.price_scope 默认值 SALE；V111 把 stocktakes/rebate_previews/rebate_settlements/dealer_kpi_snapshots 的 period_yyyymm 从 CHAR(6) 改为 VARCHAR(6)。
- **9 个陈旧测试全部修复转绿**：Product 测试补 sortOrder；Order 测试因上述 null 折扣 bug 修复而通过；Promotion 测试断言对齐现行规则（GIFT/FULL_REDUCTION 可创建，MOQ/BUNDLE/未知返回 40001）。
- 修复 PermissionQueryService 三处中文注释乱码。

## v4.1.1 (2026-08-22) - 销退金额一致性 / 赠品与 BOM 母件出库过滤 / 出库行号

### 修复
- **销退单表头金额与明细汇总不一致**：只读页 `lineTotal()` 用 `min(退货数, 当前可退数)` 计算行总价，单据审批后可退数归零，导致已保存的退货行被错误显示为 ¥0。只读态改为直接取持久化的 `finalAmount`，明细汇总恒等于表头；`el-input-number` 的 `max` 在只读态不再裁剪已保存数量。
- **促销赠品/BOM 母件流入出库与退货**：销售出库草稿生成（AutoDocGenerator）现在跳过 is_gift=true 的赠品行和 line_level='PARENT' 的 BOM 母件行（无实物、0 元），并写入 source_order_line_id；销退「选择发货单」接口同步过滤这些行，从源头杜绝退赠品。
- **销退明细带出行号**：可退明细与销退详情新增「发货行」（出库行 seq）和「订单行」（销售订单行 seq），便于核对退货来源与原始出库价。

### 测试
- 新增 E2E 回归 13-rma-amount-lineno.spec.js：校验可退明细过滤赠品、携带行号、新建销退单表头金额等于明细 finalAmount 之和。

## v4.1.0 (2026-08-22) - BOM 价格双轨 / 行折扣优先级 / 促销计价口径统一

### 新增
- **BOM 子件价与单品价双轨维护**：`product_prices` 新增 `price_context`（STANDALONE/BOM_HEADER/BOM_COMPONENT）和 `bom_parent_product_id`，同一 SKU 可分别维护单品价和作为某 BOM 子件的价格。
- BOM 母件销售价保存为 1 条 BOM_HEADER（0 元头）+ N 条 BOM_COMPONENT（子件价），母件记录完整保留不再被子件覆盖。
- BOM 母件价生效/失效级联更新其下所有子件价状态。
- 价格列表新增「价格用途」列及「经销商」「价格类型」过滤；默认隐藏 BOM_COMPONENT 子件行。
- 后端单元测试 `V4CalculatorTest` 覆盖 BOM 子件专用价/不回退单品价、行折扣优先级、行后金额占比分摊。

### 修复
- **BOM 子件下单价格回退到单品价**：子件价格只取该 BOM 上下文的 BOM_COMPONENT 价，查不到返回 0，不再回退 STANDALONE。
- **BOM 子件行折扣丢失**：`V4Calculator.buildLine` 漏设 `lineLevel=CHILD`，子件行在数据库中变成 NORMAL，提交重算时 `childDiscounts` 读不到。已修正，行折扣优先级最高（先算行折扣，再分摊整单折扣/促销满减）。
- **BOM 母件参与总价/折扣/出库**：母件金额始终为 0，不计总价、不平摊折扣、不参与出库。
- **折扣分摊口径**：按「行折扣后金额占比」平摊整单折扣+促销满减，2 位小数 HALF_UP，尾差吸收到最大行。
- **满减保存误报未选产品**：BigDecimal 金额被 instanceof Number 误判、targetType=LINE 仍强制要求 SKU。
- **产品价格只读页显示裸 ID**：SKU/经销商/BOM 母件统一显示编码+名称。

### 数据
- Flyway `V108__bom_component_price_context.sql`：新增字段与上下文隔离唯一索引；迁移历史 BOM 子件价为 BOM_COMPONENT 并补建 BOM_HEADER 0 元记录。

### 文档
- 需求：`docs/01_需求/v4.1.0/DMS_v4.1.0_需求规格.md`
- 设计：`docs/02_设计/v4.1.0/DMS_v4.1.0_技术设计.md`
- 测试：`docs/03_测试/v4.1.0/DMS_v4.1.0_测试场景.md`
- 版本决策：今后 bugfix 自动升 PATCH；MINOR/MAJOR 升级由用户决定（见 `.memory/layers/layer4-decisions.md`）。
## v4.0.3 (2026-08-21) - 促销赠品手动刷新 / 幂等 / 标准金额占比分摊

### 修复
- **赠品在每次数量/产品变动时无限累加**：原预览在每次输入变化时都执行完整促销试算，且请求未携带 `isGift`，后端把已有赠品行当作计费行再次叠加赠品。改为：数量/产品/折扣变化只做价格预览（不生成赠品、不应用满减），新增「刷新赠品及价格」按钮在需要时统一计算；保存/提交前自动刷新一次。后端忽略入参中的赠品行并以当前计费行为准，重复刷新幂等。
- **折扣分摊与业务口径不符**：原逻辑分段在“剩余可分摊余额”上重复分摊。改为仅对非赠品、非 BOM 母件行，按标准金额占比一次性分摊「行折扣 + 整单折扣 + 促销满减」总额，2 位小数 HALF_UP，尾差吸收到最大行；出库单价 = 最终金额 / 数量。示例：标准 1000/500、总折扣 300 → 最终 800/400。
- **赠品行字段名不一致**：试算接口返回 `gift` 而前端只读 `isGift`，导致刷新后赠品行不渲染。前端兼容 `gift/isGift` 两种返回。
- **管理后台测试环境白屏**：`admin-vue` 产物 base 为 `/dms/admin/`，但测试环境以 `/admin/` 挂载，JS 资源 404 回退到 index.html。按测试环境 `/admin/` 重新构建并部署。

### 新增
- 销售订单明细卡片头部「刷新赠品及价格」按钮与命中促销提示条（买赠/满减文案）。
- 明细列新增「出库单价」。
- `POST /api/sales-orders/preview` 支持 `applyPromotions` 开关，全量试算时返回 `promotionMessages`；创建/更新/提交始终走全量试算，保证落库一致。
- 需求/设计/测试文档：`docs/01_需求/v4.0.0/DMS_v4.0.0-bugfix.9_需求规格.md`、`docs/02_设计/v4.0.0/DMS_v4.0.0-bugfix.9_技术设计.md`、`docs/03_测试/v4.0.0/DMS_v4.0.0-bugfix.9_测试场景.md`。

### 自动化
- 扩展 `automation_test/e2e/specs/12-orders-promo-bom-return.spec.js`：价格预览不产生赠品、刷新幂等、命中文案、标准金额占比分摊（800/400、出库单价）、赠品持久化与锁定。
- 真实浏览器脚本 `automation_test/e2e/promo-ui-refresh.cjs` 覆盖按钮交互。
## v4.0.2 (2026-08-21) - 促销赠品动态行 / BOM 全出库 / 销退分摊金额

### 修复
- **买2送5促销不生效**：`V4Calculator.applyGift` 将 `EVERY_N.cycle` 规则的 `thresholdQty=0` 误判为不命中，且只认 camelCase 字段。兼容 `thresholdQty/buyQty/everyN` 和 `giftQty/gift_qty`，每满 N 自动循环计算赠品数量。
- **赠品未在订单明细内动态展示且不可锁定**：新增 `POST /api/sales-orders/preview` 后端试算接口，`OrderCreate.vue` 改为以后端返回的 BOM、赠品、折扣和分摊金额刷新页面；赠品行展示在明细表内，产品/数量/折扣/删除均不可编辑，最终金额为 0。
- **赠品重复/幂等问题**：每次试算前清理旧赠品行，同一赠品 SKU 按规则聚合数量，避免多规则或反复编辑产生重复行。
- **BOM 订单生成销售出库后仍部分出库**：BOM 母件无实物、无价格，却被复制到出库并纳入完成度统计。`V4ErpService` 和审批自动建单一律过滤 `line_level='PARENT'`，状态刷新只统计子件、普通行和赠品。
- **销退明细缺少平摊单价/行总价/汇总金额**：从原 `sales_out_lines.final_amount / shipped_qty` 回填平摊含税单价，编辑页和只读页展示单价、行总价及汇总退货金额；创建/更新时后端按原出库行重算总额，不信任前端金额。
- **销退原发货单区域浪费空间**：改为紧凑单行信息，在编辑页和只读页均可见。
- **销售订单经销商字段仍偏窄**：新增/编辑页经销商列加宽到 18 栅格，订单详情只读页改为两列描述，降低长名称截断。

### 自动化
- 新增 `automation_test/e2e/specs/12-orders-promo-bom-return.spec.js`，覆盖：PRD-J002 买2送5生成 PRD-J003 赠品 15、赠品持久化、BOM 审批后一键出库完成、销退平摊单价和汇总金额、Console 无报错。
- 新增需求/设计/测试文档：`docs/01_需求/v4.0.0/DMS_v4.0.0-bugfix.8_需求规格.md`、`docs/02_设计/v4.0.0/DMS_v4.0.0-bugfix.8_技术设计.md`、`docs/03_测试/v4.0.0/DMS_v4.0.0-bugfix.8_测试场景.md`。
- `.memory/layers/layer3-lessons.md` 新增 L50-L52：促销/赠品必须后端试算并回读持久化，BOM 母件不得参与出库完成度，销退金额必须由原出库行后端重算。

## v4.0.1 (2026-08-21) - 促销启用 / 引用字段显示 / 销售定价一致性 / 审批流修复

### 修复
- **促销规则列表「启用」按钮无反应**：后端只有 `/promotions/{id}/deactivate`，前端调用的 `/activate` 返回 404 且被空 catch 吞掉。新增 `PromotionController.activate` + `PromotionService.activate`，统一写入 `updatedAt/updatedBy`。
- **促销/明细只读页显示裸 id 和枚举码**：`ResourceDetail.lineValue` 只特判了 `childProductId`，`targetProductId/giftProductId/cycle(EVERY_N)` 等直接输出数字或码值。改为通用渲染：select 映射中文 label，`*Id`/picker 字段优先取 `displayKey`，再按 `xxxCode + xxxName` 拼装。
- **销售订单前端价格与后端解析不一致导致提交报「产品 22 没有有效销售价格」**：前端 `loadPrice` 未传 `partnerId`，且会回退到任意 active 价；后端严格按「经销商专属→GLOBAL(0)→GLOBAL(null)」解析。前端并发查询 DEALER+GLOBAL 并严格按同优先级取价，无价时单价置 0 并在提交前以产品编码+名称阻断；后端异常信息改为 `产品 [PRD-B001 人工骨修复颗粒（β-TCP）] 没有维护有效销售价格…`，不再抛 id。
- **销退提交提示无审批流**：`approval_node_assignees` 被清空（0 行），5 个 ENABLED 默认模板审批节点全部无审批人，Controller 捕获异常后把状态回滚为 DRAFT。新增 Flyway V107 幂等修复，为默认模板补 SYS_ADMIN(系统管理员)；提交后真实进入 `PENDING_APPROVAL/RUNNING`。
- **表单宽度不齐导致经销商等长文本被截断**：`OrderCreate` 经销商列 span 8→12，`CrudView` 通用表单列 span 8→12，`SalesReturnEdit/PurchaseReturnEdit` 主字段统一 span 12，所有选择/日期/数字控件保持 `width:100%`。
- **销退原发货单区域过大**：`el-empty` 替换为紧凑的图标+一行提示，卡片 body padding 收紧；选择发货单弹窗 680→640、表格高 240→200、列宽整体收窄。

### 数据
- Flyway `V107__repair_default_approval_assignees.sql`：幂等修复 PO/SRT/PRT/CT/AUTH 五个 ENABLED 默认模板缺失的审批人（SYS_ADMIN 角色）。

### 自动化
- 新增 `automation_test/e2e/specs/11-six-fixes-regression.spec.js`，覆盖：促销明细无裸 id/枚举、经销商列宽、点新建后表单清空 + 添加行拦截、销退原发货单区域紧凑、弹窗 640 宽度、Console 无报错。
- `AGENTS.md` 第 5 节新增 4 条强制验收项：引用字段必须显示名称、状态按钮必须回读状态、前后端价格/库存解析必须一致、审批提交必须回读 status/approval_instances。
- `.memory/layers/layer3-lessons.md` 新增 L46–L49 教训。

## v3.12.5 (2026-08-18) - 导入导出分页修复与全站时间格式化热修复

### 修复
- 修复导入导出任务分页参数传 Vue ref 对象导致接口返回 400 参数类型错误，分页查询恢复正常。
- 统一前端列表时间列格式化，避免展示原始 ISO 时间戳（含 `T` 与 `+08:00`）及页面乱码，全站按 `YYYY-MM-DD HH:mm:ss` 展示。

### 数据
- 新增 16 个专属销售账号分散到各岗位，sys_admin 可见全部手术报台。
- 手术报台按销售数据权限可见，分散经销商并绑定销售组织。

### 部署
- 生产环境统一走 80 端口网关，DMS 挂在 `/dms/` 子路径；移动端会话有效期显式化为 8 小时。
- 新增测试环境空白模块演示数据种子脚本（幂等）。
## v3.12.0 (2026-08-09) - 审批邮件通知修复 + 6 类单据审批流打通

### 修复
- **审批邮件发不出去的根因修复**：`ApprovalService.activateNode()` 创建审批任务后从未调用 `ApprovalNotifier`，导致待办/抄送/结果邮件全部不发送。现已在任务创建、节点完成、抄送记录、驳回四个节点接入 `notifier.notifyTaskCreated/notifyCc/notifyFinished`。
- **审批邮件中文乱码修复**：`ApprovalMailNotifier.java`、`ApprovalNotifier.java`、`EmailLogController.java` 三个文件原以 GBK 字节写入，导致邮件标题/正文在 163 邮箱中显示为乱码。已用纯 ASCII + `\uXXXX` 转义重写，彻底绕开文件编码问题。
- 驳回时补充 `invokeRejected/invokeReturned` 回调调用（原来定义了但从未被调用，业务单据状态不会更新）。
- 合同审批补齐 `/api/contracts/{id}/approve` 和 `/api/contracts/{id}/reject` 端点（原只有 submit/withdraw，审批人无法操作），新增 `ContractService.markApproved/markRejected`。

### 新增
- 为采购订单、销售退货、采购退货、合同、授权 5 类业务补齐默认审批模板（Flyway V78）：单节点 SYS_ADMIN 审批，ANY 模式，48 小时超时，每 24 小时提醒一次，最多提醒 3 次，驳回策略为退回发起人。销售订单保留原有金额条件模板。
- 邮件发送日志页面（/email-logs）与「发送测试邮件」功能验证通过；测试邮件和真实审批邮件均成功投递至 vinkinyu@163.com。
- 超时提醒定时任务 `ApprovalTimeoutReminderTask`（每日 09:00 执行，`@EnableScheduling` 已开启），按节点配置的 `timeoutHours/remindIntervalHours/maxRemindCount` 发送提醒邮件。

### 验证（测试环境）
- 提交销售订单 SO-20260809-00003（金额 2000，命中金额≥1000 审批模板）→ 生成 14 条审批任务（角色展开）→ 14 封待办邮件全部发送成功并写入 `email_logs`（status=SUCCESS）。
- 测试邮件接口 `POST /api/email-logs/test` 发送至 vinkinyu@163.com，status=SUCCESS。
- 后端健康检查 UP，6 类业务审批模板全部 ENABLED。
- 163 SMTP：smtp.163.com:465 SSL，发件人 vinkinyu@163.com。

## v3.11.0 (2026-08-09) - 合同模块重构（模板驱动 + 统一工作台）

### 重构
- 合同模块由原来的「合同申请 + 合同」两个割裂页面合并为统一的合同工作台（/contracts），一条合同记录贯穿草稿/审批中/已生效/已驳回/已终止/已到期全生命周期；废弃 contract_applications 实体，审批直接挂在合同上。
- 新增合同模板菜单（/contracts/templates，权限 contract_template:manage），法务可上传 Word(.docx)，后端自动识别内容控件与占位符生成可填字段，配置字段标签/类型/必填/审批可见/分组/排序，绑定业务分类后发布；发布锁定，修改走新建版本。
- 后端重建合同表（方案 A）：contracts 新增 template_id/template_version/application_type/form_data(jsonb)/source_file_id 等字段；新增 contract_templates、contract_revisions 表；删除 contract_applications/contract_diff/contract_signatures。老测试数据不保留。Flyway V77 重建并补发 contract:view、contract_template:manage 权限。

### 新增
- 合同新建/编辑页支持基础信息 + 按所选分类自动匹配已发布模板动态渲染字段 + 附件上传；提交后用 Apache POI 将字段回填模板 Word 生成成稿供下载。
- 审批中心接入合同业务快照：审批详情对 CONTRACT 类型按业务信息 + 审批可见字段展示。
- 合同审批回调驱动合同状态流转（通过生效/驳回回草稿/撤回），写入 contract_revisions 留痕。
- 合同到期定时任务，每日将过有效期且生效中合同置为 expired。
- 后端接口：/api/contracts（CRUD + submit/withdraw + 附件）、/api/contract-templates（CRUD + publish/new-version/disable + upload-and-parse）。

### 暂不实现（二期，详见 docs/合同模块改造设计_V1.0.md 第 9 节）
- 浏览器内直接编辑 Word 原文 + 段落级 diff 留痕、表格实时编辑/合计、长文本自动序号、复杂控件识别、第三方系统办结同步、电子签章、对外嵌入式集成、宿主项目数据自动回填。

### 验证
- 后端 mvn -o package -DskipTests 通过；前端 npm run build 通过。
- E2E（测试环境）：上传含 8 个占位符的 Word → 识别字段 → 建模板发布 → 建合同提交 → 自动审批生效 → 生成成稿 Word，下载校验占位符全部正确替换；状态守卫（生效合同不可编辑返回 40006）验证通过。
- 回归：合同/经销商/产品/订单/医院/授权/审批待办等核心接口全部 200，数据计数正常（订单 832、产品 208、经销商 51、授权 502）。
- 测试环境已部署：前端 http://8.133.193.238:8083/ ，后端 http://8.133.193.238:8082/ 健康检查 UP，Flyway V77 已执行。


### 详情页字段名与角色显示修复（2026-08-09）
- 账号管理查看抽屉：`roleId` 改为「角色」并隐藏原始 ID，只显示角色名；`userType=vendor/dealer` 翻译为「厂商/经销商」；补充「登录失败次数」「最近登录IP」等中文标签。
- 列表 / 编辑 / 查看三处角色字段已统一为单值；重新构建并部署前端到测试环境。

## v3.10.0 (2026-08-09) - 审批流引擎与审批中心

### 账号管理补充（2026-08-09）
- 账号改为**单角色**：用户创建/编辑表单的「角色」为单选必填，后端 `UserCreateRequest/UserUpdateRequest` 新增 `roleId`，`UserDTO` 返回 `roleId/roleName`；底层仍用 `user_roles` 表（兼容多对多，但业务上一个账号一个角色）。
- 账号列表新增「角色」列（显示 `roleName`），详情抽屉也会展示角色；新建账号时校验密码至少 8 位。
- 修复下拉操作（编辑/重置密码/解锁/删除）点不动：`el-dropdown` 由无效的 item `@click` 改为 `@command`；补齐 `CrudView` 中 `PICKER_NAME_MAP` 未定义导致编辑抽屉打不开的问题；抽屉标题乱码 `??` 修复为「编辑/新增」。
- 新增账号内置「重置密码」「解锁」处理：重置弹窗输入新密码（8–64 位），调用 `/api/users/{id}/reset-password`；解锁调用 `/api/users/{id}/unlock`。
- 修复 `ApprovalService ↔ ContractApprovalCallback ↔ ContractService` 的循环依赖（`@Lazy` 注入 `ApprovalService`）。
- 重新生成测试账号：删除旧的 `test_*` 账号，新增 `sys_admin/sales_mgr/sales/cs/biz/fin/contract/dealer_admin` 共 8 个，统一密码 `Dms@123456`，各自绑定一个角色，资料齐全可直接登录。
- 更新 `docs/DMS登录信息手册.md`：汇总三种登录方式（业务前台/平台后台/移动端）、环境地址、全部测试账号与密码、SMTP/排障说明。


### 新增
- 后端新增独立审批模块 `com.dms.approval`：审批模板（版本/草稿/发布/停用/新版本）、顺序审批节点、账号/角色审批人、ANY/ALL 多人规则、前/后加签、转办、全局委托、抄送、管理员改派与终止、待办/已办/我发起的/抄送查询、完整审批记录。
- 条件匹配：模板按业务类型 + 优先级匹配，支持 `AND/OR` 与 `EQ/NE/IN/GT/GTE/LT/LTE`，可用单据金额、单据类型、经销商/供应商等字段；空条件匹配全部单据。
- 版本与快照：实例创建时固化模板快照，修改模板不影响进行中的实例，未提交/重新提交的单据使用最新已发布版本；驳回后重新提交生成新实例。
- 驳回策略可配：退回发起人修改（RETURN_TO_SUBMITTER）或作废（CANCEL）；发起人可撤回回到草稿。
- 无匹配模板或命中自动审批模板时，单据自动审批通过并正确回调业务，不阻断提交。
- 接入销售订单、采购订单：提交进入 PENDING_APPROVAL，通过后自动生成销售出库/收货入库草稿；驳回/撤回按策略回写状态。
- 通知：站内信 + 邮件（163 SMTP），授权码写入 application.yml 默认值。
- 邮件发送日志：新增 `email_logs` 表与 `EmailLog/EmailLogService/EmailLogController`，审批邮件（待办/抄送/结果/超时提醒）及测试邮件发送成功或失败均落库；独立事务保证失败不丢日志；前端“用户与权限 → 邮件发送日志”支持状态筛选与分页；`POST /api/email-logs/test` 可发送测试邮件并返回日志状态。Flyway V74 建表、V75 注入 `email_log:view` 权限。
- Flyway V72 建审批相关表；V73 注入 approval:todo/manage/admin/template:edit/approve 权限资源并授权租户管理员。
- 前端新增“审批中心”菜单：我的审批（待办/已办/我发起的/抄送，抽屉内同意/驳回/转办/加签/撤回 + 审批记录时间线）、审批流配置（列表式配置条件/节点/审批人/抄送/驳回策略/超时/优先级）、审批委托、审批监控（管理员改派/终止）。
- 账号创建强制手机号 + 邮箱（手机号用于飞书对接，邮箱用于审批通知）。

### 修复
- 修复 AUTO_APPROVE 模板未执行业务回调导致单据不回写状态的问题。
- 修复无匹配审批流时后端报错（现改为默认自动通过）。

### 验证
- 后端 `mvn -q -DskipTests package`、前端 `npm run build` 均通过。
- 测试环境已部署：前端 `http://8.133.193.238:8083/`，后端 `http://8.133.193.238:8082/`，健康检查 UP，Flyway V72/V73/V74/V75 已执行；已通过 `POST /api/email-logs/test` 向 `vinkinyu@163.com` 实测发送成功（`email_logs.status=SUCCESS`）。

### 扩展
- 销售退货、采购退货提交接入审批流；审批通过后分别自动生成销退入库草稿、采退出库草稿，驳回/撤回/终止按策略回写状态。
- 合同申请提交接入审批流；审批通过后合同申请生效并自动生成合同。
- 授权创建接入审批流；审批通过后授权变为 active，驳回/撤回回到 draft。
- 新增超时提醒定时任务：每天 09:00 扫描到期 PENDING 任务，按默认 24 小时间隔、最多 3 次发送审批超时提醒邮件；仅提醒，不自动处理。

### 测试账号
- 测试租户 `11111111-1111-1111-1111-111111111111`，除 admin 外每个业务角色各一个账号，统一密码 `Dms@123456`，首次登录无需改密：
  - `test_sales_mgr`(SALES_MGR)、`test_sales`(SALES)、`test_cs`(CS)、`test_biz`(BIZ)、`test_fin`(FIN)、`test_contract`(CONTRACT_SPEC)、`test_dealer_admin`(DEALER_ADMIN)。
  - 各账号已绑定对应角色，邮箱/手机号为必填占位数据（`@163.com` 与 138 开头手机号）；验证真实收件请发往 `vinkinyu@163.com`。

### 待办
- 飞书卡片审批；审批记录时间线在订单/合同/授权详情页的内嵌展示。
- 邮件授权码目前硬编码在 application.yml，建议改用环境变量。

---
## v3.8.11 (2026-08-08) - 全站日期格式、日志复制与中文标签修复

### 修复
- 统一业务前台与后台管理端日期时间展示：新增 `formatDateTime`、`formatDate`、`formatAuto`，公共列表、报表和详情默认按 `YYYY-MM-DD HH:mm:ss` 渲染，避免直接显示 ISO 原始字符串。
- 业务前台接口调用日志详情增加请求头、请求体、响应头、响应体、错误信息复制按钮；后台 API 日志与审计日志也提供报文复制能力。
- 后台日志文件读取在 MinIO 对象缺失或不可用时降级为空内容，避免复制按钮返回 500。
- 修复经销商画像行操作按钮被硬编码为 `????` 的问题，恢复为“查看画像”，保留 KPI、月度达成、返利、合同、库存等页签入口。
- 修复入库、销售出库、经销商画像等页面少量中文标签和时间字段展示。
- 修复接口调用日志公共列表 render 单元格为空的问题，恢复方向、方法、结果、时间等列内容；重写日志页中文文案，并隐藏未实现的导出按钮。
- 新增 V71，修复接口日志状态筛选标签为“状态”并修正状态字典 500 文案错字。

### 数据
- 保持已执行的 V68 不变，新增 V69/V70 修复历史编码损坏造成的按钮与字典标签问号。
- 平台默认与租户覆盖继续共用 `platform_button_configs`，以 `tenant_id IS NULL / NOT NULL` 区分。

### 验证
- 后端 Maven package、业务前台 Vite build、后台管理端 Vite build 均通过。
- 测试环境已部署：业务前台 `http://8.133.193.238:8083/`，后台 `http://8.133.193.238:8083/admin/`。
- `tools/_e2e_v389_final.py` 通过；额外 smoke 验证画像按钮返回“查看画像”、后台 API/审计日志可访问、请求报文复制接口返回 200。

---
## v3.8.10 (2026-08-07) - ????????????????

### ??
v3.8.9 ?????????????? P0 ????? `permissionCode` ? `resources.code` ????????/????/????????????????????????????????????????????????????? `/api/admin/**`??? token ???? 401?

### ??
- Flyway `V67__fix_menu_permissions_profile_and_api_log.sql`??? `dashboard:view`?`products:view`?6 ?????????`api_log:view` ??????????????? `api_call_status` ?????????????? `status`?????????????????????
- `ApiCallLogController`???????? `/api/api-call-logs` ???? `/api/admin/api-call-logs`???????????? `api_log:view`/?????????? admin ???
- `PlatformPageLayoutController`??????????????? `view=????`????????????????????
- `RbacService` / `ResourceRepository`?????????????????????????????

### ??
- `directives/has.js` / `layout/index.vue`?????????????? `/api/me/permissions`?????????????????/?????????
- `ListPageLayout.vue`??????????????????? 1 ??????????????????????? `view`??????????????
- `ApiCallLog.vue`?????? `/api/api-call-logs`??????? `status/startTime/endTime`?
- `Roles.vue`??????????????????????/?????????????

### ??
- `backend/mvn -q -DskipTests package` ???
- `frontend-vue/npm run build` ???
- ????????Flyway ??? V67?Redis `dms:cfg:*` ????
- `tools/_e2e_v389_final.py` ??????/??/?????????? 7 ?????????????????? 6 ? Tab????????????/???????????/???

---
## v3.8.9 (2026-08-07) - 列表页规范全站收口 + 租户角色权限闭环

### 背景
v3.8.8 已接入页面布局接口，但仍有四个缺口：`CrudView` 搜索字段和租户筛选覆盖未完全闭环、销售订单等旧业务动作折叠后可能无回调、经销商画像入口需要保留“查看画像”、租户管理员无法在业务前台直接维护角色菜单/按钮权限和页面搜索字段。本版本按 Layer 2 第十八章 v3.8.9 规则完成收口。

### 后端
- Flyway `V66__tenant_filter_override_and_layout_fixes.sql`：新增 `tenant_filter_configs`，唯一键 `(tenant_id,page_key,filter_key)`；修正筛选/按钮中文文案；经销商画像默认隐藏导入/导出/新增；销售订单补齐驳回、取消按钮与权限资源。
- `TenantUiConfigController`：新增 `/api/tenant-ui/pages/{pageKey}/filters` 与 `/buttons`，业务 token 可维护当前租户覆盖。
- `RbacController` / `RbacService`：新增 `/api/roles/{id}/permissions`，按角色同名策略维护菜单、按钮、接口权限，并做租户隔离。
- `UiConfigService`：平台默认筛选与租户筛选合并；租户筛选保存改为按 `filterKey` upsert，重复保存不再触发唯一键冲突。
- `PlatformButtonConfigService`：按钮覆盖保存改为按 `scope+buttonKey` upsert；租户保存时强制补回并开启 `search/reset`，避免查询/重置被误隐藏。
- `StrategyResource`：补齐 `operations` 映射，角色权限保存默认写入 `['view']`，修复 `strategy_resources.operations` 非空约束导致的 500。

### 前端
- `CrudView.vue`：搜索区、工具栏、行按钮均由 `/api/ui/layout/{pageKey}` 驱动；行内按钮超过 1 个只平铺第 1 个，其余进入“更多 ▾”；桥接旧 `statusActions/actions` 和标准 `submit/approve/reject/cancel/confirm/execute`；`view` 优先执行业务回调或路由。
- `ListPageLayout.vue`：统一独立列表页搜索、固定查询/重置、工具栏排序、权限指令和行按钮折叠；修复原实现把 `rowActions` 对象当数组判断的问题。
- `DealerProfileList.vue`：修复乱码，保留“查看画像”，不展示未实现的导入/导出/新增。
- `Roles.vue`：专用“角色权限”页面，使用 `el-tree` 勾选菜单、按钮、接口资源。
- `TenantPageConfigs.vue`：租户管理员可按页面调整搜索字段、工具栏按钮、行内按钮显示与排序；查询/重置开关禁用且后端强制可见。
- `menu.js` / `router/index.js` / `api/admin.js`：补充“角色权限”“列表页配置”的菜单、路由和 API。

### 验证
- `backend/mvn -q -DskipTests package` 通过。
- `frontend-vue/npm run build` 通过。
- 测试环境已部署后端 `dms-test-backend` 与前端 `dms-test-frontend`，Flyway 为 V66，Redis `dms:cfg:*` 已清理。
- `tools/_e2e_v389_final.py` 通过：健康检查、admin 权限码、`orders` 7 个行按钮、`dealer-profile` 仅保留查询/重置/查看画像、租户筛选隐藏/恢复、租户按钮隐藏/恢复、角色权限更新/回滚。

---
## v3.8.7 (2026-08-06) - 列表页布局统一规范 + 平台/租户按钮配置 (D13)

### 背景
DMS 列表页硬编码风格不统一：搜索区/工具栏/行内操作按钮的摆放、顺序、折叠、权限控制都散落在每个 .vue 文件里，按钮位置东一个西一个；行内操作超 3 个时直接撑爆“操作”列；按钮能否展示没法按租户调整；权限只控制到页面，没控制到按钮。本版本沉淀 Layer 2 第十八章《列表页布局规范》（冻结区） + Layer 4 D13 决策，配套
新增 platform_button_configs 表 + 9 个后端类 + 1 个前端组件 + 1 个权限指令。

### 核心铁律
1. 每个列表页必须先有搜索区，查询按钮始终展示并位于工具栏第一位。
2. 顶部工具栏按钮从左到右固定顺序：`查询 → 重置 → 导入 → 导出 → 新增 → 业务专属`；同组 `gap: 8px`，禁止东一个西一个。
3. 行内操作按钮数量 > 1 时自动折叠进 `更多 ▾` 下拉，> 4 时全部折叠仅留 1 个最高频平铺。
4. 所有按钮必须受权限控制，统一通过 `v-has` 指令过滤；无权限按钮不渲染、不留白。
5. 搜索字段、按钮的可见性由租户管理员在租户后台调整，调整只影响本租户。

### 后端新增
- Flyway `V59__platform_button_configs.sql`：新建表，2 类唯一键（平台默认 / 租户覆盖），预置 14 条 seed（dealer-applications 8 条 + dealer-profile 6 条）。
- `PlatformButtonConfig` 实体 / Repository / DTO / Service / 2 个 Controller。
- `PlatformPageLayoutController`：聚合下发 `GET /api/ui/layout/{pageKey}`，一次返回 filter + columns + toolbar + rowButtons。
- `MyPermissionsController`：`GET /api/me/permissions` 返回当前用户的全量资源权限码。
- 修复 `V60__api_call_log_transfer_fields.sql` 中文引号语法错（`‘ → '`）。
- Dockerfile.aliyun 加 `ENV LANG=C.UTF-8` 修复 maven 编码错。

### 前端新增
- `frontend-vue/src/directives/has.js`：`v-has` 指令（4 路数据源回退，无权限从 DOM 移除）。
- `frontend-vue/src/components/ListPageLayout.vue`：统一列表页组件（搜索区 + 工具栏 + 表格 + 行内折叠 + 分页 + 空态）。
- `utils/auth.js` 新增 getPermissions/setPermissions/clearPermissions。
- `store/user.js` 登录后自动调用 `/api/me/permissions` 写入 Pinia + localStorage。
- 重构 `DealerProfileList.vue` 为 ListPageLayout 示范页（pageKey=`dealer-profile`）。
- `admin-vue/src/views/config/UiConfigs.vue` 增加 `按钮配置` Tab（区分 PLATFORM_DEFAULT / TENANT_OVERRIDE）。

### 平台 / 租户双层覆盖模型
- `tenant_id IS NULL` → 平台默认（admin 预置，对所有租户生效）
- `tenant_id = 租户ID` → 租户覆盖（只对当前租户生效）
- 读路径合并：LinkedHashMap.put(scope+buttonKey, 默认) → put(scope+buttonKey, 覆盖)，同 key 覆盖
- 唯一键：`UNIQUE (page_key, scope, button_key) WHERE tenant_id IS NULL` + `UNIQUE (tenant_id, page_key, scope, button_key) WHERE tenant_id IS NOT NULL`

### 部署
- 测试环境已部署并验证：8083 / 后端 health 200；Flyway V59 + V60 成功；/api/ui/layout/dealer-applications 返回 8 条按钮；/api/me/permissions 返回完整权限码。
- 本地 mvn -B -DskipTests clean package BUILD SUCCESS；frontend-vue + admin-vue vite build 成功。

### 验收清单（每个列表页上线前自检）
- [ ] 关键字搜索框在第一位，回车可触发查询
- [ ] `查询` 按钮始终在工具栏第一位
- [ ] 工具栏按钮按 `查询 → 重置 → 导入 → 导出 → 新增 → 业务` 顺序排列，无错位
- [ ] 行内按钮 > 1 时折叠到 `更多 ▾`，> 4 时全部折叠
- [ ] 删除类按钮二次确认
- [ ] 用无权限账号登录，所有越权按钮不渲染、不留白
- [ ] 租户管理员隐藏一个搜索字段后，该字段前端不再渲染
- [ ] 列表为空时空态正确显示

### v3.8.7 增量 (2026-08-06 晚) — 完成所有后续建议

#### 一、权限码对账（V61 / V63）
- 新增 V61__rbac_button_resources.sql：给 6 个非系统租户各补 128 条 type=button 的 rbac_resources，与 platform_button_configs.permissionCode 一一对应。
- 新增 V63__button_resource_auto_link.sql：trigger，任何租户新增 type=button 资源自动关联到 strategy_id=1（即 [全部权限]），admin 用户立即生效。
- admin 用户的权限码从 20 涨到 148（6 api + 14 menu + 128 button）。

#### 二、pageKey 全量灌种（V62）
- V62__platform_button_configs_seed.sql：16 个 pageKey 共 120 条 platform_button_configs 平台默认按钮。
- 覆盖：dealer-profile / dealer-applications / sales-orders / purchase-orders / receipts / sales-out / inventory / stock-moves / products / product-mappings / hospitals / contracts / reports / api-call-log / positions / users。

#### 三、老列表页迁移
- ApiCallLog.vue 重构为 ListPageLayout，保留详情抽屉。
- ProductMappings.vue 重构为 ListPageLayout，保留租户切换 + 手工新增弹窗。
- ListPageLayout.vue 加 defineExpose({ load })，支持父组件刷新。

#### 四、验证（测试环境 8.133.193.238:8083）
- 16 个 pageKey 的 /api/ui/layout/{pageKey} 全部 200。
- admin 用户 /api/me/permissions 返回 148 条权限码，button 类 128 条。
- V63 trigger 实测：新增 button 资源后自动挂到 strategy 1。
- 前端 vite build 成功；ApiCallLog / ProductMappings chunk 已上线。
- 后端 mvn -B -DskipTests package BUILD SUCCESS。

## v4.2.0 (2026-08-06) - 传输接口（库存查询/销售订单/采购订单）落地与日志增强

### 背景
原有库存查询（GET /api/inventory）走 JWT 鉴权 + 入站日志，但缺少与库存查询"配对"的写入能力。
本版本新增两个传输端点，并把 3 个接口全部纳入"传输接口日志"体系（按业务单号回溯）。

### 后端新增
- `POST /api/orders/transfer`        销售订单传输（同步）。成功返回 `data.code=SO-20260806-00001`；失败 `code` 为业务错误码 + `message` 即失败原因。
- `POST /api/purchase-orders/transfer` 采购订单传输（同步）。成功返回 `data.code=PO-20260806-00001`；失败同上。
- 响应 DTO：`com.dms.order.dto.TransferResponse`（id / code / orderType / status / amount）。
- 调用日志增强：
  - 新增字段 `biz_key`（业务单号，传输成功=SO-/PO- 单号；库存查询=warehouseId-productId）和 `biz_action`（inventory.query / order.transfer.sales / order.transfer.purchase）。
  - 新增 Flyway 迁移 `V60__api_call_log_transfer_fields.sql`（含 3 个索引 + 字段注释）。
  - `ApiCallLogFilter#deriveBizTags` 按 URI 自动打标，方便按业务单号检索。

### 3 个传输/查询接口全景
| 端点 | 鉴权 | 同步 | 业务单号 | 失败原因 | 日志 action |
|------|------|------|----------|----------|-------------|
| `GET  /api/inventory`                   | JWT | 是 | warehouseId-productId | `message` 字段 | `inventory.query` |
| `POST /api/orders/transfer`             | JWT | 是 | `data.code`（SO-*）   | `message` 字段 | `order.transfer.sales` |
| `POST /api/purchase-orders/transfer`    | JWT | 是 | `data.code`（PO-*）   | `message` 字段 | `order.transfer.purchase` |

### inventory biz_key 取值优先级（v4.2.0 补丁）
`ApiCallLogFilter#deriveBizTags` 对 `GET /api/inventory` 的 `biz_key` 抽取规则，按以下顺序生效：
1. `warehouseId` + `productId` 两个 query 参数拼成 `warehouseId-productId`（如 `1-1`）；任一缺失时该位用 `*` 补齐。
2. 1 未提供时，回退到响应体 `data.list[0].id`。
3. 仍取不到时，回退到 `message` 前 32 字符。
早期版本直接走到第 3 步，导致 `biz_key` 出现 `OK` 之类噪音；本次重打包已修复并部署测试环境。

### 部署
- 同样模式：本地 `mvn -o -DskipTests package` → 上传 jar → `docker restart`。
- 阿里云测试环境（8.133.193.238:8082）已就位，v4.2.0 jar md5 `E4008E5DCA62267E4C062DB41E43B288`（2026-08-07 00:03 UTC+8 完成重启并 health UP）。
- 阿里云生产环境（8.133.193.238:8080/8081）等待用户指令再切。

### 端到端验证（阿里云测试环境）
- `GET /api/inventory?warehouseId=1&productId=1&page=1&size=3` → HTTP 200，`data.total=7`，`biz_key=1-1`。
- `POST /api/orders/transfer`（`dealerId=1, productId=1, qty=1, unitPrice=100`）→ `code=0`，`data.id=1220`，`data.code=SO-20260807-00002`，`amount=100.00`，`biz_key=SO-20260807-00002`。
- `POST /api/purchase-orders/transfer`（`supplierId=5, warehouseId=1, productId=1, qty=1, unitPrice=80`）→ `code=0`，`data.id=82`，`data.code=PO-20260807-00001`，`amount=80.00`，`biz_key=PO-20260807-00001`。
- `api_call_log` 表最近 3 行 `biz_action` 分别为 `inventory.query` / `order.transfer.sales` / `order.transfer.purchase`，`biz_key` 全部命中预期（无 `OK` 兜底）。

## v4.1.1 (2026-08-05) - 报表体系大改版（13 张报表 + 仪表盘）

### 背景
原有报表分散、缺乏筛选/导出/穿透/时间范围，仪表盘无筛选条件、布局不可调整。一次性大调整：13 张报表（5 业务 + 5 销售/订单/库存/报台/平台 + 3 v4.1.1 新增），全部支持时间范围、维度筛选、xlsx 导出、跳转穿透。

### 后端 `BusinessReportController` v4.1.1
- 端点：
  - `GET /api/reports/sales-ranking`    经销商销售业绩排行
  - `GET /api/reports/product-top10`     产品销售 TOP10（穿透到订单明细）
  - `GET /api/reports/inventory-turnover` 库存周转
  - `GET /api/reports/surgery-stats`     手术报台统计
  - `GET /api/reports/receivables`       应收款项（账龄 30/60/90/90+）
  - `GET /api/reports/order-trace`       订单追溯（下单→审批→发货→收货）
  - `GET /api/reports/inventory-aging`   库存呆滞/超期（v4.1.1 新增）
  - `GET /api/reports/order-approval-stats` 拒单率/审批时长（v4.1.1 新增）
  - `GET /api/reports/overview`          报表中心总览
- 公共筛选：`from/to/dealerId/level/region/status/orderType/productId/hospitalId/limit`
- v4.1.1 修复：4 处 SQL 字段名错（`d.region`→`d.region_id` join `regions`；`sr.hospital_id`→`sr.terminal_id`；`sales_outs.order_id`→`source_order_id`；`receipts.order_id`→`ref_doc_id+ref_doc_type='ORDER'`）

### 前端 v4.1.1
- **`src/config/reports.js`**：13 张报表 metadata（title/icon/desc/defaultRange/filters/cols/exportable/drills），统一 `rangeFor()` 工具
- **`src/components/ReportPage.vue`**：v2 元数据驱动，统一样式（筛选条/汇总/表格/分页/图表），内置 xlsx 导出（`xlsx` 库），支持报表间穿透跳转
- **`src/views/Dashboard.vue`** v2：顶部筛选（时间范围 + 经销商 + 状态 + 类型，默认本年）、6 个可拖拽 ECharts 区块（销售趋势/库存三态/TOP 经销商/订单漏斗/TOP 医院/7 日活动）、`vuedraggable` 拖拽排序、编辑模式下可隐藏/恢复区块、布局存 localStorage
- **`src/views/Reports.vue`**：报表中心首页，6 个分组卡片，?key=xxx 直跳单张报表
- **`src/views/mobile/MDashboard.vue`**：新增"更多报表"6 项链接，点击 `router.push('/reports?key=...')`
- 6 个 `/m/report-*` 菜单改跳 `/reports?key=...`
- **`admin-vue/src/views/reports/Overview.vue`**：平台后台报表入口
- 修复 `ReceiptEdit.vue` 等小 bug

### 部署（仅测试环境）
- 后端：本地 maven 编译 → jar 上传 `/opt/dms/dms-test/backend-src/target/dms-backend.jar` → `docker cp` 进运行容器 → `docker restart`（已确认 `Started DmsApplication`）
- 前端：本地 `npm run build` × 2（frontend-vue + admin-vue）→ dist zip 上传 → `docker cp` 进 dms-test-frontend → 解压到 `/usr/share/nginx/html/` → `nginx -s reload`
- 端到端：10/10 报表端点经 8083 (frontend) → nginx → 8082 (backend) 全 200；admin 入口 301→/admin/
- 正式环境（8081/8080）未动

## v3.9.1 (2026-08-05) - 导入/导出全量修复并部署测试环境

### 背景
用户反馈合同列表页点"导出"报 `接口不存在: /api/contracts/actions/export`，授权列表页同样失败。排查发现：前端 `CrudView` 对所有模块无条件显示导入/导出按钮，而后端只实现了部分接口；此前的修复也一直停留在本地，从未部署到服务器。本次做全量审计 + 修复 + 部署 + 端到端验证。

### 后端新增导出接口
- `GET /api/contracts/actions/export` — 合同列表导出（原生 SQL join `dealers` 取经销商名称）。
- `GET /api/authorizations/actions/export` — 授权列表导出（复用 `service.list` 的 `fillNames`，带出分类/终端名称）。
- 新增 6 个导入模板接口 `GET .../actions/export/template`：销售订单、采购订单、库存调整、手术报台、库存移动、收货入库（模板表头与各自 `batch-import` 读取的列名严格一致），模板总数 8 → 14。

### 后端导入修复
- **供应商导入整体失效**：`SupplierController.batchImport` 走的是与实表不符的 JPA 实体（实体声明 `id UUID`/`status Integer`/`attrs jsonb`，实表是 `id bigint`/`status varchar`/无 `attrs`），必然 `column s1_0.attrs does not exist` → 500。改为与该控制器其余方法一致的原生 SQL upsert，`status` 兼容 `1/0`、`启用/停用`、`active/inactive`。
- **销售订单导入整体失效**：Hibernate 原生 SQL 把 `'{}'::jsonb` 的 `::jsonb` 当成命名参数，报 `syntax error at or near ":"` → 整批回滚。统一改为 `CAST('{}' AS jsonb)`；`IntegrationController` 同类写法一并修正。
- **产品导入类型强转崩溃**：Excel 同一列可能是文本也可能是数字，`(Number) value` 直接强转抛 `ClassCastException`。新增 `ExcelImportUtils.coerce(value, type)` 作为统一转换入口，产品/医院/经销商/分类/区域/仓库 6 个控制器的 `setFieldValue` 全部改为委托调用。
- **仓库导入必填字段缺失**：`warehouses.dealer_id NOT NULL`，但模板与导入列都没有经销商ID，新建必然违反非空约束。模板与导入列补 `经销商ID`，并在 `WarehouseService.upsertByCode` 新建分支前置校验，报错文案改为"经销商ID不能为空"。
- 采购订单导入补 `docNoGenerator.next("PO")`（`code` 列 NOT NULL UNIQUE，原先必然全行失败），另补 `ADJ`/`MV`/`SURG`。
- 新增 `ExcelImportUtils.toDateString()`：Excel 日期单元格解析为 `LocalDateTime`，`toString` 得到 `2026-01-31T00:00` 无法入 DATE 列；手术日期、采购期望到货、销售期望交付三处 SQL 改用 `CAST(? AS date)`。
- `surgery_date` 补空值校验；手术报台导入状态由 `DRAFT` 改为 `COMPLETED`。
- `ReceiptController.batchImport` 补 `@Transactional`，`(Number)` 强转换成容错 `toLong`。

### 后端导出字段修复
`ExcelExportUtils.getFieldValue` 反射取不到字段时静默返回 null，导致整列空白。修正：产品（`nameCn`/`nameEn`/`currentPrice`/`taxRate`）、医院（`contact`/`phone`）、收货入库（`receiptType`/`refDocType`/`refDocId`/`warehouseId`/`receivedAt`）、销售订单（移除不存在的 `hospitalName`/`surgeryName`）。另将 2 处硬编码英文 `Content-Disposition` 统一为 `ContentDispositionUtils.attachment(...)`（修中文文件名乱码）。

### 接口日志修复
`ApiCallLogFilter` 把导出接口的 xlsx 二进制当文本写入 `api_call_log.response_body`（text 列），PostgreSQL 报 `invalid byte sequence for encoding "UTF8": 0x00`，每次导出都刷一批 ERROR。改为仅对文本类 Content-Type 记录响应体，二进制记为 `<binary N bytes, content-type=...>`，并对请求/响应体统一去除 `0x00`。

### 前端
- `CrudView.vue`：`canImport`/`canExport` 由默认开启（`!== false`）改为显式开启（`=== true`），按钮不再凭空出现；新增 `canDownloadTemplate`，修复原先对所有模块无条件请求 `{api}/actions/export/template` 的 bug。
- `config/modules.js`：合同、授权补 `exportable: true`（本次报错的直接修复）；销售订单、采购订单、库存调整、手术报台补 `importable/exportable`；库存移动补 `exportable`；销售出库改 `exportable: false`；移除产品线/包装层级/组套的虚假导入导出标记；删除已废弃的 `materials` 模块（后端无控制器/表/迁移）。
- `api/productMapping.js`：5 处模板字符串漏了 `${id}`（如 `/api/product-mappings//enable`），已补全。

### 部署（仅测试环境）
- 后端：源码 + 本地编译 jar 上传 `/opt/dms/dms-test/backend-src`，`docker rmi` 后重建 `dms-backend-test:latest`，容器内 `unzip /app/app.jar` grep 确认新代码。构建期 alpine 镜像源 TLS 不稳定导致 `apk add` 失败，改用已装好 curl/tzdata 的上一版镜像为 base 只替换 jar（`Dockerfile.jaronly`）。
- 前端：本地 `npm run build`（含 `admin-vue` dist 置于 `dist/admin/`），重建 `dms-frontend-test:latest`，容器内确认 `proxy_pass` 指向 `172.17.0.1:8082`。
- 端到端验证（全部经前端 8083）：14 个导出接口全部 200 且返回 xlsx（`504b` 魔数）、12 个模板接口 200、14 个模块 `batch-import` 均可达、11 个模块真实 Excel 回灌导入成功、供应商 upsert 二次导入正确更新、`api_call_log` 不再报错。
- 正式环境（8081/8080）未改动，等用户明确指令。

## v3.9.0 (2026-08-04) - 移动端精简（销售场景）

### 背景
原有移动端从 PC 端复制而来，涵盖销售、采购、收货、发货、库存、消息等全量功能。但实际使用显示，销售在手机上的真实需求只有三类：下销售订单、填手术报台、看个人业绩。其他能力造成入口臃肿、加载慢、误操作率高。

### 前端（frontend-vue）
- 重写 `MLayout.vue`：底部 TabBar 由 5 项精简为 4 项（首页 / 订单 / 报台 / 我的），移除收货与发货。
- 重写 `MHome.vue`：聚焦销售个人业绩 KPI（今日/本月订单数与金额、本月报台数）+ 4 个快捷入口。
- 简化 `MOrders.vue`：仅销售订单 Tab，移除内置新建弹窗，统一走 `/mobile/orders/create`。
- 新增 `MOrderDetail.vue`：`GET /api/orders/{id}`。
- 重写 `MOrderCreate.vue`：Vant 风格的销售下单页（选经销商/仓库/产品、行内编辑数量单价税率、提交 `POST /api/orders`），与 PC 端 `OrderCreate.vue` 业务一致。
- 重写 `MSurgeryReportCreate.vue`：Vant 风格的报台创建（选经销商/医院/仓库/产品/批号或序列号/数量/日期/患者/医生/备注），提交 `POST /api/surgery-reports`，修复原版字段缺失导致提交失败的问题。
- 新增 `MSurgeryReports.vue`：报台列表（`GET /api/surgery-reports`，后端按 sales 角色自动过滤）。
- 新增 `MSurgeryReportDetail.vue`：报台详情（`GET /api/surgery-reports/{id}`，含产品明细）。
- 改造 `MDashboard.vue`：仅保留"销售业绩" Tab（月度 KPI + 12 月趋势 + TOP 经销商），移除采购统计与库存预警。
- 新增 `MProfile.vue`：账户信息 + 退出登录入口。
- 移除 `MMessages.vue` / `MInventory.vue` / `MReceipt.vue` / `MShipment.vue` / `MOrderTrace.vue` 与对应路由。
- 路由 `/mobile` 段重写为 9 个子路由（4 Tab + 5 跳转/详情页）。

### 后端
- 无接口变更。报台列表/详情接口已支持按 sales 角色自动过滤。
- 修复前端报台提交字段不匹配（前端原提交了 `hospitalName/productName`，后端要求 `dealerId/terminalId/warehouseId/lines`）。

### 部署
- 阿里云测试 8083：dist zip 上传 + `docker cp` + restart (dist-only 模式，容器 `dms-test-frontend`)
- 阿里云生产 8081：dist zip + Dockerfile.dist 重建 `dms-frontend-vue:latest` (28a975170d63, 79MB)，备份旧镜像 `backup-before-admin`
- 生产 nginx 增加 `/admin/` 路由块，dist 增加 admin/ 资源（从 8083 同步）
- E2E 验证：9/9 业务 API + 主页 + chunk + iPhone UA 全部 200

### 文档
- 新增 `docs/11_平台后台/13_移动端精简方案_v3.9.0.md`：背景、目标、页面调整清单、路由调整、验证方法
- 新增 `docs/DMS登录信息手册.md`：测试/生产/平台后台三类入口、登录端点、真实可登录账号、SSH 信息、MinIO/PG/Redis 默认账号、常见登录问题排查
- 部署手册：`tools/mobile-deploy-v3.9.0.md`
- 部署脚本：`tools/_deploy_test.py` / `tools/_deploy_prod.py` / `tools/_verify_*.py`（paramiko 实现，替代 MCP ssh-manager）

### 验证
- `npm run build` 通过（25.67s，11 个移动端 chunk 全部产出）。
- 销售登录 → 4 个 Tab 切换正常；订单/报台列表可下拉加载更多。
- 创建销售订单：选经销商 → 选产品 → 行内编辑数量单价 → 提交成功并返回订单号。
- 创建报台：选经销商 → 选授权医院 → 选产品 → 选批号或序列号 → 提交成功并扣减合格库存。
- 业绩页：月度趋势 + TOP 经销商数据正常。
- 退出登录后正确跳转到 `/mobile/login`。
- 服务器铁律自检通过：源码 100% 替换、旧镜像删除+构建缓存清理、产物校验、临时包保留、rm+mkdir 替换、nginx 代理指向正确、9/9 E2E API、缓存清理。

---

## v3.8.6 (2026-08-02) - 销售岗位模块改造（多销售账号+业绩占比+经销商归属）

### 背景
销售岗位需支持一个岗位挂多个销售账号并按比例分配业绩；一个经销商只能归属一个岗位；修复岗位菜单/详情 500、账号分配保存无效、界面过小等问题。

### 后端
- `SalesPositionController` 重写绑定逻辑：
  - `PUT /api/sales-positions/{id}/bind-users`，body `{users:[{id, shareRatio}]}`，全量替换；校验账号均为销售角色、未被其他岗位占用、同岗位业绩占比总和 <= 1。
  - `PUT /api/sales-positions/{id}/bind-dealers`，body `{dealerIds:[]}`，全量替换；一个经销商只能归属一个岗位，跨岗位分配被拒绝。
  - 新增 `GET /{id}/candidates/users` 与 `GET /{id}/candidates/dealers`，列出全部候选并标注 `boundPositionId/boundPositionName/occupiedByOther`。
  - `GET /{id}/users` 改读 `position_users` 并返回 `shareRatio`；`GET /{id}/dealers` 读 `position_dealers`；移除有列名 bug 的 `/dealer-accounts`。
  - 修复 PostgreSQL 数组参数：`IN (?2)` 改为 `= ANY(?2)`、`NOT IN (?2)` 改为 `<> ALL(?2)`；同步冗余字段时先清空旧绑定再写入，避免唯一约束冲突。
- Flyway V48：`position_users` 增加 `share_ratio NUMERIC(8,4)` 并回填历史绑定。
- Flyway V49：删除与“一岗多销售”冲突的唯一索引 `ux_users_position`（原限制一个岗位最多 1 个用户）。
- `User` 实体补齐 `role` 字段映射；`UserService.create` 新建用户时按 `userType` 写入 `role`（修复新用户默认 admin 的安全问题）。

### 前端（PC）
- 路由 `/positions` 切换到重写后的 `Positions.vue`：左树 + 右侧详情；新增/编辑用 520px 抽屉；分配销售账号 820px 弹窗（含占比输入、实时合计、超上限禁用），分配经销商 760px 弹窗；候选列表展示已被哪个岗位占用并禁用勾选。
- “分配经销商账号”更名为“分配经销商”。
- `api/positions.js` 绑定接口改为 PUT，补充候选接口，移除废弃的 dealer-accounts。

### 测试数据
- 新增 11 个销售账号（`sales_*`，role=sales）。
- 7 个区域岗位绑定销售账号并设置业绩占比（合计均 <= 1）；42 个经销商按 6 个/岗位分配。

### 测试
- 测试环境 E2E：多销售账号绑定、占比合计、跨岗位占用拒绝、占比超限拒绝、经销商单岗位归属、跨岗位经销商拒绝均通过；岗位树/详情/候选/我的范围接口全部 200。

## v3.8.5 (2026-08-02) - 批量导入支持“存在即更新”（upsert）

### 背景
原批量导入只做新增，重复编码会报“编码已存在”；用户希望导入同时承担数据更新职责。

### 后端
- 基础数据批量导入统一改为按业务编码 `code` 判重：编码已存在则更新（非空字段覆盖，Excel 留空列保留原值），不存在则新增。覆盖模块：产品、产品分类、经销商、医院/终端、仓库、区域、供应商。
- 产品价格无业务编码，按唯一键 `(tenant_id, product_id, partner_type, partner_id)` upsert（同价则更新价格/币种/状态）。
- 各 Repository 新增 `findByTenantIdAndCode`（仓库为 `findFirstByTenantIdAndCode`）；各 Service 新增 `upsertByCode`，仅在新增分支校验“名称必填”，更新分支不强制名称。
- 修复：供应商导入此前直接 `em.persist` 未带 tenantId；医院导入字段名误用 `contactPerson/contactPhone`（实体为 `contact/phone`）。
- 单据类（销售/采购订单、收发货、库存移动、手术报台）导入仍只新增，避免误更新已有单据。

### 前端
- 导入弹窗增加说明：“导入按‘编码’判断：编码已存在则更新该行（留空的列保留原值），不存在则新增。”

### 测试
- 测试环境 E2E：导入一个新产品成功；同编码再次导入只改规格/单价且名称/类型留空，回读确认名称/类型保留、规格/单价更新（PASS）。

## v3.8.4 (2026-08-02) - ???????????????????

### ??
??????????????"????/????"??????????????????????????????? `CrudView.vue` ?????????????????????????(picker)?????????????????????????????????????"?????/????"?

### ???frontend-vue?PC?
- `CrudView.vue`??? `PICKER_NAME_MAP` ?????????`openForm` ??????????? `detailable` ???? `getDetail` ???????? `formData` ? `displayMap`???????/?????????????????
- `modules.js`?
  - ???(dealers) ????? `contact`/`phone` ???????? `contactName`/`contactPhone`?
  - ???(suppliers) ??"????"?? `phone` ?? `contactPhone`???????

### ??
- Flyway V47?`product_categories`?`regions` ?? `sort_order`?`roles` ?? `role_type`(?? custom)?
- `ProductCategoryService` / `RegionService`?`update` ?? `sortOrder` ??????????????????
- `SalesPositionController`?`create` ?? `sort_order` ???????? `update` ??? `parentId/sortOrder` ? `GET /{id}` ?????
- `RbacService`??? `getRole` ?? `@Transactional` ??????????`toDTO` ?? `type` ???????????????? `GET/PUT /api/roles/{id}`?
- `DealerController`???/????? `contactPerson/address` ????????? `contactName/regAddress`?
- `Authorization` ????? `@JsonSetter` ??????????(categoryIds/terminalIds)?????????? CSV ????????????????????
- `SurgeryReportController`??? `GET /api/surgery-reports/{id}` ??????????????? 500?

### ?????????
- ?? `scripts/field_audit.py`??? `modules.js` ?????????????(??50?)/???????"?????????????"??????? CI???????? 1??
- ?????16 ?????? 0 ????????
- ?? `mvn clean package -DskipTests` ????? `npm run build` ???
- ???? E2E?????/?????????/???????????????????????????

## v3.8.3 (2026-08-02) - 对外开放接口(销售/采购订单创建)

### 后端
- 新增对外接口模块 `com.dms.openapi`，路径前缀 `/open/api`，采用 HMAC-SHA256 签名鉴权（appKey + 毫秒时间戳 + nonce + body 摘要），时间戳允许 ±5 分钟偏差，支持来源 IP 白名单。
- 新增 `open_app` 表（Flyway V46）存储第三方应用凭据（appKey/appSecret/租户/系统标识/状态/IP白名单），并为默认租户预置测试应用 `dms-demo-app`。
- `POST /open/api/sales-orders`：外部系统创建销售订单（草稿），主数据用 dealerCode/warehouseCode/productCode 传参，自动解析为内部 ID，金额由 DMS 汇总。
- `POST /open/api/purchase-orders`：外部系统创建采购订单（草稿），主数据用 supplierCode/warehouseCode/productCode 传参。
- 入站日志过滤器 `ApiCallLogFilter` 同步记录 `/open/api/**` 调用，带 appKey 与 system 标识。
- 新增独立文档 `docs/06_API设计/DMS对外开放接口文档.md`（含调用方式、签名算法、多语言示例、curl、错误码、排错与安全建议）。

### 测试
- 测试环境 E2E 通过：HMAC 正确签名可创建 SO/PO（含明细落库）；错误签名/缺签名返回 401；主数据编码不存在/空明细返回对应业务错误码；open 调用正确写入接口调用日志。

## v3.8.2 (2026-08-02) - 接口调用日志模块

### 后端
- 新增 `api_call_log` 表（Flyway V45），统一记录入站/出站接口调用：direction(IN/OUT)、system、endpoint、http_method、url/path、status_code、biz_code、success、client_ip、user、app_key、请求头/请求体/响应体、error_msg、spent_ms、起止时间。
- 新增 `ApiCallLogFilter`（order=60，位于 JwtFilter 之后）自动记录所有 `/api/**` 入站请求，含业务码解析与耗时；排除 actuator/swagger/日志自身路径；响应体经缓存包装回写，不影响正常返回。
- 新增 `ApiCallLogService.callExternal(...)`：DMS 调用外部系统（ERP/WMS/HR/UDI/CA/第三方）时统一发起并记录 OUT 日志，未来新增对接系统直接复用。
- 新增 `GET /api/admin/api-call-logs`（分页+多维过滤）与 `GET /api/admin/api-call-logs/{id}`（详情），仅 admin 可访问。
- 写库通过 `@Async` 异步执行，不阻塞主链路；请求/响应体超 64KB（列表接口 32KB）自动截断。

### 前端（frontend-vue，PC）
- 新增 `ApiCallLog.vue` 接口调用日志页（方向/系统/方法/状态码/关键字筛选、成功失败标签、耗时、详情抽屉展示请求头/请求体/响应体/错误）。
- 新增路由 `/api-call-logs`，并在「用户与权限」菜单组加入口。移动端不动。

### 测试
- 测试环境实测：登录、库存查询、销退列表等入站请求均正确入库，详情含响应体；V45 迁移成功（schema version 45）；后端 `mvn package` 通过、前端 `npm run build` 通过。

## v3.8.1 (2026-08-02) - 销退/采退改造、库存汇总查询接口

### 后端
- 销退订单：新增 `SalesReturnController`（`/api/sales-returns`，orders 表 is_red=true，单号 RS）。状态机 DRAFT→SUBMITTED→APPROVED→RECEIVING→COMPLETED；审批通过自动生成销退入库草稿单 RGR（库存+、入待检 PENDING），入库进度回写销退单状态。必须关联已发货的发货单，提供 `/shipped-outs`、`/shipped-outs/{id}/lines` 带明细与可退数量，校验退货数量≤可退、行可删除、退货原因必填。
- 采退订单：新增 `PurchaseReturnController`（`/api/purchase-returns`，purchase_orders 表 is_red=true，单号 RP）。状态机 DRAFT→SUBMITTED→APPROVED→SHIPPING→COMPLETED；审批通过自动生成采退出库草稿单 RGI（库存−，不限库存状态），出库进度回写采退单状态。不限制原单/数量。
- `AutoDocGenerator` 新增 `createReceiptForSalesReturn`（RGR, ref_doc_type='sales_return'）与 `createSalesOutForPurchaseReturn`（RGI, source_po_id）；`DocNoGenerator` 新增 RS/RP/RGR/RGI 前缀映射。
- 收货：`ReceiptBatchService` 确认/取消剩余时按 ref_doc_type='sales_return' 回写销退订单状态（RECEIVING/COMPLETED）。
- 出库：`SalesOutBatchService` 对红字 RGI 单据放宽库存状态限制（QUALIFIED/DEFECTIVE/QUARANTINED/PENDING 均可出），确认时按 source_po_id 回写采退订单状态（SHIPPING/COMPLETED）。
- 库存查询：新增 `POST /api/inventory/query`（JSON），按 productCodes（必填，多物料）+ warehouseId（选填）汇总库存总数，不展开批次/序列号/库存状态。
- 数据库迁移 V44：orders.ref_sales_out_id、orders.return_reason、order_lines.batch_no/serial_no、purchase_orders.return_reason、sales_outs.source_po_id。

### 前端（frontend-vue，仅 PC）
- 新增 `SalesReturnEdit.vue`（选择经销商/收货仓库/已发货单→自动带明细与可退数量，数量只能改小、行可删除、退货原因必填、底部汇总）与 `PurchaseReturnEdit.vue`（供应商/出库仓库/物料明细/汇总）。
- `modules.js`：sales-returns 改走 `/api/sales-returns`、purchase-returns 改走 `/api/purchase-returns`；列、状态（销退 RECEIVING、采退 SHIPPING）、按钮精简，配置 createPath/detailPath 跳转专用编辑页。
- 路由新增 `sales-return-edit/:id`、`purchase-return-edit/:id`；`CrudView` 支持 `detailPath`（点单号跳转）。
- 移动端 mobile/ 本期不动。

### 测试
- 后端 `mvn compile` 通过；`mvn test` 83/84 通过，唯一失败 `DmsApplicationTests.contextLoads` 因本地未启动 Redis(6380)（环境问题，与本次改动无关）；前端 `npm run build` 通过。

## v3.8.0 (2026-08-01) - 会话/权限、收货汇总、产品类型修复、库存移动

### 后端
- 会话：`application-test.yml` access-token-ttl 600000->28800000（8h）、refresh-token-ttl 3600000->604800000（7d）。
- 安全：`JwtFilter` 令牌解析失败返回 401/40101“登录已过期，请重新登录”；`SecurityConfig` 增加 authenticationEntryPoint(401) 与 accessDeniedHandler(403)。
- 收货汇总：`BizDocDetailController.receiptDetail` 聚合 receipt_lines 返回 totalExpected/totalReceived/totalCancelled/totalRemaining。
- 产品：`Product` 实体新增 productType/productTypeName；`ProductService` create/update 持久化 productType，列表/详情回填类型与分类名；Flyway V43 增加 products.product_type。
- 单号：`DocNoGenerator.next` 单号撞唯一键时自动顺延重试，避免共享测试库/历史数据导致 500。
- 库存移动 v3.7.9：STATUS_ADJUST 仓内状态调整 / WAREHOUSE_TRANSFER 跨仓移动，srcInventoryId 原子扣减+upsert，序列号/数量/状态校验（见下 v3.7.9）。

### 前端（frontend-vue，PC）
- `request.js`：401 自动 refresh + 请求排队重放；403 展示后端 message；统一中文文案。
- `ReceiptEdit.vue`：底部新增“收货汇总”卡片，字段缺失时前端按 poLines 兜底求和。
- 产品表单/列表：产品类型可保存并回显（modules.js 既有 productType 配置现已生效）。
- 物料选择器：分页每页 50，模糊搜索覆盖编码/名称/规格。

### 数据迁移
- 测试环境需应用 V39-V43（此前停留在 V38）；test profile 关闭 Flyway，部署时手动执行并补 flyway_schema_history。

### 测试
- 后端 `mvn test`：84 个测试全部通过（修复了历史测试路径 /auth->/api/auth、外键种子数据、DocNoGenerator 适配等）。
- 新增 ProductControllerIntegrationTest 覆盖产品类型/分类保存与回显。
- API 冒烟：34 个核心接口 200，0 个 500；前端 npm run build 通过。

## v3.7.9 (2026-08-01) - 库存移动两种模式（仓内状态调整 / 跨仓移动）

### 后端
- `stock_moves` 新增 `move_type`(STATUS_ADJUST/WAREHOUSE_TRANSFER)、`from_stock_status`、`to_stock_status`（Flyway V41，历史数据回填 WAREHOUSE_TRANSFER，move_type 扩到 VARCHAR(24)）。
- `stock_move_lines` 新增 `src_inventory_id`、`from_stock_status`、`to_stock_status`、`stock_batch_id`。
- 字典 `stock_status` 新增 `QUARANTINED(隔离)`（QUALIFIED/DEFECTIVE/PENDING 保留）。
- `POST /api/stock-moves` 重写：两种模式校验、基于 srcInventoryId 读真实库存、数量/序列号/状态一致性校验、单号 `MV-YYYYMMDD-NNNNN`（doc_no_sequences 原子自增）、按 inventory 主键原子扣减 + 按维度 upsert 增加库存、写明细/操作日志/库存流水（STATUS_ADJUST_OUT/IN、MOVE_OUT/IN，含操作人）。
- `InventoryStatusOps` 新增 `deductById`、`addByKey` 及写流水私有方法。
- 列表/详情/导出 SQL 增加 move_type、from/to_stock_status 字段。
- 单据保存即生效（COMPLETED，无草稿/审批）。

### 前端 PC（仅改 frontend-vue，不动 mobile）
- 新增 `StockMoveEdit.vue`：模式 radio 切换；源仓 picker（跨仓时显示目标仓，仓内时隐藏目标仓）；明细从库存弹窗多选（物料/批次/序列号/当前状态/在库数全部带出，不可手填）；目标状态 select；序列号产品锁定 qty=1；数量与同状态校验；提交 POST /api/stock-moves；已存在单据只读详情。
- `modules.js` stockMoves：列表清理为移动类型/源仓/目标仓/源状态/目标状态/状态/时间，新增 createPath 跳转专用编辑页，列表仅保留“查看”动作。
- `CrudView.vue` 新增 `createPath` 支持，配置后“新增”按钮路由跳转而非弹窗。
- 路由新增 `stock-move-edit/:id`（new 为新建，数字 id 为查看）。

### 验收
- 仓内状态调整 QUALIFIED→QUARANTINED 成功，库存与流水正确。
- 跨仓移动并同时改状态（QUALIFIED/QUARANTINED→PENDING）成功，目标仓按目标状态 upsert。
- 超额数量、跨仓同仓、源状态不匹配均被拒绝。
- 库存流水完整记录出/入与操作人。

## v3.7.8 (2026-07-31) - 销售出库子单模型（对齐收货入库）

### 销售出库（sales_outs）父子单重构
- 新增子单表 `sales_out_batches` / `sales_out_batch_lines`（Flyway V39），每次发货一张子单，独立保存/确认/取消，对齐 `receipt_batches` 模型
- 新增接口：
  - `POST /api/sales-outs/{id}/batches` 创建发货子单
  - `PUT  /api/sales-out-batches/{bid}` 保存子单明细
  - `POST /api/sales-out-batches/{bid}/confirm` 确认发货（扣 QUALIFIED 库存、写序列号 shipped_at、累加 shipped_qty、回写父单/源订单）
  - `POST /api/sales-out-batches/{bid}/cancel` 取消本次（仅 DRAFT，不动库存）
  - `POST /api/sales-outs/{id}/cancel-remaining` 取消剩余待发（未发数置 cancelled_qty，回写 COMPLETED）
- 关键差异（区别于收货）：子单行的批次号/序列号必须选择该仓该物料的在库合格库存（QUALIFIED），后端校验存在且数量足够，序列号产品必须选在库序列号
- 状态机：父单 DRAFT/APPROVED -> PARTIAL_SHIPPED -> COMPLETED；源销售订单 APPROVED -> SHIPPING -> COMPLETED；不再使用 PARTIAL_CANCELLED
- 取消规则：销售订单仅在出库单完全未发货时可取消（取消会级联取消出库单及其 DRAFT 子单）；一旦有发货记录订单不可修改/取消

### 前端 PC（仅改 frontend-vue，不动 mobile）
- `SalesOutEdit.vue` 整体重写为子单 UI（对齐 `ReceiptEdit.vue`）：创建发货单/保存明细/确认发货/取消本次/取消剩余；批次与序列号用下拉选择在库合格库存；新增发货汇总、已发货记录（含发货人）、关联订单税额显示
- 销售出库列表页清理为仅“打开/查看”，所有业务动作移入详情页；移除 PARTIAL_CANCELLED 筛选项
- 销售订单及采购/销退/采退的审批/驳回按钮统一去掉 `noRefresh`，操作后自动刷新，修复“审批通过后按钮不消失”

### 数据迁移
- V39 `sales_out_batches` / `sales_out_batch_lines`（幂等）

## v3.7.7 (2026-07-31) - 销售订单/销售出库对齐采购订单/收货入库

### 销售订单（orders）
- 新端点 `/api/sales-orders`（native SQL 实现，镜像 PurchaseOrderController）
- 状态机更新：`DRAFT → SUBMITTED → APPROVED → SHIPPING → COMPLETED`，新增 SHIPPING、废弃 SHIPPED
- 新增字段：`warehouse_id`（发货仓库）、`tax_amount`、`approved_by`、`completed_at`、`cancelled_at`、`extra`
- 创建必填：dealerId + warehouseId（发货仓库）；订单类型简化为 NORMAL / URGENT
- approve 后自动生成销售出库草稿（prefix `XS-*`）
- cancel 校验关联 sales_outs 均为 DRAFT/CANCELLED 且无发货，并级联取消

### 销售出库（sales_outs）
- 表结构对齐 receipts：`warehouse_id`、`remark`、`approved_at/by`、`shipped_at`、`completed_at`、`cancelled_at`、`extra`
- 明细语义对齐 receipt_lines：`expected_qty`（应发）、`shipped_qty`（累计已发）、`qty`/`quantity` 历史字段
- partialShip 按 `expectedLineId` 定位应发行（修复同产品多行累计校验缺陷），发货后回写源订单状态 APPROVED → SHIPPING / COMPLETED
- 列表返回 warehouseName；详情区分 lines（应发）/ shippedLines（执行记录）/ soLines（订单行参考）/ sourceOrder（来源订单）
- 单号前缀：红字 GIR，正常 XS（对齐采购的 GR/GRR）

### 前端 PC（本次只改 PC，不动 mobile/）
- `modules.js` orders：api → `/api/sales-orders`，新增发货仓库列、审核人列，状态项去 SHIPPED 加 SHIPPING；form 新增 warehouseId picker
- `SalesOutEdit.vue` 整体重写：布局对齐 `ReceiptEdit.vue`，新增出库单信息/关联销售订单/订单产品明细/发货明细/汇总/已发货记录/操作记录七张卡片
- `dict.js` 新增 SHIPPING、RECEIVING、PARTIAL_CANCELLED 状态文本和颜色

### 数据库
- Flyway V36：sales_order_out_align_purchase，幂等迁移（IF NOT EXISTS / COALESCE 回填历史数据）

### 启动参数
- 本地：`SPRING_FLYWAY_ENABLED=true` 启动会自动执行 V36 迁移

## v3.7.6 (2026-07-26) - 采购/收货三次调整 (6项)

### 单号规则
- 收货入库 `RK-*` → `GR-YYYYMMDD-N`, 子单 `GR-YYYYMMDD-N-M`
- 销售出库 `CK-*` / 硬编码 `SO-timestamp` → `GI-YYYYMMDD-N`
- 采购退入库 `RRK` → `GRR`, 销售退出库 `RCK` → `GIR`

### 状态机
- 收货完成 / 取消剩余 → 收货单 + PO 同步 COMPLETED (不再停留 RECEIVING)
- PO 取消 → 级联关闭对应 receipts + DRAFT 子单 (CANCELLED)

### 列表 / 详情页
- PO list 去除 `总金额` 列; 后端 JOIN users 回填 `auditUserName / auditAt`
- 收货 list 去除 `仓库ID` `源类型`
- ReceiptEdit.vue 底部新增 `操作记录` 卡片

### 数据 / 底层
- ReceiptBatchService 4 入口 res 加 `receiptId`, OperationLogAspect 优先取 receiptId 作 businessId
- 子单 `confirmed_at` = now() 作为入库时间; 页面卡片已显示

### 部署
- backend 180s + frontend 196s = 376s

## v3.7.5 (2026-07-26) - 采购/收货二次调整

### 后端
- PurchaseOrderController: submit/approve 操作日志中文化; SUBMITTED 状态禁止取消; allowedActions 移除 SUBMITTED cancel; list/detail SQL COALESCE supplier_name + JOIN suppliers 补齐显示
- ReceiptBatchService: 19 处 RuntimeException 改为 BusinessException; 修复 Java 字符串转义 bug; 6 条友好中文错误提示
- BizDocDetailController.poDetail(): 追加 allowedActions

### 前端
- CrudView.vue: 新增 rowEditable/rowDeletable 支持 editableWhen/deletableWhen; dictLabel 自动翻译 filter.options 列
- modules.js 采购订单: editableWhen=['DRAFT']; 取消 when=['DRAFT','APPROVED']

### 验证
- 端到端 PO create→submit→approve→over-qty 友好提示 = 通过
- 库存 stock_status dict U合格/Q待检/B不合格 生效
- 部署耗时 back 184s + front 189s = 373s

# 鍙樻洿鏃ュ織锛圕HANGELOG锛�

> 璁板綍 DMS 椤圭洰鏂囨。婕旇繘杩囩▼銆傛棩鏈熷�掑簭銆�

---

## v3.7.3 (2026-07-26) - 閲囪喘璁㈠崟+鏀惰揣鍏ュ簱鍏ㄦ祦绋嬩慨澶�

### 鏂板��
- 閲囪喘璁㈠崟/閿�鍞�璁㈠崟鍦� DRAFT 鐘舵�佹樉绀�"鎻愪氦瀹℃壒"鎸夐挳锛沗when` 浠� `PENDING_APPROVAL` 瀵归綈鍚庣�� `SUBMITTED`
- 閲囪喘璁㈠崟瀹℃壒閫氳繃鍚庤嚜鍔ㄥ垱寤烘敹璐у叆搴撹崏绋匡紙鍓嶇��闂�鐜�锛�
- 鏀惰揣鍏ュ簱缂栬緫椤垫柊澧�"鍏宠仈閲囪喘璁㈠崟"淇℃伅鍗★紙13 椤瑰瓧娈碉級
- 杩佺Щ V34__receipt_test_data.sql锛氭竻鐞嗗�ゅ効鏀惰揣 seed锛岄噸閫� 4 鍦烘櫙鍏宠仈 PO 鐨勬敹璐у叆搴�

### 淇�澶�
- [R5] 鏀惰揣鍏ュ簱鍒楄〃闅愯棌"鏂板缓"鎸夐挳锛坣oCreate:true锛�
- [R8] 鏀惰揣鍏ュ簱鍒楄〃闅愯棌"鍒犻櫎"鎸夐挳锛坣oDelete:true锛�
- [R3] CrudView 鏄庣粏瀛楁�� span=24 + .has-lines 鎶藉眽閾烘弧鑷�閫傚簲锛汱inesEditor 琛ㄥご min-width
- [R4] LinesEditor 蹇呭～鍒楄〃澶村姞绾㈡槦锛汣rudView 鎻愪氦鍓嶉亶鍘嗗繀濉�鏍￠獙
- [CrudView rowActions bug] 鏁扮粍褰㈠紡 statusActions 涔嬪墠浼氬洜 sa[row.status] 鍙� undefined 琚�蹇界暐
- [PurchaseOrder create] expected_date 鍙傛暟 CAST(:ed AS date)锛屼慨闀挎湡 500 閿�

### 楠岃瘉
- API E2E: tools/browser-use/e2e_v373_po_receipt.py 鍏ㄩ儴 PASS
- UI 鍐掔儫: tools/browser-use/ui_smoke_v373.py 12/12 PASS

---


## 2026-07-22 鈥� v3.5.2 + 鍙岀幆澧冮儴缃� + 鏂囨。鍚屾��

### 浜や粯鍐呭��
| 椤圭洰 | 璇存槑 |
|---|---|
| **14 椤瑰叏灞�鎬� UI/UX 鏁存敼** | 鍏ㄩ儴鎸夐渶姹傚畬鎴愶紝浠ｇ爜灞傞潰 14/14 鉁� |
| **淇�澶� 502 鐧诲綍闂�棰�** | 淇�姝� application.yml 榛樿�ゅ瘑鐮併�佸悗绔�鐜�澧冨彉閲忋�丯ginx 浠ｇ悊 |
| **淇�澶� Flyway V22 杩佺Щ** | 澶氭�′慨姝ｅ垪鍚嶅拰鍞�涓�绾︽潫锛屾渶缁堟垚鍔� |
| **閲嶇疆 admin 瀵嗙爜** | 鏁版嵁搴撲腑 V7 纭�缂栫爜鐨� BCrypt 鍝堝笇瀵瑰簲鐨勫瘑鐮佷笌鏃ュ織鎵撳嵃涓嶄竴鑷达紝閲嶇疆涓� `Sh123456` |
| **鍒涘缓娴嬭瘯鐜�澧�** | 绔�鍙� 8082/8083/5433/6380/9002/9003锛屼笌姝ｅ紡鐜�澧冨畬鍏ㄩ殧绂� |
| **鏈�鍦伴」鐩�娓呯悊** | 鍒犻櫎 50+ 涓�涓存椂娴嬭瘯鑴氭湰鍜屾棤鐢ㄦ枃浠� |
| **鏈嶅姟鍣ㄦ竻鐞�** | 娓呯悊 Docker 鏃犵敤闀滃儚鍜屽�瑰櫒锛岄噴鏀剧�佺洏绌洪棿 |

### 鍙岀幆澧冮儴缃�
- **姝ｅ紡鐜�澧冿紙鐢熶骇婕旂ず锛�**: 8.133.193.238:8081锛堝墠绔�锛�/ 8080锛堝悗绔�锛�/ 5432锛圖B锛�
- **娴嬭瘯鐜�澧冿紙寮�鍙戦獙璇侊級**: 8.133.193.238:8083锛堝墠绔�锛�/ 8082锛堝悗绔�锛�/ 5433锛圖B锛�

### 婕旂ず璐﹀彿锛堢粺涓�涓� Sh123456锛�
- 绉熸埛 `default`
- admin / director / sales1 / dealer1 / dealer2

### 椤圭洰瑙勫垯鏇存柊
- 鏂板�炪��3.1 鍙岀幆澧冪�＄悊瑙勫垯銆嶏細鎵�鏈夐渶姹傝皟鏁村彧鑳藉厛閮ㄧ讲鍒版祴璇曠幆澧冿紝鐢ㄦ埛鏄庣‘璇�"鎺ㄩ�佹�ｅ紡鐜�澧�"鎵嶈兘鏇存柊姝ｅ紡鐜�澧�

### 鏂囨。鏇存柊
- README.md锛氭洿鏂板弻鐜�澧冨湴鍧�銆佸瘑鐮佺粺涓�涓� Sh123456
- 浜ゆ帴鎬荤粨_v3.5.2.md锛氭洿鏂板瘑鐮佷负 Sh123456锛岃ˉ鍏呮祴璇曠幆澧冪��鍙ｄ俊鎭�
- .trae/project_rules.md锛氭柊澧炲弻鐜�澧冪�＄悊瑙勫垯绔犺妭
- docs/07_閮ㄧ讲鏂规��/鍓嶇��閮ㄧ讲鎶ュ憡_娴嬭瘯鐜�澧僟20260722.md锛氭祴璇曠幆澧冮儴缃茶�板綍

---

## 2026-07-18 鈥� v3.0 閲囪喘閿�鍞�鎷嗗垎 + 浣庝唬鐮�

### 鑳屾櫙
鐢ㄦ埛鍙嶉�� 5 涓�闂�棰橀渶瑕佷竴娆℃�т氦浠橈細鐘舵�侀┍鍔ㄦ寜閽�銆佷腑鏂囪�︽儏瑙嗗浘銆侀噰璐�閿�鍞�鎷嗗垎銆佸簱瀛樿仈鍔ㄣ�佷綆浠ｇ爜瀛楁�甸厤缃�銆�

### 浜や粯鍐呭��
| 妯″潡 | 璇存槑 |
|---|---|
| **鏁版嵁搴� V8** | 鏂板�� `purchase_orders`銆乣purchase_order_lines`銆乣form_configs` 琛�锛涗富琛ㄥ姞 `extra JSONB` 鍒� |
| **鍚庣�� 5 涓�鏂� Controller** | `PurchaseOrderController`銆乣OrderMetaController`銆乣FormConfigController`銆乣DictCrudController`銆乣InventorySummaryController` |
| **鍓嶇�� 3 澶勫崌绾�** | `workspace.html`锛氳彍鍗曟媶鍒� + 鐘舵�侀┍鍔ㄦ寜閽� + 涓�鏂囪�︽儏瑙嗗浘锛沗admin.html`锛氭柊澧炲瓧鍏哥淮鎶ゅ拰瀛楁�甸厤缃�椤碉紱`order-create.html`锛氶攢鍞�/閲囪喘鍙屾ā寮� |
| **娴嬭瘯** | 14/14 鍐掔儫娴嬭瘯鍏ㄩ儴閫氳繃 |
| **鏂囨。** | 鏂板�� [閲囪喘閿�鍞�鎷嗗垎+浣庝唬鐮佷氦浠樻姤鍛奯v3.0.md](docs/09_娴嬭瘯鎶ュ憡/閲囪喘閿�鍞�鎷嗗垎%2B浣庝唬鐮佷氦浠樻姤鍛奯v3.0.md) 鍜� [DMS鐜�澧冧俊鎭�.md](docs/DMS鐜�澧冧俊鎭�.md) |
| **鏁版嵁搴撳揩鐓�** | 瀵煎嚭鑷� `docs/05_鏁版嵁搴撹�捐��/schema_export/`锛�157 KB schema + 2.2 MB 鏁版嵁锛� |

### 鏈嶅姟鍣ㄧ増鏈�
- 鍚庣��闀滃儚锛歚dms-backend:2.0.2`
- 4 涓�瀹瑰櫒鍏ㄩ儴 Up 路 纾佺洏娓呯悊閲婃斁 12 GB

---

## 2026-07-18 鈥� 鍏ㄩ渶姹傝ˉ榻� v2.0锛圥0-P3 路 38 椤瑰姛鑳斤級

### 浜や粯鎵规��
| 鎵规�� | 浼樺厛绾� | 椤规暟 | 娴嬭瘯 |
|---|---|---|---|
| 鎵规�� 1 | P0 闃绘柇椤� | 6 | 15/15 鉁� |
| 鎵规�� 2 | P1 鐢ㄦ埛浣撻獙 | 10 | 10/10 鉁� |
| 鎵规�� 3 | P2 绠＄悊鑳藉姏 | 10 | 10/10 鉁� |
| 鎵规�� 4 | P3 瀹屾暣鍖� | 12 | 12/12 鉁� |

瑕嗙洊锛氬悎鍚� PDF/绛剧珷/ERP褰掓。銆乁DI杩芥函銆佹壒閲忓�煎叆瀵煎嚭銆佺患鍚堢湅鏉裤�佸緟鍔炲垪琛ㄣ�侀偖浠禩oken瀹℃壒銆佽秴鏃舵彁閱掋�佺紦瀛樼洃瑙嗐�侀泦鎴� Mock銆佷績閿�瀹℃壒銆佽繑鍒╁紩鎿庛�佸�熻揣鍗曘�丒xcel 瀵煎嚭銆佸井淇＄櫥褰� + 7 寮犵Щ鍔ㄧ�� H5 椤甸潰銆�

---

## 2026-07-18 鈥� V1 鍐崇瓥鍙樻洿锛圖-24 ~ D-41锛�

### 鑳屾櫙
鍦ㄦ�ｅ紡寮�鍙戝墠锛屼笟鍔℃柟瀵圭��浜岃疆 18 涓�寮�鍙戝墠闂�棰樿繘琛屼簡纭�璁わ紝浜у嚭 18 椤规柊鍐崇瓥 D-24 ~ D-41锛岄渶姹傚垎鏋愬笀銆佽�捐�″笀銆佹灦鏋勫笀鍚屾�ヤ慨璁㈠叏閮ㄦ牳蹇冩枃妗ｃ��

### 鍏抽敭鍐崇瓥鎽樿��

| # | 鍐崇瓥 | 缁撹�� |
|---|---|---|
| D-24 | 鍥㈤槦/宸ユ湡 | 15+ 浜� / 3-4 涓�鏈� |
| D-25 | 鍝佺墝瑙嗚�� | 浣跨敤缁勪欢搴撻粯璁や富棰橈紙Element Plus / Vant锛夛紝Logo 鐢ㄦ枃瀛楁牱寮� |
| D-26 | 浜や粯鐜�澧� | 浠呮湰鍦伴儴缃诧紙Docker Compose锛� |
| D-27 | 榛樿�よ秴绠� | 鍥哄畾 admin / Sh123456 |
| **D-28** | **淇冮攢闄嶇骇** | **V1 鍙�鍋氭弧鍑� + 璧疯�㈤噺**锛屽垹闄ゆ弧璧犱笌缁勫悎閿�鍞� |
| D-29 | UDI | 鍙�寮�鍏筹紝V1 涓嶇湡瀹炰笂鎶ョ洃绠� |
| D-30 | 鐢靛瓙绛剧珷 | Mock 濂戠害鎸� e绛惧疂 API |
| D-31 | ERP | 閫氱敤 REST锛屼笉缁戝畾鍘傚晢 |
| **D-32** | **鍒犻櫎 SSO** | V1 浠呰处鍙峰瘑鐮佺櫥褰� |
| **D-33** | **閫氱煡娓犻亾** | 绔欏唴 + 浼佸井/椋炰功 Webhook锛屽垹闄ら偖浠剁煭淇� |
| D-34 | 鎶ヨ〃 | 鍥哄畾 10 绫� + T+1 鐗╁寲瑙嗗浘 |
| D-35 | 鏉冮檺 | 鍥涘眰 RBAC + 琛岀骇锛堜笉鍋氬瓧娈电骇锛� |
| **D-36** | **H5 鐧诲綍** | 寰�淇℃壂鐮� + 棣栨�＄粦瀹� DMS 璐﹀彿 |
| D-37 | 澶氳��瑷� | 涓�鏂� + 棰勭暀 i18n |
| D-38 | 涓婚�� | 浜�鑹� + 绉熸埛鍙�鏀逛富鑹� |
| D-39 | 瀹¤�� | Excel 瀵煎嚭 + 3 骞� + MinIO 鍐峰瓨 |
| D-40 | 鎬ц兘 | PRD 榛樿�わ紙500 骞跺彂 / 50 TPS锛� |
| D-41 | 浜や粯鏂瑰紡 | 浠ｇ爜 + 鍩硅�� + 鎵嬪唽锛堜笉鍋氱伆搴﹁瘯鐐癸級 |

### 鏂囨。淇�璁㈡竻鍗�

#### 馃搫 [闇�姹傚垎鏋恄UserStory.md](d:/Workspace/TRAE/DMS/docs/02_闇�姹傚垎鏋�/闇�姹傚垎鏋恄UserStory.md)
- 鏂板�炲喅绛栬�板綍 D-24 ~ D-41 鍒般�岄浂銆佸叧閿�鍐崇瓥璁板綍銆嶈〃
- 鎵撳垹闄ょ嚎锛歎S-LOGIN-06 (SSO)銆乁S-B-Promo-03 (婊¤禒)銆乁S-B-Promo-05 (缁勫悎閿�鍞�)銆乁S-E-04 (SSO 闆嗘垚)
- 閲嶅啓锛歎S-M-01 (H5 鐧诲綍鏀逛负寰�淇℃壂鐮�)
- 鏇存柊锛歎S-E-01 閭�浠�/鐭�淇� 鈫� 浼佸井/椋炰功 Webhook
- 浼樺厛绾ф眹鎬伙細100 鈫� 94 鏉�

#### 馃搫 [楂樹繚鐪烾I璁捐�¤�存槑涔�.md](d:/Workspace/TRAE/DMS/docs/03_璁捐�″浘/楂樹繚鐪烾I璁捐�¤�存槑涔�.md)
- 鏂板�炪�孷1 鍐崇瓥鍙樻洿鎻愮ず銆嶇珷鑺傚埌鏂囨。寮�澶�
- 澹版槑 V1 閲囩敤 Element Plus / Vant 榛樿�や富棰橈紝鏈�璁捐�¤�存槑浣滀负闀挎湡鍙傝��
- 鏍囨敞淇冮攢椤甸潰绠�鍖栵紙W-13/W-14锛夈�丠5 鐧诲綍鏀归�狅紙W-24锛�

#### 馃搫 [鍔熻兘璇︾粏璁捐��.md](d:/Workspace/TRAE/DMS/docs/04_鍔熻兘璇︾粏璁捐��/鍔熻兘璇︾粏璁捐��.md)
- 鐗堟湰 V1.0 鈫� V1.1
- 鏂板�炵珷鑺傚ご銆孷1 鍐崇瓥鍙樻洿璁板綍銆嶈〃
- FDD-1 鐧诲綍锛氬垹闄� SSO锛屾柊澧� 1.5 寰�淇℃壂鐮佺櫥褰�
- FDD-2 宸ヤ綔鍙帮細娑堟伅閫氶亾鏀逛负绔欏唴+浼佸井/椋炰功
- FDD-10 淇冮攢锛歵ype 鏋氫妇缂╁噺涓� {MOQ, FULL_REDUCTION}锛屽垹闄� GIFT/BUNDLE 鐩稿叧娴佺▼
- FDD-13 瀹¤�★細Excel 瀵煎嚭 + 鍐峰綊妗� MinIO
- FDD-14 澶栭儴鎺ュ彛锛氬垹 SSO/閭�浠�/鐭�淇★紱鏂板�炲井淇� & 浼佸井/椋炰功 Webhook锛汣A 鎸� e绛惧疂濂戠害
- FDD-15 H5锛歎S-M-01 寰�淇℃壂鐮佺櫥褰曟祦绋�

#### 馃搫 [鏋舵瀯璇勫�＄邯瑕佷笌鎶�鏈�鏂规��.md](d:/Workspace/TRAE/DMS/docs/04_鍔熻兘璇︾粏璁捐��/鏋舵瀯璇勫�＄邯瑕佷笌鎶�鏈�鏂规��.md)
- 鏂板�炵珷鑺傚ご銆孷1 鍐崇瓥鍙樻洿 ADR 琛ヤ竵銆�
- 鏇存柊 ADR-02锛堜績閿�锛�/ ADR-07锛圡ock 濂戠害锛�/ ADR-08锛堥壌鏉冪Щ闄� SSO 鍔犲井淇★級/ ADR-10锛堝墠绔�涓婚�橈級
- 鏂板�� ADR-11锛氶�氱煡娓犻亾锛堢珯鍐�+浼佸井/椋炰功锛�
- 鏂板�� ADR-12锛氫氦浠樿寖鍥达紙浠呮湰鍦� Docker Compose锛�
- 閲岀▼纰戣皟鏁翠负 3.5 涓�鏈堬紙M5 涓婁簯绉诲嚭 V1锛�

#### 馃搫 [鏁版嵁搴撹�捐��_Part1.md](d:/Workspace/TRAE/DMS/docs/05_鏁版嵁搴撹�捐��/鏁版嵁搴撹�捐��_Part1.md)
- users 琛ㄦ柊澧� `wechat_openid`銆乣wechat_unionid`銆乣wechat_bound_at`銆乣sso_service_id`锛堥�勭暀锛�
- 鏂板�� unique 閮ㄥ垎绱㈠紩 `ux_users_wechat_openid`
- tenants 琛ㄦ柊澧� `attrs JSONB` 瀛楁�碉紝绾﹀畾 `primary_color` 瀛樻斁浣嶇疆
- user_login_logs.login_type 娉ㄩ噴锛歏1 浠� PASSWORD / WECHAT / REMEMBER

#### 馃搫 [鏁版嵁搴撹�捐��_Part2.md](d:/Workspace/TRAE/DMS/docs/05_鏁版嵁搴撹�捐��/鏁版嵁搴撹�捐��_Part2.md)
- promotions.promo_type 娉ㄩ噴锛歏1 浠� MOQ / FULL_REDUCTION锛孏IFT/BUNDLE 淇濈暀鏋氫妇浣嶄緵 V2 鎵╁睍
- 鏂板�� CHECK 绾︽潫 `ck_promo_type_v1`
- notifications.channel 娉ㄩ噴锛歏1 浠� INAPP / WECHAT_BOT / FEISHU_BOT
- 鏁版嵁瀛楀吀 PROMO_TYPE 鎷�娉� V1 鍚�鐢ㄨ寖鍥�

#### 馃搫 [API鎺ュ彛娓呭崟.md](d:/Workspace/TRAE/DMS/docs/06_API璁捐��/API鎺ュ彛娓呭崟.md)
- 鏂板�炴枃妗ｅご銆孷1 鍐崇瓥鍙樻洿姒傝�併��
- 鍒犻櫎 `/auth/sso/verify`
- 鏂板�炲井淇＄櫥褰� 4 鎺ュ彛锛歚/auth/wechat/qrcode`銆乣/callback`銆乣/bind`銆乣/unbind`
- 鍒犻櫎 `/integrations/mail/send`銆乣/integrations/sms/send`
- 鏂板�� `/integrations/wechat-bot/push`銆乣/integrations/feishu-bot/push`
- CA 璇存槑鎸� e绛惧疂锛汦RP 閫氱敤 REST
- 淇冮攢 API 澹版槑 promo_type 鈭� {MOQ, FULL_REDUCTION}
- 璁㈠崟鍝嶅簲鍒犻櫎 gifts 瀛楁��
- 鏂板�炲井淇″洖璋冪ず渚�

#### 馃搫 [閮ㄧ讲鏂规�坃DockerCompose涓嶴eed.md](d:/Workspace/TRAE/DMS/docs/07_閮ㄧ讲鏂规��/閮ㄧ讲鏂规�坃DockerCompose涓嶴eed.md)
- 鏂板�炪�孷1 浜や粯鑼冨洿澹版槑銆嶅紩鐢ㄥ潡
- 鎷撴墤鎬昏�堬細Mock 绉婚櫎 SMS/閭�浠讹紝鏂板�炰紒寰�/椋炰功 Webhook + 寰�淇℃壂鐮�
- docker-compose锛歛pi-gateway 鏂板�� WECHAT_APP_ID/SECRET锛沵ock-server 鎸傝浇 mocks/wechat
- Seed 鏁版嵁锛歶sers 2 涓�缁戝畾 wechat_openid锛沺romotions 鍏ㄤ负 MOQ/FULL_REDUCTION
- 绗�涔濈珷鏍囨敞涓哄弬鑰冩枃妗ｏ紙涓嶅睘 V1 浜や粯锛�
- 鏂板�炵��鍗佺珷銆孷1 鍐崇瓥鍙樻洿褰卞搷銆�

---

## 2026-07-17 鈥� 椤圭洰鍒濆�嬪寲 & 棣栬疆鍐崇瓥 D-01 ~ D-23

- PRD 鍏ㄩ噺妯″潡涓�娆℃�т笂绾�
- 涓昏�屼笟锛氬尰鐤楀櫒姊�
- 澶栭儴闆嗘垚鍏� Mock
- 绉诲姩绔�鍏ㄩ噺 H5 閫傞厤
- 澶氱�熸埛 V1 灏卞惎鐢�锛坱enant_id锛�
- 鏁版嵁搴� PostgreSQL 14+
- 閮ㄧ讲褰㈡�� Docker Compose 涓�閿�鍚�鍔�
- Seed 鍏ㄩ噺娴嬭瘯鏁版嵁

---

## 鍚庣画寰呭姙锛圡0 闇�姹傚喕缁撳墠锛�

- [ ] 瀹㈡埛绔�鎻愪緵姝ｅ紡 Logo锛堟垨纭�璁ゆ部鐢ㄦ枃瀛� Logo锛�
- [ ] 瀹㈡埛绔�鎻愪緵浼佸井/椋炰功 Webhook URL锛堢敤浜� Mock 濂戠害瀵归綈锛�
- [ ] 瀹㈡埛绔�鎻愪緵寰�淇″紑鏀惧钩鍙� AppID/AppSecret锛堝彲鍏堢敤 Mock锛�
- [ ] 鐢熶骇鐜�澧冮儴缃� checklist 寰呭啓鍏ユ搷浣滄墜鍐�
- [ ] Seed 鏁版嵁閲忔渶缁堣瘎瀹＄‘璁�

鈥斺�� END 鈥斺��


---

## 2026-07-26 · v3.7.2 · 供应商模块修复

### 交付内容

| 项目 | 说明 |
|---|---|
| **修复 Bug B08** | 供应商等级下拉空 — 新增 Flyway V33 seed `supplier_level` 字典（L1/L2/L3/L4/STRATEGIC）+ Controller 加 tenant 过滤 & DISTINCT 去重 |
| **修复 Bug B09** | 保存供应商后列表无新记录 — `OperationLogAspect` 在 `@PostMapping` 场景 businessId 为 null 导致主事务被 rollback-only 污染，实际未提交。修复：从返回值抽取 id + 独立事务 + null 时跳过 |
| **修复 Bug B10** | `suppliers` DB 缺 `level` 列（Entity 有）— V33 `ALTER TABLE ADD COLUMN IF NOT EXISTS` |
| **补齐测试数据** | V33 seed 30 条演示供应商（10 城市×10 药企品牌，覆盖 5 档等级、7:3 active:inactive） |
| **部署脚本可靠性** | `deploy-fast.ps1` 加 6 次重试 + `$ErrorActionPreference='Continue'` 局部保护，应对阿里云 SSH 短暂拒绝 |

### 端到端验证

- API: `GET /api/dicts/supplier_level/items` 返回 5 项、`POST /api/suppliers` 200 且立即可查
- AI (browser-use + DeepSeek): 登录 → 供应商管理 → 新增 dialog → 5 档下拉 → 填表保存 → 列表 total=32
- 测试环境 `dms_test.suppliers` 数据：SUP-0001~SUP-0030 + 2 条测试记录

### 相关文件

- `backend/src/main/java/com/dms/aspect/OperationLogAspect.java`
- `backend/src/main/java/com/dms/masterdata/controller/SupplierController.java`
- `backend/src/main/java/com/dms/system/controller/DictCrudController.java`
- `backend/src/main/resources/db/migration/V33__supplier_level_and_test_data.sql` (新增)
- `.trae/skills/dms-deploy/deploy-fast.ps1` (retry 硬化)
- `docs/09_测试报告/autotest/v3.7.2_supplier_fix_20260726.md` (新增)
- `.memory/layers/layer3-lessons.md` L25/L26/L27
## v3.7.7 - 2026-07-31 本地修复
### Fixed
- 修复 Flyway V37 checksum mismatch 阻塞测试环境后端启动。
- 修复销售出库部分发货时库存原生查询设置 JPA 锁模式导致的 500。
- 修复同键库存历史重复数据导致库存定位返回多行的 500；空串/NULL 批次和序列号归一匹配，并按库存数量和更新时间稳定取数。
- 确认销售订单新建保存接口 `POST /api/sales-orders` 正常，包含经销商、发货仓库、明细行、单价/税率/数量等字段。

### Verified
- `mvn package -DskipTests=true -Dmaven.test.skip=true -q` 通过。
- `npm run build`（frontend-vue）通过。
- 销售订单/销售出库 API 自动化链路 20 项检查全部通过。
- 浏览器同源保存销售订单返回新订单 ID。

## v3.8.8 (2026-08-07) - 列表页布局规范真正落地：CrudView 接入 platform_button_configs + 菜单按权限过滤

### 背景
v3.8.7 沉淀了 D13 规范和基础设施（platform_button_configs 表、v-has 指令、ListPageLayout 组件），但只迁移了 ApiCallLog / DealerProfileList / ProductMappings 三个示范页。`CrudView` 仍然写死工具栏按钮（导入 / 导出 / 新增）和行内操作（详情 / 编辑 / 删除），行内按钮永远平铺不折叠，没读 `platform_button_configs`，菜单仍按 `menu.js` 硬编码渲染——租户管理员完全没有可视化的页面入口去调按钮 / 搜索字段。本版本把所有列表页统一通过 `CrudView` 走 `platform_button_configs` + v-has，菜单按 `permissionCode` 过滤，admin-vue 维护入口完善。

### 核心改动
1. **CrudView 走 platform_button_configs**
   - 新增 `composables/usePageLayout.js` 拉 `/api/ui/layout/{pageKey}`，内置 5min 内存缓存。
   - `CrudView.vue` 顶部工具栏：必含 search/reset + 可选业务按钮，按 sortOrder 排；`v-has` 过滤；permissionCode 未命中则不渲染、不留白。
   - `CrudView.vue` 行内操作：从 `layout.rowButtons` 读取，>4 个自动折叠到 `更多 ▾`（el-dropdown），平铺时统一 1-4 个，`danger` 位置自动加 `text-danger` 样式和分组线。
   - `operationWidth` 根据按钮数量自适应：1 个=88px、2-3 个=160px、4 个=240px、>4 折叠=200px。
2. **菜单按权限码过滤**
   - `config/menu.js` 39 个菜单项全部加 `permissionCode` 字段（如 `sales_order:view`）。
   - `layout/index.vue` 渲染前 `menuVisible()` 过滤，无权限的菜单不显示，连空分组也自动隐藏。
3. **admin-vue 维护入口完善**
   - `RoleTemplates.vue` 权限点对话框：按 `type` 分组（menu / api / button / other），全选可见、关键字搜索、组级勾选。
   - `Menus.vue` 已有 `permissionCode` 字段（保留）。
   - `UiConfigs.vue` 按钮配置 Tab 已支持 PLATFORM_DEFAULT / TENANT_OVERRIDE 双层切换。

### 后端新增
- Flyway `V64__platform_filter_configs_seed.sql`：26 个 pageKey 灌 keyword 搜索字段 + 业务 select/date 字段（status / dealer / supplier / warehouse / category / level 等），共 83 条 seed。
- Flyway `V65__platform_button_configs_align.sql`：修正历史 seed 的 pageKey 命名不一致（sales-orders → orders / sales-out → sales-outs），补齐 15 个 pageKey 的工具栏 + 行内按钮种子（categories / dealers / warehouses / suppliers / regions / product-prices / product-lines / product-package-levels / product-bundles / contract-apps / authorizations / sales-returns / purchase-returns / inventory-adjustments / surgery-reports / promotions / roles），同步向每个非系统租户的 `rbac_resources` 写入 79 条新 button 资源。
- `UiConfigService.filtersForTenant` 加 ALL fallback：业务前台按租户类型精确匹配，无数据时 fallback 到 `ALL`，避免 V64 灌种因 tenant_type 区分而无法触达。
- 后端编译期 BOM 修复：补写时去掉 UTF-8 BOM（maven-compiler-plugin 拒绝 BOM）。

### 验证
- 26 个 pageKey 拉 `/api/ui/layout/{pageKey}`：toolbar 3-5、row 3-5、filter 1-5 全部有数据。
- admin 账号 `/api/me/permissions` 返回 227 个权限码（v3.8.7 是 148 个，新增 79 个 button 资源）。
- 行内按钮 >4 的 8 个 pageKey（contract-apps / contracts / orders / purchase-orders / sales-outs / receipts / positions / users）走折叠逻辑。
- 部署到测试服务器 8.133.193.238:8082/8083 端到端验证通过。

### 关联决策
- Layer 2 §18 列表页布局规范保持冻结（v3.8.7 入冻结区，本版未变更规范文字）。
- Layer 4 D13：本版本正式落地 D13（CrudView 接入、菜单按权限过滤、admin-vue 维护入口完善）。
- D12 状态：原文 2026-08-06 已因 PowerShell 编码异常丢失；按上下文重写并锁定 deploy-fast 流程。




