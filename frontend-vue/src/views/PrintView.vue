<template>
  <div class="print-page" v-loading="loading">
    <div class="no-print toolbar">
      <el-button type="primary" @click="doPrint"><el-icon><Printer /></el-icon> 打印</el-button>
      <el-button @click="goBack">返回</el-button>
      <span class="hint">{{ title }}（A4 纵向）</span>
    </div>

    <div class="sheet" v-if="data">
      <div class="sheet-head">
        <h2>{{ companyName }}</h2>
        <h3>{{ title }}</h3>
      </div>

      <table class="info">
        <tr>
          <td class="label">单号</td><td>{{ data.code || data.contractCode || '-' }}</td>
          <td class="label">日期</td><td>{{ fmtDate(data.orderDate || data.salesDate || data.createdAt) }}</td>
        </tr>
        <tr>
          <td class="label">客户/经销商</td><td>{{ data.dealerName || data.customerName || '-' }}</td>
          <td class="label">医院</td><td>{{ data.hospitalName || '-' }}</td>
        </tr>
        <tr v-if="type === 'contract'">
          <td class="label">合同类型</td><td>{{ data.appType || data.category || '-' }}</td>
          <td class="label">状态</td><td>{{ data.statusName || data.status || '-' }}</td>
        </tr>
        <tr v-else>
          <td class="label">业务员</td><td>{{ data.salesName || '-' }}</td>
          <td class="label">仓库</td><td>{{ data.warehouseName || '-' }}</td>
        </tr>
      </table>

      <table class="lines">
        <thead>
          <tr>
            <th class="idx">#</th>
            <th>产品编码</th>
            <th>产品名称</th>
            <th>规格</th>
            <th>批号</th>
            <th>序列号</th>
            <th class="num">数量</th>
            <th class="num">单价</th>
            <th class="num">金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in lines" :key="i">
            <td class="idx">{{ i + 1 }}</td>
            <td>{{ l.productCode || l.code || '-' }}</td>
            <td>{{ l.productName || l.name || '-' }}</td>
            <td>{{ l.spec || '-' }}</td>
            <td>{{ l.batchNo || '-' }}</td>
            <td>{{ l.serialNo || '-' }}</td>
            <td class="num">{{ l.qty || l.quantity || 0 }}</td>
            <td class="num">{{ fmtMoney(l.price || l.unitPrice) }}</td>
            <td class="num">{{ fmtMoney(l.amount || lineAmount(l)) }}</td>
          </tr>
          <tr v-if="!lines.length"><td colspan="9" class="empty">无明细</td></tr>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="8" class="num total-label">合计金额</td>
            <td class="num total-val">{{ fmtMoney(totalAmount) }}</td>
          </tr>
        </tfoot>
      </table>

      <div class="sign">
        <div>制单人：{{ data.createdName || data.creatorName || '________' }}</div>
        <div>审核人：__________</div>
        <div>收货人/客户签字：__________</div>
        <div>日期：__________</div>
      </div>

      <div class="foot-note" v-if="data.remark">备注：{{ data.remark }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Printer } from '@element-plus/icons-vue'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const type = route.params.type
const id = route.params.id
const data = ref(null)
const loading = ref(false)
const companyName = 'DMS 经销存管理系统'

const titleMap = { salesOrder: '销售订单', salesOut: '销售出库单', contract: '合同' }
const title = titleMap[type] || '单据打印'

const lines = computed(() => {
  if (!data.value) return []
  return data.value.lines || data.value.items || data.value.details || data.value.products || []
})
const totalAmount = computed(() => {
  if (data.value?.totalAmount != null) return data.value.totalAmount
  return lines.value.reduce((sum, l) => sum + Number(l.amount || lineAmount(l) || 0), 0)
})
function lineAmount(l) {
  const qty = Number(l.qty || l.quantity || 0)
  const price = Number(l.price || l.unitPrice || 0)
  return (qty * price).toFixed(2)
}
function fmtMoney(v) {
  if (v === null || v === undefined || v === '') return ''
  return Number(v).toFixed(2)
}
function fmtDate(v) {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}

const URL_MAP = {
  salesOrder: '/api/sales-orders/',
  salesOut: '/api/sales-outs/',
  contract: '/api/contracts/'
}

async function load() {
  loading.value = true
  try {
    const { data: resp } = await request({ url: URL_MAP[type] + id, method: 'get' })
    data.value = resp && resp.data ? resp.data : resp
  } finally { loading.value = false }
}
function doPrint() { window.print() }
function goBack() { router.back() }
onMounted(load)
</script>

<style>
@media print {
  .no-print { display: none !important; }
  body { background: #fff; }
}
.print-page { background: #f0f2f5; min-height: 100vh; padding: 20px; }
.print-page .toolbar { display: flex; gap: 12px; align-items: center; max-width: 800px; margin: 0 auto 16px; }
.print-page .hint { color: #909399; font-size: 13px; }
.sheet { background: #fff; max-width: 800px; margin: 0 auto; padding: 40px; box-shadow: 0 2px 12px rgba(0,0,0,.08); }
.sheet-head { text-align: center; margin-bottom: 20px; }
.sheet-head h2 { margin: 0 0 8px; font-size: 22px; }
.sheet-head h3 { margin: 0; font-size: 18px; font-weight: 600; }
table.info { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
table.info td { border: 1px solid #333; padding: 6px 10px; font-size: 13px; }
table.info .label { background: #f5f7fa; width: 14%; font-weight: 600; }
table.lines { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
table.lines th, table.lines td { border: 1px solid #333; padding: 6px 8px; font-size: 12px; }
table.lines th { background: #f5f7fa; font-weight: 600; }
table.lines .idx { width: 36px; text-align: center; }
table.lines .num { text-align: right; }
table.lines .empty { text-align: center; color: #909399; padding: 16px; }
.total-label, .total-val { font-weight: 700; }
.sign { display: flex; justify-content: space-between; margin-top: 40px; font-size: 13px; }
.foot-note { margin-top: 16px; font-size: 12px; color: #606266; }
</style>