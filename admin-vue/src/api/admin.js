import request from '@/utils/request'

export const listManufacturers = (params) => request.get('/api/admin/tenants/manufacturers', { params })
export const createManufacturer = (data) => request.post('/api/admin/tenants/manufacturers', data)
export const listDealers = (params) => request.get('/api/admin/tenants/dealers', { params })
export const createDealer = (data) => request.post('/api/admin/tenants/dealers', data)
export const getTenant = (id) => request.get(`/api/admin/tenants/${id}`)
export const getTenantStats = () => request.get('/api/admin/tenants/stats')
export const getBindings = (id) => request.get(`/api/admin/tenants/${id}/bindings`)
export const enableTenant = (id) => request.post(`/api/admin/tenants/${id}/enable`)
export const disableTenant = (id, reason) => request.post(`/api/admin/tenants/${id}/disable`, { reason })

export const listTenantAdmins = (params) => request.get('/api/admin/tenant-admins', { params })
export const createTenantAdmin = (data) => request.post('/api/admin/tenant-admins', data)
export const disableTenantAdmin = (id) => request.post(`/api/admin/tenant-admins/${id}/disable`)
export const resetTenantAdminPassword = (id, newPassword) => request.post(`/api/admin/tenant-admins/${id}/reset-password`, { newPassword })

export const listRoleTemplates = (params) => request.get('/api/admin/role-templates', { params })
export const createRoleTemplate = (data) => request.post('/api/admin/role-templates', data)
export const updateRoleTemplate = (id, data) => request.put(`/api/admin/role-templates/${id}`, data)
export const getTemplatePermissions = (id) => request.get(`/api/admin/role-templates/${id}/permissions`)
export const setTemplatePermissions = (id, resourceCodes) => request.put(`/api/admin/role-templates/${id}/permissions`, { resourceCodes })
export const listTemplateResources = (tenantType) => request.get('/api/admin/role-templates/resources', { params: { tenantType } })

export const listMenus = (params) => request.get('/api/admin/menus', { params })
export const createMenu = (data) => request.post('/api/admin/menus', data)
export const updateMenu = (id, data) => request.put(`/api/admin/menus/${id}`, data)
export const enableMenu = (id) => request.post(`/api/admin/menus/${id}/enable`)
export const disableMenu = (id) => request.post(`/api/admin/menus/${id}/disable`)
export const refreshMenuCache = () => request.post('/api/admin/menus/refresh-cache')

export const getPageConfigs = (params) => request.get('/api/admin/page-configs', { params })
export const upsertPageConfigs = (data) => request.put('/api/admin/page-configs', data)
export const getFilterConfigs = (params) => request.get('/api/admin/filter-configs', { params })
export const upsertFilterConfigs = (data) => request.put('/api/admin/filter-configs', data)
export const refreshUiCache = () => request.post('/api/admin/page-configs/refresh-cache')

export const listDictTypes = () => request.get('/api/admin/dicts/types')
export const createDictType = (data) => request.post('/api/admin/dicts/types', data)
export const updateDictType = (id, data) => request.put(`/api/admin/dicts/types/${id}`, data)
export const listDictItems = (code) => request.get(`/api/admin/dicts/types/${code}/items`)
export const createDictItem = (code, data) => request.post(`/api/admin/dicts/types/${code}/items`, data)
export const updateDictItem = (id, data) => request.put(`/api/admin/dicts/items/${id}`, data)
export const enableDictItem = (id) => request.post(`/api/admin/dicts/items/${id}/enable`)
export const disableDictItem = (id) => request.post(`/api/admin/dicts/items/${id}/disable`)
export const refreshDictCache = () => request.post('/api/admin/dicts/refresh-cache')

export const listApiLogs = (params) => request.get('/api/admin/logs/api', { params })
export const fetchApiLogFile = (id, kind) => request.get(`/api/admin/logs/api/${id}/${kind}-file`, { responseType: 'text', transformResponse: [v => v] })
export const downloadApiLogFile = (id, kind) => `/api/admin/logs/api/${id}/${kind}-file`
export const listAuditLogs = (params) => request.get('/api/admin/logs/platform-audits', { params })

export const getButtonConfigs = (params) => request.get('/api/admin/buttons', { params })
export const upsertButtonConfigs = (data) => request.post('/api/admin/buttons/batch', data)
export const refreshButtonCache = () => request.post('/api/admin/buttons/refresh-cache')

export const getMailSwitches = () => request.get('/api/admin/mail-config/switches')
export const updateMailSwitch = (key, enabled) => request.post('/api/admin/mail-config/switches', { key, enabled })