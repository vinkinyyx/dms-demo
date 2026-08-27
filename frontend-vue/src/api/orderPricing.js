import request from '@/utils/request'

// v4.3.0 计价预览：行折扣/整单折扣/一口价/整单0/代金券/促销，全部由后端重算
export function calcPreview(data) {
  return request({ url: '/api/sales-orders/preview', method: 'post', data })
}

// 客户可用代金券（下单页）。productIds 为逗号分隔的产品 id
export function availableVouchers(params) {
  return request({ url: '/api/customer-vouchers/available', method: 'get', params })
}

// 客户收货地址列表
export function dealerAddresses(dealerId) {
  return request({ url: '/api/dealer-addresses/all', method: 'get', params: { dealerId } })
}

// 可退货的销售出库单列表（支持按经销商/日期/批号/产品过滤）
export function shippedOuts(params) {
  return request({ url: '/api/sales-returns/shipped-outs', method: 'get', params })
}

// 单张出库单的可退明细
export function shippedOutLines(salesOutId) {
  return request({ url: `/api/sales-returns/shipped-outs/${salesOutId}/lines`, method: 'get' })
}

// v4.3.0 多出库单销退：创建并提交（body: { reason, outboundLines:[{salesOutId,salesOutLineId,qty,reason}] }）
export function createRmaOrder(data) {
  return request({ url: '/api/rma/orders', method: 'post', data, skipDuplicate: true })
}

// 销退单列表（v4.3.0）
export function listRmaOrders(params) {
  return request({ url: '/api/rma/orders', method: 'get', params })
}

// 销退单详情（v4.3.0，返回 outboundGroups 分组明细）
export function getRmaOrder(id) {
  return request({ url: `/api/rma/orders/${id}`, method: 'get' })
}