<template>
  <div class="receive-scan">
    <van-nav-bar :title="pageTitle" left-arrow @click-left="$router.back()" />
    <div class="search-bar">
      <van-search v-model="keyword" shape="round" placeholder="输入收货单号/UDI/产品编码" @search="onSearch" clearable>
        <template #action>
          <span @click="onSearch">查询</span>
        </template>
      </van-search>
    </div>
    <video v-show="cameraOn" ref="video" class="video" playsinline muted></video>
    <div class="actions">
      <van-button type="primary" icon="scan" block @click="toggleCamera">{{ cameraOn ? '停止扫描' : '打开摄像头扫描' }}</van-button>
      <van-button block plain type="primary" class="manual-btn" @click="manualOpen = true">手动输入</van-button>
    </div>
    <van-cell-group inset :title="groupTitle">
      <van-cell v-for="receipt in receipts" :key="receipt.id" :title="receipt.code" :label="receipt.supplierName || '待收货单'" is-link @click="openReceipt(receipt.id)">
        <template #value>
          <van-tag :type="receiptTagType(receipt.status)">{{ statusText(receipt.status) }}</van-tag>
        </template>
      </van-cell>
      <van-empty v-if="!receipts.length && loaded" description="未查到待收货单" />
    </van-cell-group>
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
const groupTitle = computed(() => '待收货单')
function receiptTagType(s) {
  if (s === 'DRAFT' || s === 'SUBMITTED' || s === 'PENDING_APPROVAL') return 'warning'
  if (s === 'APPROVED' || s === 'RECEIVED' || s === 'COMPLETED' || s === 'CONFIRMED') return 'success'
  if (s === 'REJECTED' || s === 'CANCELLED') return 'danger'
  if (s === 'RECEIVING' || s === 'PARTIAL_RECEIVED') return 'primary'
  return 'default'
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
.search-bar { position: sticky; top: 46px; z-index: 10; background: var(--van-background-2); }
.video { width: calc(100% - 24px); height: 220px; margin: 12px; background: #000; border-radius: 8px; object-fit: cover; }
.actions { padding: 0 12px 12px; display: grid; gap: 8px; }
.manual-btn { margin-top: 0; }
</style>
