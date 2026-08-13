<template>
  <div>
    <div class="toolbar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width:160px" @keyup.enter="reload(1)" />
      <el-select v-model="query.success" placeholder="结果" clearable style="width:120px">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-input v-model="query.loginType" placeholder="登录类型" clearable style="width:140px" />
      <el-date-picker v-model="dateRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始" end-placeholder="结束" />
      <el-button type="primary" @click="reload(1)">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="atTime" label="时间" width="180" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="displayName" label="姓名" width="120" />
      <el-table-column prop="loginType" label="类型" width="120" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'">{{ row.successLabel || (row.success ? '成功' : '失败') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP" width="140" />
      <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
      <el-table-column prop="userAgent" label="User Agent" min-width="220" show-overflow-tooltip />
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="query.page" :page-size="query.size" @current-change="onPage" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '@/utils/request'
const rows = ref([]), loading = ref(false), total = ref(0)
const query = reactive({ page: 1, size: 20, username: '', success: null, loginType: '' })
const dateRange = ref([])
async function reload(page = query.page) {
  query.page = page; loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value && dateRange.value.length === 2) { params.startTime = dateRange.value[0]; params.endTime = dateRange.value[1] }
    const { data } = await request({ url: '/api/system/login-logs', method: 'get', params })
    rows.value = data?.list || []; total.value = data?.total || 0
  } finally { loading.value = false }
}
function onPage(p) { reload(p) }
function reset() { query.username = ''; query.success = null; query.loginType = ''; dateRange.value = []; reload(1) }
onMounted(() => reload(1))
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }
</style>
