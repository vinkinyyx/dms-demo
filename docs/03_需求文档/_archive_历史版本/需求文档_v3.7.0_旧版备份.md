# DMS 需求文档（合并版）

**当前版本**: v3.7.0
**最后更新**: 2026-07-25

---

## 版本变更日志

### v3.7.0 (2026-07-25) - 主数据补齐（W1-W2）

**核心目标**: 为 v3.7 整合方案补齐产品/包装/组套三层主数据基础，支撑 US-A01/A04/B04/C02 部分需求。

#### 1. 产品线主数据 (US-A01 部分 / P1)

新增独立的产品线层级（BU → 产品线 → 分类 → SKU），与现有 `product_categories` 并存：

- **新表 `product_lines`**：支持层级码、父子关系、BU/产品线/分类层级 (1/2/3)
- **REST API**：`/api/product-lines` 7 个端点
- **业务规则**：编码租户内唯一、层级 1-3 范围校验
- **关联字段**：`products.product_line_id` 可选外键

#### 2. 包装层级主数据 (US-A01 部分 / P1)

新增产品多层包装父子关系，支撑后续扫码追溯 (US-C02)：

- **新表 `product_package_levels`**：4 层结构（运输包装 → 纸箱 → 彩盒 → 单品）
- **字段增强**：GTIN、SN 规则、barcode_format
- **REST API**：`/api/product-package-levels` 8 个端点
- **业务规则**：父子校验、层级递进、数量为正、自动更新产品统计

#### 3. 组套主数据 (US-B04 部分 / P1)

新增产品组套父子结构，支撑设备组套订单 (US-B04)：

- **新表 `product_bundles` + `product_bundle_lines`**：组套主表 + 明细表
- **3 种定价方式**：INHERIT/OVERRIDE/COMPONENT
- **2 种子件类型**：FIXED（固定必选）/ OPTIONAL（可选件）
- **REST API**：`/api/product-bundles` 10 个端点
- **业务规则**：拆套规则、有效期管理、OVERRIDE 必须带价格、最后一固定件不可删除

#### 数据库变更

| Flyway | 主题 | 新表 | 字段扩展 |
|--------|------|------|----------|
| V28 | product_lines | ✅ + 索引 3 个 | `products.product_line_id` |
| V29 | product_package_levels | ✅ + 索引 4 个 | `products.package_levels_count/base_unit` |
| V30 | product_bundles + lines | ✅×2 + 索引 5 个 | `products.is_bundle/bundle_id` |

#### 累计工作量

- 新增 Java 实体：5 个 (ProductLine, ProductPackageLevel, ProductBundle, ProductBundleLine)
- 新增 Java Repository：4 个
- 新增 Java Service：4 个
- 新增 Java Controller：4 个
- 新增 REST 端点：25 个
- 新增集成测试：3 个测试类，11 个测试用例
- 新增 Flyway 迁移：3 个（V28-V30）

#### 后续工作（W3-W22）

- W3-W4: 价格策略表（V31）+ 返利池（V32/V33）
- W5: 特殊价（V34）+ 维修单（V35）
- W6-W7: 库存状态机扩展（V36）+ 扫码（V37）
- W8-W10: 集成接口（V38-V41）
- W11-W22: 第二/三阶段交付

### v3.6.2 (2026-07-24) - bugfix-2026-07-24 集成 + 前端镜像重建 + nginx upstream 修复

**4个产品模块BUG修复（来自另一台机器的修复）**：
1. **BUG #1 新建物料后无法保存**：产品分类选择器未渲染。
   - 修复：`CrudView.vue` 将 `ResourcePicker v-if="f.picker..."` 渲染优先级提高，确保含 picker 字段渲染为选择器
2. **BUG #2 删除提示错误文案**：之前显示"无法删除商品"，应为"无法删除产品"。
   - 修复：`ProductService.java` 删除方法错误信息文案改为"无法删除产品"
3. **BUG #3 详情页点击报错**：API路径错误。
   - 修复：`crud.js` `getDetail(api, id)` URL 去掉 `/detail` 后缀 (`api + '/' + id` 而非 `api + '/' + id + '/detail'`)
4. **BUG #4 详情页只显示分类ID不显示名称**：
   - 修复：① `ProductService.java` 增加 `fillCategoryNames()` 私有方法，列表批量回填 categoryName；② `dict.js` LABELS 增加 `categoryName: '产品分类'` 等 7 项；③ `modules.js` 产品列表新增 `categoryName` 列

**部署缺陷修复**：
- 前端 nginx 配置 upstream 错误：`proxy_pass http://dms-backend:8080` → `http://dms-test-backend:8080`（之前指向不存在的容器名，导致所有业务 API 返回 403 Forbidden，包括操作日志接口）

**部署方式**：JAR 直投（v3.6.0 已采用）部署耗时 3.7 分钟

