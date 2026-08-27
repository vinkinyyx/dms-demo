<template>
  <div class="page-card">
    <div class="bar">
      <el-input v-model="keyword" placeholder="经销商编码/名称" clearable style="width:240px" @keyup.enter="load"/>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      <span class="tip">信用额度/账期、寄售额度在经销商主数据中维护；寄售占用按标准价自动汇总。超额/超账期触发审批。</span>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column label="编码" prop="code" width="130"/>
      <el-table-column label="经销商" prop="name" min-width="180" show-overflow-tooltip/>
      <el-table-column label="等级" prop="level" width="80"/>
      <el-table-column label="信用额度" prop="creditLimit" width="110" align="right"/>
      <el-table-column label="信用占用" prop="creditUsed" width="110" align="right"/>
      <el-table-column label="可用额度" prop="creditAvailable" width="110" align="right">
        <template #default="{row}"><span :class="{over:row.creditOver}">{{ row.creditAvailable }}</span></template>
      </el-table-column>
      <el-table-column label="账期(天)" prop="paymentDays" width="90" align="right"/>
      <el-table-column label="结算方式" prop="settlementMethod" width="110"/>
      <el-table-column label="信用等级" prop="creditGrade" width="90"/>
      <el-table-column label="寄售" width="90" align="center">
        <template #default="{row}"><el-tag :type="row.consignmentEnabled?'success':'info'" size="small">{{ row.consignmentEnabled?'开启':'未开' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="寄售额度" prop="consignmentLimit" width="110" align="right"/>
      <el-table-column label="寄售占用" width="120" align="right">
        <template #default="{row}"><span :class="{over:row.consignmentOver}">{{ row.consignmentUsed }}</span></template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination layout="total, sizes, prev, pager, next" :total="total" :page-sizes="[20,50,100]" :page-size="size" :current-page="page" @current-change="p=>{page=p;load()}" @size-change="s=>{size=s;page=1;load()}"/>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
const rows = ref([]); const loading = ref(false)
const keyword = ref(''); const page = ref(1); const size = ref(20); const total = ref(0)
async function load(){
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (keyword.value) params.keyword = keyword.value
    const res = await request({ url: '/api/dealer-credit', params })
    const d = res?.data || {}
    rows.value = d.list || []; total.value = d.total || 0
  } catch(e){ ElMessage.error('查询资信账期失败'); rows.value=[] } finally { loading.value=false }
}
function reset(){ keyword.value=''; page.value=1; load() }
onMounted(load)
</script>
<style scoped>
.page-card{background:#fff;padding:16px;border-radius:4px}
.bar{display:flex;gap:10px;align-items:center;margin-bottom:14px;flex-wrap:wrap}
.tip{color:#909399;font-size:12px}
.over{color:var(--el-color-danger);font-weight:600}
.pager{display:flex;justify-content:flex-end;margin-top:12px}
</style>
