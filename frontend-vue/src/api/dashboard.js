import request from '@/utils/request'

export function getKpi() {
  return request({ url: '/api/dashboard/kpi', method: 'get' })
}
export function getSalesTrend() {
  return request({ url: '/api/dashboard/sales-trend', method: 'get' })
}
export function getInventoryPie() {
  return request({ url: '/api/dashboard/inventory-pie', method: 'get' })
}
export function getTopDealers() {
  return request({ url: '/api/dashboard/top-dealers', method: 'get' })
}
export function getOrderFunnel() {
  return request({ url: '/api/dashboard/order-funnel', method: 'get' })
}
export function getTopHospitals() {
  return request({ url: '/api/dashboard/top-hospitals', method: 'get' })
}
export function getActivity7d() {
  return request({ url: '/api/dashboard/activity-7d', method: 'get' })
}
