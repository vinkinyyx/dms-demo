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
  return request({ url: '/api/sales-orders', method: 'post', data })
}

export function previewOrder(data) {
  return request({ url: '/api/sales-orders/preview', method: 'post', data })
}

export function listSalesOrders(params) {
  return request({ url: '/api/sales-orders', method: 'get', params })
}

export function getSalesOrder(id) {
  return request({ url: '/api/sales-orders/' + id, method: 'get' })
}

export function submitSalesOrder(id) {
  return request({ url: '/api/sales-orders/' + id + '/submit', method: 'post' })
}

export function createPurchaseOrder(data) {
  return request({ url: '/api/purchase-orders', method: 'post', data })
}
