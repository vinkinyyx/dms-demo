<template>
  <div class="dash">
    <!-- 筛选条 -->
    <el-card shadow="never" class="dash-filters">
      <div class="filter-row">
        <span class="filter-label">数据范围：</span>
        <el-radio-group v-model="rangeKey" size="small" @change="onRangeChange">
          <el-radio-button label="today">当日</el-radio-button>
          <el-radio-button label="7d">近 7 天</el-radio-button>
          <el-radio-button label="30d">近 30 天</el-radio-button>
          <el-radio-button label="month">本月</el-radio-button>
          <el-radio-button label="quarter">本季</el-radio-button>
          <el-radio-button label="year">本年</el-radio-button>
        </el-radio-group>
        <el-date-picker v-model="customRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="自定义起" end-placeholder="自定义止" size="small" style="margin-left: 8px; width: 240px" @change="onCustomRange" />
        <div class="spacer" />
        <el-button :icon="Setting" size="small" @click="editMode = !editMode">{{ editMode ? '完成' : '编辑布局' }}</el-button>
        <el-button v-if="editMode" :icon="RefreshLeft" size="small" @click="resetLayout">重置布局</el-button>
        <el-button :icon="Refresh" size="small" @click="loadAll">刷新</el-button>
      </div>
      <div class="filter-row" style="margin-top: 6px">
        <span class="filter-label">经销商：</span>
        <el-select v-model="dealerId" placeholder="全部" clearable filterable remote :remote-method="searchDealers" :loading="dLoading" size="small" style="width: 200px">
          <el-option v-for="d in dealerOpts" :key="d.id" :label="d.code + ' / ' + d.name" :value="d.id" />
        </el-select>
        <span class="filter-label" style="margin-left: 12px">状态：</span>
        <el-select v-model="status" placeholder="全部" clearable size="small" style="width: 140px">
          <el-option v-for="s in statusOpts" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <span class="filter-label" style="margin-left: 12px">类型：</span>
        <el-select v-model="orderType" placeholder="全部" clearable size="small" style="width: 120px">
          <el-option label="常规" value="NORMAL" />
          <el-option label="加急" value="URGENT" />
        </el-select>
      </div>
    </el-card>

    <!-- KPI -->
    <el-row :gutter="12" class="kpi-row">
      <el-col v-for="k in kpiCards" :key="k.key" :span="6">
        <el-card shadow="hover" class="kpi-card" :style="{ borderTop: '3px solid ' + k.color }">
          <div class="kpi-v" :style="{ color: k.color }">{{ k.display }}</div>
          <div class="kpi-l">{{ k.label }}</div>
          <div v-if="k.delta" class="kpi-d" :class="k.deltaUp ? 'up' : 'down'">
            <el-icon><CaretTop v-if="k.deltaUp" /><CaretBottom v-else /></el-icon>
            <span>{{ k.delta }} 同比</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 可拖拽的图表区 -->
    <draggable
      v-model="orderedBlocks"
      :disabled="!editMode"
      :animation="180"
      handle=".block-handle"
      item-key="key"
      class="block-grid"
    >
      <template #item="{ element: b }">
        <div v-show="visible[b.key]" class="block-wrap" :class="{ 'is-edit': editMode }">
          <el-card shadow="never" class="block-card">
            <template #header>
              <div class="block-header">
                <el-icon v-if="editMode" class="block-handle"><Rank /></el-icon>
                <span class="block-title">{{ b.title }}</span>
                <div class="spacer" />
                <el-button v-if="editMode" link size="small" type="danger" @click="hideBlock(b.key)">隐藏</el-button>
              </div>
            </template>
            <div :ref="el => bindChartRef(el, b.key)" class="block-chart"></div>
          </el-card>
        </div>
      </template>
    </draggable>

    <!-- 隐藏区块恢复（编辑模式才显示） -->
    <el-card v-if="editMode && hiddenKeys.length" shadow="never" class="restore-card">
      <template #header>已隐藏的区块（点击恢复显示）</template>
      <el-space wrap>
        <el-button v-for="k in hiddenKeys" :key="k" :icon="View" size="small" @click="showBlock(k)">{{ getBlockTitle(k) }}</el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import draggable from 'vuedraggable'
import * as echarts from 'echarts'
import { Setting, Refresh, RefreshLeft, Rank, CaretTop, CaretBottom, View } from '@element-plus/icons-vue'
import { getKpi, getSalesTrend, getInventoryPie, getTopDealers, getOrderFunnel, getTopHospitals, getActivity7d } from '@/api/dashboard'
import { rangeFor } from '@/config/reports'
import request from '@/utils/request'

const LAYOUT_KEY = 'dashboard.layout.v1'
const DEFAULT_BLOCKS = [
  { key: 'trend', title: '销售趋势' },
  { key: 'pie', title: '库存三态' },
  { key: 'topDealers', title: '销售 TOP 经销商' },
  { key: 'funnel', title: '订单漏斗' },
  { key: 'topHospitals', title: '医院手术 TOP' },
  { key: 'activity', title: '近 7 日活跃' }
]

