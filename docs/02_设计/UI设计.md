# DMS UI 设计规范

> 生成时间：2026-08-14 17:51  
> 本文由 `reorg_docs_v2.py` 自动合并。今后该类设计请直接在本文件追加章节，不要再新建独立文件。

## 章节来源

1. 一、PC 端 Design Token 规范 v1.0 — `02_设计\UI设计\UI设计系统_Design_Token规范_v1.0.md`
2. 二、移动端 H5 设计规范 v1.0 — `02_设计\UI设计\移动端H5设计规范_v1.0.md`

---

## 阅读说明

本文件合并 PC 端 Design Token 规范与移动端 H5 设计规范，是 UI 设计的唯一来源。
可视化 Demo 保留同目录下的 HTML：
- `UI设计系统_Demo.html` — PC 端组件/颜色/间距演示
- `UI设计系统_Mobile_Demo.html` — 移动端组件演示

原 03_设计图 目录的 PNG 原型已删除，UI 参考一律以本规范 + Demo 为准。


---

## 一、PC 端 Design Token 规范 v1.0


## DMS UI 设计系统 — Design Token 详细规范与 Element Plus / Vant 主题定制方案

> **文档编号**：NEW-19  详细规范
> **版本**：v1.0
> **创建日期**：2026-08-12
> **适用范围**：业务前台 PC（frontend-vue）、平台后台（admin-vue）、移动端 H5（frontend-vue/mobile）
> **关联文档**：`15_补充需求/DMS功能缺口与优化需求评估_v3.12.md` NEW-19、NEW-20、NEW-21、NEW-23
> **目的**：建立 DMS 统一设计语言，作为后续所有 UI 改造的地基；通过 Design Token + 主题变量覆盖实现三端视觉一致、可维护、可扩展（含暗色模式与租户品牌定制预留）

---

### 目录

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

### 1. 总览与分层架构

#### 1.1 三层 Token 架构

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

#### 1.2 文件组织结构

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

### 2. 颜色系统

#### 2.1 主色（Brand Color）

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

#### 2.2 语义色（Semantic Color）

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

#### 2.3 中性色（Neutral Color）

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

#### 2.4 图表色板（Chart Palette）

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

#### 2.5 功能色

| Token | 色值 | 用途 |
|---|---|---|
| `--dms-link-color` | `var(--dms-blue-500)` | 链接 |
| `--dms-link-hover` | `var(--dms-blue-400)` | 链接 hover |
| `--dms-link-active` | `var(--dms-blue-600)` | 链接 active |
| `--dms-mask-color` | `rgba(0, 0, 0, 0.45)` | 弹窗遮罩 |
| `--dms-fill-blank` | `var(--dms-gray-100)` | 空状态填充 |

---

### 3. 字体系统

#### 3.1 字体族

```scss
--dms-font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei',
  'Helvetica Neue', Helvetica, Arial, sans-serif;
--dms-font-family-mono: 'JetBrains Mono', 'Fira Code', 'Consolas', 'Courier New', monospace;
--dms-font-family-number: 'DIN Alternate', 'Roboto Mono', 'Helvetica Neue', sans-serif; /* 数字字体 */
```

#### 3.2 字号

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

#### 3.3 字重

```scss
--dms-font-weight-regular: 400;
--dms-font-weight-medium: 500;
--dms-font-weight-semibold: 600;
--dms-font-weight-bold: 700;
```

#### 3.4 行高

```scss
--dms-line-height-tight: 1.3;    /* 标题 */
--dms-line-height-base: 1.5715;  /* 正文（22/14） */
--dms-line-height-relaxed: 1.8;  /* 段落 */
```

---

### 4. 间距系统

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

### 5. 圆角系统

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

### 6. 阴影系统

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

### 7. 边框系统

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

### 8. 动效系统

#### 8.1 时长

| Token | 值 | 用途 |
|---|---|---|
| `--dms-motion-duration-instant` | 0ms | 即时 |
| `dms-motion-duration-fast` | 100ms | 微交互（hover、focus） |
| `--dms-motion-duration-quick` | 150ms | 按钮、Tab 切换 |
| `--dms-motion-duration-medium` | 200ms | ★ **默认**（路由过渡、抽屉） |
| `--dms-motion-duration-slow` | 250ms | 弹窗、复杂过渡 |
| `--dms-motion-duration-slower` | 300ms | 大面积过渡 |
| `--dms-motion-duration-slowest` | 450ms | 骨架屏 shimmer 一周期 |

#### 8.2 缓动函数

```scss
--dms-motion-ease-linear: cubic-bezier(0, 0, 1, 1);
--dms-motion-ease-in: cubic-bezier(0.4, 0, 1, 1);
--dms-motion-ease-out: cubic-bezier(0, 0, 0.2, 1);        /* ★ 默认出场 */
--dms-motion-ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);   /* ★ 默认切换 */
--dms-motion-ease-bounce: cubic-bezier(0.68, -0.55, 0.27, 1.55); /* 弹性 */
```

#### 8.3 动效规则

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

### 9. Z-index 层级

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

### 10. Element Plus 主题定制方案（PC + 平台后台）

#### 10.1 定制方式选择

Element Plus 提供 3 种定制方式，本项目采用 **SCSS 变量覆盖**（编译期）+ **CSS 变量覆盖**（运行期）组合：

