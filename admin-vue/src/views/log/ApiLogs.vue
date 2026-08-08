<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.path" placeholder="??/URL" clearable style="width:220px" />
      <el-select v-model="query.method" placeholder="????" clearable style="width:120px">
        <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
      </el-select>
      <el-input v-model="query.statusCode" placeholder="???" clearable style="width:120px" />
      <el-select v-model="query.slow" placeholder="???" clearable style="width:130px">
        <el-option label="????" :value="true" />
      </el-select>
      <el-button type="primary" @click="load">??</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border size="small">
      <el-table-column label="??" width="180">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column prop="httpMethod" label="??" width="80" />
      <el-table-column prop="path" label="??" min-width="240" show-overflow-tooltip />
      <el-table-column prop="statusCode" label="???" width="90" />
      <el-table-column prop="bizCode" label="???" width="90" />
      <el-table-column label="??" width="80">
        <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '??' : '??' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="???" width="90">
        <template #default="{ row }"><el-tag v-if="row.slow" type="warning" size="small">?</el-tag></template>
      </el-table-column>
      <el-table-column prop="spentMs" label="??(ms)" width="100" />
      <el-table-column prop="username" label="???" width="130" show-overflow-tooltip />
      <el-table-column prop="clientIp" label="IP" width="140" />
      <el-table-column label="??" width="190" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.hasRequestFile" link type="primary" @click="copyFile(row.id, 'request')">????</el-button>
          <el-button v-if="row.hasResponseFile" link type="primary" @click="copyFile(row.id, 'response')">????</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
      :page-size="size" :current-page="page" @current-change="onPage" />
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

function onPage(currentPage) {
  page.value = currentPage
  load()
}

async function copyFile(id, kind) {
  try {
    const res = await fetchApiLogFile(id, kind)
    await copyText(res.data || res)
    ElMessage.success(`???${kind === 'request' ? '??' : '??'}??`)
  } catch (e) {
    ElMessage.error('??????')
  }
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
