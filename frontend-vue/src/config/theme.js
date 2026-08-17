/**
 * DMS Design Token — 运行期色值常量
 * 用于 ECharts 等需要真实色值（无法使用 CSS 变量）的场景。
 * 业务组件/样式应优先使用 CSS 变量 var(--dms-*)，而非引用此处常量。
 * 色值与 styles/tokens/base-light.scss 保持一致。
 */
export const DMS_COLORS = {
  primary: '#1677ff',
  primaryHover: '#4096ff',
  primaryActive: '#0958d9',
  primaryBg: '#e6f4ff',
  success: '#52c41a',
  successBg: '#f6ffed',
  warning: '#faad14',
  warningBg: '#fffbe6',
  danger: '#ff4d4f',
  dangerBg: '#fff2f0',
  info: '#909399',
  text1: '#1f2329',
  text2: '#303133',
  text3: '#606266',
  text4: '#909399',
  border: '#e4e7ed',
  bgPage: '#f5f7fa',
  bgContainer: '#ffffff'
}

/** ECharts 图表色板（色盲友好，与规范一致） */
export const DMS_CHART_PALETTE = [
  '#1677ff', '#52c41a', '#faad14', '#ff4d4f',
  '#722ed1', '#13c2c2', '#eb2f96', '#fa8c16'
]

/** 注册到 ECharts 的主题对象（echarts.registerTheme('dms', DMS_ECHART_THEME)） */
export const DMS_ECHART_THEME = {
  color: DMS_CHART_PALETTE,
  backgroundColor: 'transparent',
  textStyle: { color: DMS_COLORS.text2, fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif' },
  title: { textStyle: { color: DMS_COLORS.text1, fontSize: 16, fontWeight: 600 } },
  legend: { textStyle: { color: DMS_COLORS.text3 } },
  tooltip: {
    backgroundColor: 'rgba(31,35,41,0.9)',
    borderColor: 'transparent',
    textStyle: { color: '#fff', fontSize: 12 }
  },
  grid: { left: 48, right: 24, top: 40, bottom: 32, containLabel: true }
}

/** 读取 CSS 变量解析后的色值（用于需要运行时取色的场景） */
export function cssToken(name) {
  if (typeof window === 'undefined') return ''
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}
