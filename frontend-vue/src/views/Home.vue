<template>
  <div class="home">
    <div class="home-topbar">
      <div class="ht-left">
        <DmsLogo :size="22" />
        <span class="ht-welcome">工作台 · {{ rangeLabel }}</span>
        <span class="ht-user">{{ userStore.username }} · {{ userTypeLabel }}</span>
      </div>
      <div class="ht-right">
        <el-radio-group v-model="rangeKey" size="small" @change="onRangeChange">
          <el-radio-button label="today">当日</el-radio-button>
          <el-radio-button label="month">本月</el-radio-button>
          <el-radio-button label="quarter">本季</el-radio-button>
          <el-radio-button label="year">本年</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" size="small" @click="loadAll">刷新</el-button>
        <el-button type="primary" link size="small" @click="$router.push('/dashboard')">
          完整仪表盘 →
        </el-button>
      </div>
    </div>

    <div class="shortcut-row">
      <div
        v-for="s in visibleShortcuts"
        :key="s.key"
        class="sc-card"
        @click="navigate(s)"
      >
        <span class="sc-ico"><el-icon :size="24"><component :is="s.icon" /></el-icon></span>
        <span class="sc-label">{{ s.label }}</span>
      </div>
    </div>

    <el-row :gutter="8" class="kpi-row" v-loading="kpiLoading">
      <el-col :span="6" v-for="k in kpiCards" :key="k.key">
        <div class="kpi-card">
          <div class="kpi-head">
            <span class="kpi-l">{{ k.label }}</span>
            <span class="kpi-ic" :style="{ background: k.color + '20', color: k.color }">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                <path v-if="k.key==='totalSales'" d="M12 2v20M6 8l6-6 6 6M4 14l8 8 8-8" stroke="currentColor" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
                <path v-else-if="k.key==='totalOrders'" d="M9 11l3 3L22 4M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" stroke="currentColor" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
                <circle v-else-if="k.key==='activeDealers'" cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.8" fill="none"/>
                <path v-else d="M12 21s-7-4.35-7-10a7 7 0 1114 0c0 5.65-7 10-7 10z" stroke="currentColor" stroke-width="1.8" fill="none"/>
              </svg>
            </span>
          </div>
          <div class="kpi-v" :style="{ color: k.color }">{{ k.display }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="8" class="qs-row" v-if="visibleQuickStats.length">
      <el-col :span="6" v-for="q in visibleQuickStats" :key="q.k">
        <div class="mini-stat">
          <span class="mini-l">{{ q.l }}</span>
          <span class="mini-v" :style="{ color: q.color }">{{ q.v }}</span>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="8" class="chart-row">
      <el-col :span="16">
        <el-card shadow="never" class="chart-card" v-loading="trendLoading" body-style="padding: 12px">
          <template #header>
            <div class="chart-title">销售趋势</div>
          </template>
          <div ref="trendEl" class="chart-area" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="chart-card" v-loading="funnelLoading" body-style="padding: 12px">
          <template #header>
            <div class="chart-title">订单漏斗</div>
          </template>
          <div ref="funnelEl" class="chart-area" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="8" class="chart-row chart-row-2">
      <el-col :span="24">
        <el-card shadow="never" class="chart-card" v-loading="topLoading" body-style="padding: 12px">
          <template #header>
            <div class="chart-title">经销商销售 TOP5</div>
          </template>
          <div class="rank-list">
            <div v-for="(item, idx) in topList" :key="item.name || idx" class="rank-row">
              <span class="rank-no" :class="'rank-no--' + (idx + 1)">{{ idx + 1 }}</span>
              <span class="rank-name" :title="item.name">{{ item.name || '-' }}</span>
              <span class="rank-bar-wrap">
                <span class="rank-bar" :style="{ width: rankWidth(item.value) }" />
              </span>
              <span class="rank-val">¥ {{ fmtMoney(item.value) }}</span>
            </div>
            <el-empty v-if="!topLoading && !topList.length" description="暂无数据" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Refresh } from '@element-plus/icons-vue'