// 筛选
const rangeKey = ref('year')
const customRange = ref(rangeFor('year'))
const dealerId = ref(null)
const status = ref(null)
const orderType = ref(null)
const dealerOpts = ref([])
const dLoading = ref(false)

const statusOpts = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVED', label: '已审核' },
  { value: 'SHIPPING', label: '出库中' },
  { value: 'COMPLETED', label: '已完成' }
]

const kpi = reactive({})
const kpiCards = ref([])

const editMode = ref(false)
const orderedBlocks = ref([...DEFAULT_BLOCKS])
const visible = reactive({})
DEFAULT_BLOCKS.forEach(b => visible[b.key] = true)
const chartRefs = {}
const charts = {}

function getBlockTitle(k) { return DEFAULT_BLOCKS.find(b => b.key === k)?.title || k }
const hiddenKeys = computed(() => DEFAULT_BLOCKS.map(b => b.key).filter(k => !visible[k]))

function onRangeChange(v) {
  if (v !== 'year') customRange.value = rangeFor(v)
  loadAll()
}
function onCustomRange(v) { if (v && v.length === 2) loadAll() }

async function searchDealers(q) {
  if (!q) return
  dLoading.value = true
  try {
    const res = await request({ url: '/api/dealers', method: 'get', params: { keyword: q, size: 20 } })
    const data = res?.data || {}
    dealerOpts.value = (data.records || data.rows || data || []).map(d => ({ id: d.id, code: d.code, name: d.name }))
  } finally { dLoading.value = false }
}

const commonParams = () => {
  const p = { period: rangeKey.value }
  if (dealerId.value) p.dealerId = dealerId.value
  if (status.value) p.status = status.value
  if (orderType.value) p.orderType = orderType.value
  return p
}

function bindChartRef(el, key) {
  if (el) chartRefs[key] = el
}

function ensureChart(key) {
  if (charts[key]) return charts[key]
  const el = chartRefs[key]
  if (!el) return null
  charts[key] = echarts.init(el)
  return charts[key]
}

function buildKpi() {
  const totalSales = Number(kpi.totalSales || 0)
  kpiCards.value = [
    { key: 'totalSales', label: '总销售额', display: '¥ ' + totalSales.toLocaleString('zh-CN', { maximumFractionDigits: 0 }), color: '#1677ff' },
    { key: 'totalOrders', label: '订单数', display: kpi.totalOrders || 0, color: '#52c41a' },
    { key: 'activeDealers', label: '活跃经销商', display: kpi.activeDealers || 0, color: '#faad14' },
    { key: 'totalProducts', label: '产品数', display: kpi.totalProducts || 0, color: '#909399' },
    { key: 'qualifiedStock', label: '合格库存', display: kpi.qualifiedStock || 0, color: '#52c41a' },
    { key: 'pendingStock', label: '待检库存', display: kpi.pendingStock || 0, color: '#faad14' },
    { key: 'defectiveStock', label: '不合格库存', display: kpi.defectiveStock || 0, color: '#ff4d4f' },
    { key: 'totalSurgeries', label: '报台数', display: kpi.totalSurgeries || 0, color: '#1677ff' }
  ]
}

async function loadAll() {
  const p = commonParams()
  try {
    const [k, trend, pie, td, fn, th, act] = await Promise.all([
      getKpi(p).catch(() => ({ data: {} })),
      getSalesTrend(p).catch(() => ({ data: [] })),
      getInventoryPie(p).catch(() => ({ data: [] })),
      getTopDealers(p).catch(() => ({ data: [] })),
      getOrderFunnel(p).catch(() => ({ data: [] })),
      getTopHospitals(p).catch(() => ({ data: [] })),
      getActivity7d(p).catch(() => ({ data: {} }))
    ])
    Object.assign(kpi, k.data || {})
    buildKpi()
    await nextTick()
    renderTrend((trend.data || []).slice(0, 12))
    renderPie(pie.data || [])
    renderTopDealers(td.data || [])
    renderFunnel(fn.data || [])
    renderTopHospitals(th.data || [])
    renderActivity(act.data || {})
  } catch (e) { /* 已逐项 catch */ }
}

