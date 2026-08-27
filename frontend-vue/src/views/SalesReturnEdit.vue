<template>
  <div class="area-page sales-return-edit">
    <div class="page-header">
      <div class="page-title">
        <el-button text @click="$router.push('/m/sales-returns')"><el-icon><ArrowLeft /></el-icon></el-button>
        <h3>{{ isReadonly ? '查看销退单' : (isEdit ? '编辑销退单' : '新增销退单') }}</h3>
      </div>
      <div class="page-actions">
        <el-button @click="$router.push('/m/sales-returns')">{{ isReadonly ? '返回' : '取消' }}</el-button>
        <el-button v-if="legacyMode && canCreateRedOut" type="danger" :loading="creatingRedOut" @click="createRedOut">生成红字销售出库</el-button>
        <el-button v-if="!isReadonly" type="success" :loading="submitting" @click="onSave">提交销退单</el-button>
      </div>
    </div>
    <div class="area-scroll">
      <el-alert v-if="submitError" type="error" :closable="false" show-icon :title="submitError" style="margin-bottom:14px" />

      <el-card shadow="never" class="form-card" v-loading="loadingDetail">
        <template #header>
          <div class="card-header"><el-icon><Document /></el-icon><span>销退信息</span></div>
        </template>
        <el-form ref="formRef" :model="form" label-width="110px" :disabled="isReadonly" status-icon>
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="8" :lg="8">
              <el-form-item label="销退单号">
                <el-input v-model="form.code" placeholder="提交后自动生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="8">
              <el-form-item label="经销商" required>
                <el-select
                  v-model="form.dealerId"
                  filterable remote clearable reserve-keyword
                  :remote-method="onSearchDealer"
                  :loading="dealerLoading"
                  :disabled="isReadonly"
                  placeholder="请先选择经销商（必选）"
                  style="width:100%"
                  @change="onDealerChange"
                >
                  <el-option v-for="d in dealerOptions" :key="d.id" :label="dealerLabel(d)" :value="d.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="8">
              <el-form-item label="发货仓库" required>
                <el-select
                  v-model="form.warehouseId"
                  filterable clearable
                  :loading="warehouseLoading"
                  :disabled="isReadonly"
                  placeholder="请选择发货仓库（必选）"
                  style="width:100%"
                  @change="onWarehouseChange"
                >
                  <el-option v-for="w in warehouseOptions" :key="w.id" :label="warehouseLabel(w)" :value="w.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="8">
              <el-form-item label="退货类型" required>
                <el-radio-group v-model="form.returnType" :disabled="isReadonly" @change="onReturnTypeChange">
                  <el-radio-button label="PAID">有价产品退货</el-radio-button>
                  <el-radio-button label="ZERO">0金额产品退货</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="8">
              <el-form-item label="退货金额">
                <span class="amount-text">¥{{ returnTotal.toFixed(2) }}</span>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" :lg="8">
              <el-form-item label="状态">
                <el-tag :type="statusTag(form.status)">{{ rmaStatusFmt(form.status) }}</el-tag>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注">
                <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="isReadonly" placeholder="可选" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <el-card shadow="never" class="source-card compact-source-card">
        <template #header>
          <div class="card-header">
            <el-icon><Link /></el-icon>
            <span>退货来源（支持同一客户同一仓库多张出库单合并退货）</span>
            <div class="spacer" />
            <el-tag type="info" size="small">已选 {{ groups.length }} 张出库单</el-tag>
            <el-tooltip v-if="!isReadonly && (!form.dealerId || !form.warehouseId)" :content="!form.dealerId ? '请先在上方选择经销商' : '请先在上方选择发货仓库'" placement="top">
              <span>
                <el-button type="primary" plain size="small" disabled>
                  <el-icon><Search /></el-icon>选择发货单
                </el-button>
              </span>
            </el-tooltip>
            <el-button v-else-if="!isReadonly" type="primary" plain size="small" @click="openShipmentPicker">
              <el-icon><Search /></el-icon>{{ groups.length ? '继续选择发货单' : '选择发货单' }}
            </el-button>
          </div>
        </template>
        <div v-if="groups.length" class="source-list">
          <div v-for="g in groups" :key="g.salesOutId" class="source-item">
            <div class="source-info source-info-inline">
              <el-link type="primary"><b>{{ g.salesOutCode || ('出库单#'+g.salesOutId) }}</b></el-link>
              <span>销售订单：{{ g.orderCode || '-' }}</span>
              <span>发货仓库：{{ g.warehouseName || '-' }}</span>
              <span>发货日期：{{ g.salesDate || '-' }}</span>
              <el-tag size="small" type="success">{{ statusFmt(g.status) }}</el-tag>
              <el-button v-if="!isReadonly" type="danger" link size="small" @click="removeGroup(g.salesOutId)">移除</el-button>
            </div>
          </div>
        </div>
        <div v-else class="source-empty">
          <el-icon size="18" color="#909399"><Link /></el-icon>
          <span class="source-empty-text">请按顺序操作：先在上方「销退信息」中选择经销商、选择发货仓库，再点击右上角「选择发货单」；可多次添加同一客户同一仓库的多张出库单，不同客户或不同仓库的出库单不能合并为一张销退单</span>
        </div>
        <div class="source-reason">
          <el-form ref="reasonFormRef" :model="form" label-width="110px" :disabled="isReadonly" @submit.prevent>
            <el-row :gutter="20">
              <el-col :xs="24" :sm="16" :md="12" :lg="10">
                <el-form-item label="退货原因" required>
                  <el-select v-model="form.reasonCode" placeholder="选择发货单后选择退货原因（必选）" style="width:100%" :disabled="isReadonly" @change="onReasonCodeChange">
                    <el-option v-for="r in availableReasonOptions" :key="r.value" :label="r.label" :value="r.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </el-card>

      <el-card v-for="g in groups" :key="'g'+g.salesOutId" shadow="never" class="lines-card">
        <template #header>
          <div class="card-header">
            <el-icon><Goods /></el-icon>
            <span>退货明细 - {{ g.salesOutCode || ('出库单#'+g.salesOutId) }}</span>
            <div class="spacer" />
            <el-tag type="info" size="small">{{ groupLineCount(g) }} 行</el-tag>
            <el-tag type="warning" size="small">退货 {{ groupQty(g) }} 件</el-tag>
            <el-button v-if="!isReadonly" size="small" plain @click="fillGroup(g, true)">全选</el-button>
            <el-button v-if="!isReadonly" size="small" plain @click="fillGroup(g, false)">清空数量</el-button>
          </div>
        </template>
        <el-table :data="g.lines" border size="small" stripe>
          <el-table-column label="产品编码" prop="productCode" min-width="120" show-overflow-tooltip />
          <el-table-column label="产品名称" prop="productName" min-width="160" show-overflow-tooltip />
          <el-table-column label="规格" prop="productSpec" min-width="100" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="55" />
          <el-table-column label="批号" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.batchNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="序列号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.serialNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="已发数" prop="shippedQty" width="70" align="right" />
          <el-table-column label="已退数" prop="returnedQty" width="70" align="right" />
          <el-table-column label="可退数" width="70" align="right">
            <template #default="{ row }">{{ returnableQty(row) }}</template>
          </el-table-column>
          <el-table-column label="EA退价" width="95" align="right">
            <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="行小计" width="100" align="right">
            <template #default="{ row }"><b>¥{{ lineTotal(row).toFixed(2) }}</b></template>
          </el-table-column>
          <el-table-column label="本次退货数" width="120" align="right">
            <template #header><span>本次退货数</span><span class="required-mark">*</span></template>
            <template #default="{ row }">
              <el-input-number v-model="row.qty" :min="0" :max="Number(returnableQty(row))" :step="1" :precision="0" :controls="false" :disabled="isReadonly" size="small" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column v-if="!isReadonly" label="行原因" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.reason" size="small" placeholder="可填写行退货原因" maxlength="100" />
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-empty v-if="!groups.length && !loadingDetail" description="先选择经销商、发货仓库，再选择发货单，自动带入可退货明细" :image-size="80" />

      <div v-if="groups.length" class="return-summary">
        <span>退货行数：{{ totalLines }}</span>
        <span>退货总件数：{{ totalQty }} 件</span>
        <span>汇总退货金额：<b class="amount-text">¥{{ returnTotal.toFixed(2) }}</b></span>
      </div>

      <el-dialog v-model="pickerVisible" title="选择退货出库单（可多选）" width="900px" top="8vh" append-to-body destroy-on-close class="shipment-picker-dialog">
        <div class="shipment-picker">
          <div class="picker-dealer-bar">
            <span class="picker-dealer-label">退货客户：</span>
            <el-tag type="primary" size="small">{{ dealerDisplay || '-' }}</el-tag>
            <span class="picker-dealer-label" style="margin-left:12px">发货仓库：</span>
            <el-tag type="warning" size="small">{{ form.warehouseName || '-' }}</el-tag>
            <span class="picker-dealer-hint">仅显示该客户在所选仓库的已发货出库单</span>
          </div>
          <el-form :inline="true" :model="pickerQuery" class="picker-form" @submit.prevent>
            <el-form-item label="发货时间">
              <el-date-picker v-model="pickerDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width:220px" />
            </el-form-item>
            <el-form-item label="批号">
              <el-input v-model="pickerQuery.batchNo" placeholder="产品批号" clearable style="width:130px" @keyup.enter="searchShipments" />
            </el-form-item>
            <el-form-item label="序列号">
              <el-input v-model="pickerQuery.serialNo" placeholder="产品序列号" clearable style="width:140px" @keyup.enter="searchShipments" />
            </el-form-item>
            <el-form-item label="产品">
              <el-select v-model="pickerQuery.productId" filterable remote clearable reserve-keyword :remote-method="onSearchProduct" :loading="productLoading" placeholder="产品编码/名称" style="width:200px">
                <el-option v-for="p in productOptions" :key="p.id" :label="productLabel(p)" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="searchShipments">查询</el-button>
              <el-button :icon="RefreshLeft" @click="resetPickerQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="shipmentList" v-loading="shipmentLoading" border stripe size="small" highlight-current-row height="320" @selection-change="onSelectionChange" ref="pickerTableRef">
            <el-table-column type="selection" width="42" />
            <el-table-column label="发货单号" prop="code" width="150" show-overflow-tooltip />
            <el-table-column label="销售订单" prop="orderCode" width="130" show-overflow-tooltip />
            <el-table-column label="经销商" prop="dealerName" min-width="150" show-overflow-tooltip />
            <el-table-column label="发货仓库" prop="warehouseName" width="120" show-overflow-tooltip />
            <el-table-column label="发货日期" prop="salesDate" width="100" />
            <el-table-column label="状态" width="70">
              <template #default="{ row }"><el-tag size="small" type="success">{{ statusFmt(row.status) }}</el-tag></template>
            </el-table-column>
          </el-table>
          <div class="picker-tip">提示：勾选出库单后点击「确认添加」，可多次添加同一客户的多张出库单。已选 {{ pickedShipments.length }} 张。</div>
          <div class="picker-footer">
            <el-button @click="pickerVisible=false">取消</el-button>
            <el-button type="primary" :loading="adding" @click="confirmAddShipments">确认添加（{{ pickedShipments.length }}）</el-button>
          </div>
        </div>
      </el-dialog>
    </div>
  </div>
