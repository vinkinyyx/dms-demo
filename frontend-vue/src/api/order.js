import request from '@/utils/request'

export function inventoryByProduct(productId) {
  return request({ url: '/api/inventory-summary/by-product/' + productId, method: 'get' })
}

export function dealerOverview(dealerId) {
  return request({ url: '/api/inventory-summary/dealer-overview/' + dealerId, method: 'get' })
}

export function supplierOverview(supplierId) {
  return request({ url: '/api/suppliers/' + supplierId, method: 'get' })
}

export function createOrder(data) {
  return request({ url: '/api/orders', method: 'post', data })
}

export function createPurchaseOrder(data) {
  return request({ url: '/api/purchase-orders', method: 'post', data })
}
