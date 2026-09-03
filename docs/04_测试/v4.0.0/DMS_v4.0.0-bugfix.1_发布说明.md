# DMS v4.0.0-bugfix.1 发布说明（测试团队）

- 版本号：v4.0.0-bugfix.1
- 发布日期：2026-08-17
- 部署环境：测试环境 http://43.128.145.141/
- 演示账号：租户 `default` / 账号 `admin` / 密码 `Sh123456`
- 上一版本：v4.0.0（含本次修复前反馈的 7 个问题）

> 请测试同学在收到后先执行 **Ctrl + Shift + R 强制刷新**，清除浏览器缓存后再开始验证。

---

## 一、本次修复重点（7 项）

| # | 模块 | 问题描述 | 修复说明 |
|---|---|---|---|
| 1 | 销退订单 | 页面出现 2 个"新增"按钮 | 通用列表组件去重，内置按钮与后端扩展按钮不再重复渲染 |
| 2 | 销退订单 | 原发货单需输入复杂单据号搜索，体验差 | 恢复为多条件搜索：时间段 + 经销商 + 批号 + 产品编码 + 关键字，从结果中选择发货单带出明细 |
| 3 | 销售订单 | 编辑页结构被改动 | 改回"上表头、下明细"上下结构 |
| 4 | 销售订单 | 出现 2 个"新建"按钮 | 同 #1，统一在通用列表组件去重 |
| 5 | 销售订单 | 不需要指定发货仓库 | 新建/编辑页已移除发货仓库字段 |
| 6 | 销售订单 | 选物料时无法识别/选择 BOM 组套 | 物料选择弹窗新增"类型"列，BOM 组套产品显示橙色"BOM组套"标签 |
| 7 | 销售订单 | 不需要单价和税率，但折扣字段丢失 | 恢复整单折扣（百分比/金额）和行折扣（百分比/金额），正确保存并回显 |

---

## 二、全量变更文件清单

> 本清单为本次发布包中包含的全部文件，按模块分类。所有变更均已部署至测试环境。

### 1. 后端 Java（backend/）

**masterdata 主数据模块（13 修改 + 4 删除）**

修改：
- `backend/src/main/java/com/dms/masterdata/controller/ProductBundleController.java`
- `backend/src/main/java/com/dms/masterdata/controller/ProductPriceController.java`
- `backend/src/main/java/com/dms/masterdata/entity/Product.java`
- `backend/src/main/java/com/dms/masterdata/entity/ProductBundle.java`
- `backend/src/main/java/com/dms/masterdata/entity/ProductBundleLine.java`
- `backend/src/main/java/com/dms/masterdata/entity/ProductLine.java`
- `backend/src/main/java/com/dms/masterdata/service/DealerService.java`
- `backend/src/main/java/com/dms/masterdata/service/HospitalService.java`
- `backend/src/main/java/com/dms/masterdata/service/ProductBundleService.java`
- `backend/src/main/java/com/dms/masterdata/service/ProductCategoryService.java`
- `backend/src/main/java/com/dms/masterdata/service/ProductLineService.java`
- `backend/src/main/java/com/dms/masterdata/service/ProductService.java`
- `backend/src/main/java/com/dms/masterdata/service/RegionService.java`
- `backend/src/main/java/com/dms/masterdata/service/WarehouseService.java`

删除（已从代码库移除）：
- `backend/src/main/java/com/dms/masterdata/controller/ProductPackageLevelController.java`
- `backend/src/main/java/com/dms/masterdata/entity/ProductPackageLevel.java`
- `backend/src/main/java/com/dms/masterdata/repository/ProductPackageLevelRepository.java`
- `backend/src/main/java/com/dms/masterdata/service/ProductPackageLevelService.java`

**order 订单模块（7 修改）**
- `backend/src/main/java/com/dms/order/controller/PurchaseOrderController.java`
- `backend/src/main/java/com/dms/order/controller/SalesOrderController.java`
- `backend/src/main/java/com/dms/order/controller/SalesReturnController.java`
- `backend/src/main/java/com/dms/order/dto/OrderCreateRequest.java`
- `backend/src/main/java/com/dms/order/entity/Order.java`
- `backend/src/main/java/com/dms/order/entity/OrderLine.java`
- `backend/src/main/java/com/dms/order/service/OrderService.java`
- `backend/src/main/java/com/dms/order/service/SalesOrderApprovalCallback.java`
- `backend/src/main/java/com/dms/order/service/SalesReturnApprovalCallback.java`

**其他业务模块（6 修改）**
- `backend/src/main/java/com/dms/inventory/service/StocktakeService.java`
- `backend/src/main/java/com/dms/promotion/entity/Promotion.java`
- `backend/src/main/java/com/dms/promotion/service/PromotionService.java`
- `backend/src/main/java/com/dms/sales/service/SalesOutService.java`
- `backend/src/main/java/com/dms/surgery/controller/SurgeryReportController.java`
- `backend/src/main/java/com/dms/system/controller/LookupController.java`

**tenant 租户模块（4 修改 + 3 新增）**

修改：
- `backend/src/main/java/com/dms/tenant/dto/admin/AdminTenantDTO.java`
- `backend/src/main/java/com/dms/tenant/dto/admin/DealerTenantCreateRequest.java`
- `backend/src/main/java/com/dms/tenant/dto/admin/ManufacturerTenantCreateRequest.java`
- `backend/src/main/java/com/dms/tenant/service/TenantProvisioningService.java`

新增：
- `backend/src/main/java/com/dms/tenant/controller/TenantFeatureController.java`
- `backend/src/main/java/com/dms/tenant/service/TenantFeatureAspect.java`
- `backend/src/main/java/com/dms/tenant/service/TenantFeatureGuard.java`

