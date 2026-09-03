import request from '@/utils/request'

// ===== 授权 =====
export function listAuthorizations(params) {
  return request({ url: '/api/authorizations', method: 'get', params })
}
export function getAuthorization(id) {
  return request({ url: '/api/authorizations/' + id, method: 'get' })
}
export function createAuthorization(data) {
  return request({ url: '/api/authorizations', method: 'post', data })
}
export function deleteAuthorization(id) {
  return request({ url: '/api/authorizations/' + id, method: 'delete' })
}
export function renewAuthorization(id, data) {
  return request({ url: `/api/authorizations/${id}/renew`, method: 'post', data })
}
export function terminateAuthorization(id, data) {
  return request({ url: `/api/authorizations/${id}/terminate`, method: 'post', data })
}
export function exportAuthorizations() {
  return request({ url: '/api/authorizations/actions/export', method: 'get', responseType: 'blob' })
}

// ===== 授权-下单挂钩开关 =====
export function getOrderEnforce() {
  return request({ url: '/api/authorizations/order-enforce', method: 'get' })
}
export function setOrderEnforce(enabled) {
  return request({ url: '/api/authorizations/order-enforce', method: 'post', data: { enabled } })
}

// ===== 选择器数据 =====
export function listAuthProductLines() {
  return request({ url: '/api/authorizations/product-lines', method: 'get' })
}
export function listAuthTerminals(params) {
  return request({ url: '/api/authorizations/terminals', method: 'get', params })
}
export function listDealers(params) {
  return request({ url: '/api/dealers', method: 'get', params })
}
export function listRegionsTree() {
  return request({ url: '/api/regions/tree', method: 'get' })
}
