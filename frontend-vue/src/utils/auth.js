const ACCESS_KEY = 'dms_access_token'
const REFRESH_KEY = 'dms_refresh_token'
const USER_KEY = 'dms_user'

export function getToken() {
  return localStorage.getItem(ACCESS_KEY)
}
export function setToken(token) {
  localStorage.setItem(ACCESS_KEY, token)
}
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}
export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_KEY, token)
}
export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch (e) {
    return {}
  }
}
export function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
}
export function clearAuth() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}


const PERMISSIONS_KEY = 'dms:user:permissions'
export function getPermissions() {
  try {
    const raw = localStorage.getItem(PERMISSIONS_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
export function setPermissions(perms) {
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(Array.isArray(perms) ? perms : []))
}
export function clearPermissions() {
  localStorage.removeItem(PERMISSIONS_KEY)
}

const PREFS_KEY = 'dms:user:prefs'
export function getPrefs() {
  try { return JSON.parse(localStorage.getItem(PREFS_KEY) || '{}') } catch { return {} }
}
export function setPrefs(prefs) {
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs || {}))
}
export function updatePrefs(patch) {
  const prefs = { ...getPrefs(), ...(patch || {}) }
  setPrefs(prefs)
  return prefs
}