### v3.6.1 (2026-07-23) - 测试环境部署修复与配置补齐
- **MyBatis-Plus 分页插件配置**：新增 `MybatisPlusConfig.java`，注册 `PaginationInnerInterceptor`（PostgreSQL 方言），修复 `OperationLogService.page()` 调用 500 错误
- **nginx 路由修复**：修改 `nginx-test.conf`，`/api/auth/` 保留 rewrite 到 `/auth/`，其余 `/api/` 路径直通后端，修复所有业务 API 404
- **Flyway V24/V25 修复**：V24 由 `CREATE TABLE audit_logs` 改为 `ALTER TABLE`（V5 已创建），V25 手动执行创建 `operation_log` 表、orders/purchase_orders 审计字段、字典数据初始化
- **数据库 jsonb→text 兼容**：所有 jsonb 列统一转为 text，解决 Hibernate `@Convert(JsonMapConverter)` 写入 VARCHAR 与 jsonb 类型不匹配问题
- **测试环境部署验证**：http://8.133.193.238:8083/ 全量 API 验证通过（登录/字典/产品/订单/经销商/医院/仓库/库存/仪表盘）

### v3.6.0 (2026-07-22) - 15 项批量需求修复：全局下拉化 + 操作日志修复 + 删除引用校验 + 导入导出下载 + 筛选漏斗内联 + 授权字段联动 + 订单字段统一 + 全屏 Drawer + 收发货按钮 + 库存状态筛选 + 手术报台 + 销售岗位 + 登录时长 + 订单追溯报表 + 移动端精简
- **#1 全局下拉选择器改造**：产品类型/经销商/供应商/仓库/分类/医院/产品等所有业务字段全部下拉化（picker 引用 lookups 接口）；purchaseOrders/purchaseReturns 的 supplierId picker 从 `dealers` 改为 `suppliers`，避免选错数据源
- **#2 全局操作日志修复**：OperationLogAspect 编译错误（`SecurityUtils` 不存在 → 改为 `TenantContext`）；扩大 pointcut 范围到 `masterdata/order/authz/contract/inventory` 各 Controller 包；为 10 个 Controller 的 `create/update/delete` 方法显式添加 `@OperationLog` 注解，确保主数据/订单/授权/合同/库存全量记录
- **#3 删除接口错误处理**：新增 `ErrorCode` 枚举（`RESOURCE_IN_USE` / `HAS_REFERENCES` / `CANNOT_DELETE`）；8 个 Service（Product/Category/Dealer/Hospital/Warehouse/Supplier/Order/PurchaseOrder）在删除前调用 `ReferenceCheckService` 校验外键引用；Supplier 改在 Controller 端用 `EntityManager` 执行 SQL 统计引用数
- **#4 导入/导出修复**：CrudView 的 `handleExport` / `downloadTemplate` 从 `window.open` 改为 `fetch` + `Blob` 下载；自动携带 `Authorization: Bearer <token>` Header 解决登录态丢失问题
- **#5 列表筛选漏斗内联化**：CrudView 把 el-dialog 筛选弹窗替换为 el-popover（virtualRef 模式），宽度 220px，定位 bottom-start，列表顶部直接展开筛选
- **#6 授权字段联动**：authorizations 模块 form 已有 dealerId picker（关联 dealers）和 multiselect（product-categories/hospitals），联动逻辑已正确，无需调整
- **#7 采购/销售订单字段**：purchaseOrders/purchaseReturns 的 supplierId 改 picker 'suppliers'；salesOrders 的 dealerId 用 picker 'dealers'（已正确），保证下单数据源与字段语义一致
- **#8 新建/编辑页全屏**：CrudView 的 el-dialog（表单/详情）改为 el-drawer（`direction="rtl"`, `size="100%"`），全屏右侧滑出，编辑大表单体验提升
- **#9 收货入库/销售出库按钮**：salesOuts/receipts 的 statusActions 新增 partial（部分入库/出库）和 confirm（确认入库/出库）按钮；新增 APPROVED 状态映射（已审批待执行）
- **#10 库存状态筛选**：salesOuts/receipts 的 status filter 从 `getDictOptions` 改为显式 6 项枚举（DRAFT/APPROVED/PARTIAL_*/SHIPPED|COMPLETED/CANCELLED），避免字典数据缺漏导致筛不到
- **#11 手术植入报台**：surgeryReports form 已有 dealerId/terminalId/warehouseId 三个选择器，逻辑确认正确
- **#12 销售岗位迁移**：modules.js 新增 positions 配置（api=`/api/sales-positions`），menu.js 已有 positions 菜单，路由 `/m/positions` 已存在
- **#13 登录会话时长**：JwtUtil.accessTokenTtl 默认值改为 `28800000`（8小时）；application.yml `dms.jwt.access-token-ttl` 改为 `28800000`；前端 request.js 增加 401 自动 refresh token + 队列防并发机制
- **#14 订单追溯报表**：modules.js 新增 `reportOrderTrace` 配置（10 列：单号/订单类型/经销商/下单日期/审核日期/出库日期/收货日期/状态/金额/产品数），menu.js 已有 `report-order-trace` 菜单；后端 `BusinessReportController` 新增 `/api/reports/order-trace` 端点
- **#15 移动端精简**：router/index.js 移除非核心移动端路由（inventory/messages/receipt/shipment/report），仅保留 dashboard/orders/create/surgery-reports/create/report-order-trace 4 个核心；新增 MDashboard/MSurgeryReportCreate/MOrderTrace 三个 Vue 视图