| 方式 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| SCSS 变量覆盖 | 编译期优化、无运行时开销 | 需重新编译 | ★ 主色/字号/圆角等固定主题 |
| CSS 变量覆盖 | 运行时可改、支持租户品牌 | 需 Element Plus 2.2+ | ★ 暗色模式/租户品牌 |
| 命名空间 | 隔离多主题 | 复杂 | 不用 |

#### 10.2 SCSS 变量覆盖（编译期）

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

#### 10.3 CSS 变量覆盖（运行期）

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

#### 10.4 main.js 引入顺序（关键）

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

### 11. Vant 主题定制方案（移动端 H5）

#### 11.1 SCSS 变量覆盖

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

#### 11.2 移动端业务组件统一

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

### 12. CSS 变量统一注册（:root）

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

### 13. 暗色模式预留

#### 13.1 暗色调色板

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

#### 13.2 切换机制

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

#### 13.3 Element Plus 暗色模式

Element Plus 2.3+ 内置暗色模式，只需：

```scss
/* html.dark 已自动加载 element-plus/theme-chalk/dark/css-vars.css */
```

```javascript
// main.js
import 'element-plus/theme-chalk/dark/css-vars.css'
```

---

### 14. 租户品牌定制方案

#### 14.1 设计目标

允许每个租户自定义：logo、主色、辅助色，不影响其他租户。

#### 14.2 实现方案

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

#### 14.3 平台后台品牌配置入口

`/admin/tenants` 编辑租户弹窗新增"品牌定制"区块：
- 主色颜色选择器（el-color-picker）
- Logo 上传（复用 MinIO）
- 实时预览（左侧小窗预览效果）

#### 14.4 注意事项

1. 租户品牌**仅覆盖 Layer 1 主色相关变量**，中性色/语义色保持一致以保证可读性
2. 主色对比度需校验（WCAG AA），低于 4.5:1 时提示用户
3. 平台后台（admin-vue）不应用租户品牌，统一使用平台主色

---

### 15. 落地步骤与里程碑

#### 15.1 第 1 步：建立 Token 文件（1 人日）

1. 创建 `frontend-vue/src/styles/tokens/` 目录
2. 写入 `base-light.scss`、`semantic.scss`
3. 在 `main.js` 引入

#### 15.2 第 2 步：Element Plus 主题覆盖（1.5 人日）

1. 写入 `styles/element/index.scss`（SCSS 覆盖）
2. 写入 `styles/element/runtime.scss`（CSS 变量覆盖）
3. 调整 `main.js` 引入顺序
4. 验证：按钮、表单、表格、弹窗主色变为 `#1677ff`

#### 15.3 第 3 步：Vant 主题覆盖（1 人日）

1. 写入 `styles/vant/index.scss`
2. 调整 `main.js` 引入顺序
3. 验证：移动端 Tab、按钮主色一致

#### 15.4 第 4 步：admin-vue 同步（1 人日）

1. 复制 token 文件到 `admin-vue/src/styles/`
2. 应用 Element Plus 覆盖
3. 验证三端视觉一致

#### 15.5 第 5 步：通用组件抽取（2 人日）

1. 抽取 `EmptyState`、`StatusTag`、`DmsChart`、`DmsCard` 等基础组件
2. 全部使用语义 Token

#### 15.6 第 6 步：存量页面改造（2-3 人日）

1. 全局搜索硬编码色值（`#409eff`、`#f56c6c`、`#67c23a` 等），替换为 `var(--dms-...)`
2. 全局搜索内联 `style`，迁移到语义 Token
3. 按页面分批 PR，避免大爆炸式改动

#### 15.7 第 7 步：暗色模式预留（0.5 人日）

1. 写入 `base-dark.scss`
2. 接入 `useDark`
3. 验证切换不破坏布局

#### 15.8 第 8 步：租户品牌定制（1 人日，可选）

1. 后端加 `brand_config` 字段
2. 前端 `applyTenantBrand`
3. 平台后台配置入口

**总工作量**：5-8 人日（NEW-19 原评估）

---

### 16. 验收清单

#### 16.1 Token 体系

- [ ] `frontend-vue/src/styles/tokens/` 目录存在且文件齐全
- [ ] `base-light.scss` 定义全部 Layer 1 调色板
- [ ] `semantic.scss` 定义全部 Layer 2 语义令牌
- [ ] `main.js` 引入顺序正确（token → element scss → element css → element runtime → vant → 自定义）

#### 16.2 三端视觉一致

- [ ] PC 端主色为 `#1677ff`（非 Element Plus 默认 `#409eff`）
- [ ] 移动端主色与 PC 一致
- [ ] 平台后台主色与业务前台一致
- [ ] 字体族三端一致
- [ ] 圆角、阴影、间距三端一致

#### 16.3 无硬编码

- [ ] `grep -r '#[0-9a-fA-F]\{6\}' src/` 仅出现在 token 文件中
- [ ] `grep -r 'color:' src/ | grep -v 'var('` 仅出现在 token 文件中
- [ ] ESLint 规则禁止内联色值（可选）

#### 16.4 语义令牌使用

- [ ] 业务代码使用 `var(--dms-text-1)` 而非 `var(--dms-gray-800)`
- [ ] 业务代码使用 `var(--dms-color-primary)` 而非 `var(--dms-blue-500)`
- [ ] 业务代码使用 `var(--dms-spacing-4)` 而非 `16px`

#### 16.5 暗色模式

- [ ] `html.dark` 类下定义暗色 token
- [ ] 切换暗色模式不破坏布局
- [ ] 文本与背景对比度 ≥ 4.5:1（WCAG AA）

