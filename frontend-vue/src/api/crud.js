import request from '@/utils/request'

export function listResource(api, params) {
  return request({ url: api, method: 'get', params })
}

export function getResource(api, id) {
  return request({ url: api + '/' + id, method: 'get' })
}

export function getDetail(api, id) {
  return request({ url: api + '/' + id, method: 'get' })
}

export function createResource(api, data) {
  return request({ url: api, method: 'post', data })
}

export function updateResource(api, id, data) {
  return request({ url: api + '/' + id, method: 'put', data })
}

export function deleteResource(api, id) {
  return request({ url: api + '/' + id, method: 'delete' })
}

export function actionResource(api, id, path, method, data) {
  return request({ url: api + '/' + id + path, method: method || 'post', data })
}

export function lookup(resource, params) {
  return request({ url: '/api/lookups/' + resource, method: 'get', params: { limit: 50, ...(params || {}) } })
}

export function getOperationLogs(businessType, businessId) {
  return request({ url: `/api/operation-log/list/${businessType}/${businessId}`, method: 'get' })
}

export function httpGet(url, params) {
  return request({ url, method: 'get', params })
}
