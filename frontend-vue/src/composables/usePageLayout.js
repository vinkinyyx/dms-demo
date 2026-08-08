/*
 * usePageLayout — 拉取 /api/ui/layout/{pageKey} 聚合配置（filter+page+toolbar+row）。
 *
 * 设计：
 *  - 一次调用返回平台默认 + 租户覆盖后的合并结果；
 *  - 内置 5min 内存缓存，跨页/跨模块复用；
 *  - 暴露工具方法 hasPermission / visibleToolbar / visibleRowButtons。
 *
 * 关联规范：Layer 2 §18 列表页布局规范。
 */
import { reactive, ref } from 'vue'
import { getPageLayout } from '@/api/admin'
import { useUserStore } from '@/store/user'
import { getPermissions } from '@/utils/auth'

const cache = new Map() // pageKey -> { at, value }
const TTL_MS = 5 * 60 * 1000

function loadPermissions() {
  const out = new Set()
  try {
    const s = useUserStore()
    if (s) {
      if (Array.isArray(s.permissions)) s.permissions.forEach((p) => out.add(p))
      const u = s.user || {}
      if (Array.isArray(u.permissions)) u.permissions.forEach((p) => out.add(p))
      if (Array.isArray(u.roles)) u.roles.forEach((p) => out.add(p))
    }
  } catch { /* store 未注册 */ }
  for (const p of getPermissions()) out.add(p)
  return out
}

export function usePageLayout(pageKey) {
  const layout = reactive({ filters: [], columns: [], toolbar: [], rowButtons: [] })
  const loading = ref(false)
  const error = ref(null)

  async function load(force = false) {
    const key = String(pageKey || '')
    if (!key) return
    if (!force) {
      const hit = cache.get(key)
      if (hit && Date.now() - hit.at < TTL_MS) {
        const v = hit.value || { filters: [], columns: [], toolbar: [], rowButtons: [] }
        Object.assign(layout, JSON.parse(JSON.stringify(v)))
        return
      }
    }
    loading.value = true
    error.value = null
    try {
      const res = await getPageLayout(key)
      const value = (res && res.data) || { filters: [], columns: [], toolbar: [], rowButtons: [] }
      Object.assign(layout, value)
      cache.set(key, { at: Date.now(), value: JSON.parse(JSON.stringify(value)) })
    } catch (e) {
      error.value = e
      Object.assign(layout, { filters: [], columns: [], toolbar: [], rowButtons: [] })
    } finally {
      loading.value = false
    }
  }

  function refresh() { return load(true) }

  function hasPermission(code) {
    if (!code) return true
    return loadPermissions().has(String(code))
  }

  function visibleToolbar() {
    return (layout.toolbar || [])
      .filter((b) => b.visible !== false)
      .filter((b) => hasPermission(b.permissionCode))
      .slice()
      .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100))
  }

  function visibleRowButtons() {
    return (layout.rowButtons || [])
      .filter((b) => b.visible !== false)
      .filter((b) => hasPermission(b.permissionCode))
      .slice()
      .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100))
  }

  function clearCache(pageKey2) {
    if (pageKey2) cache.delete(String(pageKey2))
    else cache.clear()
  }

  return {
    layout,
    loading,
    error,
    load,
    refresh,
    hasPermission,
    visibleToolbar,
    visibleRowButtons,
    clearCache
  }
}

export function invalidatePageLayoutCache(pageKey) {
  if (pageKey) cache.delete(String(pageKey))
  else cache.clear()
}