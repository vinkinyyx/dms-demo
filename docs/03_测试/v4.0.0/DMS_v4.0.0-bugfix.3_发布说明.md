# DMS v4.0.0-bugfix.3 发布说明（业务菜单统一调整）

- 版本号：v4.0.0-bugfix.3
- 发布日期：2026-08-18
- 部署环境：测试环境 http://43.128.145.141/
- 演示账号：租户 `default` / 账号 `admin` / 密码 `Sh123456`
- 上一版本：v4.0.0-bugfix.2

> 请测试同学执行 **Ctrl + Shift + R 强制刷新** 后再验证。

## 一、统一调整范围

覆盖菜单：基础数据、合同管理、订单业务、库存业务、手术与营销、业务报表、产品对码、审批中心、用户与权限。

统一执行 7 项规范：

1. **只读/编辑分离**：列表点击业务编码链接、点击"查看"按钮一律进入只读页；只有点"编辑"才进入编辑页。
2. **工具栏按钮**：逐页检查按钮可用性；**全局去除"批量删除"**按钮。
3. **紧凑布局**：编辑页/只读页每行 3 个字段（`el-col span=8`），标签等宽、输入框等宽 100%，长字段占整行；字段内容完整显示。
4. **筛选下拉**：所有筛选项（状态、分类、经销商、仓库等）下拉均有可选内容，补齐空下拉。
5. **操作日志**：所有只读页底部统一展示"操作日志"时间线/表格（合并审计日志 `operation-logs` 与业务操作日志）。
6. **列表宽度自适应**：数据列由固定 `width` 改为 `min-width`，表格按浏览器分辨率自适应填满，操作列固定右侧无大片留白。
7. **行操作折叠**：列表行操作按钮超过 2 个时，前 2 个平铺、其余收入"更多"下拉。

## 二、实现要点

### 通用组件 `CrudView.vue`
- `canBatchDelete` 全局强制为 `false`，所有走通用列表的模块（25 个）统一移除批量删除。
- `maxFlatRowButtons = 2`，行操作自动折叠；`operationWidth` 按按钮数动态计算。
- 列宽：窄列（≤90px）保留固定宽，其余改为 `min-width`，消除右侧留白（1920/1440/2560 三档分辨率验证 gap=0）。
- 只读/编辑分离修复 `openDetail`：链接与"查看"一律打开只读抽屉/详情页。
- 筛选下拉：
  - 新增 `DICT_FALLBACK` 覆盖各模块状态枚举（订单、BOM、价格、促销、库存等）。
  - `ensureRemoteFilterOptions` 支持 `categories/dealers/suppliers/regions/warehouses` 远程加载。
  - `selectFilterOptions` 支持 `filter.dictType` 字典与 `getDictOptions` 响应式数组，修复库存状态、仓库等空下拉。
- 详情抽屉统一标题为"操作日志"（原"操作记录"），只读时不出现保存按钮。
- 表单 CSS 统一输入框 100% 宽、标签一致；详情 `el-descriptions :column="3"` label 固定 130px。

### 自定义页面（非 CrudView）
- `contract/ContractWorkspace.vue`：合同编码链接跳转只读页；行操作（查看/编辑/提交/删除/撤回）超过 2 个折叠。
- `contract/ContractTemplateList.vue`、`approval/ApprovalTemplates.vue`、`ReportSubscriptions.vue`：行操作超过 2 个统一折叠为"更多"。
- `contract/ContractDetail.vue`：只读页新增"操作日志"卡片（合并审计与业务日志）。
- `utils/dict.js`：`getDictOptions` 返回的响应式数组标记 `__dictType`，支持列表筛选触发字典加载。
- `config/modules.js`：库存状态筛选改为内联枚举，避免异步字典空下拉。

## 三、自动化验证结果

在测试环境（http://43.128.145.141/）使用 Playwright 真实浏览器（headless Chromium）执行：

| 验证项 | 脚本 | 结果 |
|---|---|---|
| 25 个 CrudView 模块行按钮/折叠/列数/无批量删除 | `audit_menus.py` | 25/25 通过 |
| 24 个模块筛选下拉非空 | `audit_filters3.py` | 全部 ok（含修复后的 inventory 仓库/库存状态） |
| 19 个自定义+通用页面加载无 JS 错误 | `smoke_all.py` | 0 pageerror |
| 7 个模块只读页操作日志/无保存按钮 | `detail_logs.py` / `drawer_logs.py` | 全部通过 |
| 表格宽度自适应（1440/1920/2560） | `width.py` | gap=0 |
| 合同编码链接进只读页+日志 | `verify_contract.py` | 通过 |
| v4 业务流程 API 回归 | `v4_api_regression.py` | 3/3 通过 |
| v4 浏览器流程回归 | `browser_v4.py` | 13/13 通过 |

## 四、已知说明

- 合同模板、审批流配置等配置类页面保留各自编辑表单（无独立只读页），行操作已按规范折叠。
- 本次仅部署测试环境，不涉及生产环境。