</template><script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Link, Document, Goods, Search, RefreshLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { shippedOuts, shippedOutLines, createRmaOrder, getRmaOrder } from '@/api/orderPricing'

const route = useRoute()
const router = useRouter()

// 统一销退列表使用前缀 id：r<id> 为 v4.3.0 rma_orders，l<id> 为历史 orders 红字销退；纯数字兼容旧链接（默认按 RMA 优先回退）
const parsedRef = computed(() => {
  const raw = String(route.params.id || '')
  if (raw.startsWith('r')) return { source: 'RMA', id: raw.slice(1) }
  if (raw.startsWith('l')) return { source: 'LEGACY', id: raw.slice(1) }
  return { source: 'AUTO', id: raw }
})

const formRef = ref(null)
const reasonFormRef = ref(null)
const submitting = ref(false)
const loadingDetail = ref(false)
const submitError = ref('')

const pickerVisible = ref(false)
const pickerTableRef = ref(null)
const pickerQuery = reactive({ batchNo: '', serialNo: '', productId: null })
const pickerDateRange = ref([])
const shipmentList = ref([])
const shipmentLoading = ref(false)
const pickedShipments = ref([])
const adding = ref(false)
const dealerOptions = ref([])
const dealerLoading = ref(false)
let dealerTimer = null
const warehouseOptions = ref([])
const warehouseLoading = ref(false)
const productOptions = ref([])
const productLoading = ref(false)
let productTimer = null