#### 16.6 租户品牌

- [ ] `tenants.brand_config` 字段存在
- [ ] 登录后主色按租户配置变化
- [ ] 平台后台不受租户品牌影响

#### 16.7 可访问性

- [ ] 焦点可见（`--dms-shadow-focus`）
- [ ] 尊重 `prefers-reduced-motion`
- [ ] 色盲友好（图表色板）

---

### 17. 附录：Token 命名规范

#### 17.1 命名结构

```
--dms-{category}-{property}-{variant}
```

| 部分 | 说明 | 示例 |
|---|---|---|
| `--dms-` | 前缀，避免冲突 | `--dms-` |
| `{category}` | 类别 | `color` / `text` / `bg` / `border` / `font` / `spacing` / `radius` / `shadow` / `motion` / `z-index` |
| `{property}` | 属性 | `primary` / `success` / `1` / `sm` / `base` |
| `{variant}` | 变体（可选） | `hover` / `active` / `bg` / `border` |

#### 17.2 命名示例

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

#### 17.3 色阶命名约定

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

#### 17.4 禁止事项

1. **禁止业务代码引用 Layer 1**：`var(--dms-blue-500)` ❌ → `var(--dms-color-primary)` ✅
2. **禁止硬编码色值**：`color: #1677ff` ❌ → `color: var(--dms-color-primary)` ✅
3. **禁止 `!important`**：除非覆盖第三方库样式
4. **禁止内联 style 写色值**：`<div style="color: #1677ff">` ❌
5. **禁止重复定义**：同一语义只允许一个 token

---

> **下一步**：本规范评审通过后，按第 15 章落地步骤执行；建议先做第 1-4 步（约 4.5 人日）建立基础，再按页面分批改造存量代码。


---

## 二、移动端 H5 设计规范 v1.0


## DMS 移动端 H5 设计规范与组件方案

> **文档编号**：NEW-19 移动端补充
> **版本**：v1.0
> **创建日期**：2026-08-12
> **适用范围**：业务前台移动端 H5（frontend-vue/mobile）
> **关联文档**：
> - `15_补充需求/UI设计系统_Design_Token规范_v1.0.md`（PC 端 Token，移动端复用其 Layer 1/Layer 2）
> - `15_补充需求/DMS功能缺口与优化需求评估_v3.12.md` NEW-20（响应式）、NEW-25（表单交互）
> - `11_平台后台/13_移动端精简方案_v3.9.0.md`（移动端功能范围）
> **目的**：在 PC 端 Design Token 基础上，补充移动端特有的适配、安全区域、触摸交互、手势、组件、动效规范，作为移动端 H5 开发唯一依据

---

### 目录

