# MySolMed 品牌 Logo 资产库（Brand Logo Kit）

全站统一的 MySolMed 标识。所有环境、所有页面出现 logo 的地方都使用这里的同一套图形：
圆润小写 `m`（Quicksand 字形）+ 右上品牌青点；深藏青 `#0B2545` + 亮青 `#00B4D8`。

## 目录结构

- `svg/` 矢量源文件（内嵌字体，任意放大不失真，**优先使用**）
- `png/` 位图（按文件名尺寸取用）
- `fonts/` Quicksand 可变字体（OFL 开源，可商用）
- `showcase/logo-kit.html` / `logo-kit.png` 全版本总览（浏览器打开 html 即可查看）
- `tools/generate-logos.cjs` 重新生成脚本：`node brand-logo/tools/generate-logos.cjs`

## 版本与选用规则

| 版本 | 文件 | 用在什么底色 |
|------|------|--------------|
| 字标·藏青 | `mysolmed-wordmark.svg/png` | 白 / 浅灰 / 浅彩底 |
| 字标·反白 | `mysolmed-wordmark-white.svg/png` | 藏青 / 黑 / 深照片底 |
| 字标·单色 | `mysolmed-wordmark-mono.svg` | 黑白打印 / 传真 |
| 图形标·藏青 | `mysolmed-mark.svg/png` | 浅色侧边栏 / 浅底（小尺寸方形） |
| 图形标·反白 | `mysolmed-mark-white.svg/png` | 深色侧边栏 / 深色英雄区 |
| 图形标·单色 | `mysolmed-mark-mono.svg` | 单色场景 |
| App 图标 | `favicon.svg`、`favicon-16/32/48.png`、`apple-touch-icon.png(180)`、`mysolmed-appicon-192/512.png` | 浏览器标签 / 收藏夹 / 手机主屏（自带藏青底） |

**底色规则**：背景明度高 → 用藏青版；背景明度低 → 用反白版。青点在所有版本恒为 `#00B4D8`。
禁止藏青底放藏青 logo、白底放反白 logo；不得拉伸变形、不得改青点颜色。

## 全站接入点（已统一）

- PC 业务前台 / 平台后台：`frontend-vue`、`admin-vue` 的 `src/assets/brand/logo-mark*.png`
  与 `public/favicon*`、`public/apple-touch-icon.png`、`public/favicon.svg`（经 `DmsLogo` 组件全站生效，组件零改动）。
- 宣传手册（PC / 移动 / 打印）：`DMS产品宣传手册/pages/*.html` 使用内联字标 SVG
  （字母 `currentColor` 跟随底色、青点固定、字体内嵌）。

> 线上生效需重新构建前端并部署；本目录为源资产，改造后重新 `npm run build` 并发布即可。
