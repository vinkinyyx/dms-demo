<template>
  <div class="api-log-page">
    <el-card shadow="never">
      <el-form :inline="true" :model="q" size="default" @submit.prevent="load">
        <el-form-item label="方向">
          <el-select v-model="q.direction" placeholder="全部" clearable style="width:120px">
            <el-option label="入站(调用DMS)" value="IN" />
            <el-option label="出站(DMS外呼)" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统">
          <el-input v-model="q.system" placeholder="ERP/WMS/..." clearable style="width:130px" />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="q.method" placeholder="全部" clearable style="width:110px">
            <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH']" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态码">
          <el-input v-model="q.statusCode" placeholder="如200" clearable style="width:100px" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="q.keyword" placeholder="路径/用户/appKey" clearable style="width:200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <el-table :data="list" v-loading="loading" border stripe height="calc(100vh - 260px)">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="方向" width="120">
          <template #default="{ row }">
            <el-tag :type="row.direction==='OUT' ? 'warning' : 'success'" size="small">
              {{ row.direction === 'OUT' ? '出站' : '入站' }}
            </el-tag>
            <span v-if="row.system" style="margin-left:6px">{{ row.system }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="httpMethod" label="方法" width="80" />
        <el-table-column prop="path" label="路径/URL" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row)" size="small">{{ row.statusCode }}</el-tag>
            <span v-if="row.bizCode !== null && row.bizCode !== undefined" style="margin-left:4px;color:#909399">({{ row.bizCode }})</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="调用方" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.username || row.appKey || row.clientIp || '-' }}</template>
        </el-table-column>
        <el-table-column prop="spentMs" label="耗时(ms)" width="100" />
        <el-table-column prop="startedAt" label="时间" width="200" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top:12px;justify-content:flex-end"
        v-model:current-page="q.page"
        v-model:page-size="q.size"
        :total="total"
        :page-sizes="[20,50,100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </el-card>

    <el-drawer v-model="show" title="接口调用详情" size="60%" destroy-on-close>
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="方向">{{ detail.direction }}</el-descriptions-item>
        <el-descriptions-item label="系统/端点">{{ detail.system }} / {{ detail.endpoint }}</el-descriptions-item>
        <el-descriptions-item label="方法">{{ detail.httpMethod }}</el-descriptions-item>
        <el-descriptions-item label="状态码">{{ detail.statusCode }}</el-descriptions-item>
        <el-descriptions-item label="业务码">{{ detail.bizCode }}</el-descriptions-item>
        <el-descriptions-item label="成功">{{ detail.success ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.spentMs }} ms</el-descriptions-item>
        <el-descriptions-item label="调用方">{{ detail.username || detail.appKey || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.clientIp }}</el-descriptions-item>
        <el-descriptions-item label="requestId">{{ detail.requestId }}</el-descriptions-item>
        <el-descriptions-item label="traceId">{{ detail.traceId }}</el-descriptions-item>
        <el-descriptions-item label="URL" :span="2">{{ detail.url }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ detail.startedAt }}</el-descriptions-item>
        <el-descriptions-item label="结束">{{ detail.finishedAt }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail" style="margin-top:12px">
        <h4>请求头</h4>
        <pre class="code">{{ detail.requestHeaders || '(无)' }}</pre>
        <h4>请求体</h4>
        <pre class="code">{{ detail.requestBody || '(无)' }}</pre>
        <h4>响应体</h4>
        <pre class="code">{{ detail.responseBody || '(无)' }}</pre>
        <h4 v-if="detail.errorMsg">错误</h4>
        <pre v-if="detail.errorMsg" class="code err">{{ detail.errorMsg }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/request'
import { ElMessage } from 'element-plus'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const show = ref(false)
const detail = ref(null)
const q = reactive({ direction: '', system: '', method: '', statusCode: '', keyword: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const params = { page: q.page, size: q.size }
    for (const k of ['direction','system','method','statusCode','keyword']) if (q[k]) params[k] = q[k]
    const { data } = await http.get('/api/admin/api-call-logs', { params })
    list.value = data.list || []
    total.value = data.total || 0
  } finally { loading.value = false }
}
function reset() {
  for (const k of ['direction','system','method','statusCode','keyword']) q[k] = ''
  q.page = 1; load()
}
async function open(id) {
  const { data } = await http.get('/api/admin/api-call-logs/' + id)
  detail.value = data; show.value = true
}
function statusType(row) {
  if (row.success) return 'success'
  return 'danger'
}
onMounted(load)
</script>

<style scoped>
.code { background:#f5f7fa; padding:10px; border-radius:4px; max-height:260px; overflow:auto; white-space:pre-wrap; word-break:break-all; font-size:12px; }
.code.err { background:#fef0f0; color:#f56c6c; }
h4 { margin:14px 0 6px; }
</style>
