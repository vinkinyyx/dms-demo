<template>
  <div class="expiry-page">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">批次效期预警</span>
        <el-select v-model="withinDays" style="width:160px" @change="reload(1)">
          <el-option label="30 天内到期" :value="30" />
          <el-option label="90 天内到期" :value="90" />
          <el-option label="180 天内到期" :value="180" />
          <el-option label="365 天内到期" :value="365" />
        </el-select>
        <el-input v-model="keyword" placeholder="产品编码/名称/批号" clearable style="width:240px" @keyup.enter="reload(1)" />
        <el-button type="primary" @click="reload(1)">查询</el-button>
      </div>

      <el-row :gutter="12" class="summary">
        <el-col :span="6"><el-alert type="error" :closable="false" title="已过期" :description="summary.expired + ' 批次'" /></el-col>
        <el-col :span="6"><el-alert type="warning" :closable="false" title="30天内到期" :description="summary.within30 + ' 批次'" /></el-col>
        <el-col :span="6"><el-alert type="info" :closable="false" title="90天内到期" :description="summary.within90 + ' 批次'" /></el-col>
        <el-col :span="6"><el-alert type="success" :closable="false" title="180天内到期" :description="summary.within180 + ' 批次'" /></el-col>
      </el-row>

      <el-table :data="rows" v-loading="loading" border stripe style="margin-top:12px">
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.expired" type="danger">已过期</el-tag>
            <el-tag v-else-if="row.daysToExpiry <= 30" type="danger">{{ row.daysToExpiry }}天</el-tag>
            <el-tag v-else-if="row.daysToExpiry <= 90" type="warning">{{ row.daysToExpiry }}天</el-tag>
            <el-tag v-else type="info">{{ row.daysToExpiry }}天</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCode" label="产品编码" width="140" />
        <el-table-column prop="productName" label="产品名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="batchNo" label="批号" width="140" />
        <el-table-column prop="serialNo" label="序列号" width="140" />
        <el-table-column prop="qty" label="库存数量" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="warehouseName" label="仓库" width="140" show-overflow-tooltip />
        <el-table-column prop="expDate" label="到期日期" width="130" />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false), rows = ref([]), total = ref(0)
const page = ref(1), size = ref(20), withinDays = ref(90), keyword = ref('')
const summary = reactive({ expired: 0, within30: 0, within90: 0, within180: 0 })

async function reload(p = page.value) {
  page.value = p; loading.value = true
  try {
    const { data } = await request({ url: '/api/inventory/expiry-alerts', method: 'get',
      params: { withinDays: withinDays.value, page, size, keyword: keyword.value || undefined } })
    rows.value = data?.list || []; total.value = data?.total || 0
  } finally { loading.value = false }
}
async function loadSummary() {
  const { data } = await request({ url: '/api/inventory/expiry-summary', method: 'get' })
  Object.assign(summary, data || {})
}
function onPage(p) { reload(p) }
onMounted(() => { reload(1); loadSummary() })
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.title { font-size: 16px; font-weight: 600; margin-right: 12px; }
.summary { margin-bottom: 4px; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }
</style>