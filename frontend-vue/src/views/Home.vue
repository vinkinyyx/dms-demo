<template>
  <div class="home">
    <!-- 欢迎区 -->
    <el-card shadow="never" class="welcome">
      <div class="welcome-row">
        <div class="welcome-brand">
          <DmsLogo :size="40" class="welcome-logo" />
          <div>
          <h2>欢迎使用 DMS 通用经销商管理系统</h2>
          <p>
            当前登录：<b>{{ userStore.username }}</b>（{{ userTypeLabel }}），
            租户 {{ userStore.user?.tenantId || '-' }}
          </p>
          </div>
        </div>
        <div class="welcome-tip">
          <el-tag type="success" effect="plain">Vue3 + Element Plus 栈</el-tag>
          <el-tag type="info" effect="plain" style="margin-left:6px">仪表盘数据：{{ rangeLabel }}</el-tag>
        </div>
      </div>
    </el-card>

    <!-- 快捷入口 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="6" v-for="s in shortcuts" :key="s.key">
        <el-card shadow="hover" class="shortcut" @click="navigate(s)">
          <el-icon :size="30" color="var(--dms-color-primary)"><component :is="s.icon" /></el-icon>
          <div class="sc-label">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 仪表盘核心区 -->
    <el-card shadow="never" class="dash">
      <template #header>
        <div class="dash-header">
          <span><b>仪表盘速览</b>（默认本年）</span>
          <div class="spacer" />
          <el-radio-group v-model="rangeKey" size="small" @change="onRangeChange">
            <el-radio-button label="today">当日</el-radio-button>
            <el-radio-button label="month">本月</el-radio-button>
            <el-radio-button label="quarter">本季</el-radio-button>
            <el-radio-button label="year">本年</el-radio-button>
          </el-radio-group>
          <el-button :icon="Refresh" size="small" @click="loadAll">刷新</el-button>
          <el-button type="primary" link size="small" @click="$router.push('/dashboard')">
            查看完整仪表盘 →
          </el-button>
        </div>
      </template>

      <!-- KPI -->
      <el-row :gutter="12" class="kpi-row" v-loading="kpiLoading">
        <el-col :span="6" v-for="k in kpiCards" :key="k.key">
          <div class="kpi-card" :style="{ borderTop: '3px solid ' + k.color }">
            <div class="kpi-v" :style="{ color: k.color }">{{ k.display }}</div>
            <div class="kpi-l">{{ k.label }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 图表区 -->
      <el-row :gutter="12" class="chart-row">
        <el-col :span="16">
          <el-card shadow="never" class="chart-card" v-loading="trendLoading">
            <template #header>
              <div class="chart-title">销售趋势（{{ rangeLabel }}）</div>
            </template>
            <div ref="trendEl" class="chart-area" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="chart-card" v-loading="funnelLoading">
            <template #header>
              <div class="chart-title">订单漏斗</div>
            </template>
            <div ref="funnelEl" class="chart-area" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="chart-row" style="margin-top: 12px">
        <el-col :span="12">
          <el-card shadow="never" class="chart-card" v-loading="topLoading">
            <template #header>
              <div class="chart-title">经销商销售 TOP5</div>
            </template>
            <div ref="topEl" class="chart-area" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="chart-card" v-loading="topLoading">
            <template #header>
              <div class="chart-title">本月速览</div>
            </template>
            <div class="quick-stats">
              <div class="qs" v-for="q in quickStats" :key="q.k">
                <div class="qs-v" :style="{ color: q.color }">{{ q.v }}</div>
                <div class="qs-l">{{ q.l }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Refresh } from '@element-plus/icons-vue'
import { getKpi, getSalesTrend, getOrderFunnel, getTopDealers } from '@/api/dashboard'
import * as echarts from 'echarts'

const router = useRouter()
const userStore = useUserStore()
const userTypeLabel = computed(() => (
  userStore.userType === 'vendor' ? '厂商'
  : userStore.userType === 'dealer' ? '经销商'
  : '用户'
))

const shortcuts = [
  { key: 'products', icon: 'Goods', label: '产品管理' },
  { key: 'dealers', icon: 'OfficeBuilding', label: '经销商管理' },
  { key: 'orders', icon: 'Sell', label: '销售订单' },
  { key: 'inventory', icon: 'Box', label: '库存查询' }
]

function navigate(s) {
  if (s.route) router.push(s.route)
  else router.push('/m/' + s.key)
}

const RANGE_LABELS = { today: '当日', month: '本月', quarter: '本季', year: '本年' }
const rangeKey = ref(localStorage.getItem('dms:home:range') || 'year')
const rangeLabel = computed(() => RANGE_LABELS[rangeKey.value] || '本年')
function onRangeChange(v) { localStorage.setItem('dms:home:range', v); loadAll() }

// KPI
const kpiLoading = ref(false)
const kpiData = reactive({})
const kpiCards = computed(() => ([
  { key: 'totalSales', label: '销售总额', display: fmtMoney(kpiData.totalSales), color: '#1677ff' },
  { key: 'totalOrders', label: '订单数', display: fmtNum(kpiData.totalOrders), color: '#52c41a' },
  { key: 'activeDealers', label: '活跃经销商', display: fmtNum(kpiData.activeDealers), color: '#faad14' },
  { key: 'totalSurgeries', label: '手术台数', display: fmtNum(kpiData.totalSurgeries), color: '#ff4d4f' }
]))

const quickStats = computed(() => ([
  { k: 'qualified', l: '合格库存', v: fmtNum(kpiData.qualifiedStock), color: '#52c41a' },
  { k: 'pending', l: '待验库存', v: fmtNum(kpiData.pendingStock), color: '#faad14' },
  { k: 'defective', l: '不合格库存', v: fmtNum(kpiData.defectiveStock), color: '#ff4d4f' },
  { k: 'products', l: '产品总数', v: fmtNum(kpiData.totalProducts), color: '#1677ff' }
]))

function fmtMoney(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  return '¥ ' + n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function fmtNum(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  return n.toLocaleString('zh-CN')
}

// 图表
const trendEl = ref(null)
const funnelEl = ref(null)
const topEl = ref(null)
const trendLoading = ref(false)
const funnelLoading = ref(false)
const topLoading = ref(false)
let trendChart = null
let funnelChart = null
let topChart = null

async function loadKpi() {
  kpiLoading.value = true
  try {
    const r = await getKpi({ period: rangeKey.value })
    Object.assign(kpiData, r?.data || {})
  } catch (e) { /* ignore */ }
  finally { kpiLoading.value = false }
}
async function loadTrend() {
  trendLoading.value = true
  try {
    const r = await getSalesTrend({ period: rangeKey.value })
    const list = r?.data || []
    await nextTick(); ensureTrend()
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: list.map(x => x.month || x.date || x.day || '') },
      yAxis: { type: 'value' },
      series: [{
        name: '销售额', type: 'line', smooth: true, areaStyle: { opacity: 0.2 },
        data: list.map(x => Number(x.amount || x.value || 0)),
        itemStyle: { color: '#1677ff' }
      }]
    }, true)
  } catch (e) { /* ignore */ }
  finally { trendLoading.value = false }
}
async function loadFunnel() {
  funnelLoading.value = true
  try {
    const r = await getOrderFunnel({ period: rangeKey.value })
    const list = r?.data || []
    await nextTick(); ensureFunnel()
    funnelChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'funnel', left: 20, right: 20, top: 10, bottom: 10,
        data: list.map(d => ({ name: d.name, value: Number(d.value || 0) }))
      }]
    }, true)
  } catch (e) { /* ignore */ }
  finally { funnelLoading.value = false }
}
async function loadTop() {
  topLoading.value = true
  try {
    const r = await getTopDealers({ period: rangeKey.value })
    const list = r?.data || []
    await nextTick(); ensureTop()
    topChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 100, right: 20, top: 10, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: list.map(d => d.name).reverse() },
      series: [{
        type: 'bar',
        data: list.map(d => Number(d.value || 0)).reverse(),
        itemStyle: { color: '#52c41a' }
      }]
    }, true)
  } catch (e) { /* ignore */ }
  finally { topLoading.value = false }
}

