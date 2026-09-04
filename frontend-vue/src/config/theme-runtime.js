import { ref, computed } from 'vue'

const STORAGE_KEY = 'dms-theme-preference'
export const THEME_PRESETS = [
  { key: 'blue', name: '极光蓝', color: '#1677ff', gradients: ['#e6f4ff', '#f5f7ff'] },
  { key: 'violet', name: '星云紫', color: '#722ed1', gradients: ['#f9f0ff', '#f7f2ff'] },
  { key: 'green', name: '青翠绿', color: '#00b96b', gradients: ['#f6ffed', '#effdf6'] },
  { key: 'orange', name: '日暮橙', color: '#fa8c16', gradients: ['#fff7e6', '#fff3e8'] }
]
const paletteMap = {
  blue: { primary: '#1677ff', hover: '#4096ff', active: '#0958d9', bg: '#e6f4ff', border: '#91caff', dark: '#002c8c' },
  violet: { primary: '#722ed1', hover: '#9254de', active: '#531dab', bg: '#f9f0ff', border: '#d3adf7', dark: '#22075e' },
  green: { primary: '#00b96b', hover: '#29cc7d', active: '#009452', bg: '#f6ffed', border: '#b7eb8f', dark: '#135200' },
  orange: { primary: '#fa8c16', hover: '#ffa940', active: '#d46b08', bg: '#fff7e6', border: '#ffd591', dark: '#873800' }
}
const mode = ref(localStorage.getItem(`${STORAGE_KEY}:mode`) === 'dark' ? 'dark' : 'light')
// 菜单（侧边栏）独立深浅：sider='light' 浅色菜单（默认）/ 'dark' 深色菜单；仅作用于菜单区域，与整页明暗模式互不影响
const sider = ref(localStorage.getItem(`${STORAGE_KEY}:sider`) === 'dark' ? 'dark' : 'light')
const preset = ref(localStorage.getItem(`${STORAGE_KEY}:preset`) || 'blue')
if (!paletteMap[preset.value]) preset.value = 'blue'
export const currentThemePreset = computed(() => THEME_PRESETS.find(item => item.key === preset.value) || THEME_PRESETS[0])
export const currentSiderMode = computed(() => sider.value)

function applySiderVars(root, palette) {
  if (sider.value === 'dark') {
    root.style.setProperty('--dms-sider-bg', 'linear-gradient(180deg,#17233d 0%,#111b2f 100%)')
    root.style.setProperty('--dms-sider-bg-deep', 'rgba(255,255,255,.06)')
    root.style.setProperty('--dms-sider-text', '#b8c5d9')
    root.style.setProperty('--dms-sider-text-hover', '#fff')
    root.style.setProperty('--dms-sider-text-active', '#fff')
    root.style.setProperty('--dms-sider-active-bg', 'rgba(22,119,255,.30)')
    root.style.setProperty('--dms-sider-badge-bg', 'rgba(255,255,255,.10)')
    root.style.setProperty('--dms-sider-badge-text', '#b8c5d9')
    root.style.setProperty('--dms-sider-border', 'rgba(255,255,255,.06)')
  } else {
    root.style.setProperty('--dms-sider-bg', '#ffffff')
    root.style.setProperty('--dms-sider-bg-deep', '#f5f7fa')
    root.style.setProperty('--dms-sider-text', '#4b5563')
    root.style.setProperty('--dms-sider-text-hover', '#1f2937')
    root.style.setProperty('--dms-sider-text-active', palette.primary)
    root.style.setProperty('--dms-sider-active-bg', palette.bg)
    root.style.setProperty('--dms-sider-badge-bg', '#eef1f6')
    root.style.setProperty('--dms-sider-badge-text', '#8a94a6')
    root.style.setProperty('--dms-sider-border', '#eef0f4')
  }
}

