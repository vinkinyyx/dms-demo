<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.path" placeholder="路径/URL" clearable style="width:220px" />
      <el-select v-model="query.method" placeholder="请求方法" clearable style="width:120px">
        <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
      </el-select>
      <el-input v-model="query.statusCode" placeholder="状态码" clearable style="width:120px" />
      <el-select v-model="query.slow" placeholder="慢请求" clearable style="width:130px">
        <el-option label="仅慢请求" :value="true" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border size="small">
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column prop="httpMethod" label="方法" width="80" />
      <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
      <el-table-column prop="statusCode" label="状态码" width="90" />
      <el-table-column prop="bizCode" label="业务码" width="90" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="慢请求" width="90">
        <template #default="{ row }"><el-tag v-if="row.slow" type="warning" size="small">慢</el-tag></template>
      </el-table-column>
      <el-table-column prop="spentMs" label="耗时(ms)" width="100" />
      <el-table-column prop="username" label="用户" width="130" show-overflow-tooltip />
      <el-table-column prop="clientIp" label="IP" width="140" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.hasRequestFile" link type="primary" @click="copyFile(row.id, 'request')">复制请求体</el-button>
          <el-button v-if="row.hasResponseFile" link type="primary" @click="copyFile(row.id, 'response')">复制响应体</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, sizes, prev, pager, next" :total="total"
      :page-size="size" :current-page="page" :page-sizes="[20,50,100]" @current-change="onPage" @size-change="onSize" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listApiLogs, fetchApiLogFile } from '@/api/admin'
import { copyText, formatDateTime } from '@/utils/format'

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const query = reactive({ path: '', method: '', statusCode: '', slow: undefined })

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, ...query }
    if (params.statusCode) params.statusCode = Number(params.statusCode)
    const res = await listApiLogs(params)
    list.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function onSearch() { page.value = 1; load() }
function onReset() { Object.assign(query, { path: '', method: '', statusCode: '', slow: undefined }); page.value = 1; load() }
function onPage(currentPage) { page.value = currentPage; load() }
function onSize(currentSize) { size.value = currentSize; page.value = 1; load() }

async function copyFile(id, kind) {
  try {
    const res = await fetchApiLogFile(id, kind)
    await copyText(res.data || res)
    ElMessage.success(`已复制${kind === 'request' ? '请求' : '响应'}体`)
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