### v3.5.1 (2026-07-20) - 后端路由冲突修复 + 前端导出路径修复 + Vue模板语法修复 + 数据字典字段映射修复 + 标准字典数据补充
- **#1 后端路由冲突修复**：14个Controller的导出API路径从`/export`改为`/actions/export`，避免`/{id}`路径将"export"解析为ID导致400错误
- **#2 前端导出路径同步修复**：CrudView.vue中导出和模板下载路径同步改为`/actions/export`和`/actions/export/template`
- **#3 Vue模板语法修复**：Admin.vue修复v-else-if链被el-dialog打断的编译错误；ReceiptEdit.vue修复`<el-tag>`误用`</template>`闭合标签的错误
- **#4 图标导入路径修复**：CrudView.vue中Element Plus图标（Upload/Download/Filter）从`element-plus`改为`@element-plus/icons-vue`导入
- **#5 数据字典字段映射修复**：DictCrudController.items()方法返回字段名从`code/name/seq`改为前端期望的`itemCode/label/value/sortOrder/status`
- **#6 标准业务字典数据补充**：Flyway迁移V19新增5个字典类型（支付方式、发票类型、客户等级、发货方式、产品类型）及对应字典项
- **#7 收货入库优化**：PC端自动带出采购订单产品+应收数量，确认收货/取消收货需二次确认对话框；移动端扫码收货完整流程
- **#8 发货出库优化**：新增SalesOutEdit.vue，自动加载出库单详情，支持部分发货、多次发货、取消发货，确认操作需二次确认
- **#9 列表页时间列支持**：所有业务模块列表添加createdAt和updatedAt列，默认按updatedAt倒序排序
- **#10 首页仪表盘恢复**：Home.vue引入Dashboard组件，显示关键指标与图表
- **#11 订单新建路由修复**：OrderCreate.vue修复从`/order-create/sales`与`/order-create/purchase`切换时mode未更新的问题
- **#12 移动端完整业务闭环**：MHome/MOrders/MReceipt/MShipment/MReport/MLayout完善，支持订单、收货、发货、库存、报表全流程

### v3.5.0 (2026-07-20) - Vue3 前端重构 + 移动端扫码收货 + 数据字典 + 租户管理 + 列表增强
- **#1 Vue3 前端框架重构**：前端由 Vanilla HTML/JS 全面迁移至 Vue3 + Vite5 + Element Plus，组件化架构，PC 端 27+ 业务模块 + 订单向导 + 数据看板 + 后台管理，移动端 7 个 H5 页面
- **#2 移动端扫码收货（H5）**：移动端收货入库改为扫码优先模式，扫描序列号自动匹配收货单并累加数量；支持已扫描列表管理、部分收货（步进器编辑数量）、整单取消 / 部分明细取消
- **#3 数据字典管理**：后台新增"数据字典"模块，支持字典类型与字典项的增删改查（两级树结构），替代原硬编码枚举，表单字段下拉统一从字典取数
- **#4 租户管理模块**：后台新增租户管理，支持多租户的创建、编辑、状态切换；租户间数据完全隔离（tenant_id 维度）
- **#5 销售岗位管理增强**：独立的销售岗位维护页面（左树+右详情），支持岗位层级、绑定销售账号、挂载经销商，一人一位/一商一位互斥校验
- **#6 列表页全面增强**：顶部固定搜索区 + 列头漏斗筛选（传后端跨全量数据）+ 表头排序（custom 后端排序）+ Excel 导入导出按钮预留
- **#7 订单创建页重设计**：独立的 OrderCreate 组件，经销商/仓库/产品选择器 + 明细行编辑 + 合计金额实时计算，提交后回列表
- **#8 订单类型隔离**：销售订单类型下拉标准化（普通/促销/样品/试用/换货/寄售），下单必选，列表可按类型筛选
- **#9 数据刷新修复**：列表操作后自动刷新（新增/编辑/删除/状态变更后 reload），Tab 切换保持数据一致性
- **#10 操作日志全量覆盖**：所有单据（订单/采购/收货/出库/调整/移动）的创建/提交/审批/驳回/取消/收发货 + 主数据（经销商/医院/仓库/产品/供应商/价格）的增删改，全部写入 audit_logs，详情页时间轴展示
- **#11 收货入库管理增强**：PC 端进入编辑页操作收货，支持部分收货（直接编辑数量）、序列号批量录入（多行粘贴）、执行明细留痕；移动端扫码优先交互
- **#12 后台管理中心**：独立 Admin.vue 后台页面，包含系统概览、操作日志、通知管理、数据字典、系统参数、租户管理、菜单配置等模块
- **部署方式**：前端 Docker 多阶段构建（Node 构建 → Nginx 托管），Nginx 反向代理 API + Vue Router history 模式支持，镜像 `dms-frontend-vue:latest`，访问端口 8081

