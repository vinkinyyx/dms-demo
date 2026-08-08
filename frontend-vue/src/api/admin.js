import request from '@/utils/request'

export function systemStats() { return request({ url: '/api/system/stats', method: 'get' }) }
export function auditLogs(params) { return request({ url: '/api/system/audit-logs', method: 'get', params }) }
export function loginLogs(params) { return request({ url: '/api/system/login-logs', method: 'get', params }) }
export function notifications(params) { return request({ url: '/api/system/notifications', method: 'get', params }) }
export function tenantsBrief() { return request({ url: '/api/system/tenants-brief', method: 'get' }) }
export function tenants(params) { return request({ url: '/api/tenants', method: 'get', params }) }
export function createTenant(data) { return request({ url: '/api/tenants', method: 'post', data }) }
export function updateTenant(id, data) { return request({ url: '/api/tenants/' + id, method: 'put', data }) }
export function deleteTenant(id) { return request({ url: '/api/tenants/' + id, method: 'delete' }) }
export function settings() { return request({ url: '/api/system/settings', method: 'get' }) }
export function dicts() { return request({ url: '/api/system/dicts', method: 'get' }) }
export function dictTypes() { return request({ url: '/api/dicts/types', method: 'get' }) }
export function dictItems(typeCode) { return request({ url: '/api/dicts/' + typeCode + '/items', method: 'get' }) }
export function createDictItem(typeCode, data) { return request({ url: '/api/dicts/' + typeCode + '/items', method: 'post', data }) }
export function updateDictItem(id, data) { return request({ url: '/api/dicts/items/' + id, method: 'put', data }) }
export function deleteDictItem(id) { return request({ url: '/api/dicts/items/' + id, method: 'delete' }) }
export function createDictType(data) { return request({ url: '/api/dicts/types', method: 'post', data }) }

export function unlockUser(id) { return request({ url: '/api/users/' + id + '/unlock', method: 'post' }) }
export function resetPassword(id) { return request({ url: '/api/users/' + id + '/reset-password', method: 'post' }) }

export function rbacMatrix() { return request({ url: '/api/system-ops/rbac/matrix', method: 'get' }) }
export function cacheStatus() { return request({ url: '/api/system-ops/cache/status', method: 'get' }) }
export function cacheFlush() { return request({ url: '/api/system-ops/cache/flush', method: 'post' }) }
export function checkTimeouts() { return request({ url: '/api/system-ops/check-timeouts', method: 'post' }) }
export function menuConfigs() { return request({ url: '/api/menu-configs', method: 'get' }) }
export function health() { return request({ url: '/actuator/health', method: 'get' }) }

export function opLogsDownload(date) {
  return request({
    url: '/api/admin/op-logs/download',
    method: 'get',
    params: { date },
    responseType: 'blob'
  })
}


// === D13: 列表页布局 / 按钮配置 ===
/** 业务前台：聚合下发 filter + page + toolbar + row 四套配置（合并平台默认 + 租户覆盖） */
export function getPageLayout(pageKey) { return request({ url: '/api/ui/layout/' + pageKey, method: 'get' }) }
/** 业务前台：单独取某 scope 的按钮列表（toolbar / row） */
export function getMergedButtons(pageKey, scope) { return request({ url: '/api/button-configs/pages/' + pageKey + '/' + scope, method: 'get' }) }

// === v3.8.9 租户管理员维护本租户列表页覆盖配置 ===
export function getTenantFilters(pageKey) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/filters', method: 'get' }) }
export function saveTenantFilters(pageKey, filters) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/filters', method: 'post', data: filters }) }
export function getTenantButtons(pageKey) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/buttons', method: 'get' }) }
export function saveTenantButtons(pageKey, buttons) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/buttons', method: 'post', data: { buttons } }) }
export function getRolePermissions(roleId) { return request({ url: '/api/roles/' + roleId + '/permissions', method: 'get' }) }
export function setRolePermissions(roleId, resourceCodes) { return request({ url: '/api/roles/' + roleId + '/permissions', method: 'put', data: { resourceCodes } }) }
