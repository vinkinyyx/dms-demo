import request from '@/utils/request'

// 客户公开注册（无需登录）
export function customerRegister(data) {
  return request({
    url: '/api/auth/customer-register',
    method: 'post',
    data,
    headers: { Authorization: '' },
    skipAuthRefresh: true
  })
}

// v4.3.0 计价预览
export function previewV43(data) {
  return request({ url: '/api/sales-orders/preview', method: 'post', data })
}

// 可用代金券
export function availableVouchers(params) {
  return request({ url: '/api/customer-vouchers/available', method: 'get', params })
}

// 客户收货地址
export function listDealerAddresses(dealerId) {
  return request({ url: '/api/dealer-addresses/all', method: 'get', params: { dealerId } })
}

// 创建销售订单
export function createSalesOrderV43(data) {
  return request({ url: '/api/sales-orders', method: 'post', data })
}

// 提交销售订单
export function submitSalesOrderV43(id) {
  return request({ url: '/api/sales-orders/' + id + '/submit', method: 'post' })
}

// 文件上传（注册资质附件，无需登录）
export function uploadRegisterFile(formData) {
  return request.post('/api/files/upload?bizType=customerRegister', formData, {
    headers: { 'Content-Type': 'multipart/form-data', Authorization: '' },
    skipAuthRefresh: true
  })
}