1. [设计原则与 PC 端复用关系](#1-设计原则与-pc-端复用关系)
2. [视口与尺寸适配](#2-视口与尺寸适配)
3. [安全区域适配](#3-安全区域适配)
4. [移动端字号与间距](#4-移动端字号与间距)
5. [触摸交互规范](#5-触摸交互规范)
6. [手势规范](#6-手势规范)
7. [移动端组件规范](#7-移动端组件规范)
8. [移动端动效规范](#8-移动端动效规范)
9. [移动端状态规范](#9-移动端状态规范)
10. [网络与离线策略](#10-网络与离线策略)
11. [性能规范](#11-性能规范)
12. [落地步骤与验收清单](#12-落地步骤与验收清单)

---

### 1. 设计原则与 PC 端复用关系

#### 1.1 设计原则

| 原则 | 说明 |
|---|---|
| **单手可达** | 核心操作放在屏幕下半部分（拇指热区），顶部仅放标题与返回 |
| **大点击区** | 所有可点击元素 ≥ 44×44px（Apple HIG），间距 ≥ 8px 防误触 |
| **信息精简** | 每屏只解决一个问题；列表项信息 ≤ 5 个字段；详情分卡片展示 |
| **即时反馈** | 点击必有反馈（ripple/active 态），加载必有提示（骨架屏 > spinner） |
| **原生体验** | 转场动画方向与系统一致（push 从右进，pop 向右出） |
| **弱网优先** | 默认按弱网设计：图片懒加载、列表分页、骨架屏、失败重试 |

#### 1.2 与 PC 端 Token 的复用关系

```
PC 端 Design Token（base-light.scss / semantic.scss）
        │
        ├─ Layer 1 调色板 ──────── 移动端 100% 复用（不重新定义色值）
        ├─ Layer 2 语义令牌 ────── 移动端 100% 复用
        │     --dms-color-primary / --dms-text-1 / --dms-bg-page ...
        │
        └─ 移动端特化层（mobile.scss，仅覆盖尺寸/间距/字号）
              --dms-mobile-tap-size: 44px
              --dms-mobile-font-size-base: 15px  /* 比 PC 14px 略大 */
              --dms-mobile-navbar-height: 44px
              --dms-mobile-tabbar-height: 50px
              --dms-mobile-safe-top: env(safe-area-inset-top)
              --dms-mobile-safe-bottom: env(safe-area-inset-bottom)
```

**核心铁律**：
- 移动端**颜色、字体族、圆角、阴影、动效时长**全部复用 PC 端语义令牌
- 移动端**字号、间距、组件尺寸**单独定义（在 `--dms-mobile-*` 命名空间下）
- 移动端**不引用 Layer 1**，只引用 Layer 2 语义令牌 + `--dms-mobile-*` 尺寸令牌

---

### 2. 视口与尺寸适配

#### 2.1 视口配置

```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
```

- `width=device-width`：以设备宽度为视口宽度
- `maximum-scale=1.0, user-scalable=no`：禁止双指缩放（避免误触缩放破坏布局）
- `viewport-fit=cover`：允许内容延伸到安全区域外（配合 `env(safe-area-inset-*)`）

#### 2.2 尺寸单位选择

| 单位 | 用途 | 说明 |
|---|---|---|
| `px` | 字号、边框、间距、组件尺寸 | ★ **推荐**，配合 `flexible.js` 或 `postcss-px-to-viewport` 自动换算 |
| `vw` | 全屏布局、字号根值 | 1vw = 视口宽度 1% |
| `%` | 容器内比例 | 卡片宽度、进度条 |
| `rem` | 根字号联动 | 不推荐，iOS 与 Android 字号策略不一致 |
| `pt` | ❌ 禁用 | 仅原生 App |

**推荐方案**：`postcss-px-to-viewport-8-plugin`，设计稿 375px → 自动转 vw

```javascript
// vite.config.js
import pxtoviewport from 'postcss-px-to-viewport-8-plugin'

export default {
  css: {
    postcss: {
      plugins: [
        pxtoviewport({
          viewportWidth: 375,        // 设计稿宽度
          unitToConvert: 'px',
          viewportUnit: 'vw',
          unitPrecision: 5,
          minPixelValue: 1,          // 1px 不转换（边框）
          selectorBlackList: ['.no-vw', '.van-'], // Vant 内部组件不转换
        })
      ]
    }
  }
}
```

#### 2.3 主流设备断点

| 设备 | 宽度 | 设计基准 |
|---|---|---|
| iPhone SE | 320px | 最小适配 |
| iPhone 12/13/14 | 390px | ★ **设计稿基准** |
| iPhone 14 Pro Max | 430px | 大屏 |
| Android 主流 | 360px | 360~414 |
| iPad | 768px+ | 建议跳转 PC 端 |

设计稿统一按 **375px** 出图，自动适配 320~430 全宽度。

---

### 3. 安全区域适配

#### 3.1 安全区域 Token

```scss
/* mobile.scss */
:root {
  --dms-mobile-safe-top: env(safe-area-inset-top, 0px);
  --dms-mobile-safe-bottom: env(safe-area-inset-bottom, 0px);
  --dms-mobile-safe-left: env(safe-area-inset-left, 0px);
  --dms-mobile-safe-right: env(safe-area-inset-right, 0px);

  /* 常用组合 */
  --dms-mobile-navbar-height: 44px;
  --dms-mobile-navbar-height-safe: calc(var(--dms-mobile-navbar-height) + var(--dms-mobile-safe-top));
  --dms-mobile-tabbar-height: 50px;
  --dms-mobile-tabbar-height-safe: calc(var(--dms-mobile-tabbar-height) + var(--dms-mobile-safe-bottom));
}
```

#### 3.2 适配规则

```scss
/* 顶部导航栏：延伸到状态栏，内容避开安全区 */
.mobile-navbar {
  padding-top: var(--dms-mobile-safe-top);
  height: var(--dms-mobile-navbar-height-safe);
  background: var(--dms-bg-container);
  position: sticky;
  top: 0;
  z-index: var(--dms-z-index-sticky);
}

/* 底部 Tab 栏：延伸到 home indicator，内容避开安全区 */
.mobile-tabbar {
  padding-bottom: var(--dms-mobile-safe-bottom);
  height: var(--dms-mobile-tabbar-height-safe);
  background: var(--dms-bg-container);
  border-top: 1px solid var(--dms-border-2);
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
}

/* 页面内容区：顶部避开导航栏，底部避开 Tab 栏 */
.mobile-page {
  padding-top: var(--dms-mobile-navbar-height-safe);
  padding-bottom: var(--dms-mobile-tabbar-height-safe);
  min-height: 100vh;
  box-sizing: border-box;
}

/* 底部固定操作按钮（无 Tab 栏页面） */
.mobile-action-bar {
  padding-bottom: var(--dms-mobile-safe-bottom);
  padding-left: var(--dms-spacing-4);
  padding-right: var(--dms-spacing-4);
  padding-top: var(--dms-spacing-3);
}
```

#### 3.3 横屏处理

横屏时高度骤减，建议：
- 顶部导航栏高度不变，但隐藏副标题
- 底部 Tab 栏改为侧边 Tab（可选）
- 表单字段横向铺开
- 或直接提示"请竖屏使用"

---

### 4. 移动端字号与间距

#### 4.1 字号令牌（移动端特化）

移动端字号比 PC 略大（屏幕小、视距远），基准 15px。

| Token | 字号 | 行高 | 用途 |
|---|---|---|---|
| `--dms-mobile-font-size-xs` | 11px | 16px | 角标、badge |
| `--dms-mobile-font-size-sm` | 12px | 18px | 辅助说明、时间戳 |
| `--dms-mobile-font-size-base` | 15px | 22px | ★ **正文默认** |
| `--dms-mobile-font-size-md` | 16px | 24px | 卡片标题、列表主标题 |
| `--dms-mobile-font-size-lg` | 18px | 26px | 页面大标题、导航栏标题 |
| `--dms-mobile-font-size-xl` | 20px | 28px | 数字强调 |
| `--dms-mobile-font-size-kpi` | 28px | 34px | KPI 数字 |
| `--dms-mobile-font-size-display` | 36px | 42px | 仪表盘大数字 |

#### 4.2 间距令牌

移动端间距与 PC 端共用 `--dms-spacing-*`，但常用值偏小（屏幕窄）。

| Token | 值 | 移动端用途 |
|---|---|---|
| `--dms-spacing-1` | 4px | icon 与文字间距 |
| `--dms-spacing-2` | 8px | 列表项内元素间距、tag 间距 |
| `--dms-spacing-3` | 12px | 卡片内 padding、列表项间 |
| `--dms-spacing-4` | 16px | ★ 页面左右 margin、卡片 padding |
| `--dms-spacing-5` | 20px | 区块间 |
| `--dms-spacing-6` | 24px | 大区块间 |

**移动端语义映射**：

```scss
:root {
  --dms-mobile-page-padding-x: var(--dms-spacing-4);   /* 页面左右 padding */
  --dms-mobile-card-padding: var(--dms-spacing-3);     /* 卡片 padding */
  --dms-mobile-cell-padding-y: var(--dms-spacing-3);   /* 列表项上下 padding */
  --dms-mobile-cell-gap: var(--dms-spacing-2);         /* 列表项内元素 gap */
  --dms-mobile-section-gap: var(--dms-spacing-5);      /* 区块间 gap */
}
```

#### 4.3 组件尺寸令牌

```scss
:root {
  --dms-mobile-tap-size: 44px;                /* 最小点击区（Apple HIG） */
  --dms-mobile-navbar-height: 44px;
  --dms-mobile-tabbar-height: 50px;
  --dms-mobile-tabbar-item-width: auto;
  --dms-mobile-search-height: 32px;
  --dms-mobile-button-height: 44px;           /* 主按钮高度 */
  --dms-mobile-button-height-sm: 32px;        /* 小按钮 */
  --dms-mobile-input-height: 44px;            /* 输入框高度 */
  --dms-mobile-card-radius: var(--dms-radius-lg); /* 8px */
  --dms-mobile-avatar-size: 40px;             /* 头像 */
  --dms-mobile-avatar-size-lg: 64px;
  --dms-mobile-icon-size: 20px;               /* 导航 icon */
  --dms-mobile-icon-size-lg: 24px;
}
```

---

### 5. 触摸交互规范

#### 5.1 点击区域

| 元素 | 最小尺寸 | 说明 |
|---|---|---|
| 按钮 | 44×44px | 主操作按钮 |
| 列表项 | 高度 ≥ 48px | 整行可点 |
| 图标按钮 | 44×44px（含 padding） | 可视 icon 20~24px，点击区扩大 |
| Tab 项 | 高度 50px，宽度均分 | |
| 复选框/单选 | 44×44px 点击区 | 可视框 16~20px，点击区扩大 |
| 链接 | 行高 ≥ 32px | 避免误触 |

#### 5.2 间距防误触

- 相邻可点击元素间距 ≥ 8px
- 列表项操作按钮组间距 ≥ 12px

#### 5.3 点击反馈

```scss
/* 所有可点击元素必须有 active 态反馈 */
.tap-feedback {
  cursor: pointer;
  -webkit-tap-highlight-color: transparent; /* 移除 iOS 默认灰色高亮 */
}

.tap-feedback:active {
  opacity: 0.7;           /* 通用反馈：透明度降低 */
  transform: scale(0.98); /* 轻微缩小 */
  transition: transform 100ms ease-out;
}

/* 卡片类点击反馈 */
.card-tap:active {
  background: var(--dms-bg-hover);
}
```

#### 5.4 输入交互

- 输入框聚焦时：自动滚动到可视区（避免键盘遮挡）
- 数字输入：`inputmode="numeric"`
- 手机号：`inputmode="tel"`
- 邮箱：`inputmode="email"`
- 搜索：`inputmode="search"`，键盘显示"搜索"键
- 日期：优先用原生 `type="date"`（调用系统日期选择器）
- 长表单：分步表单或锚点导航，避免滚动过长
- 键盘弹出时：底部固定按钮上移（`visualViewport` 监听）

```javascript
// 监听键盘弹出，避免底部按钮被遮挡
if (window.visualViewport) {
  window.visualViewport.addEventListener('resize', () => {
    const actionbar = document.querySelector('.mobile-action-bar')
    if (actionbar) {
      actionbar.style.bottom = `${window.innerHeight - window.visualViewport.height}px`
    }
  })
}
```

---

### 6. 手势规范

#### 6.1 手势清单

| 手势 | 场景 | 实现 |
|---|---|---|
| 点击 | 通用触发 | `@click` |
| 长按 | 列表项多选、删除 | `@touchstart` + 500ms 计时 |
| 左滑 | 列表项显示删除按钮 | `@touchstart/@touchmove/@touchend` |
| 右滑 | 返回上一页 | `@touchstart/@touchmove/@touchend`，从屏幕左 20px 起 |
| 下拉 | 列表刷新 | `@touchstart/@touchmove/@touchend`，顶部触发 |
| 上拉 | 列表加载更多 | 滚动到底部触发 |
| 双指缩放 | ❌ 禁用 | viewport 已禁止 |

#### 6.2 左滑删除规范

```scss
.swipe-cell {
  position: relative;
  overflow: hidden;
}
.swipe-cell-content {
  transition: transform 200ms ease-out;
  background: var(--dms-bg-container);
}
.swipe-cell-action {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
}
.swipe-cell-action-delete {
  width: 80px;
  background: var(--dms-color-danger);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--dms-mobile-font-size-sm);
}
/* 左滑 80px 显示删除按钮 */
.swipe-cell.swiped .swipe-cell-content {
  transform: translateX(-80px);
}
```

#### 6.3 下拉刷新规范

- 下拉距离 ≥ 60px 触发刷新
- 下拉过程显示旋转 loading（不要文字）
- 释放后回弹 + 显示"刷新中"
- 刷新完成自动收起（300ms 延迟）

#### 6.4 上拉加载规范

- 滚动到底部 50px 触发
- 显示"加载中..."spinner
- 无更多数据时显示"没有更多了"
- 加载失败显示"加载失败，点击重试"

---

### 7. 移动端组件规范

#### 7.1 导航栏（NavBar）

```scss
.mobile-navbar {
  padding-top: var(--dms-mobile-safe-top);
  height: var(--dms-mobile-navbar-height-safe);
  background: var(--dms-bg-container);
  border-bottom: 1px solid var(--dms-border-2);
  display: flex;
  align-items: center;
  position: sticky;
  top: 0;
  z-index: var(--dms-z-index-sticky);
}
.mobile-navbar-left,
.mobile-navbar-right {
  min-width: var(--dms-mobile-tap-size);
  height: var(--dms-mobile-navbar-height);
  display: flex;
  align-items: center;
  padding: 0 var(--dms-spacing-3);
}
.mobile-navbar-title {
  flex: 1;
  text-align: center;
  font-size: var(--dms-mobile-font-size-lg);
  font-weight: var(--dms-font-weight-medium);
  color: var(--dms-text-1);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  padding: 0 var(--dms-spacing-2);
}
```

**变体**：
- 默认：白底黑字
- 主色：主色背景白字（详情页）
- 透明：沉浸式（图片头），滚动后变白底

#### 7.2 Tab 栏（TabBar）

```scss
.mobile-tabbar {
  padding-bottom: var(--dms-mobile-safe-bottom);
  height: var(--dms-mobile-tabbar-height-safe);
  background: var(--dms-bg-container);
  border-top: 1px solid var(--dms-border-2);
  display: flex;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: var(--dms-z-index-sticky);
}
.mobile-tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--dms-text-4);
  font-size: 10px;
  transition: var(--dms-transition-color);
}
.mobile-tabbar-item.active {
  color: var(--dms-color-primary);
}
.mobile-tabbar-item-icon {
  font-size: 22px;
  line-height: 1;
}
```

**Tab 数量**：4~5 个为佳，超过 5 个时第 5 个改为"更多"抽屉。

#### 7.3 搜索栏

```scss
.mobile-search {
  display: flex;
  align-items: center;
  gap: var(--dms-spacing-2);
  height: var(--dms-mobile-search-height);
  padding: 0 var(--dms-spacing-3);
  background: var(--dms-bg-page);
  border-radius: var(--dms-radius-pill);
  font-size: var(--dms-mobile-font-size-sm);
  color: var(--dms-text-placeholder);
}
.mobile-search-focused {
  background: var(--dms-bg-container);
  border: 1px solid var(--dms-color-primary);
}
```

#### 7.4 卡片（Card）

```scss
.mobile-card {
  background: var(--dms-bg-container);
  border-radius: var(--dms-mobile-card-radius);
  padding: var(--dms-mobile-card-padding);
  margin: 0 var(--dms-mobile-page-padding-x) var(--dms-spacing-3);
  box-shadow: var(--dms-shadow-sm);
}
.mobile-card-title {
  font-size: var(--dms-mobile-font-size-md);
  font-weight: var(--dms-font-weight-medium);
  color: var(--dms-text-1);
  margin-bottom: var(--dms-spacing-2);
}
.mobile-card-body {
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-2);
}
```

#### 7.5 列表项（Cell）

```scss
.mobile-cell {
  display: flex;
  align-items: center;
  padding: var(--dms-mobile-cell-padding-y) var(--dms-mobile-page-padding-x);
  min-height: 48px;
  background: var(--dms-bg-container);
  border-bottom: 1px solid var(--dms-border-3);
}
.mobile-cell:last-child {
  border-bottom: none;
}
.mobile-cell-label {
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-2);
  min-width: 80px;
}
.mobile-cell-value {
  flex: 1;
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-1);
  text-align: right;
}
.mobile-cell-arrow {
  color: var(--dms-gray-400);
  margin-left: var(--dms-spacing-1);
}
```

#### 7.6 按钮

```scss
.mobile-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--dms-mobile-button-height);
  padding: 0 var(--dms-spacing-4);
  font-size: var(--dms-mobile-font-size-base);
  border-radius: var(--dms-radius-base);
  border: 1px solid var(--dms-border-1);
  background: var(--dms-bg-container);
  color: var(--dms-text-2);
}
.mobile-btn-primary {
  background: var(--dms-color-primary);
  border-color: var(--dms-color-primary);
  color: #fff;
}
.mobile-btn-block { width: 100%; }
.mobile-btn-round { border-radius: var(--dms-radius-pill); }
```

#### 7.7 表单

```scss
.mobile-form-group {
  background: var(--dms-bg-container);
  border-radius: var(--dms-mobile-card-radius);
  overflow: hidden;
}
.mobile-form-item {
  display: flex;
  align-items: center;
  padding: var(--dms-spacing-3) var(--dms-spacing-4);
  min-height: 48px;
  border-bottom: 1px solid var(--dms-border-3);
}
.mobile-form-label {
  width: 90px;
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-2);
}
.mobile-form-label .req { color: var(--dms-color-danger); margin-right: 2px; }
.mobile-form-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-1);
  text-align: right;
}
.mobile-form-input::placeholder { color: var(--dms-text-placeholder); text-align: right; }
```

#### 7.8 状态标签（StatusTag）

```scss
.mobile-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--dms-spacing-2);
  font-size: var(--dms-mobile-font-size-xs);
  line-height: 16px;
  border-radius: var(--dms-radius-sm);
}
.mobile-tag-draft     { background: var(--dms-gray-100); color: var(--dms-gray-600); }
.mobile-tag-pending   { background: var(--dms-color-warning-bg); color: var(--dms-color-warning); }
.mobile-tag-approved  { background: var(--dms-color-success-bg); color: var(--dms-color-success); }
.mobile-tag-rejected  { background: var(--dms-color-danger-bg); color: var(--dms-color-danger); }
.mobile-tag-effective { background: var(--dms-blue-50); color: var(--dms-blue-600); }
```

#### 7.9 弹窗（Dialog）

```scss
.mobile-dialog-mask {
  position: fixed;
  inset: 0;
  background: var(--dms-bg-mask);
  z-index: var(--dms-z-index-modal);
  display: flex;
  align-items: center;
  justify-content: center;
}
.mobile-dialog {
  width: 80%;
  max-width: 300px;
  background: var(--dms-bg-elevated);
  border-radius: var(--dms-radius-lg);
  overflow: hidden;
  animation: dialogIn 200ms ease-out;
}
@keyframes dialogIn {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}
.mobile-dialog-header {
  padding: var(--dms-spacing-5) var(--dms-spacing-4) var(--dms-spacing-2);
  text-align: center;
  font-size: var(--dms-mobile-font-size-md);
  font-weight: var(--dms-font-weight-medium);
  color: var(--dms-text-1);
}
.mobile-dialog-body {
  padding: 0 var(--dms-spacing-4) var(--dms-spacing-4);
  text-align: center;
  font-size: var(--dms-mobile-font-size-sm);
  color: var(--dms-text-3);
}
.mobile-dialog-footer {
  display: flex;
  border-top: 1px solid var(--dms-border-2);
}
.mobile-dialog-btn {
  flex: 1;
  height: 44px;
  background: none;
  border: none;
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-2);
}
.mobile-dialog-btn + .mobile-dialog-btn { border-left: 1px solid var(--dms-border-2); }
.mobile-dialog-btn-primary { color: var(--dms-color-primary); font-weight: var(--dms-font-weight-medium); }
```

#### 7.10 动作面板（ActionSheet）

从底部滑出的菜单，用于选择操作。

```scss
.mobile-action-sheet-mask {
  position: fixed;
  inset: 0;
  background: var(--dms-bg-mask);
  z-index: var(--dms-z-index-modal);
}
.mobile-action-sheet {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--dms-bg-container);
  border-radius: var(--dms-radius-xl) var(--dms-radius-xl) 0 0;
  padding-bottom: var(--dms-mobile-safe-bottom);
  animation: actionSheetIn 250ms ease-out;
}
@keyframes actionSheetIn {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
.mobile-action-sheet-item {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-2);
  border-bottom: 1px solid var(--dms-border-3);
}
.mobile-action-sheet-item.danger { color: var(--dms-color-danger); }
.mobile-action-sheet-cancel {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--dms-mobile-font-size-base);
  color: var(--dms-text-3);
  margin-top: var(--dms-spacing-2);
  background: var(--dms-bg-container);
}
```

#### 7.11 Toast

```scss
.mobile-toast {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(31, 35, 41, 0.9);
  color: #fff;
  padding: var(--dms-spacing-3) var(--dms-spacing-4);
  border-radius: var(--dms-radius-md);
  font-size: var(--dms-mobile-font-size-sm);
  z-index: var(--dms-z-index-loading);
  max-width: 80%;
  text-align: center;
  animation: toastIn 200ms ease-out;
}
@keyframes toastIn {
  from { opacity: 0; transform: translate(-50%, -45%); }
  to { opacity: 1; transform: translate(-50%, -50%); }
}
```

#### 7.12 骨架屏

```scss
.mobile-skeleton {
  background: linear-gradient(90deg, var(--dms-gray-100) 25%, var(--dms-gray-200) 37%, var(--dms-gray-100) 63%);
  background-size: 400% 100%;
  animation: skeletonShimmer 1.5s ease infinite;
  border-radius: var(--dms-radius-sm);
}
@keyframes skeletonShimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
.mobile-skeleton-text { height: 14px; margin-bottom: var(--dms-spacing-2); }
.mobile-skeleton-title { height: 18px; width: 40%; margin-bottom: var(--dms-spacing-3); }
.mobile-skeleton-avatar { width: 40px; height: 40px; border-radius: 50%; }
```

---

### 8. 移动端动效规范

#### 8.1 页面转场

| 场景 | 动效 | 时长 |
|---|---|---|
| push（前进） | 新页从右滑入，旧页轻微左移 | 300ms |
| pop（返回） | 当前页右滑出，旧页轻微右移回 | 300ms |
| modal 上滑 | 从底部滑入 | 250ms |
| modal 下滑 | 向底部滑出 | 200ms |
| Tab 切换 | 淡入淡出 | 200ms |

```scss
.page-enter-active, .page-leave-active {
  transition: transform 300ms ease-out, opacity 300ms ease-out;
}
.page-enter-from { transform: translateX(100%); opacity: 0.5; }
.page-leave-to { transform: translateX(-30%); opacity: 0.5; }
```

#### 8.2 列表动画

- 列表项进入：从下方淡入上移（stagger 50ms）
- 列表项删除：高度收缩 + 淡出
- 列表项排序：FLIP 动画

#### 8.3 数字滚动

KPI 数字使用滚动动画（`CountUp.js` 或自实现）。

---

### 9. 移动端状态规范

#### 9.1 空状态

```html
<div class="mobile-empty">
  <div class="mobile-empty-icon">📭</div>
  <div class="mobile-empty-text">暂无订单</div>
  <button class="mobile-btn mobile-btn-primary">去下单</button>
</div>
```

- 图标：48~64px，灰色
- 文案：辅助色，14px
- 操作按钮：可选

#### 9.2 加载状态

| 场景 | 方案 |
|---|---|
| 首屏加载 | 骨架屏（与最终布局一致） |
| 下拉刷新 | 顶部旋转 loading |
| 上拉加载 | 底部 spinner + 文案 |
| 按钮提交 | 按钮内 spinner + 禁用 |
| 全屏加载 | 居中 spinner + 文案 |

#### 9.3 错误状态

| 场景 | 方案 |
|---|---|
| 网络断开 | 顶部红色提示条 + 自动重连 |
| 接口报错 | Toast 提示 + 重试按钮 |
| 页面 404 | 空状态 + 返回首页 |
| 页面 500 | 空状态 + 联系客服 |

---

### 10. 网络与离线策略

#### 10.1 网络状态监听

```javascript
// 监听网络状态
window.addEventListener('online', () => {
  showToast('网络已恢复')
  // 重新加载当前页数据
})
window.addEventListener('offline', () => {
  showOfflineBar()
})
```

#### 10.2 弱网优化

- 图片懒加载（`loading="lazy"`）
- 图片压缩（WebP，宽度按设备 dpr 出图）
- 列表分页（每页 20 条）
- 接口数据缓存（localStorage / IndexedDB）
- 请求超时 10s

#### 10.3 离线模式（二期）

- 核心数据本地缓存（订单列表、产品目录）
- 离线创建订单暂存本地，联网后同步
- 离线状态标识

---

### 11. 性能规范

| 指标 | 目标 | 方案 |
|---|---|---|
| 首屏 LCP | < 2.5s | 路由懒加载 + 骨架屏 |
| FID | < 100ms | 代码分割 + defer |
| CLS | < 0.1 | 固定宽高、骨架屏占位 |
| 包体积 | < 200KB（gzip） | 按需引入 Vant |
| 图片 | WebP + 懒加载 | `loading="lazy"` |
| 接口 | 并发 ≤ 6 | 合并请求 |

---

### 12. 落地步骤与验收清单

#### 12.1 落地步骤

1. **创建 `mobile.scss`**（0.5 人日）：定义 `--dms-mobile-*` 令牌
2. **配置 postcss-px-to-viewport**（0.5 人日）：375 设计稿自动转 vw
3. **抽取移动端通用组件**（2 人日）：NavBar/TabBar/Card/Cell/Button/Form/Tag/Dialog/ActionSheet/Toast/Skeleton/Empty
4. **手势组件**（1 人日）：SwipeCell/PullRefresh/LoadMore
5. **改造存量页面**（3-5 人日）：5 Tab 8 页面逐个改造
6. **真机测试**（1 人日）：iOS Safari + Android Chrome

#### 12.2 验收清单

##### 视口与适配
- [ ] viewport 配置正确（含 viewport-fit=cover）
- [ ] 375 设计稿在 320/390/430 宽度下无横向滚动
- [ ] 安全区域适配（刘海屏 + home indicator）

##### 触摸交互
- [ ] 所有可点击元素 ≥ 44×44px
- [ ] 点击有 active 反馈
- [ ] 相邻可点击元素间距 ≥ 8px
- [ ] 输入框聚焦不被键盘遮挡

##### 组件
- [ ] NavBar 高度 44px + 安全区 padding
- [ ] TabBar 高度 50px + 安全区 padding
- [ ] 列表项高度 ≥ 48px
- [ ] 卡片圆角 8px + 阴影 sm
- [ ] 状态标签颜色与 PC 一致

##### 手势
- [ ] 左滑显示删除按钮
- [ ] 下拉刷新可用
- [ ] 上拉加载更多可用
- [ ] 右滑返回（详情页）

##### 状态
- [ ] 空状态有图标 + 文案 + 操作
- [ ] 首屏加载骨架屏
- [ ] 接口报错 Toast + 重试
- [ ] 网络断开提示

##### 动效
- [ ] 页面转场 300ms
- [ ] Tab 切换 200ms 淡入
- [ ] Dialog/ActionSheet 进出动效
- [ ] 尊重 prefers-reduced-motion

##### 性能
- [ ] 首屏 LCP < 2.5s
- [ ] 图片懒加载
- [ ] 包体积 < 200KB（gzip）

---

> **下一步**：本规范评审通过后，配合 `UI设计系统_Demo.html`（PC 端）与 `UI设计系统_Mobile_Demo.html`（移动端）demo 验证效果；建议先做第 1-3 步（约 3 人日）建立基础。


---