### v3.4.15 (2026-07-19) - 未保存草稿修复 + 全站ID→名称 + 库存移动三方式 + 后台菜单管理
- **#1 未保存单据不再报错**：表单页签切走再切回来时按快照恢复表单（并持久化已输入内容），不再误当列表查询报错；确认系统本就不会"打开即建草稿"
- **#2 授权重开修复 + 全站名称化**：补 `GET /api/authorizations/{id}`；授权列表/详情显示经销商名、授权分类名、终端医院名；销售/销退订单显示经销商名与原单号；采购/采退显示供应商名+仓库名；库存移动显示源/目标仓库名；手术报台显示经销商/医院/仓库名；产品价格显示伙伴名（partnerName）
- **#3 价格保存报错修复**：新增价格时空日期不再报"系统内部错误"（空串转 null + `CAST(? AS DATE)` 替代 `?::DATE` 规避 Hibernate 序号参数解析冲突）；生效/失效日期改日期控件
- **#4 库存移动仓库下拉一致**：`/api/lookups/warehouses` 增加 `deleted_at IS NULL` 过滤（并同步 products/dealers/hospitals/categories），与仓库管理清单一致，不再混入已删除仓库
- **#5 库存移动三种选择方式**：①正向（仓库→物料→批次/序列号）②按批次号/序列号/物料关键字搜索所有仓库库存多选批量移动（`/api/inventory` 扩展 serialNo/keyword 模糊）③按关联收货入库单带出已入库明细。可跨源仓库多选，提交时按源仓库自动拆分为多张移动单
- **#6 销售授权口径一致**：下单校验 `AuthorizationService.check` 由只认单品ID，改为同时认分类授权（category_ids 匹配产品分类），与选料下拉过滤口径统一，避免"下拉能选但下单被拒"
- **#7 测试数据精简重整**：仓库由 150 条软删除精简为每类≤2（共 6 条），既改 seed 口径（V18 迁移）又清理线上；软删除避免破坏既有库存/单据 FK 引用
- **#8 后台菜单管理**：新增 menu_configs 表 + `/api/menu-configs` 接口；后台"系统配置→菜单管理"可调整每个前台菜单的所属分组/排序/显隐；workspace 启动时加载覆盖配置

### v3.4.14 (2026-07-19) - 编码可改 + 表单控件化 + 列表后端筛选 + 部分收货可取消
- **#1/#3 产品/分类编码可更新**：修复 ProductService/ProductCategoryService.update 遗漏 setCode，改编码保存后生效（含改后重名校验）
- **#1/#2 表单可选字段控件化**：UDI追溯等 boolean 字段改开关、number 字段改数字输入、日期用日期控件；授权的分类/终端由逗号手输改为多选下拉；产品分类下拉
- **#4 产品价格关联产品显示编码**：列表关联列改显示产品编码（productCode），产品选择器显示编码+名称
- **#5 部分收货可取消**：cancelReceipt 放开 PARTIAL_RECEIVED（与销售出库 PARTIAL_SHIPPED 对称），已收部分保留、剩余取消
- **#6 列表两级筛选且传后端**：① 列表顶部固定搜索区（按菜单 searchFields 定制）② 表头漏斗行；两者均传后端跨全部数据筛选，可选列用下拉/日期控件（主数据 SpecUtil 动态过滤）
- **#7 库存调整只读 + 时区**：库存调整为一次性凭证（创建即扣库存），去掉编辑按钮改为只读；修复库存调整列表更新时间非北京时间；库存移动标注"原库位库存状态"
- **#8 出库批次/序列号禁手输**：出库明细批次/序列号输入框设为只读，必须通过选择按钮从库存中选取

### v3.4.13 (2026-07-19) - 主数据/取消日志补全 + 时区统一 + 列头筛选 + 库存调整移动重构
- **#1 主数据操作日志补全**：经销商/医院/仓库的 create/update/deactivate、产品 create/deactivate 全部接入 OperationLogService（此前仅供应商/价格/产品 update 有）
- **#2 产品分类查看显示名称**：产品实体增加 transient `categoryName`，get 时按 categoryId 回填分类名称；详情查看态显示分类名而非 ID
- **#3 列表列头漏斗筛选**：列表工具栏新增"🔽 筛选"开关，展开后每列有筛选输入框，对当前页数据即时过滤（在既有升/降序基础上叠加）
- **#4 列表基础筛选项**：筛选行按各菜单列自动生成，支持任意列关键字过滤
- **#5 时间统一北京时区**：新增 DateFmt 工具，所有原生 SQL 列表/详情的时间字段（created_at/updated_at/at_time 等 30+ 处）统一按 Asia/Shanghai 格式化输出，解决 String.valueOf(Timestamp) 绕过 Jackson 时区配置导致非北京时间问题
- **#6 序列号收货批量录入**：收货序列号由逐个 prompt 改为多行文本框弹窗，可从 Excel 一次性粘贴一批，实时计数与查重；收货单 PARTIAL_RECEIVED 状态新增"继续收货"按钮，退出后仍可录入剩余（出库 PARTIAL_SHIPPED 同步）
- **#7 单据编号链接**：销售/采购订单、收货入库、销售出库、库存调整、库存移动的单据编号列做成链接，点击直接打开详情页
- **#8 取消动作记日志**：收货/销售出库的 cancel-draft 取消动作补写操作日志
- **#9 详情带出源单表头**：出库详情带出关联销售订单表头（单号/经销商/类型/状态/金额/时间），入库详情带出关联采购订单表头（单号/供应商/状态/金额/时间）作参考
- **#10 手术报台日期控件**：_renderFormBody 补 type=date 分支，渲染原生 `<input type="date">`（此前所有日期字段被渲染为纯文本框）
- **#11 库存调整重构**：POST 接受前端扁平结构（含批次/序列号/数量），补 GET/{id}/detail 详情接口 + 操作日志；前端明细录入编辑器支持批次/序列号/数量；修复打开查看/编辑报错（缺详情端点）
- **#12 库存移动重构**：源仓→目标仓，POST 扁平结构含批次/序列号/数量，补详情接口 + 操作日志 + 明细录入编辑器；修复查看/编辑报错
- **#13 后台菜单浅色**：admin.html 侧栏由深蓝灰 #2b3a52 改为浅色白底(#ffffff)深字，选中项浅蓝高亮

