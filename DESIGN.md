# DMS PC 端设计标准（DESIGN）

> 本文件是 DMS **PC 端（业务前台 `frontend-vue` + 平台后台 `admin-vue`）** 的唯一设计事实源。
> 以后所有 **UI 颜色、字体、间距、圆角、阴影、布局尺寸、主题 / 菜单深浅** 调整，**只改本文件列出的令牌文件**，不要在业务组件里写死色值 / 尺寸。
> 移动端（Vant/H5）特化令牌也在同一份令牌里，以 `--dms-mobile-*` 区分，本文档聚焦 PC。
>
> 适用版本：v4.6.6 起。维护规则见文末「变更流程」。

---

## 0. 一句话原则

- **三层令牌架构**：`基础色板(Layer 1)` → `语义令牌(Layer 2)` → `组件/业务代码(只引用 Layer 2)`。
- 业务 / 组件代码里 **禁止裸色值**（`#1677ff`、`rgb(...)`）和魔法尺寸（`230px`、`56px`），一律 `var(--dms-*)`。
- 换肤 / 深浅切换 **只覆盖令牌**，不改组件。
- Element Plus / Vant 主题通过 `--el-*` / `--van-*` 桥接到 `--dms-*`，不改编排库源码。

---

## 1. 令牌文件地图（改哪里）

