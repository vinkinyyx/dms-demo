import request from '@/utils/request'

// ===== 审批模板 =====
export function listTemplates(params) {
  return request({ url: '/api/approval/templates', method: 'get', params })
}
export function getTemplate(id) {
  return request({ url: '/api/approval/templates/' + id, method: 'get' })
}
export function createTemplate(data) {
  return request({ url: '/api/approval/templates', method: 'post', data })
}
export function updateTemplate(id, data) {
  return request({ url: '/api/approval/templates/' + id, method: 'put', data })
}
export function publishTemplate(id) {
  return request({ url: '/api/approval/templates/' + id + '/publish', method: 'post' })
}
export function disableTemplate(id) {
  return request({ url: '/api/approval/templates/' + id + '/disable', method: 'post' })
}
export function newTemplateVersion(id) {
  return request({ url: '/api/approval/templates/' + id + '/new-version', method: 'post' })
}

// ===== 审批实例 / 任务 =====
export function startApproval(data) {
  return request({ url: '/api/approval/instances/start', method: 'post', data })
}
export function myTodoTasks(params) {
  return request({ url: '/api/approval/tasks/my-todo', method: 'get', params })
}
export function myDoneTasks(params) {
  return request({ url: '/api/approval/tasks/my-done', method: 'get', params })
}
export function mySubmitted(params) {
  return request({ url: '/api/approval/instances/my-submitted', method: 'get', params })
}
export function myCc(params) {
  return request({ url: '/api/approval/cc/my', method: 'get', params })
}
export function adminInstances(params) {
  return request({ url: '/api/approval/admin/instances', method: 'get', params })
}
export function getInstance(id) {
  return request({ url: '/api/approval/instances/' + id, method: 'get' })
}
export function latestInstance(businessType, businessId) {
  return request({ url: '/api/approval/instances/by-business', method: 'get', params: { businessType, businessId } })
}
export function withdrawInstance(id, comment) {
  return request({ url: '/api/approval/instances/' + id + '/withdraw', method: 'post', data: { comment } })
}
export function approveTask(id, comment) {
  return request({ url: '/api/approval/tasks/' + id + '/approve', method: 'post', data: { comment } })
}
export function rejectTask(id, comment) {
  return request({ url: '/api/approval/tasks/' + id + '/reject', method: 'post', data: { comment } })
}
export function transferTask(id, targetUserId, comment) {
  return request({ url: '/api/approval/tasks/' + id + '/transfer', method: 'post', data: { targetUserId, comment } })
}
export function addSignTask(id, targetUserId, signType, comment) {
  return request({ url: '/api/approval/tasks/' + id + '/add-sign', method: 'post', data: { targetUserId, signType, comment } })
}
export function reassignTask(id, targetUserId, reason) {
  return request({ url: '/api/approval/admin/tasks/' + id + '/reassign', method: 'post', data: { targetUserId, reason } })
}
export function terminateInstance(id, reason) {
  return request({ url: '/api/approval/admin/instances/' + id + '/terminate', method: 'post', data: { reason } })
}

// ===== 委托 =====
export function listDelegations(params) {
  return request({ url: '/api/approval/delegations', method: 'get', params })
}
export function createDelegation(data) {
  return request({ url: '/api/approval/delegations', method: 'post', data })
}
export function disableDelegation(id) {
  return request({ url: '/api/approval/delegations/' + id + '/disable', method: 'post' })
}

// ===== 人员选择辅助 =====
export function listUsers(params) {
  return request({ url: '/api/users', method: 'get', params })
}
export function listRoles() {
  return request({ url: '/api/roles', method: 'get' })
}