import { getKpi, getSalesTrend, getOrderFunnel, getTopDealers } from '@/api/dashboard'
import request from '@/utils/request'
import * as echarts from 'echarts'

const router = useRouter()
const userStore = useUserStore()
const inventoryEnabled = ref(true)
const userTypeLabel = computed(() => (
  userStore.userType === 'vendor' ? '厂商'
  : userStore.userType === 'dealer' ? '经销商'
  : '用户'
))

const allShortcuts = [
  { key: 'orders', icon: 'Sell', label: '销售订单' },
  { key: 'sales-outs', icon: 'Van', label: '销售出库' },
  { key: 'sales-returns', icon: 'RefreshLeft', label: '销退订单' },
  { key: 'approval', route: '/approval/todo', icon: 'Stamp', label: '我的审批' }
]
const visibleShortcuts = computed(() =>
  allShortcuts.filter(s => !s.inventoryOnly || inventoryEnabled.value)
)

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
  { key: 'totalSales', label: '销售总额', display: fmtMoney(kpiData.totalSales), color: '#2e6ba8' },
  { key: 'totalOrders', label: '订单数', display: fmtNum(kpiData.totalOrders), color: '#52c41a' },
  { key: 'activeDealers', label: '活跃经销商', display: fmtNum(kpiData.activeDealers), color: '#faad14' },
  { key: 'totalSurgeries', label: '手术台数', display: fmtNum(kpiData.totalSurgeries), color: '#ff4d4f' }
]))

const quickStatsAll = computed(() => ([
  { k: 'qualified', l: '合格库存', v: fmtNum(kpiData.qualifiedStock), color: '#52c41a', inventoryOnly: true },
  { k: 'pending', l: '待验库存', v: fmtNum(kpiData.pendingStock), color: '#faad14', inventoryOnly: true },
  { k: 'defective', l: '不合格库存', v: fmtNum(kpiData.defectiveStock), color: '#ff4d4f', inventoryOnly: true },
  { k: 'products', l: '产品总数', v: fmtNum(kpiData.totalProducts), color: '#2e6ba8' }
]))
const visibleQuickStats = computed(() =>
  quickStatsAll.value.filter(q => !q.inventoryOnly || inventoryEnabled.value)
)

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
const trendLoading = ref(false)
const funnelLoading = ref(false)
const topLoading = ref(false)
let trendChart = null
let funnelChart = null
// TOP5 改用排行榜列表，经销商全名一行显示（避免 echarts 长中文名截断）
const topList = ref([])
function rankWidth(v) {
  const max = Math.max(1, ...topList.value.map(d => Number(d.value || 0)))
  const pct = Math.max(4, Math.round(Number(v || 0) / max * 100))
  return pct + '%'
}

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
        itemStyle: { color: '#2e6ba8' }
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
    topList.value = (list || []).slice(0, 5)
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

function loadAll() {
  loadKpi(); loadTrend(); loadFunnel(); loadTop()
}

function onResize() {
  trendChart && trendChart.resize()
  funnelChart && funnelChart.resize()
}

onMounted(async () => {
  try { const r = await request.get('/api/tenant/features'); if (r.data && typeof r.data.inventoryEnabled === 'boolean') inventoryEnabled.value = r.data.inventoryEnabled } catch(e) {}
  loadAll()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart && trendChart.dispose()
  funnelChart && funnelChart.dispose()
})
</script>

