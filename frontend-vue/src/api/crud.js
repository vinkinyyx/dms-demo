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

export function getAuditLogs(resourceType, resourceId) {
  return request({ url: '/api/operation-logs', method: 'get', params: { resourceType, resourceId }, skip404Redirect: true })
}

export function getBizOperationLogs(businessType, businessId) {
  return request({ url: `/api/operation-log/list/${businessType}/${businessId}`, method: 'get', skip404Redirect: true })
}

export async function getOperationLogs(resourceType, resourceId, businessType) {
  const safeAudit = () => getAuditLogs(resourceType, resourceId).catch((e) => {
    if (e?.response?.status !== 405) console.warn('加载审计日志失败', e)
    return null
  })
  const safeBiz = () => businessType ? getBizOperationLogs(businessType, resourceId).catch((e) => {
    if (e?.response?.status !== 405) console.warn('加载业务操作日志失败', e)
    return null
  }) : Promise.resolve(null)
  const [auditRes, bizRes] = await Promise.allSettled([safeAudit(), safeBiz()])
  const out = []
  if (auditRes.status === 'fulfilled' && Array.isArray(auditRes.value?.data)) {
    auditRes.value.data.forEach((item) => out.push({
      username: item.userName || item.username || '系统',
      action: item.actionLabel || item.action,
      changes: parseDetail(item.detail),
      atTime: item.atTime
    }))
  }
  if (bizRes.status === 'fulfilled' && bizRes.value?.data) {
    const records = bizRes.value.data.records || bizRes.value.data.list || (Array.isArray(bizRes.value.data) ? bizRes.value.data : [])
    records.forEach((item) => out.push({
      username: item.operatorName || item.username || '系统',
      action: item.remark || item.action,
      changes: item.changeJson || '',
      atTime: item.createdAt || item.operationTime
    }))
  }
  out.sort((a, b) => String(b.atTime || '').localeCompare(String(a.atTime || '')))
  return { data: out }
}

function parseDetail(detail) {
  if (!detail) return ''
  try {
    const d = typeof detail === 'string' ? JSON.parse(detail) : detail
    return d.note || d.remark || ''
  } catch (e) { return String(detail) }
}

export function httpGet(url, params, options = {}) {
  return request({ url, method: 'get', params, ...options })
}