**common 与 v4 包（2 新增）**
- `backend/src/main/java/com/dms/common/JacksonConfig.java`（新增）
- `backend/src/main/java/com/dms/v4/`（新增整个包，含 v4.0.0 新业务代码）

### 2. 数据库迁移脚本（3 新增）
- `backend/src/main/resources/db/migration/V96__v4_pricing_bom_erp.sql`
- `backend/src/main/resources/db/migration/V97__v4_order_line_discount.sql`
- `backend/src/main/resources/db/migration/V98__v4_settings_and_return_reason_code.sql`

### 3. 前端 PC 端（frontend-vue/，17 修改 + 1 新增）

修改：
- `frontend-vue/src/components/CrudView.vue`
- `frontend-vue/src/components/LinesEditor.vue`
- `frontend-vue/src/components/ListPageLayout.vue`
- `frontend-vue/src/components/ReportPage.vue`
- `frontend-vue/src/components/ResourcePicker.vue`
- `frontend-vue/src/config/menu.js`
- `frontend-vue/src/config/modules.js`
- `frontend-vue/src/directives/has.js`
- `frontend-vue/src/layout/index.vue`
- `frontend-vue/src/router/index.js`
- `frontend-vue/src/utils/format.js`
- `frontend-vue/src/views/LoginLogs.vue`
- `frontend-vue/src/views/OrderCreate.vue`
- `frontend-vue/src/views/SalesReturnEdit.vue`
- `frontend-vue/src/views/order/OrderDetail.vue`
- `frontend-vue/src/views/log-center/ApiLogTab.vue`
- `frontend-vue/src/views/log-center/LoginLogTab.vue`
- `frontend-vue/src/views/log-center/OperationLogTab.vue`

新增：
- `frontend-vue/Dockerfile.test`

### 4. 平台管理后台（admin-vue/，2 修改）
- `admin-vue/src/views/tenant/DealerTenants.vue`
- `admin-vue/src/views/tenant/ManufacturerTenants.vue`

### 5. 文档（docs/）
- `docs/01_需求/v4.0.0/`（v4.0.0 需求文档目录）
- `docs/02_设计/v4.0.0/`（v4.0.0 设计文档目录）
- `docs/04_测试/v4.0.0/DMS_v4.0.0_测试场景.md`
- `docs/04_测试/v4.0.0/DMS_v4.0.0-bugfix.1_发布说明.md`（即本文档）

> 说明：本发布包只包含本次 v4.0.0-bugfix.1 实际涉及的代码、数据库脚本与设计/测试文档。工作区中其他与本次发布无关的历史文件（如 `_prod/`、`tools/copyright_gen/`、`软著申请材料/`、`DMS产品宣传手册/`、`doc/` 等）不属于本次变更，未纳入发布包。

---

## 三、测试重点与建议用例

### 1. 销售订单
- [ ] 列表页只有一个"新建"按钮
- [ ] 新建页为上下结构：上方表头（经销商、订单类型、预计交期、整单折扣、备注），下方明细
- [ ] 表头**不出现**发货仓库、单价、税率
- [ ] 物料选择弹窗中 BOM 组套产品有"BOM组套"标签，普通产品无标签
- [ ] 整单折扣：选"百分比"填 5，保存后详情页回显 5%
- [ ] 行折扣：选"金额"填 20，保存后详情页回显 20
- [ ] 整单折扣按各行金额比例分摊到行
- [ ] 编辑已有订单，折扣字段能正常修改并再次保存

### 2. 销退订单
- [ ] 列表页只有一个"新增"按钮
- [ ] 新增页点击"选择发货单"弹出搜索框
- [ ] 搜索条件：时间段（开始/结束日期）、经销商、批号、产品编码、关键字
- [ ] 选择一张发货单后，自动带出产品编码、名称、规格、单位、批号、序列号、发货数、已退数、可退数
- [ ] "本次退货数"只能输入 0 ~ 可退数之间的整数
- [ ] 批号、序列号可在退货时编辑
- [ ] 提交后退货单创建成功，详情页信息完整

### 3. 回归测试（建议过一遍）
- [ ] 登录/登出
- [ ] 产品、经销商、医院、仓库主数据列表与新增/编辑
- [ ] 采购订单、销售出库、库存盘点
- [ ] 手术报告上报
- [ ] 平台后台（/admin/）租户管理、日志查看
- [ ] 移动端 H5 登录与订单查看

---

## 四、已执行的自动化验证

开发侧已通过端到端脚本验证 **25/25 全部通过**，覆盖：

1. 登录鉴权
2. 销售订单 / 销退订单工具栏按钮数量（均为 1）
3. 发货单多条件搜索接口返回正常
4. 产品 lookup 接口返回 `isBom` 字段
5. 销售订单创建（含整单折扣 PERCENT 5 + 行折扣 AMOUNT 20）
6. 订单详情折扣字段正确回显
7. 销退订单创建
8. 核心列表/详情 API 无 500/404

---

## 五、已知约束与说明

- 本次仅部署测试环境，正式环境待测试通过后再安排推送。
- BOM 组套判定规则：产品在 `product_bundles` 表中存在 `status='active'` 且未删除的组套定义。
- 整单折扣分摊规则：按每行"单价 × 数量"占整单金额的比例分摊，保留 2 位小数。
- 若浏览器仍看到旧版界面，请优先执行 Ctrl + Shift + R 强刷；如仍异常请联系开发核对容器版本。

---

## 六、问题反馈

请在测试管理工具中以 **`[v4.0.0-bugfix.1]`** 为前缀提交 Bug，并附上：
- 复现步骤
- 期望结果 / 实际结果
- 截图或录屏
- 出现问题的账号与时间点

开发将在下一轮迭代中统一修复。