### v3.4.12 (2026-07-19) - 收发货执行明细 + 单据号规范 + 主数据日志 + 详情页修复
- **#1 主数据编辑不保存**：product_prices 的 update 补齐 currency/生效日期/失效日期字段；供应商全字段 COALESCE 避免误清空
- **#2 主数据无操作日志**：suppliers / product_prices / products 的 create/update 接入 OperationLogService
- **#3 产品分类必选**：产品表单 categoryId 改为下拉选择（lookups/categories）+ 必填；V17 补充监护设备/康复器械分类，无分类产品自动补默认分类
- **#4 单据号规范**：所有单据统一 `PREFIX-YYYYMMDD-00001` 连续流水；新增 doc_no_sequences 序列表，DocNoGenerator 改为 DB 原子自增（PO/RPO/RK/RRK/CK/RCK/SO）
- **#5 采退/销退红字单**：确认采购审批(UI 路径)自动建 RK 链路完整；红字 RK/CK 编号 RRK-/RCK- 前缀
- **#6/#7 详情页修复**：① 供应商/仓库/经销商显示名称（后端 poDetail join suppliers/warehouses；前端名称键 dealerName 修正）；② 明细不再显示 [object Object]（跳过 type=lines 字段，用明细表渲染）；③ 详情页展示操作日志时间轴
- **#8 分次收货明细留痕**：新增 receipt_execution_lines / sales_out_execution_lines 执行明细表，每次每批次/序列号单独一条记录；详情页"收/发货执行记录"表格展示第N次、批次、序列号、数量、操作人、时间；明细行显示"待处理量=应收−已收"
- **#B 序列号逐件维护**：序列号产品在收发货页拆多行逐个录入（收货手工批量输入、出库从合格库存多选），每序列号一条 qty=1
- **#9 后台菜单**：admin.html 侧栏底色由 #111827 调为柔和深蓝 #2b3a52，文字/分组标题在深底上清晰

### v3.4.11 (2026-07-19) - 采购建单根因修复 + 序列号逐件收发 + 操作日志 + 红字单
- **#1 采购审批不建 RK 根因**：UI 新建采购单未选仓库 → PO.warehouse_id=NULL → RK 仓库空 → 收货执行报错。修复：① 采购单入库仓库**必填**；② AutoDocGenerator 无仓库时用默认仓库兜底；③ 清理历史 NULL 仓库数据
- **#2 序列号逐件收货**：execute 接口支持同一明细行**多条子录入**（一行序列号产品可录 N 个序列号，每条 qty=1）；序列号查重
- **#2 多行单部分收货**：某些行本次量填 0/不录入 → 跳过该行不报错，单据保持 PARTIAL_RECEIVED
- **#3 销售出库**：同机制多子录入 + 多行部分发货
- **#4 操作日志**：新增 OperationLogService + `/api/operation-logs`；订单/采购单/收货/出库的创建/提交/审批/驳回/取消/收发货全部记录；详情页新增"🕓 操作记录"时间轴
- **#5 红字单**：修复采购单 create 未写 is_red 列（采退红字 RK）；补 Order 实体 is_red/ref_order_id 映射（销退红字 CK）
- **#6 DocNo 冲突修复**：单据号追加毫秒尾数，避免与历史种子数据撞号导致 500
- 批次录入子页支持"本次量"分次填写 + 来源单号 + 状态徽章 + 取消剩余

