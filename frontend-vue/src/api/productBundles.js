import { listResource, getResource, createResource, updateResource, deleteResource } from './crud'

const API_BASE = '/api/product-bundles'

export function listProductBundles(params) {
  return listResource(API_BASE, params)
}

export function getProductBundle(id) {
  return getResource(API_BASE, id)
}

export function createProductBundle(data) {
  return createResource(API_BASE, data)
}

export function updateProductBundle(id, data) {
  return updateResource(API_BASE, id, data)
}

export function deleteProductBundle(id) {
  return deleteResource(API_BASE, id)
}
