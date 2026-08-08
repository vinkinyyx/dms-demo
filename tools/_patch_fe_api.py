from pathlib import Path
p=Path('frontend-vue/src/api/admin.js')
s=p.read_text(encoding='utf-8')
s += '''

// === v3.8.9 租户管理员维护本租户列表页覆盖配置 ===
export function getTenantFilters(pageKey) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/filters', method: 'get' }) }
export function saveTenantFilters(pageKey, filters) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/filters', method: 'post', data: filters }) }
export function getTenantButtons(pageKey) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/buttons', method: 'get' }) }
export function saveTenantButtons(pageKey, buttons) { return request({ url: '/api/tenant-ui/pages/' + pageKey + '/buttons', method: 'post', data: { buttons } }) }
export function getRolePermissions(roleId) { return request({ url: '/api/roles/' + roleId + '/permissions', method: 'get' }) }
export function setRolePermissions(roleId, resourceCodes) { return request({ url: '/api/roles/' + roleId + '/permissions', method: 'put', data: { resourceCodes } }) }
'''
p.write_text(s, encoding='utf-8', newline='\n')