<style scoped>
.home { padding: 0; }
.home-topbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; margin-bottom: 8px; padding: 6px 10px;
  background: var(--dms-bg-container); border: 1px solid var(--dms-border-2); border-radius: 4px;
}
.ht-left { display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--dms-text-3); }
.ht-welcome { font-weight: 600; color: var(--dms-text-1); }
.ht-user { color: var(--dms-text-4); }
.ht-right { display: flex; align-items: center; gap: 6px; }
.shortcut-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}
.sc-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--dms-bg-container);
  border: 1px solid var(--dms-border-2);
  border-radius: var(--dms-radius-lg, 8px);
  cursor: pointer;
  transition: all .18s var(--dms-motion-ease-out, ease);
}
.sc-card:hover {
  border-color: var(--dms-color-primary-border);
  box-shadow: var(--dms-shadow-md);
  transform: translateY(-1px);
}
.sc-ico {
  display: inline-flex; align-items: center; justify-content: center;
  width: 42px; height: 42px; flex-shrink: 0;
  border-radius: 10px;
  color: var(--dms-color-primary);
  background: var(--dms-color-primary-bg);
}
.sc-label { font-size: 14px; font-weight: 600; color: var(--dms-text-1); white-space: nowrap; }
.kpi-row { margin-bottom: 6px; }
.kpi-card {
  background: var(--dms-bg-container); border: 1px solid var(--dms-border-2);
  border-radius: 4px; padding: 10px 12px;
  display: flex; flex-direction: column; gap: 4px;
  transition: box-shadow .2s;
}
.kpi-card:hover { box-shadow: 0 2px 10px rgba(22,119,255,0.08); }
.kpi-head { display: flex; align-items: center; justify-content: space-between; }
.kpi-l { font-size: 12px; color: var(--dms-text-4); }
.kpi-ic {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border-radius: 4px;
}
.kpi-v { font-size: 20px; font-weight: 700; line-height: 1.2; letter-spacing: -0.3px; }
.qs-row { margin-bottom: 6px; }
.mini-stat {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 12px; background: var(--dms-bg-container);
  border: 1px dashed var(--dms-border-2); border-radius: 4px;
  font-size: 12px;
}
.mini-l { color: var(--dms-text-4); }
.mini-v { font-weight: 600; font-size: 14px; }
.chart-row { margin-bottom: 6px; }
.chart-row-2 { margin-bottom: 8px; }
.chart-card { margin-bottom: 0; }
.chart-card :deep(.el-card__header) { padding: 8px 12px; border-bottom: none; }
.chart-card :deep(.el-card__body) { padding: 8px 12px !important; }
.chart-title { font-size: 13px; font-weight: 600; color: var(--dms-text-1); }
.chart-area { width: 100%; height: 200px; }
.chart-area-sm { width: 100%; height: 180px; }
.rank-list { display: flex; flex-direction: column; gap: 4px; padding: 2px 4px; }
.rank-row { display: flex; align-items: center; gap: 10px; padding: 7px 8px; border-radius: 6px; }
.rank-row:hover { background: var(--dms-color-primary-bg, #f5f8ff); }
.rank-no {
  flex: 0 0 22px; width: 22px; height: 22px; border-radius: 6px;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: #fff; background: #b8c0cc;
}
.rank-no--1 { background: linear-gradient(135deg,#ff9f43,#f56c00); }
.rank-no--2 { background: linear-gradient(135deg,#8fa6c4,#64789b); }
.rank-no--3 { background: linear-gradient(135deg,#d9a066,#b07b3a); }
.rank-name {
  flex: 0 0 172px; font-size: 13px; color: var(--dms-text-1, #1f2937);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: 500;
}
.rank-bar-wrap { flex: 1 1 auto; height: 12px; background: var(--dms-sider-badge-bg, #f0f2f5); border-radius: 6px; overflow: hidden; min-width: 60px; }
.rank-bar { display: block; height: 100%; border-radius: 6px; background: linear-gradient(90deg,#52c41a,#73d13d); min-width: 6px; }
.rank-val { flex: 0 0 auto; min-width: 86px; text-align: right; font-size: 13px; font-weight: 700; color: var(--dms-text-1, #1f2937); font-family: var(--dms-font-family-number, inherit); }
</style>
