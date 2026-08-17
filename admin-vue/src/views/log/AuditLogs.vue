<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.action" placeholder="操作类型" clearable style="width:220px" />
      <el-input v-model="query.targetType" placeholder="对象类型" clearable style="width:160px" />
      <el-select v-model="query.success" placeholder="结果" clearable style="width:120px">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border size="small">
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="adminUsername" label="操作人" width="130" />
      <el-table-column prop="action" label="操作" min-width="160" show-overflow-tooltip />
      <el-table-column prop="targetType" label="对象类型" width="150" />
      <el-table-column prop="targetId" label="对象ID" width="160" show-overflow-tooltip />
      <el-table-column label="结果" width="90">
        <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="open(row)">查看详情</el-button></template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
      :page-size="size" :current-page="page" @current-change="onPage" />

    <el-drawer v-model="show" title="审计日志详情" size="60%" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.adminUsername || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作" :span="2">{{ detail.action || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ detail.success ? '成功' : '失败' }}</el-descriptions-item>
          <el-descriptions-item label="对象">{{ detail.targetType || '-' }} / {{ detail.targetId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ detail.ip || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-for="section in sections" :key="section.title" class="log-section">
          <div class="log-section-head">
            <h4>{{ section.title }}</h4>
            <el-button size="small" @click="copy(section.value)">复制</el-button>
          </div>
          <pre class="code">{{ section.value }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAuditLogs } from '@/api/admin'
import { copyText, formatDateTime } from '@/utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const query = reactive({ action: '', targetType: '', success: undefined })
const show = ref(false)
const detail = ref(null)

const sections = computed(() => {
  if (!detail.value) return []
  const items = [
    { title: '变更前', value: pretty(detail.value.beforeJson) },
    { title: '变更后', value: pretty(detail.value.afterJson) },
    { title: 'User-Agent', value: detail.value.userAgent || '-' }
  ]
  if (detail.value.errorMessage) items.push({ title: '错误信息', value: detail.value.errorMessage })
  return items
})

function pretty(value) {
  if (!value) return '(无)'
  if (typeof value === 'string') {
    try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
  }
  return JSON.stringify(value, null, 2)
}

async function load() {
  loading.value = true
  try {
    const res = await listAuditLogs({ page: page.value, size: size.value, ...query })
    list.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function onPage(currentPage) {
  page.value = currentPage
  load()
}

function open(row) {
  detail.value = row
  show.value = true
}

async function copy(value) {
  try {
    await copyText(value)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.log-section { margin-top: 14px; }
.log-section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.log-section-head h4 { margin: 0; }
.code { background: var(--dms-bg-page); padding: 10px; border-radius: 4px; max-height: 280px; overflow: auto; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
</style>