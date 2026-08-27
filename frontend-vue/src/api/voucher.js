import request from '@/utils/request'

// 代金券管理（厂家发放/管理）
export function batchIssueVouchers(data) {
  return request({ url: '/api/customer-vouchers/batch-issue', method: 'post', data })
}
export function listVouchers(params) {
  return request({ url: '/api/customer-vouchers', method: 'get', params })
}
export function getVoucher(id) {
  return request({ url: '/api/customer-vouchers/' + id, method: 'get' })
}
export function disableVoucher(id) {
  return request({ url: `/api/customer-vouchers/${id}/disable`, method: 'post' })
}
export function enableVoucher(id) {
  return request({ url: `/api/customer-vouchers/${id}/enable`, method: 'post' })
}
export function voidVoucher(id) {
  return request({ url: `/api/customer-vouchers/${id}/void`, method: 'post' })
}
// 下单页可用券查询
export function availableVouchers(params) {
  return request({ url: '/api/customer-vouchers/available', method: 'get', params })
}
