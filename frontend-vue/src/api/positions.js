import request from '@/utils/request'

export function salesPositionsTree() {
  return request({ url: '/api/sales-positions/tree', method: 'get' })
}
export function salesPositions(params) {
  return request({ url: '/api/sales-positions', method: 'get', params })
}
export function createSalesPosition(data) {
  return request({ url: '/api/sales-positions', method: 'post', data })
}
export function updateSalesPosition(id, data) {
  return request({ url: '/api/sales-positions/' + id, method: 'put', data })
}
export function deleteSalesPosition(id) {
  return request({ url: '/api/sales-positions/' + id, method: 'delete' })
}
export function candidateUsers(role) {
  return request({ url: '/api/sales-positions/candidate-users', method: 'get', params: role ? { role } : {} })
}
export function bindDealers(id, data) {
  return request({ url: '/api/sales-positions/' + id + '/bind-dealers', method: 'put', data })
}
export function bindUsers(id, data) {
  return request({ url: '/api/sales-positions/' + id + '/bind-users', method: 'put', data })
}
export function getPositionUsers(id, role) {
  return request({ url: '/api/sales-positions/' + id + '/users', method: 'get', params: role ? { role } : {} })
}
export function getPositionDealers(id) {
  return request({ url: '/api/sales-positions/' + id + '/dealers', method: 'get' })
}
export function getCandidateUsers(id) {
  return request({ url: '/api/sales-positions/' + id + '/candidates/users', method: 'get' })
}
export function getCandidateDealers(id) {
  return request({ url: '/api/sales-positions/' + id + '/candidates/dealers', method: 'get' })
}