### v3.4.10 (2026-07-19) - 分次收发货 + 授权前置过滤修正 + 列表排序
- **供应商/价格菜单** 并入"主数据管理"组（renderMenu 合并同名分组）
- **价格维护关联物料**：productId 字段改为 product-picker 弹窗选择（编码/名称搜索）
- **销售订单订单类型**：新建时必选（普通/促销/样品/试用/换货/寄售），去掉硬编码 PURCHASE
- **授权前置过滤修正**（根因）：LookupController /products?dealerId 的授权 SQL 与 AuthorizationService.check 语义对齐 —— 支持 product_id 精确匹配 / product_id=NULL 通配 / category_ids 范围三种；DISTINCT 去重，同一产品不再重复出现
- **列表表头排序**：所有列可点击升/降序，默认 updatedAt desc；reloadList 带 sort 参数
- **分次收货**（收货入库）：executeReceipt 支持"本次收货量"< 预期，累计 received_qty；未收完 → PARTIAL_RECEIVED，收满 → COMPLETED；可"取消剩余"
- **分次发货**（销售出库）：executeSalesOut 同机制（shipped_qty 累计，PARTIAL_SHIPPED / COMPLETED / 取消）
- 批次录入子页新增"应收/发、已收/发、本次量"列 + 来源单号 + 状态徽章
- 出库批次/序列号选择器已按"本仓库 + 合格库存"过滤（v3.4.4 起）
- 采购审批自动建 RK：真实验证通过（审批返回 autoCreatedReceiptId，收货入库列表可见 DRAFT）

### v3.4.9 (2026-07-19) - 主数据补齐 + 交易数据重建
- **销售下单**：修复 dealerId 无法提交 bug（body 结构从嵌套 `{order:{}, lines:[]}` 改为平铺 `{dealerId, orderType, lines:[]}`）
- **产品选择后**显示"可用库存"列（异步查 /api/inventory/available-lots + qualifieds sum）
- **产品价格独立**：新增 product_prices 表（采购价 + 销售价 + 多维度 GLOBAL/DEALER/SUPPLIER），从产品主数据剥离
- **产品单位** unit_type 字段（EA/SET），产品选择器与下单页显示单位
- **供应商主数据**：新增 suppliers 表（10 条测试数据） + /api/suppliers CRUD + /api/lookups/suppliers 下拉
- **采购下单** 改为"选中供应商"下拉（不再手工输入名称），提交时携带 supplierId + supplierName
- **测试数据重建**：清空全部交易单，注入 30 天完整业务链（90 张销售订单 + 30 张采购订单 + 8 张 RK 草稿 + 16 张 CK 草稿）
- **销售用户扩充**：新增 sales1-sales5 五个测试账号（role=sales，密码 Sh123456）
- **报表字段大扩展**：五张报表 cols 从原 4 列 → 8-12 列，含明细/经销商级别/区域/岗位/账龄/单位/批次数/周转天数等
- 报表首字段 ID → 变链接，点击跳详情页
- **产品价格/供应商菜单**：新增 "主数据 · 价格供应" 菜单组

### v3.4.8 (2026-07-19) - Tab 化下单/执行 + 图表本地化 + 岗位候选用户过滤
- 销售/采购订单**新建** → 走 openOrderCreateTab 子页（不再跳独立 order-create.html，也不弹窗）：经销商/仓库/明细行/产品选择器/合计金额；创建成功后回列表 Tab
- 销售出库/收货入库**执行发货/收货** → 走 openLotFillTab 子页（不再弹 Modal）：产品明细 + 批次/序列号录入 + 从合格库存选批次；执行成功后回列表 Tab
- 列表内**关联字段链接**行为改为跳"资源详情页"（openDetailTab → openFormTab view）而非列表 filter
- 采购订单列表移除"收货入库"按钮（审批后自动生成 RK 草稿，需去"收货入库"页操作）
- 采购审批自动建 RK 单：确认 AutoDocGenerator.createReceiptForPurchaseOrder 已闭环，测试通过
- 岗位绑定销售账号 → 新增 `/api/sales-positions/candidate-users` 只返回 role=sales 用户 + 各自的所属岗位映射
- 首页仪表盘 6 张图表**筛选后无数据**修复：Object dispose 旧 echarts 实例 → renderHome 重写 innerHTML 后 chart canvas 引用失效
- ECharts CDN 无法访问（境内网络）→ 下载到 /root/dms/frontend/echarts.min.js 本地服务
- UI 再紧凑：内容 padding 12/14、卡片 padding 12/16、KPI 值 20px、表格 6/10px、按钮高 28px、页面标题 18px

### v3.4.7 (2026-07-19) - 企业风 UI + 多标签表单页 + 岗位绑定修复
- 圆角标准企业风：按钮 6px / 卡片 8px / Modal 8px / 输入框 6px（原 M3 Pill 28px 太占地方）
- 新建/编辑/详情 → **新开一个 Tab 呈现子页面**（不再弹窗）；创建成功后关闭当前 Tab 回列表 Tab
- 详情页与新建页字段/位置完全一致（只读态），关联字段变链接；**底部新增操作/审批/自动执行历史轨迹**
- 首页仪表盘筛选：时间范围（今天/本周/本月/本季度/本年/全部）+ 经销商 + 订单状态 + 类型，7 个 API 支持动态刷新
- 移除销售架构树菜单（已完全被销售岗位取代）
- 岗位绑定 Bug 修复：
  - 一位一人业务规则：绑定新用户前先清空该岗位已绑其他用户；userId=null 允许解绑
  - 一位挂多个经销商 / 一经销商只挂一位；挂载语义改为**全量替换**，未勾选自动解挂
  - 前端下拉/复选：已被其他岗位占用的销售/经销商置灰不可选 + 显示所属岗位名
  - 岗位列表 API 补充 `boundUserId`；Dealer Entity 补上 `salesPositionId` 映射
