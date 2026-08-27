<template>
  <div class="page-card">
    <div class="bar">
      <ResourcePicker resource="dealers" v-model="dealerId" :display-value="dealerName" placeholder="按经销商筛选（可选）" @pick="onDealer"/>
      <el-input v-model="keyword" placeholder="产品编码/名称/批号/序列号" clearable style="width:240px" @keyup.enter="load"/>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      <span class="tip">寄售金额按产品标准价汇总；可用量 = 在库 - 锁定（开票预占）。</span>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column label="经销商" prop="dealerName" min-width="160" show-overflow-tooltip/>
      <el-table-column label="产品编码" prop="productCode" width="130"/>
      <el-table-column label="产品名称" prop="productName" min-width="180" show-overflow-tooltip/>
      <el-table-column label="规格" prop="productSpec" width="120" show-overflow-tooltip/>
      <el-table-column label="批号" prop="batchNo" width="130"/>
      <el-table-column label="序列号" prop="serialNo" width="140"/>
      <el-table-column label="仓库" prop="warehouseName" width="110"/>
      <el-table-column label="在库" prop="onHandQty" width="80" align="right"/>
      <el-table-column label="锁定" prop="lockedQty" width="80" align="right"/>
      <el-table-column label="可用" prop="availableQty" width="80" align="right">
        <template #default="{row}"><el-tag type="success">{{ row.availableQty }}</el-tag></template>
      </el-table-column>
      <el-table-column label="标准单价" prop="stdUnitPrice" width="110" align="right"/>
      <el-table-column label="可用金额" prop="availableAmount" width="120" align="right"/>
    </el-table>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import ResourcePicker from '@/components/ResourcePicker.vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
const rows = ref([]); const loading = ref(false)
const dealerId = ref(null); const dealerName = ref(''); const keyword = ref('')
function onDealer(p){ dealerId.value = p?.value ?? null; dealerName.value = p?.label ?? ''; load() }
async function load(){
  loading.value = true
  try {
    const params = {}
    if (dealerId.value) params.dealerId = dealerId.value
    if (keyword.value) params.keyword = keyword.value
    const res = await request({ url: '/api/consignment/available', params })
    rows.value = res?.data || []
  } catch(e){ ElMessage.error('查询寄售库存失败'); rows.value=[] } finally { loading.value=false }
}
function reset(){ dealerId.value=null; dealerName.value=''; keyword.value=''; load() }
onMounted(load)
</script>
<style scoped>
.page-card{background:#fff;padding:16px;border-radius:4px}
.bar{display:flex;gap:10px;align-items:center;margin-bottom:14px;flex-wrap:wrap}
.tip{color:#909399;font-size:12px}
</style>
