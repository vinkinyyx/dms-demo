// 设备形态判定 + PC/移动视图手动覆盖偏好
// 单 SPA（VITE_BASE=/dms/）同时承载 PC 业务端与移动 H5：
// - 移动设备访问任意 PC 业务路由时，自动分流到 /mobile/*；
// - 用户可在移动端「我的」里手动「切换到电脑版」，本次会话内不再自动弹回。

const PREF_KEY = 'dms_view_pref'

// UA 形态判定：手机/平板归为移动端，桌面（含触控笔记本）归为 PC
export function detectMobileByUA(ua = '') {
  const s = String(ua || '').toLowerCase()
  if (/android|iphone|ipad|ipod|windows phone|harmonyos|mobile/.test(s)) return true
  // iPadOS 13+ 默认伪装成 Mac，需用触控点兜底
  if (/macintosh/.test(s) && typeof navigator !== 'undefined' && navigator.maxTouchPoints > 1) return true
  return false
}

export function isMobileDevice() {
  if (typeof navigator === 'undefined') return false
  return detectMobileByUA(navigator.userAgent)
}

// 用户手动选择的视图形态：'pc' | 'mobile' | null
export function getViewPref() {
  try { return sessionStorage.getItem(PREF_KEY) } catch (e) { return null }
}

export function setViewPref(pref) {
  try {
    if (!pref) sessionStorage.removeItem(PREF_KEY)
    else sessionStorage.setItem(PREF_KEY, pref)
  } catch (e) { /* ignore */ }
}

export function clearViewPref() {
  try { sessionStorage.removeItem(PREF_KEY) } catch (e) { /* ignore */ }
}

// 当前应使用的形态：手动覆盖优先，否则按设备自动判定
export function shouldUseMobile() {
  const pref = getViewPref()
  if (pref === 'pc') return false
  if (pref === 'mobile') return true
  return isMobileDevice()
}