// groups: [{ salesOutId, salesOutCode, orderCode, dealerId, dealerName, warehouseId, warehouseName, salesDate, status, lines:[...] }]
const groups = ref([])
const legacyMode = ref(false)
const canCreateRedOut = ref(false)
const creatingRedOut = ref(false)

const reasonOptions = [
  { value: 'NORMAL', label: '常规退货' },
  { value: 'PRE_OP_CONTAMINATION', label: '术前污染' },
  { value: 'QUALITY_ISSUE', label: '质量问题' },
  { value: 'NEAR_EXPIRY', label: '近效期退货' },
  { value: 'DAMAGED', label: '运输破损' },
  { value: 'OTHER', label: '其他' }
]
// v4.3.2: 0金额产品退货不允许「常规退货」
const availableReasonOptions = computed(() =>
  form.returnType === 'ZERO' ? reasonOptions.filter(r => r.value !== 'NORMAL') : reasonOptions
)
function onReturnTypeChange() {
  // 切换退货类型：清空已选原因（若不再合法）、清空已带出明细，避免混入另一类型产品
  if (form.returnType === 'ZERO' && form.reasonCode === 'NORMAL') form.reasonCode = ''
  groups.value = []
}

const form = reactive({
  id: null,
  code: '',
  status: 'DRAFT',
  dealerId: null,
  dealerName: '',
  warehouseId: null,
  warehouseName: '',
  returnType: 'PAID',
  reasonCode: '',
  remark: ''
})

