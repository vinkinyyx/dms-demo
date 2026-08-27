import request from '@/utils/request'

// 产品全局折扣
export function listProductGlobalDiscounts(params) {
  return request({ url: '/api/product-global-discounts', method: 'get', params })
}
export function getProductGlobalDiscount(id) {
  return request({ url: '/api/product-global-discounts/' + id, method: 'get' })
}
export function createProductGlobalDiscount(data) {
  return request({ url: '/api/product-global-discounts', method: 'post', data })
}
export function updateProductGlobalDiscount(id, data) {
  return request({ url: '/api/product-global-discounts/' + id, method: 'put', data })
}
export function deleteProductGlobalDiscount(id) {
  return request({ url: '/api/product-global-discounts/' + id, method: 'delete' })
}
export function activateProductGlobalDiscount(id) {
  return request({ url: `/api/product-global-discounts/${id}/activate`, method: 'post' })
}
export function deactivateProductGlobalDiscount(id) {
  return request({ url: `/api/product-global-discounts/${id}/deactivate`, method: 'post' })
}

// 客户（经销商）全局折扣
export function listDealerGlobalDiscounts(params) {
  return request({ url: '/api/dealer-global-discounts', method: 'get', params })
}
export function getDealerGlobalDiscount(id) {
  return request({ url: '/api/dealer-global-discounts/' + id, method: 'get' })
}
export function createDealerGlobalDiscount(data) {
  return request({ url: '/api/dealer-global-discounts', method: 'post', data })
}
export function updateDealerGlobalDiscount(id, data) {
  return request({ url: '/api/dealer-global-discounts/' + id, method: 'put', data })
}
export function deleteDealerGlobalDiscount(id) {
  return request({ url: '/api/dealer-global-discounts/' + id, method: 'delete' })
}
export function activateDealerGlobalDiscount(id) {
  return request({ url: `/api/dealer-global-discounts/${id}/activate`, method: 'post' })
}
export function deactivateDealerGlobalDiscount(id) {
  return request({ url: `/api/dealer-global-discounts/${id}/deactivate`, method: 'post' })
}
