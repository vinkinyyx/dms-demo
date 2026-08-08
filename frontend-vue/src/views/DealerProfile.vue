<template>
  <div class="dp">
    <el-card shadow="never" class="dp-header">
      <el-button :icon="ArrowLeft" link @click="goBack">返回</el-button>
      <span v-if="basic" class="dp-title">{{ basic.name }} ({{ basic.code }})</span>
      <span v-else class="dp-title">经销商 360 画像</span>
      <div class="spacer" />
      <el-tag v-if="basic" size="small" :type="basic.status === 'active' ? 'success' : 'info'">{{ basic.status || '-' }}</el-tag>
      <el-tag v-if="basic && basic.level" size="small" effect="plain" style="margin-left:6px">{{ basic.level }}</el-tag>
    </el-card>

    <el-row :gutter="12">
      <el-col :xs="24" :sm="24" :md="7" :lg="6">
        <el-card shadow="never" class="dp-side">
          <template v-if="basic">
            <div class="side-row"><span class="lbl">编码</span><span>{{ basic.code }}</span></div>
            <div class="side-row"><span class="lbl">名称</span><span>{{ basic.name }}</span></div>
            <div class="side-row"><span class="lbl">等级</span><el-tag size="small">{{ basic.level || '-' }}</el-tag></div>
            <div class="side-row"><span class="lbl">法人</span><span>{{ basic.legalPerson || '-' }}</span></div>
            <div class="side-row"><span class="lbl">税号</span><span>{{ basic.uscNo || '-' }}</span></div>
            <div class="side-row"><span class="lbl">GSP状态</span><span>{{ basic.gspStatus || '-' }}</span></div>
            <div class="side-row"><span class="lbl">GSP有效期</span><span>{{ fmtDate(basic.gspExpire) }}</span></div>
            <div class="side-row"><span class="lbl">联系人</span><span>{{ basic.contactName || '-' }}</span></div>
            <div class="side-row"><span class="lbl">联系电话</span><span>{{ basic.contactPhone || '-' }}</span></div>
            <div class="side-row"><span class="lbl">邮箱</span><span>{{ basic.contactEmail || '-' }}</span></div>
          </template>
          <el-skeleton v-else :rows="8" animated />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="17" :lg="18">
        <el-tabs v-model="activeTab" class="dp-tabs">
          <el-tab-pane label="KPI 概览" name="kpi" />
          <el-tab-pane label="月度达成" name="achievement" />
          <el-tab-pane label="返利明细" name="rebate" />
          <el-tab-pane label="合同列表" name="contracts" />
          <el-tab-pane label="库存明细" name="inventory" />
        </el-tabs>

        <div v-if="activeTab === 'kpi'" class="tab-pane">
          <el-row :gutter="12" v-loading="kpiLoading">
            <el-col :xs="24" :sm="12" :md="8" v-for="card in summaryCards" :key="card.key">
              <el-card shadow="never" class="kpi-big" :style="{ borderTop: '4px solid ' + card.color }">
                <div class="kpi-b-title">{{ card.title }}</div>
                <div class="kpi-b-v" :style="{ color: card.color }">{{ card.value }}</div>
                <div class="kpi-b-sub">{{ card.sub }}</div>
                <el-progress :percentage="card.percent" :color="card.color" :stroke-width="6" :show-text="false" />
              </el-card>
            </el-col>
          </el-row>
          <el-row :gutter="12" style="margin-top:12px">
            <el-col :xs="12" :sm="8" :md="4" v-for="card in miniCards" :key="card.key">
              <div class="kpi-card-sm" :style="{ borderTopColor: card.color }">
                <div class="kpi-v" :style="{ color: card.color }">{{ card.value }}</div>
                <div class="kpi-l">{{ card.label }}</div>
                <div v-if="card.sub" class="kpi-sub">{{ card.sub }}</div>
              </div>
            </el-col>
          </el-row>
          <el-card shadow="never" style="margin-top:12px">
            <template #header><div class="card-h"><el-icon><TrendCharts /></el-icon> 月度目标达成趋势</div></template>
            <div ref="achEl" class="ach-chart" v-loading="achLoading" />
          </el-card>
        </div>

        <el-card v-show="activeTab !== 'kpi'" shadow="never" class="tab-pane" body-style="padding:0">
          <el-table :data="tabRows" v-loading="tabLoading" border stripe size="small" height="540">
            <template v-for="col in tabCols" :key="col.prop">
              <el-table-column :prop="col.prop" :label="col.label" :width="col.width" :min-width="col.minWidth" :align="col.align" :fixed="col.fixed" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag v-if="col.type === 'tag'" size="small" :type="col.tagType ? col.tagType(row[col.prop]) : ''">{{ col.formatter ? col.formatter(row[col.prop], row) : row[col.prop] }}</el-tag>
                  <span v-else-if="col.type === 'money'" :class="col.negative && Number(row[col.prop] || 0) < 0 ? 'neg' : ''">¥ {{ fmtNum(row[col.prop], 2) }}</span>
                  <span v-else-if="col.type === 'percent'">{{ fmtPct(row[col.prop]) }}</span>
                  <span v-else-if="col.formatter">{{ col.formatter(row[col.prop], row) }}</span>
                  <span v-else>{{ row[col.prop] ?? '-' }}</span>
                </template>
              </el-table-column>
            </template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()
