import request from '@/utils/request'

// ===== 合同 =====
export function listContracts(params) {
  return request({ url: '/api/contracts', method: 'get', params })
}
export function getContract(id) {
  return request({ url: '/api/contracts/' + id, method: 'get' })
}
export function createContract(data) {
  return request({ url: '/api/contracts', method: 'post', data })
}
export function updateContract(id, data) {
  return request({ url: '/api/contracts/' + id, method: 'put', data })
}
export function deleteContract(id) {
  return request({ url: '/api/contracts/' + id, method: 'delete' })
}
export function submitContract(id) {
  return request({ url: '/api/contracts/' + id + '/submit', method: 'post' })
}
export function withdrawContract(id) {
  return request({ url: '/api/contracts/' + id + '/withdraw', method: 'post' })
}
export function terminateContract(id, data) {
  return request({ url: '/api/contracts/' + id + '/terminate', method: 'post', data })
}
export function addContractAttachment(id, data) {
  return request({ url: '/api/contracts/' + id + '/attachments', method: 'post', params: data })
}
export function deleteContractAttachment(id, attId) {
  return request({ url: '/api/contracts/' + id + '/attachments/' + attId, method: 'delete' })
}

// ===== 合同模板 =====
export function listTemplates(params) {
  return request({ url: '/api/contract-templates', method: 'get', params })
}
export function getTemplate(id) {
  return request({ url: '/api/contract-templates/' + id, method: 'get' })
}
export function matchTemplate(category) {
  return request({ url: '/api/contract-templates/match', method: 'get', params: { category } })
}
export function createTemplate(data) {
  return request({ url: '/api/contract-templates', method: 'post', data })
}
export function updateTemplate(id, data) {
  return request({ url: '/api/contract-templates/' + id, method: 'put', data })
}
export function publishTemplate(id) {
  return request({ url: '/api/contract-templates/' + id + '/publish', method: 'post' })
}
export function newTemplateVersion(id) {
  return request({ url: '/api/contract-templates/' + id + '/new-version', method: 'post' })
}
export function disableTemplate(id) {
  return request({ url: '/api/contract-templates/' + id + '/disable', method: 'post' })
}
export function deleteTemplate(id) {
  return request({ url: '/api/contract-templates/' + id, method: 'delete' })
}
export function exportContracts(params) {
  return request({ url: '/api/contracts/actions/export', method: 'get', params, responseType: 'blob' })
}