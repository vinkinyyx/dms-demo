# DMS PC 端高保真原型

方案 A：静态 HTML 高保真原型，浏览器直接打开即可评审，不依赖构建。

## 打开方式
直接用浏览器打开 `index.html`（原型总览），或逐页打开：

| 页面 | 文件 | 说明 |
|------|------|------|
| 原型总览 | `index.html` | 设计说明 + 页面导航 + 规范速览 |
| 登录页 | `login.html` | 品牌主视觉 + 登录卡片 |
| 业务工作台 | `home.html` | KPI / 快捷入口 / 待办 / 销售趋势 / 近期订单 |
| 数据驾驶舱 | `dashboard.html` | 筛选 + KPI + 柱线趋势 + 品类环图 + TOP 榜 + 术式 + 库存预警 |
| 销售订单列表 | `orders.html` | CrudView 规范：查询重置常驻、工具栏顺序、状态标签、外键显名称 |
| 销售订单详情 | `order-detail.html` | 审批步骤条 + BOM 子母件 + 折扣/代金券分摊 + 价税合计 + 审批时间线 |

## 技术说明
- `assets/dms.css`：配色对齐移动端 H5「藏青琥珀」主题（主色藏青 #2E6BA8、页面底 #F4F7FB、琥珀 #D97706 点缀），含语义色与图表色板。
- 顶部多页签（tags-view，若依风格）：打开的菜单以页签保留，可点击切换 / ×关闭 / 关闭其他 / 刷新，用 localStorage 记忆已打开页签。
- 品牌 Logo 使用项目真实资源 `assets/logo-mark.png`（浅底）与 `logo-mark-white.png`（深底），复制自 `frontend-vue/src/assets/brand/`。
- `assets/app.js`：内置 SVG 图标库 + 侧边菜单 + 顶栏渲染，各页只写主体。
- 图表用 ECharts（CDN），需联网；其余为纯静态。
- 数据均为示例数据。定稿后按 Element Plus + CrudView 规范落到 `frontend-vue`。
- `shot.cjs`：Playwright 逐页截图到 `shots/`（`node shot.cjs`）。
