<template>
  <div>
    <div class="toolbar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width:140px" @keyup.enter="reload(1)" />
      <el-select v-model="query.layer" placeholder="层级" clearable style="width:130px">
        <el-option label="CONTROLLER" value="CONTROLLER" />
        <el-option label="SERVICE" value="SERVICE" />
        <el-option label="HTTP" value="HTTP" />
      </el-select>
      <el-input v-model="query.bizType" placeholder="业务类型" clearable style="width:140px" @keyup.enter="reload(1)" />
      <el-input v-model="query.keyword" placeholder="路径/方法/备注关键词" clearable style="width:200px" @keyup.enter="reload(1)" />
      <el-date-picker v-model="dateRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始" end-placeholder="结束" />
      <el-button type="primary" @click="reload(1)">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe>
<el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="layer" label="层级" width="110" />
      <el-table-column label="方法" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="methodType(row.httpMethod)">{{ row.httpMethod || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status && row.status < 400 ? 'success' : 'danger'">{{ row.status ?? '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="spentMs" label="耗时(ms)" width="90" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column label="业务对象" width="160">
        <template #default="{ row }">
          <el-button v-if="row.bizType && row.bizId" link type="primary" @click="jumpDetail(row)">
            {{ row.bizType }}#{{ row.bizId }}
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="query.page" :page-size="query.size" @current-change="onPage" />

    <el-drawer v-model="show" title="操作日志详情" size="55%" destroy-on-close>
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="层级">{{ detail.layer }}</el-descriptions-item>
        <el-descriptions-item label="方法">{{ detail.httpMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">{{ detail.status ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="路径" :span="2">{{ detail.path }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.spentMs ?? 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ detail.bizType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务ID">{{ detail.bizId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="User Agent" :span="2">{{ detail.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="requestId">{{ detail.requestId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="traceId">{{ detail.traceId || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail" class="log-body">
        <div class="log-section">
          <h4>请求体（已脱敏）</h4>
          <pre class="code">{{ detail.requestBody || '-' }}</pre>
        </div>
        <div class="log-section">
          <h4>响应体（已脱敏）</h4>
          <pre class="code">{{ detail.response || '-' }}</pre>
        </div>
        <div v-if="detail.stack" class="log-section error">
          <h4>异常栈</h4>
          <pre class="code">{{ detail.stack }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { formatDateTime } from '@/utils/format'
import request from '@/utils/request'

const props = defineProps({ jumpDetail: { type: Function, default: null } })

const rows = ref([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ page: 1, size: 20, username: '', layer: '', bizType: '', keyword: '' })
const dateRange = ref([])

async function reload(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    Object.keys(params).forEach(k => (params[k] === '' || params[k] === null) && delete params[k])
    const { data } = await request({ url: '/api/operation-logs/fullchain', method: 'get', params })
    rows.value = data?.list || []
    total.value = data?.total || 0
  } finally { loading.value = false }
}
function onPage(p) { reload(p) }
function reset() {
  query.username = ''; query.layer = ''; query.bizType = ''; query.keyword = ''; dateRange.value = []; reload(1)
}
function methodType(m) {
  const u = String(m || '').toUpperCase()
  if (u === 'GET') return 'info'
  if (u === 'POST') return 'success'
  if (u === 'PUT' || u === 'PATCH') return 'warning'
  if (u === 'DELETE') return 'danger'
  return ''
}

const show = ref(false)
const detail = ref(null)
async function openDetail(row) {
  const { data } = await request({ url: `/api/operation-logs/fullchain/${row.id}`, method: 'get' })
  detail.value = data
  show.value = true
}
function jumpDetail(row) {
  if (props.jumpDetail) props.jumpDetail(row.bizType, row.bizId)
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