function setRootVars() {
  const root = document.documentElement
  const palette = paletteMap[preset.value]
  root.dataset.theme = preset.value
  root.dataset.mode = mode.value
  root.dataset.sider = sider.value
  Object.entries({
    '--dms-color-primary': palette.primary,
    '--dms-color-primary-hover': palette.hover,
    '--dms-color-primary-active': palette.active,
    '--dms-color-primary-bg': palette.bg,
    '--dms-color-primary-border': palette.border,
    '--dms-blue-500': palette.primary,
    '--dms-blue-400': palette.hover,
    '--dms-blue-600': palette.active,
    '--dms-blue-50': palette.bg,
    '--dms-blue-300': palette.border,
    '--dms-blue-700': palette.dark
  }).forEach(([key, value]) => root.style.setProperty(key, value))
  if (mode.value === 'dark') {
    root.style.setProperty('--dms-bg-page', '#0f172a')
    root.style.setProperty('--dms-bg-container', '#111c33')
    root.style.setProperty('--dms-bg-elevated', '#16233f')
    root.style.setProperty('--dms-bg-hover', '#1c2b4a')
    root.style.setProperty('--dms-text-1', '#f8fafc')
    root.style.setProperty('--dms-text-2', '#dbe4f0')
    root.style.setProperty('--dms-text-3', '#aab8cc')
    root.style.setProperty('--dms-text-4', '#8a99b0')
    root.style.setProperty('--dms-border-1', '#2b3b5a')
    root.style.setProperty('--dms-border-2', '#233351')
    root.style.setProperty('--dms-border-3', '#1c2a44')
  } else {
    root.style.removeProperty('--dms-bg-page')
    root.style.removeProperty('--dms-bg-container')
    root.style.removeProperty('--dms-bg-elevated')
    root.style.removeProperty('--dms-bg-hover')
    root.style.removeProperty('--dms-text-1')
    root.style.removeProperty('--dms-text-2')
    root.style.removeProperty('--dms-text-3')
    root.style.removeProperty('--dms-text-4')
    root.style.removeProperty('--dms-border-1')
    root.style.removeProperty('--dms-border-2')
    root.style.removeProperty('--dms-border-3')
  }
  // 菜单深浅独立于整页明暗模式
  applySiderVars(root, palette)
  root.style.setProperty('--el-color-primary', palette.primary)
  root.style.setProperty('--el-color-primary-light-3', palette.hover)
  root.style.setProperty('--el-color-primary-light-5', palette.border)
  root.style.setProperty('--el-color-primary-light-7', palette.bg)
  root.style.setProperty('--el-color-primary-light-8', palette.bg)
  root.style.setProperty('--el-color-primary-light-9', palette.bg)
  root.style.setProperty('--el-color-primary-dark-2', palette.active)
  root.style.setProperty('--van-primary-color', palette.primary)
  root.style.setProperty('--van-nav-bar-background', mode.value === 'dark' ? '#111c33' : palette.bg)
  root.style.setProperty('--van-tabbar-item-active-color', palette.primary)
}
export function applyTheme(nextPreset = preset.value, nextMode = mode.value) {
  preset.value = paletteMap[nextPreset] ? nextPreset : 'blue'
  mode.value = nextMode === 'dark' ? 'dark' : 'light'
  localStorage.setItem(`${STORAGE_KEY}:preset`, preset.value)
  localStorage.setItem(`${STORAGE_KEY}:mode`, mode.value)
  setRootVars()
}
export function toggleMode() {
  applyTheme(preset.value, mode.value === 'dark' ? 'light' : 'dark')
}
// 主应用中内容区固定浅色：月亮/太阳按钮只用于切换菜单底色，不改变内容区
export function forceContentLight() {
  if (mode.value !== 'light') applyTheme(preset.value, 'light')
}
export function toggleSider() {
  sider.value = sider.value === 'dark' ? 'light' : 'dark'
  localStorage.setItem(`${STORAGE_KEY}:sider`, sider.value)
  document.documentElement.dataset.sider = sider.value
  const root = document.documentElement
  applySiderVars(root, paletteMap[preset.value])
}
export function setPreset(key) {
  applyTheme(key, mode.value)
}
export function initTheme() {
  setRootVars()
}
