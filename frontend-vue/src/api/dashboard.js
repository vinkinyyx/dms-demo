import request from '@/utils/request'

export function getKpi(params) {
  return request({ url: '/api/dashboard/kpi', method: 'get', params })
}
export function getSalesTrend(params) {
  return request({ url: '/api/dashboard/sales-trend', method: 'get', params })
}
export function getInventoryPie(params) {
  return request({ url: '/api/dashboard/inventory-pie', method: 'get', params })
}
export function getTopDealers(params) {
  return request({ url: '/api/dashboard/top-dealers', method: 'get', params })
}
export function getOrderFunnel(params) {
  return request({ url: '/api/dashboard/order-funnel', method: 'get', params })
}
export function getTopHospitals(params) {
  return request({ url: '/api/dashboard/top-hospitals', method: 'get', params })
}
export function getActivity7d(params) {
  return request({ url: '/api/dashboard/activity-7d', method: 'get', params })
}
