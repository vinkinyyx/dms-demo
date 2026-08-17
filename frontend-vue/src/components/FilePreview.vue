<template>
  <el-drawer v-model="visible" :title="fileName || '附件预览'" size="60%" destroy-on-close>
    <div class="preview-wrap" v-loading="loading">
      <template v-if="fileType">
        <img v-if="fileType === 'image'" :src="previewUrl" class="preview-img" @error="onError" />
        <iframe v-else-if="fileType === 'pdf'" :src="previewUrl" class="preview-iframe"></iframe>
        <video v-else-if="fileType === 'video'" :src="previewUrl" controls class="preview-video"></video>
        <div v-else class="unsupported">
          <el-icon size="48"><Document /></el-icon>
          <p>该文件类型不支持在线预览</p>
          <el-button type="primary" @click="download">下载文件</el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="无法预览" />
    </div>
    <template #footer>
      <el-button @click="download">下载</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Document } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  fileId: { type: [String, Number], default: null },
  fileName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const loading = ref(true)
const error = ref(false)

watch(() => props.modelValue, (v) => { visible.value = v; if (v) { loading.value = true; error.value = false } })
watch(visible, (v) => emit('update:modelValue', v))

const previewUrl = computed(() => props.fileId ? `/api/files/${props.fileId}/preview` : '')
const fileType = computed(() => {
  const n = (props.fileName || '').toLowerCase()
  if (/\.(png|jpg|jpeg|gif|webp|bmp)$/.test(n)) return 'image'
  if (/\.pdf$/.test(n)) return 'pdf'
  if (/\.(mp4|webm|mov)$/.test(n)) return 'video'
  if (/\.(txt|json|log)$/.test(n)) return 'text'
  return 'other'
})
function onError() { error.value = true; loading.value = false }
function download() {
  if (props.fileId) window.open(`/api/files/${props.fileId}/download`, '_blank')
}
</script>

<style scoped>
.preview-wrap { min-height: 400px; display: flex; justify-content: center; align-items: flex-start; }
.preview-img { max-width: 100%; max-height: 70vh; }
.preview-iframe { width: 100%; height: 70vh; border: none; }
.preview-video { max-width: 100%; max-height: 70vh; }
.unsupported { text-align: center; color: #909399; padding: 40px; }
.unsupported .el-icon { margin-bottom: 12px; }
</style>