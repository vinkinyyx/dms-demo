/*
 * v-has 权限指令。
 *
 * 用法：
 *   <el-button v-has="'products:export'">导出</el-button>
 *   <el-button v-has="['products:edit', 'products:delete']">操作</el-button>  (任一命中即通过)
 *
 * 数据源（按顺序回退）：
 *   1) useUserStore().permissions
 *   2) useUserStore().user.permissions
 *   3) localStorage 'dms:user:permissions'
 *   4) localStorage 'dms_user.permissions' / 'dms_user.roles'
 *
 * 未命中：从 DOM 移除元素（不留白），符合 Layer 2 第十八章 §18.1 铁律 4。
 */
import { useUserStore } from '@/store/user'
import { getPermissions, getToken } from '@/utils/auth'

let permissionReady = null

function hasToken() {
  try { return !!getToken() } catch { return false }
}

function loadAll() {
  const out = new Set()
  try {
    const s = useUserStore()
    if (s) {
      if (Array.isArray(s.permissions)) s.permissions.forEach(p => out.add(p))
      const u = s.user || {}
      if (Array.isArray(u.permissions)) u.permissions.forEach(p => out.add(p))
      if (Array.isArray(u.roles)) u.roles.forEach(p => out.add(p))
    }
  } catch { /* store 未注册 */ }
  for (const p of getPermissions()) out.add(p)
  return out
}

export async function ensurePermissions() {
  if (!hasToken()) return loadAll()
  if (permissionReady) return permissionReady
  try {
    const store = useUserStore()
    permissionReady = Promise.resolve(store.fetchPermissions ? store.fetchPermissions() : [])
    await permissionReady
  } catch {
// Permission directive: missing or failed permission checks hide elements.
  } finally {
    permissionReady = null
  }
  return loadAll()
}

function check(value, set) {
  if (value == null || value === '' || value === false) return true
  if (Array.isArray(value)) return value.some(v => set.has(String(v)))
  return set.has(String(value))
}

function apply(el, value) {
  if (!check(value, loadAll())) {
    if (el && el.parentNode) el.parentNode.removeChild(el)
  }
}

export const has = {
  mounted(el, binding) { apply(el, binding.value) },
  updated(el, binding) { apply(el, binding.value) }
}

export default {
  install(app) { app.directive('has', has) }
}
