import { listResource, getResource, createResource, updateResource, deleteResource } from './crud'

const API_BASE = '/api/product-package-levels'

export function listProductPackageLevels(params) {
  return listResource(API_BASE, params)
}

export function getProductPackageLevel(id) {
  return getResource(API_BASE, id)
}

export function createProductPackageLevel(data) {
  return createResource(API_BASE, data)
}

export function updateProductPackageLevel(id, data) {
  return updateResource(API_BASE, id, data)
}

export function deleteProductPackageLevel(id) {
  return deleteResource(API_BASE, id)
}
