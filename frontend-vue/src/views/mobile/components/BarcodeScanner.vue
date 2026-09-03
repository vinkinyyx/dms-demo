<template>
  <van-popup :show="show" position="bottom" round :style="{ height: '78%' }" @update:show="onToggle" @close="stopCamera">
    <div class="scanner">
      <van-nav-bar title="扫码" :left-arrow="false">
        <template #right>
          <van-button size="small" type="primary" @click="onClose">关闭</van-button>
        </template>
      </van-nav-bar>

      <div v-if="supported" class="cam-wrap">
        <video ref="video" class="video" playsinline muted></video>
        <div class="scan-frame"></div>
        <div class="cam-tip">{{ cameraOn ? '将条码 / UDI 对准取景框' : '点击下方按钮开启摄像头' }}</div>
      </div>
      <div v-else class="unsupported">
        <van-icon name="info-o" size="40" />
        <p class="unsup-title">当前环境无法调用摄像头</p>
        <p class="unsup-sub">浏览器仅允许在 HTTPS 或 localhost 环境使用摄像头，当前为 HTTP 访问。请直接手动输入，或改用 HTTPS 访问系统。</p>
      </div>

      <div class="manual">
        <van-field v-model="manualCode" label="手动输入" :placeholder="placeholder" clearable>
          <template #button>
            <van-button size="small" type="primary" @click="confirmManual">确定</van-button>
          </template>
        </van-field>
      </div>

      <div class="actions">
        <van-button v-if="supported" type="primary" icon="scan" block @click="toggleCamera">
          {{ cameraOn ? '停止扫描' : '打开摄像头扫描' }}
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, watch, onBeforeUnmount, nextTick } from 'vue'
import { showToast } from 'vant'

const props = defineProps({
  show: { type: Boolean, default: false },
  placeholder: { type: String, default: '请输入或扫码批号 / 序列号' }
})
const emit = defineEmits(['update:show', 'scanned'])

const video = ref(null)
const manualCode = ref('')
const cameraOn = ref(false)
const supported = typeof navigator !== 'undefined'
  && !!navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function'
let stream = null
let raf = null

function onToggle(v) { emit('update:show', v) }
function onClose() { stopCamera(); emit('update:show', false) }

async function toggleCamera() {
  if (cameraOn.value) { stopCamera(); return }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    await nextTick()
    if (video.value) {
      video.value.srcObject = stream
      await video.value.play()
    }
    cameraOn.value = true
    scanLoop()
  } catch (e) {
    cameraOn.value = false
    showToast('无法打开摄像头，请检查权限或改用手动输入')
  }
}

function stopCamera() {
  cameraOn.value = false
  if (raf) { cancelAnimationFrame(raf); raf = null }
  if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null }
}

function scanLoop() {
  const Detector = window.BarcodeDetector
  if (!Detector || !video.value || !cameraOn.value) return
  const detector = new Detector({ formats: ['qr_code', 'code_128', 'ean_13', 'ean_8', 'code_39', 'data_matrix'] })
  const tick = async () => {
    if (!cameraOn.value) return
    try {
      const codes = await detector.detect(video.value)
      if (codes && codes.length) {
        emitResult(codes[0].rawValue)
        return
      }
    } catch (e) { /* keep scanning */ }
    raf = requestAnimationFrame(tick)
  }
  tick()
}

function emitResult(code) {
  const val = (code || '').trim()
  if (!val) return
  stopCamera()
  emit('scanned', val)
  emit('update:show', false)
}

function confirmManual() {
  const val = (manualCode.value || '').trim()
  if (!val) { showToast('请输入内容'); return }
  emitResult(val)
}

watch(() => props.show, (v) => {
  if (v) {
    manualCode.value = ''
    if (supported) nextTick(() => toggleCamera())
  } else {
    stopCamera()
  }
})

onBeforeUnmount(stopCamera)
</script>

<style scoped>
.scanner { display: flex; flex-direction: column; height: 100%; }
.cam-wrap { position: relative; margin: 12px; }
.video { width: 100%; height: 220px; background: #000; border-radius: 8px; object-fit: cover; display: block; }
.scan-frame {
  position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%);
  width: 200px; height: 120px; border: 2px solid var(--dms-color-primary);
  border-radius: 8px; box-shadow: 0 0 0 999px rgba(0,0,0,.25); pointer-events: none;
}
.cam-tip { position: absolute; bottom: 10px; left: 0; right: 0; text-align: center; color: #fff; font-size: 13px; text-shadow: 0 1px 2px rgba(0,0,0,.6); }
.unsupported { text-align: center; padding: 28px 24px 8px; color: var(--dms-text-3, var(--van-gray-6)); }
.unsup-title { font-size: 15px; font-weight: 600; color: var(--dms-text-1); margin: 12px 0 6px; }
.unsup-sub { font-size: 13px; line-height: 1.6; color: var(--dms-text-4); margin: 0; }
.manual { padding: 4px 12px; }
.actions { padding: 8px 12px; }
</style>