- 数据流验证：采购审批仅创建 RK 草稿（不自动执行收货入库），需要人工去"收货入库"页点击执行

### v3.4.6 (2026-07-19) - 多标签 Tabs + 关联链接 + Fixed 头部布局
- 顶部/侧栏固定 fixed，内容区独立滚动，滚长表格不再顶死头部
- 每打开一个页面新增一个 Tab（首页 pinned），保留页面状态（DOM 快照/滚动位置）
- 列表内关联字段（经销商/仓库/来源订单/来源采购单/产品）变链接 → 点击新开 Tab + filter 过滤
- 销售出库/收货入库列表**含来源单号字段+链接**（sourceOrderId/sourceOrderCode，sourcePoId/sourcePoCode）
- 销售岗位新建/编辑改成右侧面板**正式表单**（必填校验、保存/取消）
- 岗位挂经销商/绑定账号 200 分页限制修复（PageQuery 上限提到 1000）
- 移动端自适应：CSS 断点 480/900/1024px，侧栏抽屉、Modal 全屏、KPI 双列

### v3.4.5 (2026-07-19) - 单号前缀 + 授权前置过滤 + M3 UI 落地
- 单号前缀标准化：SO/RSO/PO/RPO/CK/RK（历史数据 V14 迁移全部改名）
- 销售订单产品选择器：选完经销商后，只显示授权分类下的产品（前置过滤）
- 报表增强：每份报表返回 8-12 个业务字段（编码/规格/账龄/批次数/周转天数等）
- 销售岗位维护页面：左侧岗位树 + 右侧详情面板 + 绑定账号/挂载经销商
- UI 全面升级 Material Design 3：白底侧栏 + Google Blue + Pill 按钮 + 卡片阴影
- Bug 修复：sales-outs/receipts readonly 但也应显示操作按钮

### v3.4.4 (2026-07-19) - 详情接口 + 自动建单闭环
- 新增 GET /api/sales-outs/{id}/detail 和 /api/receipts/{id}/detail
- 订单/采购单审批自动生成对应出/入库草稿（AutoDocGenerator 服务）
- V13 迁移：注入 700 订单 + 57 采购 + 34 手术报台 + 20 序列号库存
- 库存查询 join 产品/仓库/经销商，返回丰富字段
- Bug 修复：`::text` cast 与 Hibernate 参数占位符冲突 → 改用 CAST(x AS TEXT)

### v3.4 (2026-07-19) - 岗位模型 + 自动建单 + M3 首页仪表盘
- R1 出库批次/序列号库存下拉选择（不再手填）
- R2 库存严格按批次/序列号分片
- R3 修复库存查询 500 错误
- R4 销退关联校验（原正向单+产品）
- R5 订单审批 → 自动生成出/入库草稿 → 执行/取消
- R6 首页仪表盘（8 KPI + 6 图表）
- R7 销售岗位模型（4 层 29 岗位）
- R8 Material Design 3 + Google Cloud Console 布局
- R9 需求目录合并

### v3.3 (2026-07-19)
- 批次/序列号严格管理
- 三角色权限（admin/sales/dealer）
- 销售组织架构（层级）
- 手术植入报台
- 5 张常规业务报表

### v3.2 (2026-07)
- 授权改产品分类
- 库存状态机 QUALIFIED/PENDING/DEFECTIVE
- 销退/采退 + 红字入/出库

### v3.1 (2026-06)
- 分页规范
- 数据授权重构
- Excel 导入导出

### v3.0 (2026-04)
- 采购/销售单据完全拆分
- 状态驱动
- 低代码字段配置

### v2.0 (2025)
- 合同签章 + UDI 追溯 + 批量导入 + 邮件审批

### v1.0 (2025 初)
- 41 项架构决策 + 94 用户故事 + 66 张表 + 60+ API

---

## 核心业务规则

### 单号前缀规范（v3.4.12 起：PREFIX-YYYYMMDD-连续流水）
| 类型 | 前缀 | 示例 |
|---|---|---|
| 销售订单 | SO- | SO-20260719-00001 |
| 销退订单 | RSO- | RSO-20260719-00001 |
| 采购订单 | PO- | PO-20260719-00001 |
| 采退订单 | RPO- | RPO-20260719-00001 |
| 销售出库 | CK- | CK-20260719-00001 |
| 采购入库(收货) | RK- | RK-20260719-00001 |
| 红字采购入库 | RRK- | RRK-20260719-00001 |
| 红字销售出库 | RCK- | RCK-20260719-00001 |

> 由 `doc_no_sequences` 序列表按（租户+前缀+日期）原子自增，保证同租户同日单号连续、并发不撞号。