const dealerId = ref(route.params.id || route.params.dealerId || route.query.dealerId)
const basic = ref(null)
const activeTab = ref('kpi')
const kpiLoading = ref(false)
const achLoading = ref(false)
const tabLoading = ref(false)
const kpi = reactive({})
const achRows = ref([])
const tabRows = ref([])
const achEl = ref(null)
let achChart = null
let achResizeObserver = null
let achRenderTimer = null

const fmtNum = (v, digits = 0) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
const fmtPct = v => fmtNum(Number(v || 0) * 100, 1) + '%'
const signedPct = v => (Number(v || 0) >= 0 ? '+' : '') + fmtPct(v)
const fmtPeriod = v => v ? String(v).slice(0, 4) + '-' + String(v).slice(4, 6) : '-'
const fmtDate = v => v ? String(v).slice(0, 10) : '-'
const clampPct = v => Math.min(100, Math.max(0, Math.round(Number(v || 0) * 100)))
const rateColor = v => Number(v || 0) >= 1 ? '#67C23A' : Number(v || 0) >= 0.8 ? '#E6A23C' : '#F56C6C'
const mom = (prev, actual) => prev ? (Number(actual || 0) - Number(prev || 0)) / Number(prev) : 0

const summaryCards = computed(() => [
  { key: 'month', title: '本月销售目标达成', value: '¥ ' + fmtNum(kpi.monthActual, 2), sub: '目标 ¥' + fmtNum(kpi.monthTarget, 2) + ' / 差额 ¥' + fmtNum(kpi.monthGap, 2), percent: clampPct(kpi.monthAchievement), color: rateColor(kpi.monthAchievement) },
  { key: 'ytd', title: '年累计目标达成', value: '¥ ' + fmtNum(kpi.ytdActual, 2), sub: '年目标 ¥' + fmtNum(kpi.ytdTarget, 2) + ' / 剩余 ¥' + fmtNum(kpi.ytdGap, 2), percent: clampPct(kpi.ytdAchievement), color: rateColor(kpi.ytdAchievement) },
  { key: 'mom', title: '环比上月', value: signedPct(kpi.momRate), sub: '上月 ¥' + fmtNum(kpi.prevActual, 2) + ' / 本月 ¥' + fmtNum(kpi.monthActual, 2), percent: Math.min(100, Math.abs(Number(kpi.momRate || 0) * 100)), color: Number(kpi.momRate || 0) >= 0 ? '#67C23A' : '#F56C6C' }
])
const miniCards = computed(() => [
  { key: 'orders', value: kpi.monthOrders ?? 0, label: '本月订单数', sub: 'YTD ' + (kpi.ytdOrders ?? 0) + ' 单', color: '#409EFF' },
  { key: 'rebate', value: '¥' + fmtNum(kpi.ytdRebate, 0), label: 'YTD净返利', sub: '按当前达成预提', color: '#67C23A' },
  { key: 'return', value: '¥' + fmtNum(kpi.returnAmount, 0), label: 'YTD退货', sub: '退货率 ' + fmtPct(kpi.returnRate), color: '#E6A23C' },
  { key: 'stock', value: fmtNum(kpi.inventoryQty, 0), label: '库存数量', sub: '库存金额 ¥' + fmtNum(kpi.inventoryAmount, 0), color: '#9b59b6' },
  { key: 'sku', value: kpi.inventorySku ?? 0, label: '库存SKU', sub: '合格率 ' + fmtPct(kpi.qualifiedRate), color: '#16a085' },
  { key: 'contracts', value: kpi.activeContracts ?? 0, label: '有效合同', sub: '90天到期 ' + (kpi.expiringContracts ?? 0), color: '#F56C6C' }
])

