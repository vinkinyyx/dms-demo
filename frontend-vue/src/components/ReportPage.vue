<template>
  <el-card shadow="never" class="rp">
    <template #header>
      <div class="rp-head">
        <div class="rp-head-left">
          <el-button :icon="ArrowLeft" link @click="emit('back')">返回报表中心</el-button>
          <span class="rp-title">{{ meta.title }}</span>
          <el-tag v-if="meta.placeholder" type="warning" size="small" effect="plain">后端接口待补</el-tag>
        </div>
        <div class="rp-head-right">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="table">表格</el-radio-button>
            <el-radio-button label="chart" :disabled="!hasChart">图表</el-radio-button>
            <el-radio-button label="split" :disabled="!hasChart">图表+表格</el-radio-button>
          </el-radio-group>
          <el-button :icon="Refresh" size="small" @click="load">刷新</el-button>
          <el-button :icon="Download" size="small" type="primary" @click="exportXlsx">导出 xlsx</el-button>
        </div>
      </div>
    </template>

    <!-- 筛选区 -->
    <div class="rp-filters">
      <el-select v-model="rangeKey" size="small" style="width: 120px" @change="onRangeChange">
        <el-option v-for="r in rangeOptions" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>
      <template v-for="f in meta.filters" :key="f.key">
        <el-input v-if="f.type === 'text'" v-model="filterValues[f.key]" :placeholder="f.placeholder || f.label" clearable size="small" style="width: 160px" @keyup.enter="load" />
        <el-input v-else-if="f.type === 'date'" v-model="filterValues[f.key]" :placeholder="f.label" type="date" value-format="YYYY-MM-DD" size="small" style="width: 160px" @change="load" />
        <el-select v-else-if="f.type === 'select'" v-model="filterValues[f.key]" :placeholder="f.label" clearable size="small" style="width: 150px" @change="load">
          <el-option v-for="o in (f.options || [])" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </template>
      <el-button type="primary" size="small" @click="load">查询</el-button>
      <el-button size="small" @click="resetFilters">重置</el-button>
      <el-button v-if="favKey" size="small" :icon="Star" @click="saveView">保存视图</el-button>
    </div>

    <!-- KPI 摘要 -->
    <el-row v-if="kpiCards.length" :gutter="12" class="rp-kpi">
      <el-col v-for="k in kpiCards" :key="k.key" :xs="12" :sm="12" :md="6" :lg="6">
        <div class="kpi-card" :style="{ borderTopColor: k.color }">
          <div class="kpi-v">{{ k.display }}</div>
          <div class="kpi-l">{{ k.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <div v-show="viewMode !== 'table'" ref="chartRef" class="rp-chart"></div>

    <!-- 表格 -->
    <div v-show="viewMode !== 'chart'" class="rp-table">
      <el-table :data="rows" v-loading="loading" border stripe size="small" max-height="540" :row-class-name="rowClass" @row-click="onRowClick" highlight-current-row>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column v-for="c in meta.cols" :key="c.k" :prop="c.k" :label="c.l" :width="c.w" :align="c.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="c.format === 'money'" class="money">¥ {{ fmtNum(row[c.k], 2) }}</span>
            <span v-else-if="c.format === 'number'" class="num">{{ fmtNum(row[c.k], 0) }}</span>
            <a v-else-if="c.link" class="link" @click.stop="onLinkClick(c.link, row)">{{ row[c.k] }}</a>
            <span v-else>{{ formatAuto(row[c.k], c.k) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" :description="meta.placeholder || '暂无报表数据'" />
    </div>

    <!-- 穿透明细子卡片 -->
    <el-card v-if="expandedKey" shadow="never" class="rp-child" :body-style="{ padding: '8px 12px' }">
      <template #header>
        <div class="rp-child-head">
          <el-icon><Aim /></el-icon>
          <span class="rp-child-title">{{ childTitle }}</span>
          <el-tag size="small" type="success">穿透数据</el-tag>
          <span class="rp-child-count">共 {{ childRows.length }} 条</span>
          <div class="spacer" />
          <el-button link size="small" @click="expandedKey = null; childRows = []">收起</el-button>
        </div>
      </template>
      <el-table :data="childRows" v-loading="childLoading" border stripe size="small" max-height="320" empty-text="该维度下暂无明细">
        <el-table-column v-if="hasChildSection">
          <template #default="{ row }"><el-tag v-if="row.__section" type="primary" effect="dark">{{ row.__section }}</el-tag></template>
        </el-table-column>
        <el-table-column v-for="(c, idx) in childCols" :key="idx" :prop="c.k" :label="c.l" :width="c.w" :min-width="c.minW" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.__section">&nbsp;</span>
            <span v-else-if="c.format === 'money'" class="money">¥ {{ fmtNum(row[c.k], 2) }}</span>
            <span v-else-if="c.format === 'number'" class="num">{{ fmtNum(row[c.k], 0) }}</span>
            <span v-else>{{ formatAuto(row[c.k], c.k) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import * as XLSX from 'xlsx'
import { ArrowLeft, Aim, Refresh, Download, Star } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { rangeFor } from '@/config/reports'
import { formatAuto } from '@/utils/format'

const props = defineProps({ meta: { type: Object, required: true } })
const emit = defineEmits(['back'])
const router = useRouter()

const viewMode = ref('split')
const loading = ref(false)
const childLoading = ref(false)
const childRows = ref([])
const childTitle = ref('')
const expandedKey = ref(null)
const rows = ref([])
const filterValues = reactive({})
const rangeKey = ref(props.meta.defaultRange || 'year')
const fromTo = ref(rangeFor(rangeKey.value))
const chartRef = ref(null)
let chartInst = null

const PALETTE = ['#2e6ba8', '#52c41a', '#faad14', '#ff4d4f', '#909399', '#722ed1', '#13c2c2', '#fa8c16']

const rangeOptions = [
  { value: 'today', label: '当日' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: '90d', label: '近 90 天' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
  { value: 'quarter', label: '本季' },
  { value: 'year', label: '本年' },
  { value: 'none', label: '不限' }
]

const hasChart = computed(() => !!props.meta.chart)
const childCols = computed(() => {
  const r = childRows.value || []
  if (!r.length) return []
  const skip = new Set(['__section'])
  const sample = r.find(x => !x.__section) || r[0]
  return Object.keys(sample).filter(k => !skip.has(k)).map(k => ({ k, l: humanKey(k), minW: 120 }))
})
const hasChildSection = computed(() => (childRows.value || []).some(r => r.__section))

function humanKey(k) {
  const map = { orderId: '订单ID', orderCode: '订单号', productId: '产品ID', productCode: '产品编码', productName: '产品名',
    qty: '数量', unitPrice: '单价', subTotal: '小计', orderTotal: '订单金额', orderDate: '下单时间',
    dealerId: '经销商ID', dealerName: '经销商', status: '状态', orderType: '类型',
    totalAmount: '总金额', finalAmount: '实付', productCount: '产品数',
    submittedAt: '提交时间', approvedAt: '审批时间',
    lineId: '行号', batchNo: '批次',
    id: 'ID', fromStatus: '原状态', toStatus: '新状态', atTime: '变更时间', remark: '备注',
    reportId: '报台ID', code: '编号', surgeryDate: '手术日', patientInfo: '患者', doctorName: '医生',
    implantQty: '植入数', createdAt: '创建时间' }
  return map[k] || k
}
const kpiCards = ref([])
const favKey = computed(() => `report.view.${props.meta.api}`)

function fmtNum(v, digits = 0) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

function exportValue(row, col) {
  const value = row[col.k]
  if (value == null || value === '') return ''
  if (typeof col.exportFormatter === 'function') return col.exportFormatter(value, row)
  if (col.format === 'money') return Number(value || 0)
  if (col.format === 'number') return Number(value || 0)
  if (col.format === 'percent') return Number(value || 0)
  return value
}

function displayValue(value, col) {
  if (value == null || value === '') return ''
  if (typeof col.exportFormatter === 'function') return col.exportFormatter(value, { [col.k]: value })
  if (col.format === 'money') return '¥ ' + fmtNum(value, 2)
  if (col.format === 'number') return fmtNum(value, 0)
  if (col.format === 'percent') return fmtNum(Number(value) * 100, 1) + '%'
  return value
}

function onRangeChange(v) {
  fromTo.value = rangeFor(v)
  load()
}

function buildKpi() {
  const list = props.meta.kpi || []
  kpiCards.value = list.map((s, i) => {
    let v = 0
    if (s.agg === 'sum') v = rows.value.reduce((a, r) => a + Number(r[s.value] || 0), 0)
    else if (s.agg === 'count') v = rows.value.length
    else if (s.agg === 'avg') v = rows.value.length ? rows.value.reduce((a, r) => a + Number(r[s.value] || 0), 0) / rows.value.length : 0
    else if (s.agg === 'custom') v = rows.value[0] ? Number(rows.value[0][s.value] || 0) : 0
    const display = s.format === 'money' ? '¥ ' + fmtNum(v, 2)
      : s.format === 'percent' ? fmtNum(v * 100, 1) + ' %'
      : fmtNum(v, 0)
    return { key: s.key, label: s.label, display, color: s.color || PALETTE[i % PALETTE.length] }
  })
}

async function load() {
  if (props.meta.placeholder) { rows.value = []; buildKpi(); return }
  loading.value = true
  try {
    const params = {}
    Object.keys(filterValues).forEach(k => {
      const v = filterValues[k]
      if (v == null || v === '') return
      params[k] = v
    })
    if (rangeKey.value !== 'none' && fromTo.value && fromTo.value[0]) {
      params.from = fromTo.value[0]
      params.to = fromTo.value[1]
    }
    const res = await request({ url: props.meta.api, method: props.meta.method || 'get', params: props.meta.method === 'post' ? params : params })
    rows.value = (res && res.data) || []
    buildKpi()
    if (viewMode.value !== 'table' && hasChart.value) await renderChart()
  } catch (e) {
    rows.value = []
    buildKpi()
    ElMessage.error('加载失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function renderChart() {
  await nextTick()
  if (!chartRef.value) return
  if (!chartInst) chartInst = echarts.init(chartRef.value)
  const c = props.meta.chart
  let data = rows.value.slice()
  if (c.topN) data = data.slice(0, c.topN)
  if (!data.length) { chartInst.clear(); return }
  const yArr = Array.isArray(c.y) ? c.y : [c.y]
  if (c.type === 'pie') {
    chartInst.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{ type: 'pie', radius: ['40%', '65%'], data: data.map(r => ({ name: r[c.x] || '-', value: Number(r[c.y] || 0) })) }]
    }, true)
    return
  }
  if (c.type === 'stackBar') {
    const xData = data.map(r => r[c.x])
    const series = yArr.map((y, i) => ({ name: y, type: 'bar', stack: 's', data: data.map(r => Number(r[y] || 0)), itemStyle: { color: PALETTE[i] } }))
    chartInst.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { top: 0 },
      grid: { left: 60, right: 20, top: 30, bottom: 60 },
      xAxis: { type: 'category', data: xData, axisLabel: { rotate: xData.length > 6 ? 30 : 0 } },
      yAxis: { type: 'value' },
      series
    }, true)
    return
  }
  const xData = data.map(r => r[c.x])
  const series = yArr.map((y, i) => ({ name: y, type: c.type || 'bar', data: data.map(r => Number(r[y] || 0)), smooth: true, areaStyle: c.type === 'line' ? {} : undefined, itemStyle: { color: PALETTE[i] } }))
  chartInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: yArr.length > 1 ? { top: 0 } : undefined,
    grid: { left: 60, right: 20, top: yArr.length > 1 ? 30 : 16, bottom: 60 },
    xAxis: { type: 'category', data: xData, axisLabel: { rotate: xData.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value' },
    series
  }, true)
}

function onResize() { chartInst && chartInst.resize() }
watch(viewMode, async (v) => {
  if (v !== 'table' && hasChart.value) {
    await renderChart()
    setTimeout(() => chartInst && chartInst.resize(), 80)
  }
})

function rowClass({ row }) { return props.meta.drilldown && props.meta.drilldown.type === 'row' ? 'rp-clickable' : '' }
async function onRowClick(row) {
  if (!props.meta.drilldown || props.meta.drilldown.type !== 'row') return
  const target = row[props.meta.drilldown.target]
  if (target == null) return
  const child = props.meta.drilldown.child
  // 有子明细时优先行内展开；只有没有 child 时才跳转详情路由
  if (!child) {
    if (props.meta.drilldown.route) {
      router.push({ name: props.meta.drilldown.route.name, params: { id: target } })
    }
    return
  }
  const key = target + ':' + child.endpoint(row)
  if (expandedKey.value === key) {
    expandedKey.value = null
    childRows.value = []
    return
  }
  expandedKey.value = key
  childTitle.value = child.title || '明细'
  childLoading.value = true
  childRows.value = []
  try {
    const url = child.endpoint(row, { from: fromTo.value ? fromTo.value[0] : null, to: fromTo.value ? fromTo.value[1] : null, filters: { ...filterValues } })
    const res = await request({ url, method: 'get' })
    const data = res?.data
    if (Array.isArray(data)) childRows.value = data
    else if (data && typeof data === 'object') {
      // 复合返回（如 order-detail-child）：拆成 lines + history
      childRows.value = []
      if (data.lines) childRows.value.push({ __section: '订单行' }, ...data.lines)
      if (data.history) childRows.value.push({ __section: '状态历史' }, ...data.history)
    }
  } catch (e) {
    childRows.value = []
    ElMessage.error('加载明细失败：' + (e?.message || '未知错误'))
  } finally {
    childLoading.value = false
  }
}
function onLinkClick(link, row) {
  const id = row[link.param]
  if (id != null) router.push({ name: link.route, params: { id } })
}

function resetFilters() {
  Object.keys(filterValues).forEach(k => filterValues[k] = null)
  rangeKey.value = props.meta.defaultRange || 'year'
  fromTo.value = rangeFor(rangeKey.value)
  load()
}

function saveView() {
  const view = { range: rangeKey.value, filters: { ...filterValues } }
  try { localStorage.setItem(favKey.value, JSON.stringify(view)); ElMessage.success('已保存当前筛选为我的视图') } catch (e) {}
}

function tryLoadView() {
  try {
    const raw = localStorage.getItem(favKey.value)
    if (!raw) return
    const v = JSON.parse(raw)
    rangeKey.value = v.range || props.meta.defaultRange
    fromTo.value = rangeFor(rangeKey.value)
    Object.assign(filterValues, v.filters || {})
  } catch (e) {}
}

function exportXlsx() {
  if (!rows.value.length) { ElMessage.warning('暂无数据可导出'); return }
  const headers = ['#', ...props.meta.cols.map(c => c.l)]
  const aoa = [headers]
  rows.value.forEach((r, i) => aoa.push([i + 1, ...props.meta.cols.map(c => displayValue(r[c.k], c))]))
  const aoa2 = []
  aoa2.push(['报表', props.meta.title])
  aoa2.push(['生成时间', new Date().toLocaleString('zh-CN')])
  aoa2.push(['筛选', JSON.stringify({ range: rangeKey.value, ...filterValues })])
  aoa2.push(['数据行数', rows.value.length])
  aoa2.push(['KPI 摘要'])
  ;(props.meta.kpi || []).forEach(k => {
    const c = kpiCards.value.find(x => x.key === k.key)
    aoa2.push([k.label, c ? c.display : ''])
  })
  aoa2.push([])
  aoa2.push(...aoa)
  const ws = XLSX.utils.aoa_to_sheet(aoa2)
  ws['!cols'] = [{ wch: 8 }, ...props.meta.cols.map(c => ({ wch: Math.max(12, String(c.l).length * 2, c.w ? Math.round(c.w / 8) : 12) }))]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '报表')
  const fname = (props.meta.exportName || props.meta.title) + '_' + new Date().toISOString().slice(0, 10) + '.xlsx'
  XLSX.writeFile(wb, fname)
  ElMessage.success('已导出 ' + rows.value.length + ' 行')
}

onMounted(() => { tryLoadView(); load(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); chartInst && chartInst.dispose() })
</script>

<style scoped>
.rp { margin-bottom: 12px; }
.rp-head { display: flex; align-items: center; justify-content: space-between; }
.rp-head-left { display: flex; align-items: center; gap: 10px; }
.rp-title { font-size: 16px; font-weight: 600; }
.rp-head-right { display: flex; align-items: center; gap: 8px; }
.rp-filters { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; padding: 8px 12px; background: var(--dms-bg-page); border-radius: 4px; }
.rp-kpi { margin-bottom: 12px; }
.kpi-card { background: var(--dms-bg-container); border: 1px solid var(--dms-border-2); border-top: 3px solid; border-radius: 4px; padding: 14px 16px; text-align: center; }
.kpi-v { font-size: 22px; font-weight: 700; color: var(--dms-text-2); }
.kpi-l { font-size: 12px; color: var(--dms-text-4); margin-top: 4px; }
.rp-chart { width: 100%; height: 360px; margin-bottom: 12px; }
.rp-table { width: 100%; }
.rp-table :deep(.rp-clickable) { cursor: pointer; }
.rp-table :deep(.rp-clickable:hover) td { background-color: var(--dms-color-primary-bg) !important; }
.rp-table .money { color: var(--dms-color-danger); font-weight: 600; }
.rp-table .num { font-variant-numeric: tabular-nums; }
.rp-table .link { color: var(--dms-color-primary); cursor: pointer; text-decoration: underline; }
.rp-child { margin-top: 12px; border: 1px solid var(--dms-color-success); }
.rp-child-head { display: flex; align-items: center; gap: 8px; }
.rp-child-title { font-weight: 600; }
.rp-child-count { color: var(--dms-text-4); font-size: 12px; }
.spacer { flex: 1; }
</style>

