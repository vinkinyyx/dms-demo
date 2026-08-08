import request from '@/utils/request'

export const listProductMappings = (params) => request({ url: '/api/product-mappings', method: 'get', params })
export const getProductMapping = (id) => request({ url: `/api/product-mappings/${id}`, method: 'get' })
export const createProductMapping = (data) => request({ url: '/api/product-mappings', method: 'post', data })
export const updateProductMapping = (id, data) => request({ url: `/api/product-mappings/${id}`, method: 'put', data })
export const enableProductMapping = (id) => request({ url: `/api/product-mappings/${id}/enable`, method: 'post' })
export const disableProductMapping = (id) => request({ url: `/api/product-mappings/${id}/disable`, method: 'post' })
export const listMyDealerTenants = () => request({ url: '/api/my-dealer-tenants', method: 'get' })
export const downloadTemplateUrl = '/api/product-mappings/template'
export const previewImport = (formData) => request({ url: '/api/product-mappings/import/preview', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
export const confirmImport = (batchId) => request({ url: '/api/product-mappings/import/confirm', method: 'post', params: { batchId } })
export const importErrorsUrl = (id) => `/api/product-mappings/import-batches/${id}/errors`
