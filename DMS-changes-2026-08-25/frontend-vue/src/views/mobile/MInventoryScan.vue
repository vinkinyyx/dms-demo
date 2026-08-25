<template>
  <div class="inventory-scan">
    <van-nav-bar title="库存扫码查询" left-arrow @click-left="$router.back()" />
    <div class="search">
      <van-search v-model="keyword" shape="round" placeholder="UDI/产品编码/批号" @search="onSearch" clearable>
        <template #action><span @click="onSearch">查询</span></template>
      </van-search>
      <van-button type="primary" icon="scan" block @click="toggleCamera">{{ cameraOn ? '停止扫描' : '扫码' }}</van-button>
    </div>
    <video v-show="cameraOn" ref="video" class="video" playsinline muted></video>
    <van-cell-group inset title="库存结果">
      <van-cell v-for="item in list" :key="item.id" :title="item.productName || item.productCode" :label="`批号：${item.batchNo || item.lotNo || '-'}  库位：${item.locationName || '-'}`">
        <template #value>
          <div class="qty">
            <van-tag :type="statusType(item.status)">{{ statusLabel(item.status) }}</van-tag>
            <div>{{ item.availableQty ?? item.qty ?? 0 }}</div>
          </div>
        </template>
      </van-cell>
      <van-empty v-if="loaded && !list.length" description="未查到库存" />
    </van-cell-group>
  </div>
</template>
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import { ENUMS, STATUS_MAP } from '@/utils/dict'
const route = useRoute()
const STOCK_STATUS_LABEL = Object.fromEntries((ENUMS.stockStatus || []).map(it => [it.value, it.label]))
function statusLabel(s) { return STOCK_STATUS_LABEL[s] || STATUS_MAP[s] || s || '-' }
const keyword = ref(route.query.keyword || '')
const list = ref([])
const loaded = ref(false)
const cameraOn = ref(false)
const video = ref(null)
let stream = null
let raf = null
onMounted(() => { if (keyword.value) onSearch() })
onBeforeUnmount(stopCamera)
async function onSearch() {
  loaded.value = false
  try {
    const { data } = await request({ url: '/api/inventory', method: 'get', params: { keyword: keyword.value, page: 1, size: 50 } })
    list.value = Array.isArray(data?.list) ? data.list : (data?.records || data?.content || [])
  } catch { list.value = [] }
  finally { loaded.value = true }
}
function statusType(status) {
  if (status === 'QUALIFIED') return 'success'
  if (status === 'PENDING' || status === 'QUARANTINED') return 'warning'
  if (status === 'DEFECTIVE') return 'danger'
  return 'default'
}
async function toggleCamera() {
  if (cameraOn.value) { stopCamera(); return }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    video.value.srcObject = stream
    await video.value.play()
    cameraOn.value = true
    scanLoop()
  } catch { /* fallback to manual search */ }
}
function stopCamera() {
  cameraOn.value = false
  if (raf) cancelAnimationFrame(raf)
  if (stream) { stream.getTracks().forEach(track => track.stop()); stream = null }
}
function scanLoop() {
  const detector = window.BarcodeDetector
  if (!detector || !video.value || !cameraOn.value) return
  const d = new detector({ formats: ['qr_code', 'code_128', 'ean_13', 'ean_8', 'code_39'] })
  const tick = async () => {
    if (!cameraOn.value) return
    try {
      const codes = await d.detect(video.value)
      if (codes?.length) { keyword.value = codes[0].rawValue; stopCamera(); onSearch(); return }
    } catch { /* ignore */ }
    raf = requestAnimationFrame(tick)
  }
  tick()
}
</script>
<style scoped>
.search { padding: 12px 12px 0; display: grid; gap: 8px; }
.video { width: calc(100% - 24px); height: 200px; margin: 12px; background: #000; border-radius: 8px; object-fit: cover; }
.qty { font-weight: 600; text-align: right; margin-bottom: 4px; }
</style>