const tabCols = computed(() => {
  if (activeTab.value === 'achievement') return [
    { prop: 'periodYyyymm', label: '期间', width: 100, formatter: v => fmtPeriod(v) },
    { prop: 'targetAmount', label: '目标额', width: 130, align: 'right', type: 'money' },
    { prop: 'actualAmount', label: '实际额', width: 130, align: 'right', type: 'money' },
    { prop: 'achievementRate', label: '达成率', width: 110, align: 'right', type: 'percent' },
    { prop: 'gapAmount', label: '差额', width: 130, align: 'right', type: 'money', negative: true },
    { prop: 'mom', label: '环比', width: 100, align: 'right', type: 'percent', formatter: (_, row) => fmtPct(mom(row.prevActual, row.actualAmount)) },
    { prop: 'tierHit', label: '档位', width: 90 },
    { prop: 'grossRebate', label: '毛返利', width: 120, align: 'right', type: 'money' },
    { prop: 'netRebate', label: '净返利', width: 120, align: 'right', type: 'money' }
  ]
  if (activeTab.value === 'rebate') return [
    { prop: 'periodYyyymm', label: '期间', width: 100, formatter: v => fmtPeriod(v) },
    { prop: 'targetAmount', label: '销售目标', width: 130, align: 'right', type: 'money' },
    { prop: 'actualAmount', label: '实际销售', width: 130, align: 'right', type: 'money' },
    { prop: 'achievementRate', label: '达成率', width: 100, align: 'right', type: 'percent' },
    { prop: 'tierHit', label: '档位', width: 80 },
    { prop: 'grossRebate', label: '毛返利', width: 120, align: 'right', type: 'money' },
    { prop: 'deductionAmount', label: '扣减', width: 110, align: 'right', type: 'money' },
    { prop: 'netRebate', label: '净返利', width: 120, align: 'right', type: 'money' },
    { prop: 'settlementStatus', label: '结算状态', width: 110, type: 'tag', tagType: v => v === '已结算' ? 'success' : v === '预提中' ? 'warning' : 'info' }
  ]
  if (activeTab.value === 'contracts') return [
    { prop: 'code', label: '合同编号', width: 135, fixed: true },
    { prop: 'contractName', label: '合同名称', minWidth: 220 },
    { prop: 'category', label: '类型', width: 110 },
    { prop: 'vendorParty', label: '甲方', minWidth: 180 },
    { prop: 'dealerParty', label: '乙方', minWidth: 180 },
    { prop: 'term', label: '期限', width: 210, formatter: (_, row) => fmtDate(row.validFrom) + ' 至 ' + fmtDate(row.validTo) },
    { prop: 'termDays', label: '天数', width: 80, align: 'right' },
    { prop: 'targetAmount', label: '目标额', width: 120, align: 'right', type: 'money' },
    { prop: 'signedAmount', label: '签约额', width: 120, align: 'right', type: 'money' },
    { prop: 'rebateRate', label: '返利率', width: 90, align: 'right', formatter: v => fmtPct(v) },
    { prop: 'settlementCycle', label: '结算周期', width: 100 },
    { prop: 'status', label: '状态', width: 100, type: 'tag', tagType: statusTag, formatter: statusLabel },
    { prop: 'ownerName', label: '负责人', width: 100 },
    { prop: 'ownerPhone', label: '电话', width: 130 },
    { prop: 'businessScope', label: '业务范围', minWidth: 260 }
  ]
  return [
    { prop: 'productCode', label: '产品编码', width: 130 },
    { prop: 'productNameCn', label: '产品名称', minWidth: 180 },
    { prop: 'productSpec', label: '规格', width: 120 },
    { prop: 'categoryName', label: '分类', width: 120 },
    { prop: 'warehouseName', label: '仓库', width: 120 },
    { prop: 'batchNo', label: '批次', width: 140 },
    { prop: 'qty', label: '数量', width: 110, align: 'right', formatter: (v, row) => fmtNum(v, 2) + ' ' + (row.productUnit || '') },
    { prop: 'unitPrice', label: '单价', width: 110, align: 'right', type: 'money' },
    { prop: 'amount', label: '金额', width: 120, align: 'right', type: 'money' },
    { prop: 'stockStatus', label: '状态', width: 90, type: 'tag', tagType: stockTag, formatter: stockLabel },
    { prop: 'dates', label: '生产/效期', width: 210, formatter: (_, row) => fmtDate(row.prodDate) + ' / ' + fmtDate(row.expDate) }
  ]
})

