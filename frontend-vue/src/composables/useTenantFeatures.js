/*
 * useTenantFeatures — 拉取 /api/tenant/features（inventoryEnabled / purchaseEnabled 等模块开关）。
 * 模块开关在一次登录内不变，做全局单例缓存 + 一次 in-flight 去重，避免每个页面各拉一次。
 * 拉取失败时默认全开（fail-open），保证旧租户/接口异常时功能不被误隐藏。
 */
import { ref } from 'vue'
import request from '@/utils/request'

const features = ref({ inventoryEnabled: true, purchaseEnabled: true })
let loaded = false
let inflight = null

async function load(force = false) {
  if (loaded && !force) return features.value
  if (inflight) return inflight
  inflight = (async () => {
    try {
      const r = await request.get('/api/tenant/features')
      const d = r?.data || {}
      features.value = {
        inventoryEnabled: typeof d.inventoryEnabled === 'boolean' ? d.inventoryEnabled : true,
        purchaseEnabled: typeof d.purchaseEnabled === 'boolean' ? d.purchaseEnabled : true,
        ...d
      }
    } catch (e) {
      /* fail-open：接口异常保持默认全开 */
    } finally {
      loaded = true
      inflight = null
    }
    return features.value
  })()
  return inflight
}

export function useTenantFeatures() {
  return { features, loadFeatures: load }
}
