<template>
  <div class="async-tasks">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">导入导出任务</span>
        <el-radio-group v-model="taskType" size="default" @change="reload(1)">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="EXPORT">导出</el-radio-button>
          <el-radio-button value="IMPORT">导入</el-radio-button>
          <el-radio-button value="REPORT">报表</el-radio-button>
        </el-radio-group>
        <div style="flex:1" />
        <el-button @click="reload()">刷新</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.taskType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务" width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <span v-if="row.taskType === 'IMPORT'">
              成功 {{ row.successRows || 0 }} / 失败 {{ row.failedRows || 0 }} / 共 {{ row.totalRows || 0 }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdName" label="提交人" width="120" />
        <el-table-column prop="createdAt" label="提交时间" width="180" />
        <el-table-column prop="finishedAt" label="完成时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUCCESS' && row.objectKey" link type="primary" @click="download(row)">下载</el-button>
            <el-tooltip v-if="row.status === 'FAILED' && row.errorMessage" :content="row.errorMessage" placement="top">
              <el-tag type="danger" size="small">失败原因</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <StateView v-if="!loading && rows.length===0" type="empty" text="暂无导入导出任务" />`r`n      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import StateView from '@/components/StateView.vue'

const loading = ref(false), rows = ref([]), total = ref(0)
const page = ref(1), size = ref(20), taskType = ref('')

async function reload(p = page.value) {
  page.value = p; loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (taskType.value) params.taskType = taskType.value
    const { data } = await request({ url: '/api/async-tasks', method: 'get', params })
    rows.value = data?.list || []; total.value = data?.total || 0
  } finally { loading.value = false }
}
function onPage(p) { reload(p) }
function typeLabel(t) { return ({ EXPORT: '导出', IMPORT: '导入', REPORT: '报表' })[t] || t }
function statusLabel(s) { return ({ PENDING: '等待中', RUNNING: '处理中', SUCCESS: '成功', FAILED: '失败' })[s] || s }
function statusType(s) { return ({ PENDING: 'info', RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger' })[s] || '' }
function download(row) {
  window.open(`/api/async-tasks/${row.id}/download`, '_blank')
}
onMounted(() => reload(1))
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.title { font-size: 16px; font-weight: 600; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }
</style>