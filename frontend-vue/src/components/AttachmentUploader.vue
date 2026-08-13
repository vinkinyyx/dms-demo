<template>
  <div class="att-uploader">
    <el-upload :action="uploadUrl" :headers="headers" :show-file-list="false" :before-upload="beforeUpload" :on-success="onSuccess" :on-error="onError">
      <el-button type="primary" plain><el-icon><Upload /></el-icon>选择文件</el-button>
    </el-upload>
    <div class="att-info" v-if="value">
      <el-link :href="value.url" target="_blank" type="primary">{{ value.originalName }}</el-link>
      <el-button size="small" type="danger" link @click="clear">移除</el-button>
    </div>
    <div class="att-tip">支持任意格式，最大 50MB（保存在服务器本地，可下载/替换）</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'

const props = defineProps({ modelValue: { default: null } })
const emit = defineEmits(['update:modelValue'])
const value = computed(() => props.modelValue)
const uploadUrl = '/api/files/upload?bizType=surgeryReport'
const headers = computed(() => ({ Authorization: 'Bearer ' + (getToken() || '') }))

function beforeUpload(file) {
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件不能超过 50MB')
    return false
  }
  return true
}
function onSuccess(res) {
  if (res && res.code === 0) {
    emit('update:modelValue', res.data)
    ElMessage.success('上传成功')
  } else {
    ElMessage.error((res && res.message) || '上传失败')
  }
}
function onError() { ElMessage.error('上传失败') }
function clear() { emit('update:modelValue', null) }
</script>

<style scoped>
.att-uploader { display: flex; flex-direction: column; gap: 8px; }
.att-info { display: flex; align-items: center; gap: 8px; }
.att-tip { color: var(--dms-text-4); font-size: 12px; }
</style>