function ensureTrend() {
  if (!trendChart && trendEl.value) trendChart = echarts.init(trendEl.value)
  return trendChart
}
function ensureFunnel() {
  if (!funnelChart && funnelEl.value) funnelChart = echarts.init(funnelEl.value)
  return funnelChart
}
function ensureTop() {
  if (!topChart && topEl.value) topChart = echarts.init(topEl.value)
  return topChart
}

function loadAll() {
  loadKpi(); loadTrend(); loadFunnel(); loadTop()
}

function onResize() {
  trendChart && trendChart.resize()
  funnelChart && funnelChart.resize()
  topChart && topChart.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart && trendChart.dispose()
  funnelChart && funnelChart.dispose()
  topChart && topChart.dispose()
})
</script>

<style scoped>
.home { padding: 0; }
.welcome { margin-bottom: 16px; }
.welcome h2 { margin: 0 0 8px; }
.welcome p { color: #666; margin: 0; }
.welcome-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.welcome-tip { white-space: nowrap; }
.cards { margin-bottom: 16px; }
.shortcut { text-align: center; cursor: pointer; padding: 14px 0; }
.shortcut:hover { box-shadow: 0 4px 12px rgba(64,158,255,0.2); }
.sc-label { margin-top: 10px; font-size: 14px; color: #333; }
.dash-header { display: flex; align-items: center; gap: 8px; }
.spacer { flex: 1; }
.kpi-row { margin-bottom: 12px; }
.kpi-card { background: var(--dms-bg-container); border: 1px solid var(--dms-border-2); border-top: 3px solid; border-radius: 4px; padding: 14px 16px; text-align: center; }
.kpi-v { font-size: 24px; font-weight: 700; }
.kpi-l { font-size: 13px; color: var(--dms-text-4); margin-top: 6px; }
.chart-row { display: flex; }
.chart-card { margin-bottom: 0; }
.chart-title { font-size: 14px; font-weight: 600; }
.chart-area { width: 100%; height: 280px; }
.quick-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 20px 12px; }
.qs { text-align: center; padding: 10px; border: 1px solid var(--dms-border-2); border-radius: 4px; }
.qs-v { font-size: 22px; font-weight: 700; }
.qs-l { font-size: 13px; color: var(--dms-text-4); margin-top: 4px; }
.welcome-brand { display: flex; align-items: center; gap: 12px; }
.welcome-brand h2 { margin: 0; }
.welcome-brand p { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.welcome-logo { filter: drop-shadow(0 2px 6px rgba(22,119,255,.25)); }
</style>