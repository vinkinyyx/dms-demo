<template>
  <div class="receive-scan m-page-scroll">
    <van-nav-bar title="扫码收货" left-arrow @click-left="$router.back()">
      <template #right><button type="button" class="nav-link nav-link-btn" @click="manualOpen = true">手动输入</button></template>
    </van-nav-bar>

    <div class="m-scan-stage">
      <video v-show="cameraOn" ref="video" playsinline muted></video>
      <template v-if="!cameraOn">
        <div class="m-scan-frame"><span class="corner"></span></div>
        <div class="scan-idle">
          <van-icon name="scan" size="40" color="#7DD3FC" />
          <div class="scan-idle-tx">点击下方按钮打开摄像头</div>
        </div>
      </template>
      <div v-if="cameraOn" class="m-scan-line"></div>
      <div class="m-scan-hint">将收货单条码 / UDI 对准框内<br/>系统将自动识别单号</div>
    </div>

    <div class="scan-actions">
      <van-button type="primary" icon="scan" block round @click="toggleCamera">{{ cameraOn ? '停止扫描' : '继续扫码' }}</van-button>
    </div>

    <div class="m-section">本次已扫 <span class="m-more">{{ receipts.length }} 单</span></div>
    <div class="m-card-list">
      <div v-for="receipt in receipts" :key="receipt.id" class="m-ord" role="button" tabindex="0" :aria-label="'确认收货单 ' + receipt.code" @click="openReceipt(receipt.id)" @keydown.enter="openReceipt(receipt.id)" @keydown.space.prevent="openReceipt(receipt.id)">
        <div class="ot">
          <span class="no">{{ receipt.code }}</span>
          <span class="st" :class="receiptCls(receipt.status)"><i></i>{{ statusText(receipt.status) }}</span>
        </div>
        <div class="ol">
          <div class="th"><van-icon name="orders-o" /></div>
          <div>
            <div class="pn">{{ receipt.supplierName || '待收货单' }}</div>
            <div class="pm">点击确认收货入库</div>
          </div>
        </div>
        <div class="of">
          <span class="tot">待收货物料</span>
          <button class="ob">确认收货</button>
        </div>
      </div>
      <van-empty v-if="!receipts.length && loaded" description="未查到待收货单" />
    </div>

    <van-dialog v-model:show="manualOpen" title="手动输入" show-cancel-button @confirm="onManual">
      <van-field v-model="manualCode" placeholder="请输入收货单号/UDI/产品编码" />
    </van-dialog>
    <van-popup v-model:show="confirmOpen" position="bottom" round :style="{ height: '82%' }">
      <ReceiptConfirm v-if="confirmOpen" :id="selectedId" @close="confirmOpen = false" @done="onConfirmed" />
    </van-popup>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from 'vant'
import request from '@/utils/request'
import { statusText } from '@/utils/dict'
import ReceiptConfirm from './components/ReceiptConfirm.vue'

const route = useRoute()
const keyword = ref(route.query.code || '')
const manualCode = ref('')
const manualOpen = ref(false)
const receipts = ref([])
const loaded = ref(false)
const cameraOn = ref(false)
const video = ref(null)
const confirmOpen = ref(false)
const selectedId = ref(null)
let stream = null
let raf = null
const pageTitle = computed(() => '扫码收货')
function receiptCls(s) {
  if (s === 'DRAFT' || s === 'SUBMITTED' || s === 'PENDING_APPROVAL') return 'st-pen'
  if (s === 'APPROVED' || s === 'RECEIVED' || s === 'COMPLETED' || s === 'CONFIRMED') return 'st-ok'
  if (s === 'REJECTED' || s === 'CANCELLED') return 'st-rej'
  return 'st-info'
}

onMounted(() => { if (keyword.value) onSearch() })
onBeforeUnmount(stopCamera)
async function onSearch() {
  loaded.value = false
  try {
    const { data } = await request({ url: '/api/receipts', method: 'get', params: { page: 1, size: 50, status: 'DRAFT' } })
    const list = Array.isArray(data?.list) ? data.list : (data?.records || data?.content || [])
    const term = (keyword.value || '').trim().toLowerCase()
    receipts.value = term ? list.filter(item => JSON.stringify(item).toLowerCase().includes(term)) : list
  } catch {
    receipts.value = []
    showToast('加载待收货单失败')
  } finally {
    loaded.value = true
  }
}
function onManual() {
  keyword.value = manualCode.value
  onSearch()
}
function openReceipt(id) {
  selectedId.value = id
  confirmOpen.value = true
}
function onConfirmed() {
  confirmOpen.value = false
  onSearch()
}
async function toggleCamera() {
  if (cameraOn.value) { stopCamera(); return }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    video.value.srcObject = stream
    await video.value.play()
    cameraOn.value = true
    scanLoop()
  } catch {
    manualOpen.value = true
  }
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
.scan-idle { text-align: center; color: #9fb4cc; }
.scan-idle-tx { margin-top: 12px; font-size: 12px; color: #9fb4cc; }
.m-ord { cursor: pointer; }
</style>
