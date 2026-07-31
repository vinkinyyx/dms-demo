import { listResource, getResource, createResource, updateResource, deleteResource } from './crud'

const API_BASE = '/api/product-lines'

export function listProductLines(params) {
  return listResource(API_BASE, params)
}

export function getProductLine(id) {
  return getResource(API_BASE, id)
}

export function createProductLine(data) {
  return createResource(API_BASE, data)
}

export function updateProductLine(id, data) {
  return updateResource(API_BASE, id, data)
}

export function deleteProductLine(id) {
  return deleteResource(API_BASE, id)
}