| 端 | 作用 | 文件 | 何时改 |
|----|------|------|--------|
| 业务前台 | Layer 1 基础色板（浅色） | `frontend-vue/src/styles/tokens/base-light.scss` | 调品牌主色阶、语义色阶、中性灰阶、图表色板 |
| 业务前台 | Layer 2 语义令牌（**主入口**） | `frontend-vue/src/styles/tokens/semantic.scss` | 调文本/背景/边框/菜单/字体/间距/圆角/阴影/动效/z-index/**PC 布局尺寸** |
| 业务前台 | Layer 1 深色预留 | `frontend-vue/src/styles/tokens/base-dark.scss` | 启用整页暗色时（当前内容区固定浅色，基本不动） |
| 业务前台 | 运行时主题（品牌预设+菜单深浅） | `frontend-vue/src/config/theme-runtime.js` | 增删品牌色预设、改菜单深/浅两套底色、改月亮按钮行为 |
| 业务前台 | EP 组件精修 | `frontend-vue/src/styles/enterprise.scss` | 调按钮/表格/卡片/输入框等组件级视觉 |
| 业务前台 | EP 变量桥接 | `frontend-vue/src/styles/element/runtime.scss` | 调 Element Plus 变量映射 |
| 平台后台 | 单文件令牌(L1+L2+EP 桥接) | `admin-vue/src/styles/tokens.css` | 后台端全部颜色/字体/间距/圆角/阴影 |
| 平台后台 | 运行时主题（菜单深浅） | `admin-vue/src/config/theme-runtime.js` | 后台菜单深/浅两套底色 |
| 两端 | Logo（随主题切换） | `frontend-vue/src/components/DmsLogo.vue`、`admin-vue/src/components/DmsLogo.vue` | 换 Logo 图形/字标 |

> 前台样式引入顺序（`frontend-vue/src/main.js`）：`base-light → semantic → base-dark → element/index → element/runtime → vant → reset → app → enterprise`。
> `enterprise.scss` 最后引入，在令牌之上做组件精修：**改全局基调改令牌，改单个组件视觉改 enterprise.scss**。

---

## 2. 颜色系统

### 2.1 品牌主色（默认「极光蓝」）

| 令牌 | 值 | 用途 |
|------|-----|------|
| `--dms-blue-500` | `#1677ff` | 主色 / 主按钮 / 选中态 / 链接 |
| `--dms-blue-400` | `#4096ff` | 主色 hover |
| `--dms-blue-600` | `#0958d9` | 主色 active/pressed |
| `--dms-blue-50`  | `#e6f4ff` | 主色浅底（选中行、标签底） |
| `--dms-blue-300` | `#69b1ff` | 主色描边 |
| `--dms-blue-700` | `#003eb3` | 深品牌文字 |

完整 10 阶（50→900）见 `base-light.scss`。**换品牌主色 = 改 blue 阶 + 运行时预设（见 §6）。**

### 2.2 语义色

| 语义 | 主令牌 | 浅底 | 加深 |
|------|--------|------|------|
| 成功 success | `--dms-green-500 #52c41a` | `--dms-green-50 #f6ffed` | `--dms-green-600 #389e0d` |
| 警告 warning | `--dms-gold-500 #faad14` | `--dms-gold-50 #fffbe6` | `--dms-gold-600 #d48806` |
| 危险 danger  | `--dms-red-500 #ff4d4f`  | `--dms-red-50 #fff2f0`  | `--dms-red-600 #cf1322` |
| 信息 info    | `--dms-gray-500 #909399` | `--dms-gray-100 #f5f7fa` | — |

业务状态色（单据/审批）：草稿 `--dms-status-draft`(灰)、待审 `--dms-status-pending`(金)、通过 `--dms-status-approved`(绿)、驳回 `--dms-status-rejected`(红)、生效 `--dms-status-effective`(蓝)、终止 `--dms-status-terminated`(深灰)、失效 `--dms-status-expired`(浅灰)。

### 2.3 中性灰阶与文本/背景

灰阶 13 阶 `--dms-gray-50 #fafafa` → `--dms-gray-900 #0d0e10`。常用映射：

| 令牌 | 灰阶/值 | 用途 |
|------|---------|------|
| `--dms-text-1` | gray-800 `#1f2329` | 一级标题/强调正文 |
| `--dms-text-2` | gray-700 `#303133` | 正文 |
| `--dms-text-3` | gray-600 `#606266` | 次要文字 |
| `--dms-text-4` | gray-500 `#909399` | 辅助说明 |
| `--dms-text-placeholder` | gray-400 `#c0c4cc` | 输入占位 |
| `--dms-text-inverse` | `#ffffff` | 深底上的文字 |
| `--dms-bg-page` | gray-100 `#f5f7fa` | 内容区页面底色 |
| `--dms-bg-container` | `#ffffff` | 卡片/表格/弹层底 |
| `--dms-bg-hover` | gray-50 `#fafafa` | 悬停底 |
| `--dms-bg-selected` | blue-50 `#e6f4ff` | 选中行底 |
| `--dms-border-1/2/3` | gray-300/200/100 | 强/中/弱边框 |

### 2.4 图表色板（色盲友好，勿改顺序）

`--dms-chart-1 #1677ff`、`chart-2 #52c41a`、`chart-3 #faad14`、`chart-4 #ff4d4f`、`chart-5 #722ed1`、`chart-6 #13c2c2`、`chart-7 #eb2f96`、`chart-8 #fa8c16`；涨跌 `--dms-chart-up #52c41a` / `--dms-chart-down #ff4d4f`。

---

## 3. 侧边菜单（深 / 浅两套）—— 重点

菜单区配色是**独立令牌组** `--dms-sider-*`，与内容区明暗解耦。右上角月亮 / 太阳按钮 **只切菜单底色**，内容区恒为浅色（`forceContentLight()` 保证）。

| 令牌 | 浅色菜单（默认） | 深色菜单 |
|------|-----------------|----------|
| `--dms-sider-bg` | `#ffffff` | `linear-gradient(180deg,#17233d,#111b2f)` |
| `--dms-sider-bg-deep`（悬停/二级底） | `#f5f7fa` | `rgba(255,255,255,.06)` |
| `--dms-sider-text` | `#4b5563` | `#b8c5d9` |
| `--dms-sider-text-hover` | `#1f2937` | `#ffffff` |
| `--dms-sider-text-active` | 品牌主色 | `#ffffff` |
| `--dms-sider-active-bg`（选中项底） | 主色浅底 `#e8f1ff` | `rgba(22,119,255,.30)` |
| `--dms-sider-badge-bg/text` | `#eef1f6 / #8a94a6` | `rgba(255,255,255,.10) / #b8c5d9` |
| `--dms-sider-border` | `#eef0f4` | `rgba(255,255,255,.06)` |

- **默认（CSS）值**：`semantic.scss`（浅色）。
- **切换 / 深色值**：`theme-runtime.js` 的 `applySiderVars()` 用 `root.style.setProperty` 覆盖；状态挂在 `<html data-sider="light|dark">`，偏好存 `localStorage['dms-theme-preference:sider']`。
- Logo 通过 `<html data-sider>` 自动选深 / 浅版本（`DmsLogo.vue variant="auto"`）。
- 登录页、平台后台（admin）各自实现同一套 `data-sider` 机制（admin 见 `admin-vue/src/config/theme-runtime.js` + `admin-vue/src/styles/tokens.css`）。

> 改菜单深色观感 → 改 `applySiderVars()` 的 `dark` 分支 8 个值；改浅色默认 → 改 `semantic.scss` 的 `--dms-sider-*`。

---

## 4. 字体 / 字号 / 字重

- 字体族：`--dms-font-family`（`-apple-system,"PingFang SC","Microsoft YaHei",...`）；KPI / 数字用 `--dms-font-family-number`；等宽用 `--dms-font-family-mono`。
- 字号阶：`xs 12 / sm 13 / base 14 / md 16 / lg 18 / xl 20 / 2xl 24 / 3xl 30 / 4xl 38`（px）。
- 字重：regular 400 / medium 500 / semibold 600 / bold 700。行高：tight 1.3 / base 1.5715 / relaxed 1.8。
- 语义文字样式（直接引用）：
  - `--dms-text-title-1` 600 24/32（页面大标题）
  - `--dms-text-title-2` 600 20/28（卡片标题）
  - `--dms-text-title-3` 500 16/24（区块标题）
  - `--dms-text-body` 400 14/22（正文）
  - `--dms-text-caption` 400 12/20（辅助说明）
  - `--dms-text-kpi` 700 30/38（仪表盘大数字，数字字体）
- 表格正文 13px（`enterprise.scss` 的 `.el-table`）。

---

## 5. 间距 / 圆角 / 阴影 / 动效 / 层级

**间距**（4px 基准）：`--dms-spacing-1 4 / 2 8 / 3 12 / 4 16 / 5 20 / 6 24 / 8 32 / 10 40 / 12 48 / 16 64`。
语义间距：页边距 `--dms-padding-page`(16)、卡片内边距 `--dms-padding-card`(16)、表单项 `--dms-gap-form`(12)、工具栏 `--dms-gap-toolbar`(8)、卡片栅格 `--dms-gap-card-grid`(24)。

**圆角**（企业克制风，偏小）：`sm 2 / base 4 / md 4 / lg 6 / xl 8 / pill 9999`。
按钮 / 输入 / 卡片 = 4px；弹窗 = 6px；菜单项 = 8px。**约束：不要大圆角**（卡片 ≤ 8px），不拟物。

**阴影**（克制）：`sm`（悬停/轻卡片）、`md`（弹层）、`lg`（弹窗）、`xl`（超大浮层）、`focus`（蓝色双环聚焦）。卡片默认 `box-shadow:none` + 1px 边框（`enterprise.scss`）。

**动效**：时长 instant 0 / fast 100 / quick 150 / medium 200 / slow 250 / slower 300 / slowest 450 ms；默认缓动 `--dms-motion-ease-out cubic-bezier(0,0,.2,1)`。已全局尊重 `prefers-reduced-motion`。

**z-index**：dropdown 1000 / sticky 1100 / fixed 1200 / overlay 1300 / modal 1400 / drawer 1500 / popover 1600 / tooltip 1700 / message 2000 / notification 2100 / loading 3000。

---

## 6. 品牌预设（运行时换肤）

`theme-runtime.js` 内置 4 套预设，右上角可切换，偏好存 `localStorage['dms-theme-preference:preset']`：

| key | 名称 | 主色 | 浅底 |
|-----|------|------|------|
| `blue`（默认） | 极光蓝 | `#1677ff` | `#e6f4ff` |
| `violet` | 星云紫 | `#722ed1` | `#f9f0ff` |
| `green` | 青翠绿 | `#00b96b` | `#f6ffed` |
| `orange` | 日暮橙 | `#fa8c16` | `#fff7e6` |

- 新增预设：在 `THEME_PRESETS`（展示）与 `paletteMap`（实际 primary/hover/active/bg/border/dark）各加一项。
- 切换时同时写 `--dms-color-primary*`、`--dms-blue-*`（覆盖基础阶）、`--el-color-primary*`、`--van-*`，全组件联动。

---

## 7. PC 布局尺寸（v4.6.6 起令牌化）

布局结构尺寸统一在 `semantic.scss` 的「PC 布局尺寸」段，`layout/index.vue` 引用令牌（不再写死）：

| 令牌 | 默认值 | 含义 |
|------|--------|------|
| `--dms-layout-sider-width` | `230px` | 侧栏展开宽度 |
| `--dms-layout-sider-collapsed-width` | `64px` | 侧栏折叠宽度 |
| `--dms-layout-header-height` | `56px` | 顶部栏高度 |
| `--dms-layout-logo-height` | `60px` | 侧栏顶部 Logo 块高度 |
| `--dms-layout-menu-item-height` | `42px` | 一级菜单项高度 |
| `--dms-layout-submenu-item-height` | `38px` | 子菜单项高度 |
| `--dms-layout-menu-icon-size` | `17px` | 菜单图标尺寸 |
| `--dms-layout-content-padding` | `16px` | 内容区内边距 |

> 调整侧栏宽窄 / 顶栏高低 / 菜单项疏密，**只改这 8 个令牌**即可，不要改 `layout/index.vue` 里的数值。

---

## 8. 组件视觉规范（PC，企业沉稳风）

统一组件与详细交互规则见 `AGENTS.md §3` 与 `frontend-vue/src/components/CrudView.vue`；此处只定视觉基调（精修集中在 `enterprise.scss`）：

- **按钮**：圆角 4px、`font-weight:500`、无阴影；主按钮底 = `--dms-color-primary`，hover = `--dms-color-primary-hover`；小按钮 min-height 28px、大按钮 42px。危险操作 danger 样式 + 二次确认。
- **输入 / 选择 / 文本域**：圆角 4px；默认 1px 内描边 `#dcdfe6`，聚焦时 1px `var(--dms-color-primary)`。
- **表格**：1px 边框 `#ebeef5`、圆角 4px、`overflow:hidden`；表头底 `#f5f7fa`、行 hover `#f7faff`、正文 13px；统一 `border stripe size=small`，操作列 `fixed=right`。
- **卡片**：1px 边框 `#e4e7ed`、圆角 4px、**无阴影**；卡片头 padding `12px 16px`、底 `#fafbfc`、标题 600。
- **弹窗**：圆角 6px（`--el-dialog-border-radius`）。
- **分页**：`page-sizes=[20,50,100]`，右对齐。
- **日期时间**：日期 `YYYY-MM-DD`、日期时间 `YYYY-MM-DD HH:mm:ss`，用统一格式化工具，禁止直接渲染 ISO/UTC。
- **引用字段**：外键一律显示编码 + 名称（如 `PRD-B001 测试产品A`），禁止裸数字 ID；枚举显示中文 label。

---

## 9. 常见调整配方（改哪里）

| 需求 | 改什么 |
|------|--------|
| 换品牌主色（全局） | `base-light.scss` 的 blue 阶 + `theme-runtime.js` 的 `paletteMap.blue`；admin 改 `tokens.css` 的 blue 阶 |
| 新增一套品牌色 | `THEME_PRESETS` + `paletteMap` 各加一项 |
| 菜单深色底色 / 文字 | `theme-runtime.js` → `applySiderVars()` 的 `dark` 分支 |
| 菜单浅色默认 | `semantic.scss` 的 `--dms-sider-*`（admin 改 `tokens.css`） |
| 内容区页面底色 / 卡片底 | `semantic.scss` 的 `--dms-bg-page` / `--dms-bg-container` |
| 正文字号 / 字体 | `semantic.scss` 的 `--dms-font-size-*` / `--dms-font-family` |
| 侧栏宽度 / 顶栏高度 / 菜单项高 | `semantic.scss` 的 `--dms-layout-*`（§7） |
| 圆角大小（按钮/卡片/弹窗） | `semantic.scss` 的 `--dms-radius-*` + `enterprise.scss` 的 EP 圆角覆盖 |
| 表格 / 按钮 / 输入框细节 | `enterprise.scss` |
| 图表配色 | `base-light.scss` 的 `--dms-chart-*` |
| Logo 图形 / 字标 | 两端 `DmsLogo.vue`（保持深 / 浅两版随 `data-sider` 切换） |

---

## 10. 变更流程（铁律）

1. **先改令牌，后看效果**：颜色 / 尺寸调整优先在 §1 的令牌文件里改 `--dms-*`，不要在业务 `.vue` 里写死。
2. **两端同步**：业务前台改 `frontend-vue` 令牌后，如后台也受影响，同步 `admin-vue/src/styles/tokens.css`，保持色板一致。
3. **本文件随代码更新（铁律11）**：新增 / 重命名令牌、改默认值、增删品牌预设时，同步更新本文件表格；本文件与令牌实际值必须一致。
4. **改完构建验证**：`cd frontend-vue && npm run build`（admin 同理 `cd admin-vue && npm run build`），并按铁律9 真实浏览器验证登录页、工作台、菜单深/浅切换、后台、移动端。
5. **不破坏三层架构**：组件只引用 Layer 2 语义令牌；Layer 1 色板不被业务代码直接引用（图表色板 `--dms-chart-*` 除外）。
