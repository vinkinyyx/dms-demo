<template>
  <div class="resource-detail">
    <div class="rd-header">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <span class="rd-title">{{ title }}</span>
      <div class="rd-actions">
        <slot name="actions" :data="detail">
          <el-button v-if="canEdit" @click="onEdit">编辑</el-button>
        </slot>
        <el-button
          v-if="canDeactivate"
          v-has="'product-price:deactivate'"
          type="warning"
          plain
          :loading="deactivating"
          @click="onDeactivate"
        >失效</el-button>
        <el-button
          v-if="canActivate"
          v-has="'product-price:activate'"
          type="success"
          plain
          :loading="activating"
          @click="onActivate"
        >启用</el-button>
      </div>
    </div>

    <el-card shadow="never" v-loading="loading">
      <div v-if="detail">
        <template v-for="(group, gi) in groupedFields" :key="group.name || '__main__'">
          <el-divider v-if="gi > 0" content-position="left">{{ group.name || '其他' }}</el-divider>
          <el-descriptions :column="group.name === '价格信息' ? 2 : 3" border size="small">
            <el-descriptions-item v-for="f in group.items" :key="f.key" :label="f.label || f.key">
              <el-tag v-if="f.key === statusKey" size="small" :type="statusTagType(detail[f.key])">{{ statusText(detail[f.key]) }}</el-tag>
              <span v-else-if="isDate(f)" class="date-text">{{ fmt(detail[f.key]) }}</span>
              <span v-else-if="f.type === 'boolean'" class="bool-text">{{ detail[f.key] ? '是' : '否' }}</span>
              <span v-else class="cell-text">{{ fieldDisplay(f, detail) }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
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

// R2.1 按字段 group 分组渲染（基本信息 / 价格信息 / 有效期 等）
// 过滤掉 'component-prices' 类型的字段（详情页底部 'BOM子件价格' 表已单独渲染，避免重复）
const groupedFields = computed(() => {
  const all = (fields.value || []).filter((f) => f.type !== 'component-prices')
  // 如果没有任何字段标记 group，则整体作为单一组，避免插入空 divider
  const hasGroup = all.some((f) => f.group)
  if (!hasGroup) return [{ name: '', items: all }]
  // 按 group 名收集；前几个无 group 的归入
  const order = []
  const map = new Map()
  const pre = []
  for (const f of all) {
    const g = f.group
    if (!g) { pre.push(f); continue }
    if (!map.has(g)) { map.set(g, []); order.push(g) }
    map.get(g).push(f)
  }
  const out = []
  if (pre.length) out.push({ name: '基本信息', items: pre })
  for (const g of order) out.push({ name: g, items: map.get(g) })
  return out
})

// R2.2 失效/启用按钮状态
const canDeactivate = computed(() => props.moduleKey === 'product-prices' && detail.value && String(detail.value.status || '').toLowerCase() === 'active')
const canActivate = computed(() => props.moduleKey === 'product-prices' && detail.value && String(detail.value.status || '').toLowerCase() !== 'active')
const deactivating = ref(false)
const activating = ref(false)

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

// R2.2 失效按钮：调后端 deactivate，原地更新 detail.value.status，**不刷新页面**
async function onDeactivate() {
  if (!detail.value || !detail.value.id) return
  try {
  await ElMessageBox.confirm('确认将该产品价格置为失效？失效后不影响历史单据。', '提示', { type: 'warning' })
  } catch (_) { return }
  deactivating.value = true
  try {
  await request({ url: cfg.value.api + '/' + detail.value.id + '/deactivate', method: 'post' })
  detail.value = { ...detail.value, status: 'inactive' }
  ElMessage.success('已失效')
  } catch (e) {
  ElMessage.error((e && e.message) || '失效失败')
  } finally {
  deactivating.value = false
  }
}

// R2.2 启用按钮（对称实现）
async function onActivate() {
  if (!detail.value || !detail.value.id) return
  activating.value = true
  try {
  await request({ url: cfg.value.api + '/' + detail.value.id + '/activate', method: 'post' })
  detail.value = { ...detail.value, status: 'active' }
  ElMessage.success('已启用')
  } catch (e) {
  ElMessage.error((e && e.message) || '启用失败')
  } finally {
  activating.value = false
  }
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
