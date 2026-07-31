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
