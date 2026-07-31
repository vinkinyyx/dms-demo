<template>
  <div class="dashboard">
    <el-row :gutter="12" class="kpi-row">
      <el-col :span="6" v-for="k in kpiCards" :key="k.key">
        <el-card shadow="hover" class="kpi-card" :style="{ borderTop: '3px solid ' + k.color }">
          <div class="kpi-value" :style="{ color: k.color }">{{ k.display }}</div>
          <div class="kpi-label">{{ k.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :span="16"><el-card shadow="never"><div class="chart-title">近12个月销售趋势</div><div ref="trendRef" class="chart"></div></el-card></el-col>
      <el-col :span="8"><el-card shadow="never"><div class="chart-title">库存状态分布</div><div ref="pieRef" class="chart"></div></el-card></el-col>
    </el-row>
    <el-row :gutter="12">
      <el-col :span="8"><el-card shadow="never"><div class="chart-title">本月销售 TOP5 经销商</div><div ref="dealerRef" class="chart"></div></el-card></el-col>
      <el-col :span="8"><el-card shadow="never"><div class="chart-title">订单状态漏斗</div><div ref="funnelRef" class="chart"></div></el-card></el-col>
      <el-col :span="8"><el-card shadow="never"><div class="chart-title">医院手术 TOP10</div><div ref="hospRef" class="chart"></div></el-card></el-col>
    </el-row>
    <el-row :gutter="12">
      <el-col :span="24"><el-card shadow="never"><div class="chart-title">近7天活跃度（订单/手术/入库）</div><div ref="actRef" class="chart"></div></el-card></el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getKpi, getSalesTrend, getInventoryPie, getTopDealers, getOrderFunnel, getTopHospitals, getActivity7d } from '@/api/dashboard'

const trendRef = ref(); const pieRef = ref(); const dealerRef = ref(); const funnelRef = ref(); const hospRef = ref(); const actRef = ref()
const charts = []
const kpi = reactive({})

const kpiCards = ref([])
const PIE_COLORS = { QUALIFIED: '#67C23A', PENDING: '#E6A23C', DEFECTIVE: '#F56C6C' }

function money(v) { return '¥ ' + Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }

function buildKpi() {
  kpiCards.value = [
    { key: 'totalSales', label: '总销售额', display: money(kpi.totalSales), color: '#409EFF' },
    { key: 'totalOrders', label: '订单总数', display: kpi.totalOrders || 0, color: '#67C23A' },
    { key: 'activeDealers', label: '活跃经销商', display: kpi.activeDealers || 0, color: '#E6A23C' },
    { key: 'totalProducts', label: '产品总数', display: kpi.totalProducts || 0, color: '#909399' },
    { key: 'qualifiedStock', label: '合格库存', display: kpi.qualifiedStock || 0, color: '#67C23A' },
    { key: 'pendingStock', label: '待检库存', display: kpi.pendingStock || 0, color: '#E6A23C' },
    { key: 'defectiveStock', label: '不合格库存', display: kpi.defectiveStock || 0, color: '#F56C6C' },
    { key: 'totalSurgeries', label: '手术报台数', display: kpi.totalSurgeries || 0, color: '#409EFF' }
  ]
}

function mk(el) { const c = echarts.init(el); charts.push(c); return c }

async function loadAll() {
  const kpiRes = await getKpi()
  Object.assign(kpi, kpiRes.data || {})
  buildKpi()
  await nextTick()

  const trend = (await getSalesTrend()).data || []
  mk(trendRef.value).setOption({
    tooltip: { trigger: 'axis' }, grid: { left: 60, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: trend.map((d) => d.month) },
    yAxis: { type: 'value' },
    series: [{ name: '销售额', type: 'line', smooth: true, areaStyle: {}, data: trend.map((d) => Number(d.amount || 0)) }]
  })

  const pie = (await getInventoryPie()).data || []
  mk(pieRef.value).setOption({
    tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['40%', '65%'], data: pie.map((d) => ({ name: d.name, value: Number(d.value || 0), itemStyle: { color: PIE_COLORS[d.name] } })) }]
  })

  const dealers = (await getTopDealers()).data || []
  mk(dealerRef.value).setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } }, grid: { left: 90, right: 30, top: 20, bottom: 30 },
    xAxis: { type: 'value' }, yAxis: { type: 'category', data: dealers.map((d) => d.name).reverse() },
    series: [{ type: 'bar', data: dealers.map((d) => Number(d.value || 0)).reverse(), itemStyle: { color: '#409EFF' } }]
  })

  const funnel = (await getOrderFunnel()).data || []
  mk(funnelRef.value).setOption({
    tooltip: { trigger: 'item' },
    series: [{ type: 'funnel', left: 20, right: 20, data: funnel.map((d) => ({ name: d.name, value: Number(d.value || 0) })) }]
  })

  const hosp = (await getTopHospitals()).data || []
  mk(hospRef.value).setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } }, grid: { left: 100, right: 30, top: 20, bottom: 30 },
    xAxis: { type: 'value' }, yAxis: { type: 'category', data: hosp.map((d) => d.name).reverse() },
    series: [{ type: 'bar', data: hosp.map((d) => Number(d.value || 0)).reverse(), itemStyle: { color: '#67C23A' } }]
  })

  const act = (await getActivity7d()).data || {}
  const days = Array.from(new Set([...(act.orders || []), ...(act.surgeries || []), ...(act.receipts || [])].map((d) => d.date))).sort()
  const mapBy = (arr) => days.map((d) => { const f = (arr || []).find((x) => x.date === d); return f ? Number(f.count || 0) : 0 })
  mk(actRef.value).setOption({
    tooltip: { trigger: 'axis' }, legend: { data: ['订单', '手术', '入库'], top: 0 }, grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value' },
    series: [
      { name: '订单', type: 'bar', data: mapBy(act.orders) },
      { name: '手术', type: 'bar', data: mapBy(act.surgeries) },
      { name: '入库', type: 'bar', data: mapBy(act.receipts) }
    ]
  })
}

function onResize() { charts.forEach((c) => c.resize()) }

onMounted(() => { loadAll(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); charts.forEach((c) => c.dispose()) })
</script>

<style scoped>
.kpi-row { margin-bottom: 12px; }
.kpi-card { text-align: center; margin-bottom: 12px; }
.kpi-value { font-size: 24px; font-weight: 700; }
.kpi-label { font-size: 13px; color: #909399; margin-top: 6px; }
.chart-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.chart { height: 300px; }
.el-row { margin-bottom: 12px; }
</style>
