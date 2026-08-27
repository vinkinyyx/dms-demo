import request from '@/utils/request'

// 客户自助注册审核（厂家管理员）
export function listRegistrations(params) {
  return request({ url: '/api/customer-registrations', method: 'get', params })
}
export function getRegistration(id) {
  return request({ url: '/api/customer-registrations/' + id, method: 'get' })
}
export function approveRegistration(id) {
  return request({ url: `/api/customer-registrations/${id}/approve`, method: 'post' })
}
export function rejectRegistration(id, rejectReason) {
  return request({ url: `/api/customer-registrations/${id}/reject`, method: 'post', data: { rejectReason } })
}

// 客户联系人
export function listDealerContacts(params) {
  return request({ url: '/api/dealer-contacts', method: 'get', params })
}
export function listAllDealerContacts(dealerId) {
  return request({ url: '/api/dealer-contacts/all', method: 'get', params: { dealerId } })
}
export function createDealerContact(data) {
  return request({ url: '/api/dealer-contacts', method: 'post', data })
}
export function updateDealerContact(id, data) {
  return request({ url: '/api/dealer-contacts/' + id, method: 'put', data })
}
export function deleteDealerContact(id) {
  return request({ url: '/api/dealer-contacts/' + id, method: 'delete' })
}
export function setDefaultDealerContact(id) {
  return request({ url: `/api/dealer-contacts/${id}/set-default`, method: 'post' })
}

// 客户收货地址
export function listDealerAddresses(params) {
  return request({ url: '/api/dealer-addresses', method: 'get', params })
}
export function listAllDealerAddresses(dealerId) {
  return request({ url: '/api/dealer-addresses/all', method: 'get', params: { dealerId } })
}
export function createDealerAddress(data) {
  return request({ url: '/api/dealer-addresses', method: 'post', data })
}
export function updateDealerAddress(id, data) {
  return request({ url: '/api/dealer-addresses/' + id, method: 'put', data })
}
export function deleteDealerAddress(id) {
  return request({ url: '/api/dealer-addresses/' + id, method: 'delete' })
}
export function setDefaultDealerAddress(id) {
  return request({ url: `/api/dealer-addresses/${id}/set-default`, method: 'post' })
}