### 库存状态机
```
入库 → PENDING（待检）
     ↓ 质检合格
     → QUALIFIED（可出库）
     ↓ 质检不合格
     → DEFECTIVE（不可出库）
```

### 出入库批次录入规则
| 场景 | 批次录入方式 |
|---|---|
| 采购入库(RK) 执行时 | **手工录入**（唯一手填场景） |
| 销售出库(CK) 执行时 | 弹窗从合格库存下拉选择 |
| 手术上报执行时 | 弹窗选序列号 |
| 红字销售出库 | 只能从原单批次中选 |

### 数据权限
- **admin**：全部数据
- **sales**：递归下级岗位负责的所有经销商
- **dealer**：绑定的唯一经销商

### 审批联动
```
销售订单 approve → 自动创建销售出库(CK-)草稿
采购订单 approve → 自动创建收货入库(RK-)草稿
销退订单 approve → 自动创建红字销售出库草稿  
采退订单 approve → 自动创建红字采购入库草稿

草稿 execute → 弹窗填批次 → 库存变化 → COMPLETED
草稿 cancel  → CANCELLED (库存不变)
```

### 分次收发货与执行明细留痕（v3.4.10 → v3.4.12）
- **分次收货/发货**：单据可多次执行，`received_qty` / `shipped_qty` 累计已处理量；未处理完为 `PARTIAL_RECEIVED` / `PARTIAL_SHIPPED`，处理满为 `COMPLETED`，可"取消剩余"
- **待处理量**：明细行展示"待处理量 = 应收/发量 − 已收/发量"，每次收完后自动扣减
- **执行明细留痕**：每一次、每一个批次/序列号在 `receipt_execution_lines` / `sales_out_execution_lines` 各留一条记录（第 N 次 / 批次 / 序列号 / 数量 / 操作人 / 时间），详情页"🔖 收/发货执行记录"完整展示（如第一次收 ABC×5、第二次收 DEF×5 两条独立记录）
- **序列号逐件维护**：序列号产品在收发货录入页拆多行逐件维护，每序列号一条 `qty=1`（收货手工批量输入 / 出库从合格库存多选）

### 操作日志（v3.4.11）
- 所有单据的创建 / 提交 / 审批 / 驳回 / 取消 / 收发货，以及主数据（供应商 / 产品价格 / 产品）的创建 / 编辑，全部写入 `audit_logs`
- 详情页"🕓 操作记录"时间轴展示，接口 `GET /api/operation-logs?resourceType=&resourceId=`

### 授权前置过滤（v3.4.5）
- 用户创建销售订单先选经销商
- 产品选择器只显示该经销商授权分类下的产品
- API：`GET /api/lookups/products?dealerId=X`

---

## 累计需求全景

### 主数据（11 类）
经销商 · 医院/终端 · 产品（批次/序列号/单位）· 产品分类（下拉必填）· 供应商 · 产品价格（多维度）· 仓库 · 销售岗位 · 用户（三角色）· 字典 · 授权

### 业务单据（10 类）
- 销售订单 SO
- 销退订单 RSO
- 采购订单 PO
- 采退订单 RPO
- 销售出库 CK
- 采购入库 RK
- 手术植入报台
- 库存调整
- 库存移动
- 授权

### 报表体系（v3.4.5 字段丰富）
- 销售业绩排行：编码/级别/订单均值/审批数/最近下单
- 产品销售 TOP：编码/规格/单位/订单数/经销商数/均价
- 库存周转：合格/待检/不合格分状态/批次数/周转天数
- 手术报台统计：编码/级别/经销商数/医生数/平均植入/最近手术
- 应收账款：编码/级别/账龄 30/60/90/最早未收日期

### 仪表盘（v3.4）
8 KPI（销售额/订单/经销商/产品/库存三态/手术）+ 6 图表（趋势/占比/TOP5/漏斗/医院/活跃度）

### 移动端扫码收货（v3.5.0）
- **扫码优先**：进入页面自动聚焦扫码输入框，扫描/输入序列号后自动匹配收货单并累加收货数量
- **已扫描列表**：实时显示已扫描的序列号清单，可单独移除，底部显示合计件数
- **部分收货**：明细行支持步进器调整本次收货数量（0 ≤ 本次 ≤ 待收），未收完单据保持 PARTIAL_RECEIVED
- **取消收货**：支持"取消整单"和"取消部分明细"两种模式，已收部分保留、剩余取消
- **PC 端对称**：PC 端进入收货编辑页操作，支持序列号批量录入（多行文本粘贴）、部分收货、执行明细留痕

### 前端技术栈（v3.5.0 起）
- PC 端：Vue 3 + Vite 5 + Element Plus + Vue Router + Pinia
- 移动端 H5：Vue 3 + Vant 4 + Vue Router
- 构建部署：Docker 多阶段构建（Node build → Nginx serve），Nginx 反向代理 /api 到后端，history 模式路由支持
- 访问地址：PC http://{server}:8081/ ，移动端 http://{server}:8081/m/
