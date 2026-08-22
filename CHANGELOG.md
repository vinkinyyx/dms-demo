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

