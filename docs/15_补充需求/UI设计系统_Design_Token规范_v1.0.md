# DMS UI 设计系统 — Design Token 详细规范与 Element Plus / Vant 主题定制方案

> **文档编号**：NEW-19  详细规范
> **版本**：v1.0
> **创建日期**：2026-08-12
> **适用范围**：业务前台 PC（frontend-vue）、平台后台（admin-vue）、移动端 H5（frontend-vue/mobile）
> **关联文档**：`15_补充需求/DMS功能缺口与优化需求评估_v3.12.md` NEW-19、NEW-20、NEW-21、NEW-23
> **目的**：建立 DMS 统一设计语言，作为后续所有 UI 改造的地基；通过 Design Token + 主题变量覆盖实现三端视觉一致、可维护、可扩展（含暗色模式与租户品牌定制预留）

---

## 目录

1. [总览与分层架构](#1-总览与分层架构)
2. [颜色系统](#2-颜色系统)
3. [字体系统](#3-字体系统)
4. [间距系统](#4-间距系统)
5. [圆角系统](#5-圆角系统)
6. [阴影系统](#6-阴影系统)
7. [边框系统](#7-边框系统)
8. [动效系统](#8-动效系统)
9. [Z-index 层级](#9-z-index-层级)
10. [Element Plus 主题定制方案（PC + 平台后台）](#10-element-plus-主题定制方案pc--平台后台)
11. [Vant 主题定制方案（移动端 H5）](#11-vant-主题定制方案移动端-h5)
12. [CSS 变量统一注册（:root）](#12-css-变量统一注册root)
13. [暗色模式预留](#13-暗色模式预留)
14. [租户品牌定制方案](#14-租户品牌定制方案)
15. [落地步骤与里程碑](#15-落地步骤与里程碑)
16. [验收清单](#16-验收清单)
17. [附录：Token 命名规范](#17-附录token-命名规范)

---

## 1. 总览与分层架构

### 1.1 三层 Token 架构

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Component Tokens（组件令牌）                        │
│   --el-button-bg-color = var(--dms-color-primary)            │
│   仅组件内部使用，不对外暴露                                   │
├─────────────────────────────────────────────────────────────┤
│ Layer 2: Semantic Tokens（语义令牌）★ 业务开发唯一使用入口    │
│   --dms-color-primary / --dms-color-success / --dms-text-1   │
│   表达"用途"而非"色值"，主题切换时只改 Layer 1                │
├─────────────────────────────────────────────────────────────┤
│ Layer 1: Base Tokens（基础令牌）★ 调色板，唯一色值来源        │
│   --dms-blue-500: #1677ff; --dms-gray-100: #f5f7fa;          │
│   暗色模式 / 租户品牌定制 仅覆盖此层                          │
└─────────────────────────────────────────────────────────────┘
```

**核心铁律**：
- 业务代码、组件代码**只引用 Layer 2 语义令牌**，禁止直接引用 Layer 1 基础令牌或硬编码色值
- 主题切换（暗色/租户品牌）**只修改 Layer 1**，Layer 2 通过 `var()` 自动联动
- Layer 3 组件令牌由 Element Plus / Vant 内部消费，对外不可见

### 1.2 文件组织结构

```
frontend-vue/src/styles/
├── tokens/
│   ├── base-light.scss       # Layer 1 浅色调色板（默认）
│   ├── base-dark.scss        # Layer 1 暗色调色板（预留）
│   ├── semantic.scss         # Layer 2 语义令牌（含 light/dark 两套映射）
│   └── component.scss        # Layer 3 组件令牌（覆盖 Element Plus 变量）
├── element/
│   └── index.scss            # Element Plus 主题变量覆盖
├── vant/
│   └── index.scss            # Vant 主题变量覆盖
├── mixins/
│   ├── flex.scss             # 布局 mixin
│   ├── scrollbar.scss        # 滚动条 mixin
│   └── truncate.scss         # 文本截断 mixin
├── reset.scss                # 基础重置
├── variables.scss            # SCSS 变量（用于计算，非运行时）
└── index.scss                # 统一入口（main.js 引入）
```

---

## 2. 颜色系统

### 2.1 主色（Brand Color）

DMS 品牌主色采用**蓝色系**，呼应医疗/科技行业信任感，与 Element Plus 默认主色 `#409eff` 区分以建立品牌识别。

| Token | 色值 | 用途 |
|---|---|---|
| `--dms-blue-50` | `#e6f4ff` | 主色背景（hover/tag） |
| `--dms-blue-100` | `#bae0ff` | 主色浅背景 |
| `--dms-blue-200` | `#91caff` | 主色描边浅 |
| `--dms-blue-300` | `#69b1ff` | 主色描边 |
| `--dms-blue-400` | `#4096ff` | 主色 hover |
| `--dms-blue-500` | `#1677ff` | ★ **主色（默认）** |
| `--dms-blue-600` | `#0958d9` | 主色 active |
| `--dms-blue-700` | `#003eb3` | 主色深 |
| `--dms-blue-800` | `#002c8c` | 主色更深 |
| `--dms-blue-900` | `#001d66` | 主色最深 |

**语义映射**（Layer 2）：

```scss
--dms-color-primary: var(--dms-blue-500);
--dms-color-primary-hover: var(--dms-blue-400);
--dms-color-primary-active: var(--dms-blue-600);
--dms-color-primary-bg: var(--dms-blue-50);
--dms-color-primary-border: var(--dms-blue-300);
```

### 2.2 语义色（Semantic Color）

| 语义 | Token | 默认色值 | 浅背景 Token | 用途 |
|---|---|---|---|---|
| 成功 | `--dms-color-success` | `#52c41a` | `--dms-color-success-bg: #f6ffed` | 保存成功、状态正常、库存充足 |
| 警告 | `--dms-color-warning` | `#faad14` | `--dms-color-warning-bg: #fffbe6` | 待审批、库存预警、临期提醒 |
| 危险 | `--dms-color-danger` | `#ff4d4f` | `--dms-color-danger-bg: #fff2f0` | 删除、报错、库存不足、状态异常 |
| 信息 | `--dms-color-info` | `#909399` | `--dms-color-info-bg: #f4f4f5` | 中性提示、辅助说明 |

**业务状态色专用映射**（订单/审批状态）：

```scss
--dms-status-draft: var(--dms-gray-500);       // 草稿 - 灰
--dms-status-pending: var(--dms-color-warning); // 待审批 - 橙
--dms-status-approved: var(--dms-color-success);// 已通过 - 绿
--dms-status-rejected: var(--dms-color-danger); // 已驳回 - 红
--dms-status-effective: var(--dms-blue-500);    // 已生效 - 蓝
--dms-status-terminated: var(--dms-gray-700);   // 已终止 - 深灰
--dms-status-expired: var(--dms-gray-400);      // 已过期 - 浅灰
```

### 2.3 中性色（Neutral Color）

中性色采用 13 阶灰阶（参考 Ant Design），覆盖文本、背景、边框、分割线、禁用态。

| Token | 色值 | 用途 |
|---|---|---|
| `--dms-gray-50` | `#fafafa` | 最浅背景 |
| `--dms-gray-100` | `#f5f7fa` | 页面背景、表格斑马纹 |
| `--dms-gray-200` | `#ebeef5` | 边框浅、分割线 |
| `--dms-gray-300` | `#e4e7ed` | 边框 |
| `--dms-gray-400` | `#c0c4cc` | 占位符、禁用文本 |
| `--dms-gray-500` | `#909399` | 辅助文本、次要信息 |
| `--dms-gray-600` | `#606266` | 正文次要 |
| `--dms-gray-700` | `#303133` | 正文主体 |
| `--dms-gray-800` | `#1f2329` | 标题 |
| `--dms-gray-900` | `#0d0e10` | 最深文本 |

**语义映射**（Layer 2）：

```scss
/* 文本 */
--dms-text-1: var(--dms-gray-800);   /* 主标题 */
--dms-text-2: var(--dms-gray-700);   /* 正文主体 */
--dms-text-3: var(--dms-gray-600);   /* 次要文本 */
--dms-text-4: var(--dms-gray-500);   /* 辅助说明 */
--dms-text-placeholder: var(--dms-gray-400); /* 占位符 */
--dms-text-disabled: var(--dms-gray-400);    /* 禁用 */
--dms-text-inverse: #ffffff;                 /* 反白文本（深色背景上） */

/* 背景 */
--dms-bg-page: var(--dms-gray-100);   /* 页面背景 */
--dms-bg-container: #ffffff;          /* 卡片/容器背景 */
--dms-bg-elevated: #ffffff;           /* 浮层背景（弹窗/抽屉） */
--dms-bg-hover: var(--dms-gray-50);   /* hover 态背景 */
--dms-bg-selected: var(--dms-blue-50);/* 选中态背景 */
--dms-bg-mask: rgba(0, 0, 0, 0.45);   /* 遮罩 */

/* 边框 */
--dms-border-1: var(--dms-gray-300);  /* 主边框 */
--dms-border-2: var(--dms-gray-200);  /* 浅边框/分割线 */
--dms-border-3: var(--dms-gray-100);  /* 最浅分割 */
--dms-border-focus: var(--dms-blue-500); /* 聚焦边框 */
```

### 2.4 图表色板（Chart Palette）

8 色主色板 + 语义色，色盲友好（参考 Tableau 10），用于 ECharts、驾驶舱大屏、经销商画像。

```scss
--dms-chart-1: #1677ff;  /* 主色蓝 */
--dms-chart-2: #52c41a;  /* 成功绿 */
--dms-chart-3: #faad14;  /* 警告橙 */
--dms-chart-4: #ff4d4f;  /* 危险红 */
--dms-chart-5: #722ed1;  /* 紫色 */
--dms-chart-6: #13c2c2;  /* 青色 */
--dms-chart-7: #eb2f96;  /* 品红 */
--dms-chart-8: #fa8c16;  /* 橙色 */

/* 语义：增长/下降 */
--dms-chart-up: #52c41a;   /* 增长-绿 */
--dms-chart-down: #ff4d4f; /* 下降-红 */
```

**ECharts 主题 JSON 片段**（注册为 `dms-theme`）：

```json
{
  "color": ["#1677ff", "#52c41a", "#faad14", "#ff4d4f", "#722ed1", "#13c2c2", "#eb2f96", "#fa8c16"],
  "backgroundColor": "transparent",
  "textStyle": { "color": "#303133", "fontFamily": "PingFang SC, Microsoft YaHei, sans-serif" },
  "title": { "textStyle": { "color": "#1f2329", "fontSize": 16, "fontWeight": 600 } },
  "legend": { "textStyle": { "color": "#606266" } },
  "tooltip": {
    "backgroundColor": "rgba(31, 35, 41, 0.9)",
    "textStyle": { "color": "#fff", "fontSize": 12 },
    "borderColor": "transparent"
  },
  "grid": { "left": 48, "right": 24, "top": 40, "bottom": 32 }
}
```

### 2.5 功能色

| Token | 色值 | 用途 |
|---|---|---|
| `--dms-link-color` | `var(--dms-blue-500)` | 链接 |
| `--dms-link-hover` | `var(--dms-blue-400)` | 链接 hover |
| `--dms-link-active` | `var(--dms-blue-600)` | 链接 active |
| `--dms-mask-color` | `rgba(0, 0, 0, 0.45)` | 弹窗遮罩 |
| `--dms-fill-blank` | `var(--dms-gray-100)` | 空状态填充 |

---

## 3. 字体系统

### 3.1 字体族

```scss
--dms-font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei',
  'Helvetica Neue', Helvetica, Arial, sans-serif;
--dms-font-family-mono: 'JetBrains Mono', 'Fira Code', 'Consolas', 'Courier New', monospace;
--dms-font-family-number: 'DIN Alternate', 'Roboto Mono', 'Helvetica Neue', sans-serif; /* 数字字体 */
```

### 3.2 字号

采用 1.25 倍率字号阶梯，基础 12px，与 Element Plus 对齐。

| Token | 字号 | 行高 | 字重 | 用途 |
|---|---|---|---|---|
| `--dms-font-size-xs` | 12px | 20px | 400 | 辅助文本、表格次要列 |
| `--dms-font-size-sm` | 13px | 22px | 400 | 表格主体、表单 |
| `--dms-font-size-base` | 14px | 22px | 400 | ★ **正文默认** |
| `--dms-font-size-md` | 16px | 24px | 500 | 卡片标题、小标题 |
| `--dms-font-size-lg` | 18px | 28px | 600 | 页内标题 |
| `--dms-font-size-xl` | 20px | 28px | 600 | 页面标题 |
| `--dms-font-size-2xl` | 24px | 32px | 600 | 区块大标题 |
| `--dms-font-size-3xl` | 30px | 38px | 700 | 仪表盘 KPI 数字 |
| `--dms-font-size-4xl` | 38px | 46px | 700 | 大屏数字 |

**语义映射**：

```scss
--dms-text-title-1: 600 24px/32px var(--dms-font-family);   /* 页面主标题 */
--dms-text-title-2: 600 20px/28px var(--dms-font-family);   /* 区块标题 */
--dms-text-title-3: 500 16px/24px var(--dms-font-family);   /* 卡片标题 */
--dms-text-body:    400 14px/22px var(--dms-font-family);   /* 正文 */
--dms-text-caption: 400 12px/20px var(--dms-font-family);   /* 辅助说明 */
--dms-text-kpi:     700 30px/38px var(--dms-font-family-number); /* KPI 数字 */
```

### 3.3 字重

```scss
--dms-font-weight-regular: 400;
--dms-font-weight-medium: 500;
--dms-font-weight-semibold: 600;
--dms-font-weight-bold: 700;
```

### 3.4 行高

```scss
--dms-line-height-tight: 1.3;    /* 标题 */
--dms-line-height-base: 1.5715;  /* 正文（22/14） */
--dms-line-height-relaxed: 1.8;  /* 段落 */
```

---

## 4. 间距系统

采用 **8 倍数法则** + 4px 微调，覆盖所有 padding/margin/gap。

| Token | 值 | 用途 |
|---|---|---|
| `--dms-spacing-0` | 0 | 无间距 |
| `--dms-spacing-1` | 4px | 紧凑（icon 与文字） |
| `--dms-spacing-2` | 8px | ★ 最小默认（按钮内 padding、列表 gap） |
| `--dms-spacing-3` | 12px | 表单字段间、卡片内 padding |
| `--dms-spacing-4` | 16px | ★ 常用（卡片 padding、区块间） |
| `--dms-spacing-5` | 20px | 区块间稍大 |
| `--dms-spacing-6` | 24px | 大区块间、抽屉 padding |
| `--dms-spacing-8` | 32px | 页面级区块间 |
| `--dms-spacing-10` | 40px | 大留白 |
| `--dms-spacing-12` | 48px | 仪表盘卡片间 |
| `--dms-spacing-16` | 64px | 页面顶部 hero 区 |

**语义映射**：

```scss
--dms-padding-page: var(--dms-spacing-6);      /* 页面 padding */
--dms-padding-card: var(--dms-spacing-4);      /* 卡片 padding */
--dms-padding-cell: var(--dms-spacing-3);      /* 单元格 padding */
--dms-gap-form: var(--dms-spacing-3);          /* 表单字段 gap */
--dms-gap-toolbar: var(--dms-spacing-2);       /* 工具栏按钮 gap */
--dms-gap-card-grid: var(--dms-spacing-6);     /* 卡片网格 gap */
```

---

## 5. 圆角系统

| Token | 值 | 用途 |
|---|---|---|
| `--dms-radius-none` | 0 | 无圆角 |
| `--dms-radius-sm` | 2px | 小元素（tag、badge） |
| `--dms-radius-base` | 4px | ★ **默认**（按钮、输入框、卡片） |
| `--dms-radius-md` | 6px | 中等（下拉面板、tooltip） |
| `--dms-radius-lg` | 8px | 大（弹窗、抽屉、大卡片） |
| `--dms-radius-xl` | 12px | 超大（仪表盘卡片） |
| `--dms-radius-pill` | 9999px | 胶囊（开关、圆形头像） |
| `--dms-radius-circle` | 50% | 圆形（图标按钮） |

---

## 6. 阴影系统

3 级阴影 + 1 级内阴影，统一光感（顶光，自上而下）。

| Token | 值 | 用途 |
|---|---|---|
| `--dms-shadow-none` | `none` | 无阴影 |
| `--dms-shadow-sm` | `0 1px 2px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04)` | 卡片、低层级浮层 |
| `--dms-shadow-md` | `0 4px 12px rgba(0, 0, 0, 0.08), 0 2px 4px rgba(0, 0, 0, 0.04)` | ★ **默认**（下拉、hover 卡片） |
| `--dms-shadow-lg` | `0 8px 24px rgba(0, 0, 0, 0.12), 0 4px 8px rgba(0, 0, 0, 0.06)` | 弹窗、抽屉 |
| `--dms-shadow-xl` | `0 16px 48px rgba(0, 0, 0, 0.16), 0 8px 16px rgba(0, 0, 0, 0.08)` | 全屏弹窗 |
| `--dms-shadow-focus` | `0 0 0 2px var(--dms-blue-50), 0 0 0 4px var(--dms-blue-500)` | 聚焦光环 |
| `--dms-shadow-inner` | `inset 0 1px 2px rgba(0, 0, 0, 0.04)` | 内阴影（输入框内嵌） |

---

## 7. 边框系统

| Token | 值 | 用途 |
|---|---|---|
| `--dms-border-width-thin` | 1px | 默认 |
| `--dms-border-width-thick` | 2px | 强调 |
| `--dms-border-style` | `solid` | 默认样式 |
| `--dms-border-color` | `var(--dms-gray-300)` | 默认颜色 |
| `--dms-border-color-light` | `var(--dms-gray-200)` | 浅色分割 |
| `--dms-border-color-lighter` | `var(--dms-gray-100)` | 最浅分割 |
| `--dms-border-color-focus` | `var(--dms-blue-500)` | 聚焦 |
| `--dms-border-color-hover` | `var(--dms-blue-400)` | hover |

**分割线**：

```scss
--dms-divider-color: var(--dms-gray-200);
--dms-divider-style: solid;
--dms-divider-width: 1px;
```

---

## 8. 动效系统

### 8.1 时长

| Token | 值 | 用途 |
|---|---|---|
| `--dms-motion-duration-instant` | 0ms | 即时 |
| `dms-motion-duration-fast` | 100ms | 微交互（hover、focus） |
| `--dms-motion-duration-quick` | 150ms | 按钮、Tab 切换 |
| `--dms-motion-duration-medium` | 200ms | ★ **默认**（路由过渡、抽屉） |
| `--dms-motion-duration-slow` | 250ms | 弹窗、复杂过渡 |
| `--dms-motion-duration-slower` | 300ms | 大面积过渡 |
| `--dms-motion-duration-slowest` | 450ms | 骨架屏 shimmer 一周期 |

### 8.2 缓动函数

```scss
--dms-motion-ease-linear: cubic-bezier(0, 0, 1, 1);
--dms-motion-ease-in: cubic-bezier(0.4, 0, 1, 1);
--dms-motion-ease-out: cubic-bezier(0, 0, 0.2, 1);        /* ★ 默认出场 */
--dms-motion-ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);   /* ★ 默认切换 */
--dms-motion-ease-bounce: cubic-bezier(0.68, -0.55, 0.27, 1.55); /* 弹性 */
```

### 8.3 动效规则

- 路由切换：fade + slide，200ms `ease-out`
- 弹窗/抽屉：250ms `ease-out`
- 列表项增删：FLIP 动画，200ms
- KPI 数字：滚动动画，600ms
- 骨架屏：shimmer 循环，1500ms
- 尊重 `prefers-reduced-motion`：用户开启"减少动效"时，所有动效降为 0ms 或仅 opacity 过渡

```scss
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

---

## 9. Z-index 层级

统一管理堆叠顺序，避免 z-index 战争。

| Token | 值 | 用途 |
|---|---|---|
| `--dms-z-index-base` | 0 | 默认 |
| `--dms-z-index-dropdown` | 1000 | 下拉菜单 |
| `--dms-z-index-sticky` | 1100 | 吸顶元素 |
| `--dms-z-index-fixed` | 1200 | 固定定位 |
| `--dms-z-index-overlay` | 1300 | 遮罩 |
| `--dms-z-index-modal` | 1400 | 弹窗 |
| `--dms-z-index-drawer` | 1500 | 抽屉 |
| `--dms-z-index-popover` | 1600 | 气泡 |
| `--dms-z-index-tooltip` | 1700 | tooltip |
| `--dms-z-index-message` | 2000 | 全局消息 |
| `--dms-z-index-notification` | 2100 | 通知 |
| `--dms-z-index-loading` | 3000 | 全屏 loading |

---

## 10. Element Plus 主题定制方案（PC + 平台后台）

### 10.1 定制方式选择

Element Plus 提供 3 种定制方式，本项目采用 **SCSS 变量覆盖**（编译期）+ **CSS 变量覆盖**（运行期）组合：

| 方式 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| SCSS 变量覆盖 | 编译期优化、无运行时开销 | 需重新编译 | ★ 主色/字号/圆角等固定主题 |
| CSS 变量覆盖 | 运行时可改、支持租户品牌 | 需 Element Plus 2.2+ | ★ 暗色模式/租户品牌 |
| 命名空间 | 隔离多主题 | 复杂 | 不用 |

### 10.2 SCSS 变量覆盖（编译期）

**文件**：`frontend-vue/src/styles/element/index.scss`

```scss
/* ===== Element Plus 主题变量覆盖 ===== */
/* 必须在引入 element-plus 之前定义 */

/* 1. 主色（通过 color.generate 生成色阶） */
@forward 'element-plus/theme-chalk/src/common/var.scss' with (
  $colors: (
    'primary': (
      'base': #1677ff,
    ),
    'success': (
      'base': #52c41a,
    ),
    'warning': (
      'base': #faad14,
    ),
    'danger': (
      'base': #ff4d4f,
    ),
    'error': (
      'base': #ff4d4f,
    ),
    'info': (
      'base': #909399,
    ),
  ),

  /* 2. 字体 */
  $font-size: (
    'extra-large': 20px,
    'large': 18px,
    'medium': 16px,
    'base': 14px,
    'small': 13px,
    'extra-small': 12px,
  ),
  $font-family: (
    '': -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei',
       'Helvetica Neue', Helvetica, Arial, sans-serif,
  ),

  /* 3. 圆角 */
  $border-radius: (
    'base': 4px,
    'small': 2px,
    'round': 20px,
    'circle': 100%,
  ),

  /* 4. 边框 */
  $border-color: (
    '': var(--dms-gray-300),
    'light': var(--dms-gray-200),
    'lighter': var(--dms-gray-100),
    'extra-light': var(--dms-gray-50),
    'dark': var(--dms-gray-400),
    'darker': var(--dms-gray-500),
  ),

  /* 5. 文本颜色 */
  $text-color: (
    'primary': var(--dms-gray-800),
    'regular': var(--dms-gray-700),
    'secondary': var(--dms-gray-600),
    'placeholder': var(--dms-gray-400),
    'disabled': var(--dms-gray-400),
  ),

  /* 6. 填充色 */
  $fill-color: (
    '': var(--dms-gray-100),
    'light': var(--dms-gray-50),
    'blank': #ffffff,
  ),

  /* 7. 阴影 */
  $box-shadow: (
    '': 0 4px 12px rgba(0, 0, 0, 0.08),
    'light': 0 1px 2px rgba(0, 0, 0, 0.06),
    'lighter': 0 1px 2px rgba(0, 0, 0, 0.04),
    'dark': 0 8px 24px rgba(0, 0, 0, 0.12),
  ),

  /* 8. 组件级覆盖 */
  $button: (
    'border-radius': var(--dms-radius-base),
    'font-weight': var(--dms-font-weight-regular),
  ),
  $input: (
    'border-radius': var(--dms-radius-base),
  ),
  $card: (
    'border-radius': var(--dms-radius-base),
    'padding': var(--dms-spacing-4),
  ),
  $table: (
    'header-font-weight': var(--dms-font-weight-medium),
    'row-hover-bg-color': var(--dms-bg-hover),
  ),
  $dialog: (
    'border-radius': var(--dms-radius-lg),
  ),
  $menu: (
    'item-height': 44px,
    'active-color': var(--dms-color-primary),
  ),
  $pagination: (
    'button-color': var(--dms-gray-700),
    'button-bg-color': #ffffff,
  )
);
```

### 10.3 CSS 变量覆盖（运行期）

**文件**：`frontend-vue/src/styles/element/runtime.scss`

```scss
/* Element Plus 运行期 CSS 变量覆盖（用于暗色模式/租户品牌切换） */
:root {
  /* 主色 */
  --el-color-primary: var(--dms-color-primary);
  --el-color-primary-light-3: var(--dms-blue-300);
  --el-color-primary-light-5: var(--dms-blue-200);
  --el-color-primary-light-7: var(--dms-blue-100);
  --el-color-primary-light-8: var(--dms-blue-50);
  --el-color-primary-light-9: var(--dms-blue-50);
  --el-color-primary-dark-2: var(--dms-blue-600);

  /* 语义色 */
  --el-color-success: var(--dms-color-success);
  --el-color-warning: var(--dms-color-warning);
  --el-color-danger: var(--dms-color-danger);
  --el-color-info: var(--dms-color-info);

  /* 文本 */
  --el-text-color-primary: var(--dms-text-1);
  --el-text-color-regular: var(--dms-text-2);
  --el-text-color-secondary: var(--dms-text-3);
  --el-text-color-placeholder: var(--dms-text-placeholder);
  --el-text-color-disabled: var(--dms-text-disabled);

  /* 边框 */
  --el-border-color: var(--dms-border-1);
  --el-border-color-light: var(--dms-border-2);
  --el-border-color-lighter: var(--dms-border-3);
  --el-border-color-extra-light: var(--dms-gray-50);

  /* 填充 */
  --el-fill-color: var(--dms-bg-page);
  --el-fill-color-light: var(--dms-bg-hover);
  --el-fill-color-blank: var(--dms-bg-container);

  /* 圆角 */
  --el-border-radius-base: var(--dms-radius-base);
  --el-border-radius-small: var(--dms-radius-sm);
  --el-border-radius-round: var(--dms-radius-pill);

  /* 阴影 */
  --el-box-shadow: var(--dms-shadow-md);
  --el-box-shadow-light: var(--dms-shadow-sm);
  --el-box-shadow-dark: var(--dms-shadow-lg);

  /* 字体 */
  --el-font-size-base: 14px;
  --el-font-size-small: 13px;
  --el-font-size-extra-small: 12px;
  --el-font-family: var(--dms-font-family);
}
```

### 10.4 main.js 引入顺序（关键）

```javascript
// frontend-vue/src/main.js
import './styles/tokens/base-light.scss';   // 1. Layer 1 调色板
import './styles/tokens/semantic.scss';     // 2. Layer 2 语义令牌
import './styles/element/index.scss';       // 3. Element Plus SCSS 覆盖（编译期）
import 'element-plus/dist/index.css';       // 4. Element Plus 原生样式
import './styles/element/runtime.scss';     // 5. Element Plus CSS 变量覆盖（运行期）
import './styles/vant/index.scss';          // 6. Vant 主题覆盖
import 'vant/lib/index.css';                // 7. Vant 原生样式
import './styles/index.scss';               // 8. 全局自定义样式
```

> ⚠️ **关键**：SCSS 变量覆盖文件必须在 `element-plus/dist/index.css` 之前引入；CSS 变量覆盖文件必须在之后引入。

---

## 11. Vant 主题定制方案（移动端 H5）

### 11.1 SCSS 变量覆盖

**文件**：`frontend-vue/src/styles/vant/index.scss`

```scss
/* ===== Vant 主题变量覆盖 ===== */
/* 必须在引入 vant 之前定义 */

/* 主色 */
$primary-color: #1677ff;
$success-color: #52c41a;
$warning-color: #faad14;
$danger-color: #ff4d4f;
$info-color: #909399;

/* 文本 */
$text-color: #303133;
$text-color-2: #606266;
$text-color-3: #909399;

/* 背景 */
$background-color: #f5f7fa;
$background-color-light: #ffffff;

/* 边框 */
$border-color: #e4e7ed;
$border-color-light: #ebeef5;

/* 圆角 */
$border-radius-sm: 2px;
$border-radius-md: 4px;
$border-radius-lg: 8px;
$border-radius-max: 9999px;

@forward 'vant/lib/vant' with (
  $blue: $primary-color,
  $green: $success-color,
  $orange: $warning-color,
  $red: $danger-color,
  $gray-1: #f5f7fa,
  $gray-2: #ebeef5,
  $gray-3: #e4e7ed,
  $gray-4: #c0c4cc,
  $gray-5: #909399,
  $gray-6: #606266,
  $gray-7: #303133,
  $gray-8: #1f2329,
  $text-color: $text-color,
  $active-color: rgba(22, 119, 255, 0.1),
  $background: $background-color,
  $background-2: $background-color-light,
  $border-color: $border-color,
  $radius-sm: $border-radius-sm,
  $radius-md: $border-radius-md,
  $radius-lg: $border-radius-lg,
  $radius-max: $border-radius-max,
  $cell-background-color: #ffffff,
  $cell-border-color: $border-color-light,
  $button-primary-background-color: $primary-color,
  $button-primary-border-color: $primary-color,
  $tab-active-text-color: $primary-color,
  $tab-active-color: $primary-color,
  $nav-bar-icon-color: $primary-color,
  $nav-bar-text-color: $primary-color,
  $tabbar-item-active-color: $primary-color
);
```

### 11.2 移动端业务组件统一

```scss
/* frontend-vue/src/views/mobile/styles/mobile.scss */

.dms-mobile-page {
  min-height: 100vh;
  background: var(--dms-bg-page);
  padding-bottom: env(safe-area-inset-bottom);
}

.dms-mobile-card {
  background: var(--dms-bg-container);
  border-radius: var(--dms-radius-lg);
  padding: var(--dms-spacing-4);
  margin: var(--dms-spacing-3);
  box-shadow: var(--dms-shadow-sm);
}

.dms-mobile-section-title {
  font: var(--dms-text-title-3);
  color: var(--dms-text-1);
  padding: var(--dms-spacing-3) var(--dms-spacing-4);
}

/* 状态标签 */
.dms-status-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--dms-radius-sm);
  font-size: var(--dms-font-size-xs);
  line-height: 18px;

  &--draft     { background: var(--dms-gray-100); color: var(--dms-gray-600); }
  &--pending   { background: var(--dms-color-warning-bg); color: var(--dms-color-warning); }
  &--approved  { background: var(--dms-color-success-bg); color: var(--dms-color-success); }
  &--rejected  { background: var(--dms-color-danger-bg); color: var(--dms-color-danger); }
  &--effective { background: var(--dms-blue-50); color: var(--dms-blue-600); }
}
```

---

## 12. CSS 变量统一注册（:root）

**文件**：`frontend-vue/src/styles/tokens/base-light.scss`

```scss
/* ===== DMS Design Token - Layer 1: Base Palette (Light) ===== */

:root {
  /* ---------- 主色（蓝色系） ---------- */
  --dms-blue-50:  #e6f4ff;
  --dms-blue-100: #bae0ff;
  --dms-blue-200: #91caff;
  --dms-blue-300: #69b1ff;
  --dms-blue-400: #4096ff;
  --dms-blue-500: #1677ff;
  --dms-blue-600: #0958d9;
  --dms-blue-700: #003eb3;
  --dms-blue-800: #002c8c;
  --dms-blue-900: #001d66;

  /* ---------- 语义色 ---------- */
  --dms-green-50:  #f6ffed;
  --dms-green-500: #52c41a;
  --dms-green-600: #389e0d;

  --dms-gold-50:  #fffbe6;
  --dms-gold-500: #faad14;
  --dms-gold-600: #d48806;

  --dms-red-50:  #fff2f0;
  --dms-red-500: #ff4d4f;
  --dms-red-600: #cf1322;

  --dms-gray-50:  #fafafa;
  --dms-gray-100: #f5f7fa;
  --dms-gray-200: #ebeef5;
  --dms-gray-300: #e4e7ed;
  --dms-gray-400: #c0c4cc;
  --dms-gray-500: #909399;
  --dms-gray-600: #606266;
  --dms-gray-700: #303133;
  --dms-gray-800: #1f2329;
  --dms-gray-900: #0d0e10;

  /* ---------- 紫色（辅助） ---------- */
  --dms-purple-500: #722ed1;
  --dms-cyan-500: #13c2c2;
  --dms-magenta-500: #eb2f96;
  --dms-orange-500: #fa8c16;

  /* ---------- 图表色板 ---------- */
  --dms-chart-1: #1677ff;
  --dms-chart-2: #52c41a;
  --dms-chart-3: #faad14;
  --dms-chart-4: #ff4d4f;
  --dms-chart-5: #722ed1;
  --dms-chart-6: #13c2c2;
  --dms-chart-7: #eb2f96;
  --dms-chart-8: #fa8c16;
  --dms-chart-up: #52c41a;
  --dms-chart-down: #ff4d4f;
}
```

**文件**：`frontend-vue/src/styles/tokens/semantic.scss`

```scss
/* ===== DMS Design Token - Layer 2: Semantic Tokens ===== */

:root {
  /* ---------- 主色语义 ---------- */
  --dms-color-primary: var(--dms-blue-500);
  --dms-color-primary-hover: var(--dms-blue-400);
  --dms-color-primary-active: var(--dms-blue-600);
  --dms-color-primary-bg: var(--dms-blue-50);
  --dms-color-primary-border: var(--dms-blue-300);

  /* ---------- 语义色 ---------- */
  --dms-color-success: var(--dms-green-500);
  --dms-color-success-bg: var(--dms-green-50);
  --dms-color-warning: var(--dms-gold-500);
  --dms-color-warning-bg: var(--dms-gold-50);
  --dms-color-danger: var(--dms-red-500);
  --dms-color-danger-bg: var(--dms-red-50);
  --dms-color-info: var(--dms-gray-500);
  --dms-color-info-bg: var(--dms-gray-100);

  /* ---------- 业务状态色 ---------- */
  --dms-status-draft: var(--dms-gray-500);
  --dms-status-pending: var(--dms-color-warning);
  --dms-status-approved: var(--dms-color-success);
  --dms-status-rejected: var(--dms-color-danger);
  --dms-status-effective: var(--dms-blue-500);
  --dms-status-terminated: var(--dms-gray-700);
  --dms-status-expired: var(--dms-gray-400);

  /* ---------- 文本 ---------- */
  --dms-text-1: var(--dms-gray-800);
  --dms-text-2: var(--dms-gray-700);
  --dms-text-3: var(--dms-gray-600);
  --dms-text-4: var(--dms-gray-500);
  --dms-text-placeholder: var(--dms-gray-400);
  --dms-text-disabled: var(--dms-gray-400);
  --dms-text-inverse: #ffffff;

  /* ---------- 背景 ---------- */
  --dms-bg-page: var(--dms-gray-100);
  --dms-bg-container: #ffffff;
  --dms-bg-elevated: #ffffff;
  --dms-bg-hover: var(--dms-gray-50);
  --dms-bg-selected: var(--dms-blue-50);
  --dms-bg-mask: rgba(0, 0, 0, 0.45);

  /* ---------- 边框 ---------- */
  --dms-border-1: var(--dms-gray-300);
  --dms-border-2: var(--dms-gray-200);
  --dms-border-3: var(--dms-gray-100);
  --dms-border-focus: var(--dms-blue-500);

  /* ---------- 链接 ---------- */
  --dms-link-color: var(--dms-blue-500);
  --dms-link-hover: var(--dms-blue-400);
  --dms-link-active: var(--dms-blue-600);

  /* ---------- 字体 ---------- */
  --dms-font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei',
    'Helvetica Neue', Helvetica, Arial, sans-serif;
  --dms-font-family-mono: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  --dms-font-family-number: 'DIN Alternate', 'Roboto Mono', sans-serif;

  --dms-font-size-xs: 12px;
  --dms-font-size-sm: 13px;
  --dms-font-size-base: 14px;
  --dms-font-size-md: 16px;
  --dms-font-size-lg: 18px;
  --dms-font-size-xl: 20px;
  --dms-font-size-2xl: 24px;
  --dms-font-size-3xl: 30px;
  --dms-font-size-4xl: 38px;

  --dms-font-weight-regular: 400;
  --dms-font-weight-medium: 500;
  --dms-font-weight-semibold: 600;
  --dms-font-weight-bold: 700;

  --dms-line-height-tight: 1.3;
  --dms-line-height-base: 1.5715;
  --dms-line-height-relaxed: 1.8;

  /* ---------- 间距 ---------- */
  --dms-spacing-0: 0;
  --dms-spacing-1: 4px;
  --dms-spacing-2: 8px;
  --dms-spacing-3: 12px;
  --dms-spacing-4: 16px;
  --dms-spacing-5: 20px;
  --dms-spacing-6: 24px;
  --dms-spacing-8: 32px;
  --dms-spacing-10: 40px;
  --dms-spacing-12: 48px;
  --dms-spacing-16: 64px;

  --dms-padding-page: var(--dms-spacing-6);
  --dms-padding-card: var(--dms-spacing-4);
  --dms-padding-cell: var(--dms-spacing-3);
  --dms-gap-form: var(--dms-spacing-3);
  --dms-gap-toolbar: var(--dms-spacing-2);

  /* ---------- 圆角 ---------- */
  --dms-radius-none: 0;
  --dms-radius-sm: 2px;
  --dms-radius-base: 4px;
  --dms-radius-md: 6px;
  --dms-radius-lg: 8px;
  --dms-radius-xl: 12px;
  --dms-radius-pill: 9999px;
  --dms-radius-circle: 50%;

  /* ---------- 阴影 ---------- */
  --dms-shadow-none: none;
  --dms-shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  --dms-shadow-md: 0 4px 12px rgba(0, 0, 0, 0.08), 0 2px 4px rgba(0, 0, 0, 0.04);
  --dms-shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.12), 0 4px 8px rgba(0, 0, 0, 0.06);
  --dms-shadow-xl: 0 16px 48px rgba(0, 0, 0, 0.16), 0 8px 16px rgba(0, 0, 0, 0.08);
  --dms-shadow-focus: 0 0 0 2px var(--dms-blue-50), 0 0 0 4px var(--dms-blue-500);
  --dms-shadow-inner: inset 0 1px 2px rgba(0, 0, 0, 0.04);

  /* ---------- 边框 ---------- */
  --dms-border-width-thin: 1px;
  --dms-border-width-thick: 2px;
  --dms-border-style: solid;
  --dms-divider-color: var(--dms-gray-200);
  --dms-divider-width: 1px;

  /* ---------- 动效 ---------- */
  --dms-motion-duration-instant: 0ms;
  --dms-motion-duration-fast: 100ms;
  --dms-motion-duration-quick: 150ms;
  --dms-motion-duration-medium: 200ms;
  --dms-motion-duration-slow: 250ms;
  --dms-motion-duration-slower: 300ms;
  --dms-motion-duration-slowest: 450ms;

  --dms-motion-ease-linear: cubic-bezier(0, 0, 1, 1);
  --dms-motion-ease-in: cubic-bezier(0.4, 0, 1, 1);
  --dms-motion-ease-out: cubic-bezier(0, 0, 0.2, 1);
  --dms-motion-ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
  --dms-motion-ease-bounce: cubic-bezier(0.68, -0.55, 0.27, 1.55);

  /* ---------- Z-index ---------- */
  --dms-z-index-base: 0;
  --dms-z-index-dropdown: 1000;
  --dms-z-index-sticky: 1100;
  --dms-z-index-fixed: 1200;
  --dms-z-index-overlay: 1300;
  --dms-z-index-modal: 1400;
  --dms-z-index-drawer: 1500;
  --dms-z-index-popover: 1600;
  --dms-z-index-tooltip: 1700;
  --dms-z-index-message: 2000;
  --dms-z-index-notification: 2100;
  --dms-z-index-loading: 3000;

  /* ---------- 过渡 ---------- */
  --dms-transition-base: all var(--dms-motion-duration-medium) var(--dms-motion-ease-out);
  --dms-transition-color: color var(--dms-motion-duration-fast) var(--dms-motion-ease-out),
                          background-color var(--dms-motion-duration-fast) var(--dms-motion-ease-out),
                          border-color var(--dms-motion-duration-fast) var(--dms-motion-ease-out);
}
```

---

## 13. 暗色模式预留

### 13.1 暗色调色板

**文件**：`frontend-vue/src/styles/tokens/base-dark.scss`

```scss
/* ===== DMS Design Token - Layer 1: Base Palette (Dark) ===== */

html.dark {
  /* 主色（暗色模式略提亮以保证对比度） */
  --dms-blue-400: #4096ff;  /* 暗色模式主色用 400 */
  --dms-blue-500: #4096ff;  /* 覆盖为主色 */
  --dms-blue-50:  #111d2c;  /* 浅背景在暗色下变深 */

  /* 中性色反转 */
  --dms-gray-50:  #1f1f1f;
  --dms-gray-100: #1f2329;  /* 页面背景 */
  --dms-gray-200: #2b2b2b;
  --dms-gray-300: #3a3a3a;
  --dms-gray-400: #4e5969;
  --dms-gray-500: #86909c;
  --dms-gray-600: #a9aeb8;
  --dms-gray-700: #c9cdd4;
  --dms-gray-800: #e5e6eb;
  --dms-gray-900: #f7f8fa;

  /* 语义色背景反转 */
  --dms-green-50:  #16231a;
  --dms-gold-50:   #2a1f0a;
  --dms-red-50:    #2a1215;

  /* 容器背景 */
  --dms-bg-container: #1f2329;
  --dms-bg-elevated: #2b2b2b;
  --dms-bg-hover: #2b2b2b;
  --dms-bg-selected: #111d2c;
}
```

### 13.2 切换机制

```javascript
// frontend-vue/src/composables/useTheme.js
import { useDark, useToggle } from '@vueuse/core'

export const isDark = useDark({
  selector: 'html',
  attribute: 'class',
  valueDark: 'dark',
  valueLight: '',
})

export const toggleDark = useToggle(isDark)
```

### 13.3 Element Plus 暗色模式

Element Plus 2.3+ 内置暗色模式，只需：

```scss
/* html.dark 已自动加载 element-plus/theme-chalk/dark/css-vars.css */
```

```javascript
// main.js
import 'element-plus/theme-chalk/dark/css-vars.css'
```

---

## 14. 租户品牌定制方案

### 14.1 设计目标

允许每个租户自定义：logo、主色、辅助色，不影响其他租户。

### 14.2 实现方案

**后端**：`tenants` 表新增字段：

```sql
ALTER TABLE tenants ADD COLUMN brand_config JSONB DEFAULT '{"primaryColor":"#1677ff","logoUrl":null}';
```

**前端**：登录后拉取租户品牌配置，注入 CSS 变量：

```javascript
// frontend-vue/src/composables/useTenantBrand.js
import { useUserStore } from '@/stores/user'

export function applyTenantBrand() {
  const userStore = useUserStore()
  const brand = userStore.tenant?.brandConfig

  if (brand?.primaryColor) {
    // 基于 primaryColor 生成色阶（使用 chroma-js 或 color2k）
    const shades = generateColorShades(brand.primaryColor) // {50, 100, ..., 900}

    const root = document.documentElement
    root.style.setProperty('--dms-blue-50', shades[50])
    root.style.setProperty('--dms-blue-100', shades[100])
    root.style.setProperty('--dms-blue-200', shades[200])
    root.style.setProperty('--dms-blue-300', shades[300])
    root.style.setProperty('--dms-blue-400', shades[400])
    root.style.setProperty('--dms-blue-500', shades[500])
    root.style.setProperty('--dms-blue-600', shades[600])
    root.style.setProperty('--dms-blue-700', shades[700])
    // --dms-color-primary 等语义令牌自动联动
  }

  if (brand?.logoUrl) {
    document.querySelector('link[rel="icon"]').href = brand.logoUrl
    // 顶部导航 logo 也用此 URL
  }
}
```

**色阶生成工具**：使用 `chroma-js`：

```javascript
import chroma from 'chroma-js'

export function generateColorShades(baseColor) {
  const scale = chroma.scale(['#ffffff', baseColor, '#000000']).mode('lab')
  return {
    50:  scale(0.85).hex(),  // 接近白的浅色
    100: scale(0.75).hex(),
    200: scale(0.65).hex(),
    300: scale(0.55).hex(),
    400: scale(0.4).hex(),
    500: baseColor,           // 用户指定主色
    600: scale(0.35).hex(),
    700: scale(0.25).hex(),
    800: scale(0.15).hex(),
    900: scale(0.08).hex(),
  }
}
```

### 14.3 平台后台品牌配置入口

`/admin/tenants` 编辑租户弹窗新增"品牌定制"区块：
- 主色颜色选择器（el-color-picker）
- Logo 上传（复用 MinIO）
- 实时预览（左侧小窗预览效果）

### 14.4 注意事项

1. 租户品牌**仅覆盖 Layer 1 主色相关变量**，中性色/语义色保持一致以保证可读性
2. 主色对比度需校验（WCAG AA），低于 4.5:1 时提示用户
3. 平台后台（admin-vue）不应用租户品牌，统一使用平台主色

---

## 15. 落地步骤与里程碑

### 15.1 第 1 步：建立 Token 文件（1 人日）

1. 创建 `frontend-vue/src/styles/tokens/` 目录
2. 写入 `base-light.scss`、`semantic.scss`
3. 在 `main.js` 引入

### 15.2 第 2 步：Element Plus 主题覆盖（1.5 人日）

1. 写入 `styles/element/index.scss`（SCSS 覆盖）
2. 写入 `styles/element/runtime.scss`（CSS 变量覆盖）
3. 调整 `main.js` 引入顺序
4. 验证：按钮、表单、表格、弹窗主色变为 `#1677ff`

### 15.3 第 3 步：Vant 主题覆盖（1 人日）

1. 写入 `styles/vant/index.scss`
2. 调整 `main.js` 引入顺序
3. 验证：移动端 Tab、按钮主色一致

### 15.4 第 4 步：admin-vue 同步（1 人日）

1. 复制 token 文件到 `admin-vue/src/styles/`
2. 应用 Element Plus 覆盖
3. 验证三端视觉一致

### 15.5 第 5 步：通用组件抽取（2 人日）

1. 抽取 `EmptyState`、`StatusTag`、`DmsChart`、`DmsCard` 等基础组件
2. 全部使用语义 Token

### 15.6 第 6 步：存量页面改造（2-3 人日）

1. 全局搜索硬编码色值（`#409eff`、`#f56c6c`、`#67c23a` 等），替换为 `var(--dms-...)`
2. 全局搜索内联 `style`，迁移到语义 Token
3. 按页面分批 PR，避免大爆炸式改动

### 15.7 第 7 步：暗色模式预留（0.5 人日）

1. 写入 `base-dark.scss`
2. 接入 `useDark`
3. 验证切换不破坏布局

### 15.8 第 8 步：租户品牌定制（1 人日，可选）

1. 后端加 `brand_config` 字段
2. 前端 `applyTenantBrand`
3. 平台后台配置入口

**总工作量**：5-8 人日（NEW-19 原评估）

---

## 16. 验收清单

### 16.1 Token 体系

- [ ] `frontend-vue/src/styles/tokens/` 目录存在且文件齐全
- [ ] `base-light.scss` 定义全部 Layer 1 调色板
- [ ] `semantic.scss` 定义全部 Layer 2 语义令牌
- [ ] `main.js` 引入顺序正确（token → element scss → element css → element runtime → vant → 自定义）

### 16.2 三端视觉一致

- [ ] PC 端主色为 `#1677ff`（非 Element Plus 默认 `#409eff`）
- [ ] 移动端主色与 PC 一致
- [ ] 平台后台主色与业务前台一致
- [ ] 字体族三端一致
- [ ] 圆角、阴影、间距三端一致

### 16.3 无硬编码

- [ ] `grep -r '#[0-9a-fA-F]\{6\}' src/` 仅出现在 token 文件中
- [ ] `grep -r 'color:' src/ | grep -v 'var('` 仅出现在 token 文件中
- [ ] ESLint 规则禁止内联色值（可选）

### 16.4 语义令牌使用

- [ ] 业务代码使用 `var(--dms-text-1)` 而非 `var(--dms-gray-800)`
- [ ] 业务代码使用 `var(--dms-color-primary)` 而非 `var(--dms-blue-500)`
- [ ] 业务代码使用 `var(--dms-spacing-4)` 而非 `16px`

### 16.5 暗色模式

- [ ] `html.dark` 类下定义暗色 token
- [ ] 切换暗色模式不破坏布局
- [ ] 文本与背景对比度 ≥ 4.5:1（WCAG AA）

### 16.6 租户品牌

- [ ] `tenants.brand_config` 字段存在
- [ ] 登录后主色按租户配置变化
- [ ] 平台后台不受租户品牌影响

### 16.7 可访问性

- [ ] 焦点可见（`--dms-shadow-focus`）
- [ ] 尊重 `prefers-reduced-motion`
- [ ] 色盲友好（图表色板）

---

## 17. 附录：Token 命名规范

### 17.1 命名结构

```
--dms-{category}-{property}-{variant}
```

| 部分 | 说明 | 示例 |
|---|---|---|
| `--dms-` | 前缀，避免冲突 | `--dms-` |
| `{category}` | 类别 | `color` / `text` / `bg` / `border` / `font` / `spacing` / `radius` / `shadow` / `motion` / `z-index` |
| `{property}` | 属性 | `primary` / `success` / `1` / `sm` / `base` |
| `{variant}` | 变体（可选） | `hover` / `active` / `bg` / `border` |

### 17.2 命名示例

```
✅ --dms-color-primary
✅ --dms-color-primary-hover
✅ --dms-color-primary-bg
✅ --dms-text-1
✅ --dms-bg-page
✅ --dms-spacing-4
✅ --dms-radius-base
✅ --dms-shadow-md
✅ --dms-motion-duration-medium
✅ --dms-z-index-modal

❌ --dms-blue-500（业务代码不应直接引用 Layer 1）
❌ --dms-primary（缺少 category）
❌ --primary-color（缺少前缀，可能冲突）
❌ --dms-color-primary-light-hover（层级过深）
```

### 17.3 色阶命名约定

主色采用 50~900 十阶（50 为最浅，900 为最深），与 Tailwind / Ant Design 对齐：

```
50  → 浅背景、tag
100 → 浅背景
200 → 描边浅
300 → 描边
400 → hover
500 → ★ 默认主色
600 → active
700 → 深
800 → 更深
900 → 最深
```

### 17.4 禁止事项

1. **禁止业务代码引用 Layer 1**：`var(--dms-blue-500)` ❌ → `var(--dms-color-primary)` ✅
2. **禁止硬编码色值**：`color: #1677ff` ❌ → `color: var(--dms-color-primary)` ✅
3. **禁止 `!important`**：除非覆盖第三方库样式
4. **禁止内联 style 写色值**：`<div style="color: #1677ff">` ❌
5. **禁止重复定义**：同一语义只允许一个 token

---

> **下一步**：本规范评审通过后，按第 15 章落地步骤执行；建议先做第 1-4 步（约 4.5 人日）建立基础，再按页面分批改造存量代码。
