<template>
  <div class="inventory-scan m-page-scroll">
    <van-nav-bar title="库存扫码查询" left-arrow @click-left="$router.back()">
      <template #right><button type="button" class="nav-link nav-link-btn" @click="manualOpen = true">手动输入</button></template>
    </van-nav-bar>

    <div class="m-scan-stage">
      <video v-show="cameraOn" ref="video" playsinline muted></video>
      <template v-if="!cameraOn">
        <div class="m-scan-frame"><span class="corner"></span></div>
        <div class="scan-idle">
          <van-icon name="search" size="38" color="#7DD3FC" />
          <div class="scan-idle-tx">扫描产品条码 / UDI / 批号<br/>查询实时库存</div>
        </div>
      </template>
      <div v-if="cameraOn" class="m-scan-line"></div>
      <div class="m-scan-hint">扫描产品条码 / 批号 / 序列号<br/>查看实时库存与库位</div>
    </div>

    <div class="scan-actions">
      <van-button type="primary" icon="scan" block round @click="toggleCamera">{{ cameraOn ? '停止扫描' : '打开扫码' }}</van-button>
    </div>

    <div class="manual-search">
      <van-search v-model="keyword" shape="round" placeholder="UDI / 产品编码 / 批号" @search="onSearch" clearable />
    </div>

    <template v-if="loaded">
      <div class="m-card-list" v-if="list.length">
        <div v-for="item in list" :key="item.id" class="m-ord">
          <div class="ot">
            <span class="no">{{ item.productCode || item.code || 'INV-' + item.id }}</span>
            <span class="st" :class="stockCls(item.status)"><i></i>{{ statusLabel(item.status) }}</span>
          </div>
          <div class="ol">
            <div class="th"><van-icon name="balance-o" /></div>
            <div>
              <div class="pn">{{ item.productName || '库存产品' }}</div>
              <div class="pm">批号：{{ item.batchNo || item.lotNo || '-' }} · 库位：{{ item.locationName || '-' }}</div>
            </div>
          </div>
          <div class="m-invstock">
            <div class="s ok"><div class="n">{{ item.availableQty ?? item.qty ?? 0 }}</div><div class="t">可用</div></div>
            <div class="s lock"><div class="n">{{ item.lockedQty ?? 0 }}</div><div class="t">锁定</div></div>
            <div class="s warn"><div class="n">{{ item.safetyStock ?? 0 }}</div><div class="t">安全库存</div></div>
          </div>
        </div>
      </div>
      <van-empty v-else description="未查到库存" />
    </template>

    <van-dialog v-model:show="manualOpen" title="手动输入" show-cancel-button @confirm="onManual">
      <van-field v-model="manualCode" placeholder="请输入 UDI / 产品编码 / 批号" />
    </van-dialog>
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
function stockCls(s) {
  if (s === 'QUALIFIED' || s === 'AVAILABLE') return 'st-ok'
  if (s === 'PENDING' || s === 'QUARANTINED') return 'st-pen'
  if (s === 'DEFECTIVE' || s === 'SCRAPPED') return 'st-rej'
  return 'st-info'
}
const keyword = ref(route.query.keyword || '')
const manualCode = ref('')
const manualOpen = ref(false)
const list = ref([])
const loaded = ref(false)
const cameraOn = ref(false)
const video = ref(null)
let stream = null
let raf = null
onMounted(() => { if (keyword.value) onSearch() })
onBeforeUnmount(stopCamera)
function onManual() { keyword.value = manualCode.value; manualOpen.value = false; onSearch() }
async function onSearch() {
  loaded.value = false
  try {
    const { data } = await request({ url: '/api/inventory', method: 'get', params: { keyword: keyword.value, page: 1, size: 50 } })
    list.value = Array.isArray(data?.list) ? data.list : (data?.records || data?.content || [])
  } catch { list.value = [] }
  finally { loaded.value = true }
}
async function toggleCamera() {
  if (cameraOn.value) { stopCamera(); return }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    video.value.srcObject = stream
    await video.value.play()
    cameraOn.value = true
    scanLoop()
  } catch { manualOpen.value = true }
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
.nav-link { color: #fff; font-size: 13px; }
.nav-link-btn { background:none; border:0; padding:0; font:inherit; cursor:pointer; }
.scan-actions { padding: 0 13px 4px; }
.manual-search { padding: 4px 6px 0; }
.scan-idle { text-align: center; color: #9fb4cc; }
.scan-idle-tx { margin-top: 12px; font-size: 12px; color: #9fb4cc; line-height: 1.6; }
</style>