function renderTrend(data) {
  const c = ensureChart('trend'); if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: data.map(d => d.month) },
    yAxis: { type: 'value' },
    series: [{ name: '销售额', type: 'line', smooth: true, areaStyle: {}, data: data.map(d => Number(d.amount || 0)), itemStyle: { color: '#1677ff' } }]
  }, true)
}
function renderPie(data) {
  const c = ensureChart('pie'); if (!c) return
  const COLORS = { QUALIFIED: '#52c41a', PENDING: '#faad14', DEFECTIVE: '#ff4d4f' }
  c.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['40%', '65%'], data: data.map(d => ({ name: d.name, value: Number(d.value || 0), itemStyle: { color: COLORS[d.name] || '#909399' } })) }]
  }, true)
}
function renderTopDealers(data) {
  const c = ensureChart('topDealers'); if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 100, right: 20, top: 10, bottom: 20 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: data.map(d => d.name).reverse() },
    series: [{ type: 'bar', data: data.map(d => Number(d.value || 0)).reverse(), itemStyle: { color: '#1677ff' } }]
  }, true)
}
function renderFunnel(data) {
  const c = ensureChart('funnel'); if (!c) return
  c.setOption({
    tooltip: { trigger: 'item' },
    series: [{ type: 'funnel', left: 20, right: 20, top: 10, bottom: 10, data: data.map(d => ({ name: d.name, value: Number(d.value || 0) })) }]
  }, true)
}
function renderTopHospitals(data) {
  const c = ensureChart('topHospitals'); if (!c) return
  c.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 100, right: 20, top: 10, bottom: 20 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: data.map(d => d.name).reverse() },
    series: [{ type: 'bar', data: data.map(d => Number(d.value || 0)).reverse(), itemStyle: { color: '#52c41a' } }]
  }, true)
}
function renderActivity(data) {
  const c = ensureChart('activity'); if (!c) return
  const days = Array.from(new Set([...(data.orders || []), ...(data.surgeries || []), ...(data.receipts || [])].map(d => d.date))).sort()
  const mapBy = (arr) => days.map(d => { const f = (arr || []).find(x => x.date === d); return f ? Number(f.count || 0) : 0 })
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单', '手术', '入库'], top: 0 },
    grid: { left: 50, right: 20, top: 30, bottom: 20 },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value' },
    series: [
      { name: '订单', type: 'bar', data: mapBy(data.orders), itemStyle: { color: '#1677ff' } },
      { name: '手术', type: 'bar', data: mapBy(data.surgeries), itemStyle: { color: '#52c41a' } },
      { name: '入库', type: 'bar', data: mapBy(data.receipts), itemStyle: { color: '#faad14' } }
    ]
  }, true)
}

function onResize() { Object.values(charts).forEach(c => c.resize()) }

function hideBlock(k) { visible[k] = false; saveLayout() }
function showBlock(k) { visible[k] = true; saveLayout() }
function resetLayout() {
  DEFAULT_BLOCKS.forEach(b => visible[b.key] = true)
  orderedBlocks.value = [...DEFAULT_BLOCKS]
  saveLayout()
  loadAll()
}

function saveLayout() {
  try {
    localStorage.setItem(LAYOUT_KEY, JSON.stringify({ order: orderedBlocks.value.map(b => b.key), visible: { ...visible } }))
  } catch (e) {}
}
function loadLayout() {
  try {
    const raw = localStorage.getItem(LAYOUT_KEY)
    if (!raw) return
    const v = JSON.parse(raw)
    if (Array.isArray(v.order)) {
      const sorted = v.order.map(k => DEFAULT_BLOCKS.find(b => b.key === k)).filter(Boolean)
      DEFAULT_BLOCKS.forEach(b => { if (!sorted.find(x => x.key === b.key)) sorted.push(b) })
      orderedBlocks.value = sorted
    }
    if (v.visible) {
      DEFAULT_BLOCKS.forEach(b => { if (v.visible[b.key] === false) visible[b.key] = false })
    }
  } catch (e) {}
}

watch(orderedBlocks, () => saveLayout(), { deep: true })

onMounted(() => { loadLayout(); loadAll(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); Object.values(charts).forEach(c => c.dispose()) })
</script>

<style scoped>
.dash { padding: 0; }
.dash-filters { margin-bottom: 12px; }
.filter-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.filter-label { color: var(--dms-text-4); font-size: 13px; }
.spacer { flex: 1; }
.kpi-row { margin-bottom: 12px; }
.kpi-card { text-align: center; }
.kpi-v { font-size: 24px; font-weight: 700; }
.kpi-l { font-size: 13px; color: var(--dms-text-4); margin-top: 6px; }
.kpi-d { font-size: 12px; margin-top: 4px; display: flex; align-items: center; justify-content: center; gap: 2px; }
.kpi-d.up { color: var(--dms-color-success); }
.kpi-d.down { color: var(--dms-color-danger); }
.block-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.block-wrap.is-edit .block-card { border: 2px dashed var(--dms-color-primary); }
.block-card { margin-bottom: 0; }
.block-header { display: flex; align-items: center; gap: 8px; }
.block-handle { cursor: move; color: var(--dms-text-4); }
.block-title { font-size: 14px; font-weight: 600; }
.block-chart { width: 100%; height: 300px; }
.restore-card { margin-top: 12px; }
</style>
