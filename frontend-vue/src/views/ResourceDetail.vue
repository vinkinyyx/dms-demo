<template>
  <div class="resource-detail">
    <div class="rd-header">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <span class="rd-title">{{ title }}</span>
      <div class="rd-actions">
        <slot name="actions" :data="detail">
          <el-button v-if="canEdit" @click="onEdit">编辑</el-button>
        </slot>
      </div>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-descriptions :column="3" border size="small" v-if="detail">
        <el-descriptions-item v-for="f in fields" :key="f.key" :label="f.label || f.key">
          <el-tag v-if="f.key === statusKey" size="small" :type="statusTagType(detail[f.key])">{{ statusText(detail[f.key]) }}</el-tag>
          <span v-else-if="isDate(f)" class="date-text">{{ fmt(detail[f.key]) }}</span>
          <span v-else-if="f.type === 'boolean'" class="bool-text">{{ detail[f.key] ? '是' : '否' }}</span>
          <span v-else class="cell-text">{{ fieldDisplay(f, detail) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-if="!loading && !detail" description="未找到记录" />
    </el-card>

    <el-card shadow="never" v-if="linesFields.length && detailLines.length" class="rd-lines">
      <template #header><span>{{ linesTitle || '明细' }}</span></template>
      <el-table :data="detailLines" border size="small" stripe>
        <el-table-column v-for="f in linesFields" :key="f.k" :prop="f.k" :label="f.l" :width="f.w" align="center">
          <template #default="{ row }">{{ lineValue(row, f) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="moduleKey === 'product-prices' && Array.isArray(detail?.componentPrices) && detail.componentPrices.length" shadow="never" class="rd-lines">
      <template #header><span>BOM子件价格</span></template>
      <el-table :data="detail.componentPrices" border size="small" stripe>
        <el-table-column prop="productCode" label="子件SKU" width="160" />
        <el-table-column prop="productName" label="子件名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="inclPrice" label="含税价" width="120" align="right" />
        <el-table-column prop="exclPrice" label="不含税价" width="120" align="right" />
        <el-table-column prop="taxRate" label="税率" width="100" align="right" />
      </el-table>
    </el-card>

    <slot name="extra" :data="detail" />

    <el-card shadow="never" class="rd-logs">
      <template #header><span>操作日志</span></template>
      <el-timeline v-if="logs.length">
        <el-timeline-item v-for="(log, idx) in logs" :key="idx" :timestamp="fmt(log.atTime)" placement="top">
          <div class="log-head">
            <el-tag size="small">{{ log.username || log.operator || '系统' }}</el-tag>
            <span class="log-action">{{ log.action }}</span>
          </div>
          <div class="log-changes" v-if="log.changes">{{ log.changes }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作日志" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getOperationLogs } from '@/api/crud'
import { MODULE_CONFIGS } from '@/config/modules'
import { statusText as st, statusTagType as stt } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  moduleKey: { type: String, required: true },
  title: { type: String, default: '' },
  idGetter: { type: Function, default: null }
})
const route = useRoute()
const router = useRouter()
const cfg = computed(() => MODULE_CONFIGS[props.moduleKey] || {})
const fields = computed(() => (cfg.value.form || []).filter(f => f.key && f.type !== 'lines' && f.type !== 'button'))
const linesFields = computed(() => {
  const lines = (cfg.value.form || []).find(f => f.type === 'lines')
  return lines?.cols || []
})
const linesTitle = computed(() => (cfg.value.form || []).find(f => f.type === 'lines')?.label)
const statusKey = computed(() => (cfg.value.cols || []).find(c => c.k === 'status') ? 'status' : 'versionStatus')
const canEdit = computed(() => false)

const detail = ref(null)
const detailLines = ref([])
const logs = ref([])
const loading = ref(false)

function display(f, v) {
  if (v === null || v === undefined || v === '') return '-'
  if (f.type === 'select' && Array.isArray(f.options)) {
    const o = f.options.find(x => String(x.value) === String(v))
    return o ? o.label : v
  }
  return v
}
function fieldDisplay(f, row) {
  const k = f.key
  if (k === 'productId') return [row.productCode, row.productName].filter(Boolean).join(' ') || row.sku || display(f, row[k])
  if (k === 'partnerId') return row.partnerName || display(f, row[k])
  if (k === 'bomParentProductId') return [row.bomParentCode, row.bomParentName].filter(Boolean).join(' ') || display(f, row[k])
  if (k === 'inclPrice' && row.priceContext === 'BOM_HEADER') return '见子件价格'
  if (k === 'exclPrice' && row.priceContext === 'BOM_HEADER') return '见子件价格'
  return display(f, row[k])
}
function lineValue(row, f) {
  const raw = row[f.k]
  if (f.type === 'select' && Array.isArray(f.options)) {
    const opt = f.options.find((o) => String(o.value) === String(raw))
    if (opt) return opt.label
  }
  if (f.type === 'picker' || /Id$/.test(f.k)) {
    const display = pickerDisplay(row, f)
    if (display) return display
  }
  return fmt(raw, f)
}

function pickerDisplay(row, f) {
  const k = f.k
  const stem = k.replace(/Id$/, '')
  const displayKey = f.displayKey
  const name = displayKey || (stem ? stem + 'Name' : null)
  const code = stem ? stem + 'Code' : null
  const nameVal = name ? row[name] : null
  const codeVal = code ? row[code] : null
  if (nameVal && codeVal) return `${codeVal} ${nameVal}`
  if (nameVal) return nameVal
  if (codeVal) return codeVal
  if (k === 'childProductId') return row.childProductCode || row.childProductName || row[k]
  return ''
}
function isDate(f) { return f.type === 'datetime' || f.type === 'date' }
function statusText(v) { return st(v) }
function statusTagType(v) { return stt(v) }

function normalizeLines(lines) {
  return (lines || []).map((x) => ({ ...x, ...(x.ruleDetail || {}) }))
}
function fmt(v, f) {
  if (v === null || v === undefined || v === '') return '-'
  if (typeof v === 'number' && f && /price|amount|Price|Amount/.test(f.k || f.key || '')) return Number(v).toFixed(2)
  if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(v)) return formatDateTime(v)
  if (typeof v === 'object') return JSON.stringify(v)
  return v
}