const isEdit = computed(() => !!route.params.id)
const isReadonly = computed(() => isEdit.value || ['SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'REJECTED'].includes(form.status))
const dealerDisplay = computed(() => form.dealerName || (groups.value[0]?.dealerName || ''))

function dealerLabel(d) {
  return d ? [d.code, d.name].filter(Boolean).join(' ') : ''
}
function productLabel(p) {
  return p ? [p.code, p.name || p.nameCn].filter(Boolean).join(' ') : ''
}

const allLines = computed(() => groups.value.flatMap(g => g.lines))
const totalLines = computed(() => allLines.value.filter(l => Number(l.qty) > 0).length)
const totalQty = computed(() => allLines.value.reduce((s, l) => s + Number(l.qty || 0), 0))

function todayStr() {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

function statusFmt(s) {
  return ({
    DRAFT: '草稿', SUBMITTED: '已提交', PENDING_APPROVAL: '审批中', APPROVED: '已审批', REJECTED: '已驳回',
    RECEIVING: '收货中', COMPLETED: '已完成', CANCELLED: '已取消',
    SHIPPED: '已发货', PARTIAL_SHIPPED: '部分发货', CONFIRMED: '已确认',
    COMPLETED_OUT: '已完成', PARTIAL_OUTBOUND: '部分出库'
  })[s] || s || '-'
}
function rmaStatusFmt(s) {
  return ({ DRAFT: '草稿', SUBMITTED: '待审批', PENDING_APPROVAL: '审批中', APPROVED: '已审批', COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已驳回' })[s] || statusFmt(s)
}
function statusTag(s) {
  return ({ DRAFT: 'info', SUBMITTED: 'warning', PENDING_APPROVAL: 'warning', APPROVED: 'primary', REJECTED: 'danger', RECEIVING: 'warning', COMPLETED: 'success', CANCELLED: 'danger' })[s] || ''
}

function onReasonCodeChange() {}

function returnableQty(row) {
  const shipped = Number(row.shippedQty || row.qty || 0)
  const returned = Number(row.returnedQty || 0)
  const locked = Number(row.otherLockedQty != null ? row.otherLockedQty : (row.lockedQty || 0))
  const r = shipped - returned - locked
  return r > 0 ? r : 0
}
function lineTotal(row) {
  const price = Number(row.unitPrice || 0)
  const qty = Math.min(Number(row.qty || 0), Number(returnableQty(row)))
  return Math.round(price * qty * 100) / 100
}
const returnTotal = computed(() => allLines.value.reduce((sum, row) => sum + lineTotal(row), 0))
function groupLineCount(g) { return g.lines.filter(l => Number(l.qty) > 0).length }
function groupQty(g) { return g.lines.reduce((s, l) => s + Number(l.qty || 0), 0) }
function fillGroup(g, all) { g.lines.forEach(l => { l.qty = all ? Number(returnableQty(l)) : 0 }) }
function removeGroup(salesOutId) {
  groups.value = groups.value.filter(g => g.salesOutId !== salesOutId)
}

async function loadDealerOptions(keyword) {
  dealerLoading.value = true
  try {
    const res = await request({ url: '/api/lookups/dealers', method: 'get', params: { keyword: keyword || undefined, limit: 50 } })
    dealerOptions.value = res?.data || []
  } catch { dealerOptions.value = [] }
  finally { dealerLoading.value = false }
}

function onSearchDealer(q) {
  if (dealerTimer) clearTimeout(dealerTimer)
  dealerTimer = setTimeout(() => loadDealerOptions(q || ''), 250)
}

function ensureDealerOption(id, name) {
  if (!id) return
  if (!dealerOptions.value.some(d => String(d.id) === String(id))) {
    dealerOptions.value.unshift({ id, code: '', name: name || ('经销商#' + id) })
  }
}

async function onDealerChange(id) {
  const d = dealerOptions.value.find(x => String(x.id) === String(id))
  if (id && !d) {
    try {
      const res = await request({ url: '/api/lookups/dealers', method: 'get', params: { keyword: String(id), limit: 50 } })
      const list = res?.data || []
      const hit = list.find(x => String(x.id) === String(id))
      if (hit && !dealerOptions.value.some(x => String(x.id) === String(id))) dealerOptions.value.unshift(hit)
    } catch { /* 忽略，保留 id */ }
  }
  const picked = dealerOptions.value.find(x => String(x.id) === String(id))
  form.dealerName = picked ? picked.name : ''
  if (id && groups.value.length) {
    try {
      await ElMessageBox.confirm(
        '切换经销商将清空已选择的全部出库单和退货明细，是否继续？',
        '切换经销商',
        { confirmButtonText: '继续切换', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      form.dealerId = groups.value[0]?.dealerId || null
      form.dealerName = groups.value[0]?.dealerName || ''
      return
    }
    groups.value = []
  }
}

function warehouseLabel(w) {
  return w ? [w.code, w.name].filter(Boolean).join(' ') : ''
}

async function loadWarehouseOptions(keyword) {
  warehouseLoading.value = true
  try {
    const res = await request({ url: '/api/lookups/warehouses', method: 'get', params: { keyword: keyword || undefined, limit: 50 } })
    warehouseOptions.value = res?.data || []
  } catch { warehouseOptions.value = [] }
  finally { warehouseLoading.value = false }
}

function ensureWarehouseOption(id, name) {
  if (!id) return
  if (!warehouseOptions.value.some(w => String(w.id) === String(id))) {
    warehouseOptions.value.unshift({ id, code: '', name: name || ('仓库#' + id) })
  }
}

async function onWarehouseChange(id) {
  const picked = warehouseOptions.value.find(x => String(x.id) === String(id))
  form.warehouseName = picked ? picked.name : ''
  if (id && groups.value.length) {
    const diffWarehouse = groups.value.some(g => g.warehouseId && String(g.warehouseId) !== String(id))
    if (diffWarehouse) {
      try {
        await ElMessageBox.confirm(
          '切换发货仓库将清空已选择的全部出库单和退货明细，是否继续？',
          '切换发货仓库',
          { confirmButtonText: '继续切换', cancelButtonText: '取消', type: 'warning' }
        )
      } catch {
        form.warehouseId = groups.value[0]?.warehouseId || null
        form.warehouseName = groups.value[0]?.warehouseName || ''
        return
      }
      groups.value = []
    }
  }
}

async function onSearchProduct(q) {
  if (productTimer) clearTimeout(productTimer)
  productTimer = setTimeout(async () => {
    productLoading.value = true
    try {
      const res = await request({ url: '/api/lookups/products', method: 'get', params: { keyword: q || undefined, limit: 30 } })
      productOptions.value = res?.data?.list || res?.data || []
    } catch { productOptions.value = [] }
    finally { productLoading.value = false }
  }, 250)
}

function openShipmentPicker() {
  if (!form.dealerId) {
    ElMessage.warning('请先选择经销商，一个销退订单只能针对一个客户')
    return
  }
  if (!form.warehouseId) {
    ElMessage.warning('请先选择发货仓库，一个销退订单只能针对同一发货仓库的出库单')
    return
  }
  pickerVisible.value = true
  pickedShipments.value = []
  pickerQuery.batchNo = ''
  pickerQuery.serialNo = ''
  pickerQuery.productId = null
  pickerDateRange.value = []
  productOptions.value = []
  searchShipments()
}

function resetPickerQuery() {
  pickerQuery.batchNo = ''
  pickerQuery.serialNo = ''
  pickerQuery.productId = null
  pickerDateRange.value = []
  productOptions.value = []
  searchShipments()
}

async function searchShipments() {
  if (!form.dealerId) {
    ElMessage.warning('请先选择经销商，一个销退订单只能针对一个客户')
    return
  }
  if (!form.warehouseId) {
    ElMessage.warning('请先选择发货仓库')
    return
  }
  shipmentLoading.value = true
  try {
    const params = {
      dealerId: form.dealerId,
      warehouseId: form.warehouseId,
      amountType: form.returnType || 'PAID',
      batchNo: pickerQuery.batchNo || undefined,
      serialNo: pickerQuery.serialNo || undefined,
      productId: pickerQuery.productId || undefined
    }
    if (pickerDateRange.value && pickerDateRange.value.length === 2) {
      params.startDate = pickerDateRange.value[0]
      params.endDate = pickerDateRange.value[1]
    }
    const res = await shippedOuts(params)
    shipmentList.value = (res?.data || []).filter(s => !(groups.value.some(g => g.salesOutId === s.id)))
  } catch (e) {
    ElMessage.error('查询发货单失败：' + (e?.response?.data?.message || e?.message || ''))
    shipmentList.value = []
  } finally {
    shipmentLoading.value = false
  }
}

function onSelectionChange(rows) { pickedShipments.value = rows }

async function confirmAddShipments() {
  if (!pickedShipments.value.length) { ElMessage.warning('请先勾选要加入的出库单'); return }
  const mismatch = pickedShipments.value.find(s => String(s.dealerId) !== String(form.dealerId))
  if (mismatch) {
    ElMessage.error(`出库单 ${mismatch.code || mismatch.id} 属于其他客户，不能加入本销退单`)
    return
  }
  const whMismatch = pickedShipments.value.find(s => s.warehouseId && String(s.warehouseId) !== String(form.warehouseId))
  if (whMismatch) {
    ElMessage.error(`出库单 ${whMismatch.code || whMismatch.id} 不属于所选发货仓库，不能加入本销退单`)
    return
  }
  adding.value = true
  try {
    for (const s of pickedShipments.value) {
      if (groups.value.some(g => g.salesOutId === s.id)) continue
      const res = await shippedOutLines(s.id, { amountType: form.returnType || 'PAID' })
      const d = res?.data
      if (!d || !Array.isArray(d.lines) || !d.lines.length) { ElMessage.warning(`出库单 ${s.code || s.id} 无可退明细，已跳过`); continue }
      const whId = d.warehouseId || s.warehouseId
      const whName = d.warehouseName || s.warehouseName || ''
      if (!form.warehouseId && whId) {
        form.warehouseId = whId
        form.warehouseName = whName
        ensureWarehouseOption(whId, whName)
      }
      groups.value.push({
        salesOutId: d.id || s.id,
        salesOutCode: d.code || s.code,
        orderCode: s.orderCode || '',
        dealerId: form.dealerId,
        dealerName: form.dealerName || s.dealerName || '',
        warehouseId: whId,
        warehouseName: whName,
        salesDate: s.salesDate || '',
        status: s.status || '',
        lines: d.lines.map(l => ({
          salesOutId: d.id || s.id,
          salesOutLineId: l.sourceOutLineId || l.id,
          productId: l.productId,
          productCode: l.productCode,
          productName: l.productName,
          productSpec: l.productSpec,
          unit: l.unit,
          batchNo: l.batchNo || '',
          serialNo: l.serialNo || '',
          shippedQty: Number(l.shippedQty || 0),
          returnedQty: Number(l.returnedQty || 0),
          lockedQty: Number(l.lockedQty || 0),
          otherLockedQty: Number(l.otherLockedQty || 0),
          unitPrice: Number(l.unitPrice || 0),
          taxRate: l.taxRate != null ? Number(l.taxRate) : 0.13,
          qty: 0,
          reason: ''
        }))
      })
    }
    ElMessage.success(`已添加 ${groups.value.length} 张出库单的可退明细`)
    pickerVisible.value = false
  } catch (e) {
    ElMessage.error('加载出库单明细失败：' + (e?.response?.data?.message || e?.message || ''))
  } finally {
    adding.value = false
  }
}
async function onSave() {
  if (submitting.value) return
  if (!form.dealerId) { ElMessage.error('请先选择经销商'); return }
  if (!form.warehouseId) { ElMessage.error('请先选择发货仓库'); return }
  if (!groups.value.length) { ElMessage.error('请先选择至少一张出库单'); return }
  if (!form.reasonCode) { ElMessage.error('请选择退货原因'); return }
  if (form.returnType === 'ZERO' && form.reasonCode === 'NORMAL') { ElMessage.error('0金额产品退货不能选择「常规退货」原因'); return }
  const chosen = allLines.value.filter(l => Number(l.qty) > 0)
  if (!chosen.length) { ElMessage.error('请至少填写一行本次退货数量'); return }
  const bad = chosen.find(l => !Number.isInteger(Number(l.qty)) || Number(l.qty) <= 0)
  if (bad) { ElMessage.error(`产品「${bad.productCode || ''}」退货数量必须是大于 0 的整数`); return }
  const over = chosen.find(l => Number(l.qty) > Number(returnableQty(l)))
  if (over) { ElMessage.error(`产品「${over.productCode || ''}」退货数量不能大于可退数量 ${returnableQty(over)}（不同出库单之间不可挪用）`); return }
  const seen = new Set()
  for (const l of chosen) {
    const key = l.salesOutLineId
    if (seen.has(key)) { ElMessage.error(`出库行 ${key} 出现重复，请合并为一行后提交`); return }
    seen.add(key)
  }
  submitError.value = ''
  submitting.value = true
  try {
    const reasonText = reasonOptions.find(r => r.value === form.reasonCode)?.label || form.reasonCode
    const payload = {
      dealerId: form.dealerId,
      rmaType: form.returnType === 'ZERO' ? 'ZERO_RETURN' : 'RETURN',
      reason: reasonText,
      reasonCode: form.reasonCode,
      remark: form.remark || '',
      salesOutIds: groups.value.map(g => g.salesOutId),
      outboundLines: chosen.map(l => ({
        salesOutId: l.salesOutId,
        salesOutLineId: l.salesOutLineId,
        qty: Number(l.qty),
        reason: l.reason || reasonText
      }))
    }
    const res = await createRmaOrder(payload)
    ElMessage.success('销退单已创建并提交，可退数量已锁定')
    router.push('/m/sales-returns')
    return res
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '提交失败'
    submitError.value = msg
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

async function loadForView() {
  if (!route.params.id) return
  loadingDetail.value = true
  try {
    let d = null
    const ref = parsedRef.value
    if (ref.source === 'LEGACY') {
      const legacy = await request({ url: `/api/sales-returns/${ref.id}`, method: 'get' })
      const ld = legacy?.data
      if (ld && (ld.lines || ld.sourceSalesOutId || ld.refSalesOutId)) { loadLegacy(ld); return }
      throw new Error('未找到销退单')
    }
    if (ref.source === 'RMA') {
      d = (await getRmaOrder(ref.id))?.data
    } else {
      // AUTO（旧的纯数字链接）：先按 v4.3.0 RMA，查不到再回退历史销退
      try {
        d = (await getRmaOrder(ref.id))?.data
      } catch (e) { d = null }
      if (!d) {
        try {
          const legacy = await request({ url: `/api/sales-returns/${ref.id}`, method: 'get' })
          const ld = legacy?.data
          if (ld && (ld.lines || ld.sourceSalesOutId || ld.refSalesOutId)) { loadLegacy(ld); return }
        } catch (e2) { /* fall through */ }
      }
    }
    if (!d) throw new Error('未找到销退单')
    form.id = d.id
    form.code = d.code || ''
    form.status = d.status || 'SUBMITTED'
    form.dealerId = d.dealerId || null
    form.dealerName = d.dealerName || ''
    form.remark = d.remark || ''
    if (form.dealerId) {
      try {
        const res = await request({ url: '/api/lookups/dealers', method: 'get', params: { limit: 200 } })
        const list = res?.data || []
        const hit = list.find(x => String(x.id) === String(form.dealerId))
        if (hit) {
          form.dealerName = hit.name || form.dealerName
          if (!dealerOptions.value.some(x => String(x.id) === String(hit.id))) dealerOptions.value.unshift(hit)
        } else {
          ensureDealerOption(form.dealerId, form.dealerName)
        }
      } catch { ensureDealerOption(form.dealerId, form.dealerName) }
    }
    const outGroups = Array.isArray(d.outboundGroups) ? d.outboundGroups : []
    form.warehouseId = d.warehouseId || outGroups[0]?.warehouseId || null
    form.warehouseName = d.warehouseName || outGroups[0]?.warehouseName || ''
    if (form.warehouseId) {
      try {
        const wres = await request({ url: '/api/lookups/warehouses', method: 'get', params: { limit: 200 } })
        const wlist = wres?.data || []
        const whit = wlist.find(x => String(x.id) === String(form.warehouseId))
        if (whit) {
          form.warehouseName = whit.name || form.warehouseName
          if (!warehouseOptions.value.some(x => String(x.id) === String(whit.id))) warehouseOptions.value.unshift(whit)
        } else {
          ensureWarehouseOption(form.warehouseId, form.warehouseName)
        }
      } catch { ensureWarehouseOption(form.warehouseId, form.warehouseName) }
    }
    groups.value = outGroups.map(g => ({
      salesOutId: g.salesOutId,
      salesOutCode: g.salesOutCode || ('出库单#' + g.salesOutId),
      orderCode: g.orderCode || '',
      dealerId: g.dealerId || d.dealerId,
      dealerName: g.dealerName || d.dealerName || '',
      warehouseId: g.warehouseId || form.warehouseId,
      warehouseName: g.warehouseName || form.warehouseName || '',
      salesDate: g.salesDate || '',
      status: g.status || '',
      lines: (g.lines || []).map(l => ({
        salesOutId: g.salesOutId,
        salesOutLineId: l.salesOutLineId,
        productId: l.productId,
        productCode: l.productCode,
        productName: l.productName,
        productSpec: l.productSpec,
        unit: l.unit || 'EA',
        batchNo: l.batchNo || '',
        serialNo: l.serialNo || '',
        shippedQty: Number(l.shippedQty || l.qty || 0),
        returnedQty: Number(l.returnedQty || 0),
        lockedQty: 0,
        unitPrice: Number(l.unitPriceInclTax || l.unitPrice || 0),
        taxRate: l.taxRate != null ? Number(l.taxRate) : 0.13,
        qty: Number(l.qty || 0),
        reason: l.reason || ''
      }))
    }))
    const rc = d.reasonCode || (d.lines && d.lines.reasonCode) || ''
    form.reasonCode = rc || reasonOptions.find(r => r.label === d.reason)?.value || ''
  } catch (e) {
    submitError.value = e?.response?.data?.message || e.message || '加载销退单失败'
  } finally {
    loadingDetail.value = false
  }
}

function loadLegacy(d) {
  legacyMode.value = true
  form.id = d.id
  form.code = d.code || ''
  form.status = d.status || 'DRAFT'
  form.dealerId = d.dealerId || null
  form.dealerName = d.dealerName || ''
  form.warehouseId = d.warehouseId || null
  form.warehouseName = d.warehouseName || ''
  form.remark = d.remark || ''
  form.reasonCode = d.reasonCode || ''
  ensureDealerOption(form.dealerId, form.dealerName)
  ensureWarehouseOption(form.warehouseId, form.warehouseName)
  canCreateRedOut.value = ['APPROVED', 'RECEIVING'].includes(d.status)
  const outId = d.sourceSalesOutId || d.refSalesOutId
  const outCode = d.sourceSalesOutCode || d.refSalesOutCode || ''
  groups.value = [{
    salesOutId: outId,
    salesOutCode: outCode,
    orderCode: d.refOrderCode || d.sourceOrderCode || '',
    dealerId: d.dealerId,
    dealerName: d.dealerName || '',
    warehouseId: d.warehouseId || null,
    warehouseName: d.warehouseName || '',
    salesDate: d.sourceSalesOutDate || d.refSalesOutDate || '',
    status: d.sourceSalesOutStatus || '',
    lines: (d.lines || []).filter(l => !l.isGift || true).map(l => ({
      salesOutId: outId,
      salesOutLineId: l.sourceOutLineId || l.id,
      productId: l.productId,
      productCode: l.productCode,
      productName: l.productName,
      productSpec: l.productSpec,
      unit: l.unit || 'EA',
      batchNo: l.batchNo || '',
      serialNo: l.serialNo || '',
      shippedQty: Number(l.shippedQty || l.qty || 0),
      returnedQty: Number(l.returnedQty || 0),
      lockedQty: Number(l.lockedQty || 0),
      otherLockedQty: Number(l.otherLockedQty || 0),
      unitPrice: Number(l.unitPrice || 0),
      taxRate: l.taxRate != null ? Number(l.taxRate) : 0.13,
      qty: Number(l.qty || 0),
      reason: ''
    }))
  }]
}

async function createRedOut() {
  if (!form.id) return
  creatingRedOut.value = true
  try {
    const res = await request({ url: `/api/sales-returns/${form.id}/create-red-out`, method: 'post' })
    const outId = res?.data?.id
    if (outId) {
      ElMessage.success(res?.data?.existed ? '红字销售出库已存在' : '已生成红字销售出库草稿')
      router.push(`/sales-out-edit/${outId}`)
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '生成红字出库失败')
  } finally { creatingRedOut.value = false }
}
function resetForm() {
  form.id = null
  form.code = ''
  form.status = 'DRAFT'
  form.dealerId = null
  form.dealerName = ''
  form.warehouseId = null
  form.warehouseName = ''
  form.reasonCode = ''
  form.remark = ''
  groups.value = []
  submitError.value = ''
  pickerQuery.batchNo = ''
  pickerQuery.serialNo = ''
  pickerQuery.productId = null
  pickerDateRange.value = []
  shipmentList.value = []
  pickedShipments.value = []
}

onMounted(() => {
  loadDealerOptions('')
  loadWarehouseOptions('')
  if (isEdit.value) loadForView(); else resetForm()
})
onBeforeUnmount(() => {
  if (dealerTimer) { clearTimeout(dealerTimer); dealerTimer = null }
  if (productTimer) { clearTimeout(productTimer); productTimer = null }
})
watch(() => route.params.id, (newId) => { if (newId) loadForView(); else resetForm() })
</script>

<style scoped>
.sales-return-edit { padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.page-header { display: flex; align-items: center; justify-content: space-between; padding: 4px 2px; }
.page-title { display: flex; align-items: center; gap: 8px; }
.page-title h3 { margin: 0; font-size: 18px; font-weight: 600; }
.page-actions { display: flex; gap: 8px; }
.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.card-header .spacer { flex: 1; }
.source-card :deep(.el-card__header) { background: linear-gradient(90deg,#ecf5ff 0%,#fff 100%); }
.form-card :deep(.el-card__header), .lines-card :deep(.el-card__header) { background: #fafafa; }
.source-list { display: flex; flex-direction: column; gap: 6px; }
.source-item { padding: 4px 0; border-bottom: 1px dashed #ebeef5; }
.source-item:last-child { border-bottom: none; }
.return-summary { display:flex;justify-content:flex-end;gap:24px;padding:12px 4px 0;color:#606266;font-size:13px }
.return-summary .amount-text { font-size:16px }
.source-card :deep(.el-card__body) { padding: 8px 14px; }
.compact-source-card :deep(.el-card__header){ padding: 8px 14px; }
.source-info-inline { display:flex;flex-wrap:wrap;gap:10px 22px;align-items:center;font-size:13px }
.source-info-inline b { color:#606266;font-weight:600 }
.source-empty { display: flex; align-items: center; gap: 8px; padding: 4px 0; color: #909399; }
.source-empty-text { font-size: 13px; }
.source-reason { margin-top: 10px; padding-top: 12px; border-top: 1px dashed #dcdfe6; }
.source-reason :deep(.el-form-item) { margin-bottom: 0; }
.shipment-picker .picker-form { margin-bottom: 8px; }
.shipment-picker-dialog .el-dialog__body { padding-top: 12px; padding-bottom: 12px; }
.shipment-picker-dialog .picker-form .el-form-item { margin-bottom: 8px; margin-right: 8px; }
.picker-dealer-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; padding: 8px 12px; background: #f4f8ff; border: 1px solid #d9e8ff; border-radius: 4px; font-size: 13px; }
.picker-dealer-label { color: #606266; font-weight: 600; }
.picker-dealer-hint { color: #909399; font-size: 12px; }
.shipment-picker .picker-tip { margin-top: 8px; font-size: 12px; color: #909399; }
.picker-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.required-mark { color: var(--el-color-danger); margin-left: 2px; }
.el-form-item { margin-bottom: 14px; }
.amount-text { font-weight: 600; color: var(--el-color-danger); }
.lines-card { margin-bottom: 4px; }
@media (max-width: 768px) { .page-actions { flex-wrap: wrap; } }
</style>