function statusTag(s) { return { effective: 'success', approved: 'success', signed: 'success', draft: 'info', expired: 'warning', terminated: 'danger' }[s] || 'info' }
function statusLabel(s) { return { effective: '生效中', approved: '已审批', signed: '已签署', draft: '草稿', expired: '已到期', terminated: '已终止' }[s] || (s || '-') }
function stockTag(s) { return { QUALIFIED: 'success', PENDING: 'warning', DEFECTIVE: 'danger', QUARANTINED: 'danger' }[s] || 'info' }
function stockLabel(s) { return { QUALIFIED: '合格', PENDING: '待验', DEFECTIVE: '不合格', QUARANTINED: '隔离' }[s] || (s || '-') }
function goBack() { if (window.history.length > 1) router.back(); else router.push('/home') }

async function loadBasic() {
  try { basic.value = (await request({ url: '/api/dealer-profile/' + dealerId.value + '/basic', method: 'get' }))?.data || null }
  catch { ElMessage.error('加载经销商信息失败') }
}
async function loadKpi() {
  kpiLoading.value = true; achLoading.value = true
  try {
    const res = await request({ url: '/api/dealer-profile/' + dealerId.value + '/kpi', method: 'get' })
    Object.assign(kpi, res?.data || {})
    const r = await request({ url: '/api/dealer-profile/' + dealerId.value + '/achievement', method: 'get' })
    achRows.value = r?.data || []
    await nextTick(); renderAch()
  } catch { ElMessage.error('加载KPI失败') }
  finally { kpiLoading.value = false; achLoading.value = false }
}
async function loadTab(name) {
  tabLoading.value = true
  try { tabRows.value = (await request({ url: '/api/dealer-profile/' + dealerId.value + '/' + name, method: 'get' }))?.data || [] }
  catch { tabRows.value = []; ElMessage.error('加载' + name + '失败') }
  finally { tabLoading.value = false }
}
function renderAch() {
  if (!achEl.value) return
  if (achChart) { achChart.dispose(); achChart = null }
  if (achResizeObserver) { achResizeObserver.disconnect(); achResizeObserver = null }
  achChart = echarts.init(achEl.value)
  const rows = achRows.value || []
  achChart.setOption({
    tooltip: { trigger: 'axis' }, legend: { top: 0 },
    grid: { left: 55, right: 60, top: 40, bottom: 35 },
    xAxis: { type: 'category', data: rows.map(x => fmtPeriod(x.periodYyyymm || x.period)) },
    yAxis: [{ type: 'value', name: '金额' }, { type: 'value', name: '达成率', axisLabel: { formatter: '{value}%' }, max: 150 }],
    series: [
      { name: '目标', type: 'line', data: rows.map(x => Number(x.targetAmount || 0)), lineStyle: { type: 'dashed' } },
      { name: '实际', type: 'bar', data: rows.map(x => Number(x.actualAmount || 0)), itemStyle: { color: '#409EFF' } },
      { name: '达成率', type: 'line', yAxisIndex: 1, smooth: true, data: rows.map(x => Math.round(Number(x.achievementRate || 0) * 100)), itemStyle: { color: '#67C23A' } }
    ]
  })
  achChart.resize()
  if (!achResizeObserver && window.ResizeObserver) {
    achResizeObserver = new ResizeObserver(() => achChart && achChart.resize())
    achResizeObserver.observe(achEl.value)
  }
}
function scheduleRenderAch() {
  if (achRenderTimer) clearTimeout(achRenderTimer)
  achRenderTimer = setTimeout(() => {
    if (activeTab.value === 'kpi' && achRows.value.length) {
      nextTick(() => {
        renderAch()
        requestAnimationFrame(() => achChart && achChart.resize())
      })
    }
  }, 80)
}
watch(activeTab, async n => {
  await nextTick()
  if (n === 'kpi') { if (achRows.value.length) scheduleRenderAch(); else loadKpi() }
  else loadTab(n)
})
function onResize() { achChart && achChart.resize() }
onMounted(() => { loadBasic(); loadKpi(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (achRenderTimer) clearTimeout(achRenderTimer)
  if (achResizeObserver) achResizeObserver.disconnect()
  if (achChart) achChart.dispose()
})
</script>

<style scoped>
.dp-header { margin-bottom: 12px; display: flex; align-items: center; gap: 10px; }
.dp-title { font-size: 16px; font-weight: 600; }
.spacer { flex: 1; }
.dp-side { min-height: 420px; }
.side-row { display: flex; justify-content: space-between; padding: 7px 0; font-size: 13px; border-bottom: 1px dashed #ebeef5; gap: 10px; }
.side-row .lbl { color: #909399; flex-shrink: 0; }
.side-row span:last-child { text-align: right; word-break: break-all; }
.tab-pane { padding: 12px 0; }
.kpi-big { padding: 14px 18px; }
.kpi-b-title { font-size: 13px; color: #909399; margin-bottom: 6px; }
.kpi-b-v { font-size: 25px; font-weight: 700; line-height: 1.3; }
.kpi-b-sub { font-size: 12px; color: #909399; margin: 6px 0 8px; }
.kpi-card-sm { background: #fff; border: 1px solid #ebeef5; border-top: 3px solid; border-radius: 4px; padding: 12px 10px; text-align: center; min-height: 104px; }
.kpi-card-sm .kpi-v { font-size: 21px; font-weight: 700; }
.kpi-card-sm .kpi-l { font-size: 12px; color: #909399; margin-top: 4px; }
.kpi-card-sm .kpi-sub { font-size: 11px; color: #c0c4cc; margin-top: 3px; }
.ach-chart { width: 100%; height: 300px; }
.card-h { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.dp-tabs { background: #fff; padding: 0 12px; border-radius: 4px; margin-bottom: 10px; }
.dp-tabs :deep(.el-tabs__nav-wrap::after) { background: transparent; }
.neg { color: #f56c6c !important; }
.pos { color: #67c23a !important; }
</style>
