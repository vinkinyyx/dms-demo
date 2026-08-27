import request from '@/utils/request'

// 合同价格清单（路径注意：资源 id 在 /api/contracts/prices/{id}）
export function listContractPrices(contractId, params) {
  return request({ url: `/api/contracts/${contractId}/prices`, method: 'get', params })
}
export function createContractPrice(contractId, data) {
  return request({ url: `/api/contracts/${contractId}/prices`, method: 'post', data })
}
export function updateContractPrice(id, data) {
  return request({ url: `/api/contracts/prices/${id}`, method: 'put', data })
}
export function deleteContractPrice(id) {
  return request({ url: `/api/contracts/prices/${id}`, method: 'delete' })
}
export function batchSaveContractPrices(contractId, rows) {
  return request({ url: `/api/contracts/${contractId}/prices/batch`, method: 'post', data: rows })
}
export function importContractPrices(contractId, file) {
  const form = new FormData()
  form.append('file', file)
  return request({ url: `/api/contracts/${contractId}/prices/actions/import`, method: 'post', data: form, headers: { 'Content-Type': 'multipart/form-data' } })
}
function downloadBlob(url) {
  return request({ url, method: 'get', responseType: 'blob' }).then((res) => {
    const blob = res instanceof Blob ? res : new Blob([res])
    const a = document.createElement('a')
    a.href = window.URL.createObjectURL(blob)
    const m = /filename\*?=(?:UTF-8'')?([^;]+)/i.exec(blob.type || '')
    a.download = 'contract-prices.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(a.href)
  })
}
export function exportContractPrices(contractId) {
  return downloadBlob(`/api/contracts/${contractId}/prices/actions/export`)
}
export function downloadContractPriceTemplate() {
  return downloadBlob('/api/contracts/0/prices/actions/export-template')
}