function onEdit() {
  if (cfg.value.editPath) { router.push(cfg.value.editPath.replace(':id', route.params.id)); return }
  router.push({ path: `/m/${props.moduleKey}`, query: { edit: route.params.id } })
}

async function load() {
  const id = props.idGetter ? props.idGetter() : route.params.id
  if (!id || !cfg.value.api) return
  loading.value = true
  try {
    const res = await request({ url: `${cfg.value.api}/${id}`, method: 'get' })
    detail.value = res?.data || null
    detailLines.value = normalizeLines(detail.value?.lines || detail.value?.rules || [])
  } catch (e) { detail.value = null } finally { loading.value = false }
  try {
    const resourceType = cfg.value.businessType || props.moduleKey
    const bizMap = { 'product-bundles': 'productBundle', promotions: 'promotion', orders: 'salesOrder', 'sales-returns': 'salesReturn', 'purchase-orders': 'purchaseOrder', 'purchase-returns': 'purchaseReturn' }
    const bizType = bizMap[props.moduleKey] || null
    const lr = await getOperationLogs(resourceType, id, bizType)
    logs.value = Array.isArray(lr?.data) ? lr.data : []
  } catch (e) { logs.value = [] }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.resource-detail { padding: 4px 4px 24px; }
:deep(.el-descriptions) { max-width: 100%; }
.rd-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.rd-title { font-size: 18px; font-weight: 600; }
.rd-actions { margin-left: auto; }
.rd-lines, .rd-logs { margin-top: 12px; }
.log-head { display: flex; gap: 8px; align-items: center; }
.log-action { color: var(--el-text-color-regular); }
.log-changes { color: var(--el-text-color-secondary); font-size: 13px; margin-top: 4px; white-space: pre-wrap; }
.cell-text, .date-text, .bool-text { word-break: break-all; }
</style>
