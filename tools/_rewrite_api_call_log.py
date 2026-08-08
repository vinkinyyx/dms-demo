from pathlib import Path
content = '''<template>
  <ListPageLayout
    page-key="api-call-log"
    :table-columns="tableColumns"
    :fetch-data="fetchData"
    :row-actions="rowActions"
    :initial-filter="initialFilter"
  />

  <el-drawer v-model="show" title="接口调用详情" size="60%" destroy-on-close>
    <el-descriptions v-if="detail" :column="2" border size="small">
      <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
      <el-descriptions-item label="方向">{{ directionText(detail.direction) }}</el-descriptions-item>
      <el-descriptions-item label="系统/端点">{{ detail.system || '-' }} / {{ detail.endpoint || '-' }}</el-descriptions-item>
      <el-descriptions-item label="方法">{{ detail.httpMethod || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态码">{{ detail.statusCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="业务码">{{ detail.bizCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="成功">{{ detail.success ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="耗时">{{ detail.spentMs ?? 0 }} ms</el-descriptions-item>
      <el-descriptions-item label="调用方">{{ detail.username || detail.appKey || '-' }}</el-descriptions-item>
      <el-descriptions-item label="IP">{{ detail.clientIp || '-' }}</el-descriptions-item>
      <el-descriptions-item label="requestId">{{ detail.requestId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="traceId">{{ detail.traceId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="URL" :span="2">{{ detail.url || detail.path || '-' }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ formatDateTime(detail.startedAt) }}</el-descriptions-item>
      <el-descriptions-item label="结束时间">{{ formatDateTime(detail.finishedAt) }}</el-descriptions-item>
    </el-descriptions>

    <div v-if="detail" class="log-body">
      <div v-for="section in logSections" :key="section.title" class="log-section" :class="{ error: section.error }">
        <div class="log-section-head">
          <h4>{{ section.title }}</h4>
          <el-button size="small" @click="copyPayload(section.value)">复制</el-button>
        </div>
        <pre class="code">{{ section.value }}</pre>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ListPageLayout from '@/components/ListPageLayout.vue'
import request from '@/utils/request'
import { copyText, formatDateTime } from '@/utils/format'

const initialFilter = { keyword: '', dateFrom: '', dateTo: '', status: '' }

const tableColumns = [
  { prop: 'direction', label: '方向', width: 90, render: (row) => directionText(row.direction) },
  { prop: 'httpMethod', label: '方法', width: 90 },
  { prop: 'path', label: '路径/URL', minWidth: 260 },
  { prop: 'statusCode', label: '状态码', width: 90 },
  { prop: 'success', label: '结果', width: 80, render: (row) => row.success ? '成功' : '失败' },
  { prop: 'username', label: '调用方', width: 130 },
  { prop: 'spentMs', label: '耗时(ms)', width: 100 },
  { prop: 'startedAt', label: '时间', width: 180, render: (row) => formatDateTime(row.startedAt) }
]

async function fetchData(params) {
  return request({ url: '/api/api-call-logs', method: 'get', params })
}

function directionText(value) {
  if (value === 'OUT') return '出站'
  if (value === 'IN') return '入站'
  return value || '-'
}

const show = ref(false)
const detail = ref(null)

async function openDetail(id) {
  try {
    const res = await request({ url: `/api/api-call-logs/${id}`, method: 'get' })
    detail.value = res.data || res
    show.value = true
  } catch (e) {
    ElMessage.error('加载日志详情失败')
  }
}

const rowActions = {
  view: (row) => openDetail(row.id)
}

const logSections = computed(() => {
  if (!detail.value) return []
  const sections = [
    { title: '请求头', value: detail.value.requestHeaders || '(无)' },
    { title: '请求体', value: detail.value.requestBody || '(无)' },
    { title: '响应头', value: detail.value.responseHeaders || '(无)' },
    { title: '响应体', value: detail.value.responseBody || '(无)' }
  ]
  if (detail.value.errorMsg) sections.push({ title: '错误信息', value: detail.value.errorMsg, error: true })
  return sections
})

async function copyPayload(value) {
  try {
    await copyText(value)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.log-body { margin-top: 12px; }
.log-section { margin-top: 12px; }
.log-section-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.log-section-head h4 { margin: 0; font-size: 14px; }
.code { background: #f5f7fa; padding: 10px; border-radius: 4px; max-height: 260px; overflow: auto; font-size: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; }
.error .code { background: #fef0f0; color: #c45656; }
</style>
'''
Path('frontend-vue/src/views/ApiCallLog.vue').write_text(content, encoding='utf-8', newline='\n')
print(Path('frontend-vue/src/views/ApiCallLog.vue').read_bytes()[:120])
