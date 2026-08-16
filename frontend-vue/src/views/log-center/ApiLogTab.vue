<template>
  <div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="路径/URL/用户名" clearable style="width:220px" @keyup.enter="reload(1)" />
      <el-select v-model="query.direction" placeholder="方向" clearable style="width:120px">
        <el-option label="入站" value="IN" />
        <el-option label="出站" value="OUT" />
      </el-select>
      <el-select v-model="query.method" placeholder="方法" clearable style="width:110px">
        <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH']" :key="m" :label="m" :value="m" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态码" clearable style="width:120px">
        <el-option v-for="s in [200,400,401,403,404,500]" :key="s" :label="s" :value="s" />
      </el-select>
      <el-date-picker v-model="dateRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始" end-placeholder="结束" />
      <el-button type="primary" @click="reload(1)">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column label="方向" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.direction === 'OUT' ? 'warning' : 'info'">{{ row.direction === 'OUT' ? '出站' : '入站' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="httpMethod" label="方法" width="90" />
      <el-table-column prop="path" label="路径/URL" min-width="260" show-overflow-tooltip />
      <el-table-column label="状态码" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.statusCode)">{{ row.statusCode ?? '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="80">
        <template #default="{ row }">{{ row.success ? '成功' : '失败' }}</template>
      </el-table-column>
      <el-table-column prop="username" label="调用方" width="130" />
      <el-table-column prop="spentMs" label="耗时(ms)" width="100" />
<el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="query.page" :page-size="query.size" @current-change="onPage" />

    <el-drawer v-model="show" title="接口调用详情" size="60%" destroy-on-close>
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="方向">{{ detail.direction }}</el-descriptions-item>
        <el-descriptions-item label="系统/端点" :span="2">{{ detail.system || '-' }} / {{ detail.endpoint || '-' }}</el-descriptions-item>
        <el-descriptions-item label="方法">{{ detail.httpMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态码">{{ detail.statusCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务码">{{ detail.bizCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成功">{{ detail.success ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.spentMs ?? 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="调用方">{{ detail.username || detail.appKey || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="URL" :span="2">{{ detail.url || detail.path || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail" class="log-body">
        <div v-for="section in sections" :key="section.title" class="log-section" :class="{ error: section.error }">
          <div class="log-section-head"><h4>{{ section.title }}</h4></div>
          <pre class="code">{{ section.value || '-' }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { formatDateTime } from '@/utils/format'
import request from '@/utils/request'

const rows = ref([]), loading = ref(false), total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', direction: '', method: '', status: null })
const dateRange = ref([])

async function reload(page = query.page) {
  query.page = page; loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value && dateRange.value.length === 2) { params.startTime = dateRange.value[0]; params.endTime = dateRange.value[1] }
    Object.keys(params).forEach(k => (params[k] === '' || params[k] === null) && delete params[k])
    const { data } = await request({ url: '/api/api-call-logs', method: 'get', params })
    rows.value = data?.list || []; total.value = data?.total || 0
  } finally { loading.value = false }
}
function onPage(p) { reload(p) }
function reset() { query.keyword = ''; query.direction = ''; query.method = ''; query.status = null; dateRange.value = []; reload(1) }
function statusType(s) { if (!s) return ''; if (s < 400) return 'success'; if (s < 500) return 'warning'; return 'danger' }

const show = ref(false), detail = ref(null)
const sections = computed(() => {
  if (!detail.value) return []
  return [
    { title: '请求体', value: detail.value.requestBody, error: false },
    { title: '响应体', value: detail.value.responseBody, error: false },
    { title: '错误信息', value: detail.value.errorMsg, error: !!detail.value.errorMsg }
  ].filter(s => s.value)
})
async function openDetail(row) {
  const { data } = await request({ url: `/api/api-call-logs/${row.id}`, method: 'get' })
  detail.value = data; show.value = true
}
onMounted(() => reload(1))
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }
.log-body { margin-top: 16px; }
.log-section { margin-bottom: 16px; }
.log-section h4 { margin: 0 0 6px; font-size: 13px; color: #606266; }
.log-section.error h4 { color: #f56c6c; }
.code { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 10px; border-radius: 4px; font-size: 12px; max-height: 320px; overflow: auto; margin: 0; }
</style>
