<template>
  <div class="area-page sales-return-edit">
    <div class="page-header">
      <div class="page-title">
        <el-button text @click="$router.push('/m/sales-returns')"><el-icon><ArrowLeft /></el-icon></el-button>
        <h3>{{ isReadonly ? '查看销退单' : (isEdit ? '编辑销退单' : '新增销退单') }}</h3>
      </div>
      <div class="page-actions">
        <el-button @click="$router.push('/m/sales-returns')">{{ isReadonly ? '返回' : '取消' }}</el-button>
        <el-button v-if="canCreateRedOut" type="danger" :loading="creatingRedOut" @click="createRedOut">生成红字销售出库</el-button>
        <el-button v-if="!isReadonly" type="primary" :loading="saving" @click="onSave('DRAFT')">暂存</el-button>
        <el-button v-if="!isReadonly" type="success" :loading="submitting" @click="onSave('PENDING_APPROVAL')">提交审批</el-button>
      </div>
    </div>
    <div class="area-scroll">


    <el-alert v-if="submitError" type="error" :closable="false" show-icon :title="submitError" style="margin-bottom:14px" />

    <el-card shadow="never" class="source-card compact-source-card">
      <template #header>
        <div class="card-header">
          <el-icon><Link /></el-icon>
          <span>原发货单</span>
          <div class="spacer" />
          <el-button v-if="!isReadonly" type="primary" plain size="small" @click="openShipmentPicker">
            <el-icon><Search /></el-icon>{{ form.sourceSalesOutId ? '重新选择' : '选择发货单' }}
          </el-button>
        </div>
      </template>
      <div v-if="sourceSalesOut" class="source-info source-info-inline">
        <span><b>原发货单：</b><el-link type="primary">{{ sourceSalesOut.code }}</el-link></span>
        <span><b>销售订单：</b>{{ sourceSalesOut.orderCode || '-' }}</span>
        <span><b>经销商：</b>{{ sourceSalesOut.dealerName || '-' }}</span>
        <span><b>发货仓库：</b>{{ sourceSalesOut.warehouseName || '-' }}</span>
        <span><b>发货日期：</b>{{ sourceSalesOut.salesDate || '-' }}</span>
        <span><b>状态：</b><el-tag size="small" type="success">{{ statusFmt(sourceSalesOut.status) }}</el-tag></span>
      </div>
      <div v-else class="source-empty">
        <el-icon size="18" color="#909399"><Link /></el-icon>
        <span class="source-empty-text">请点击右上角「选择发货单」，通过时间 / 经销商 / 批号搜索并关联</span>
      </div>
    </el-card>

    <el-card shadow="never" class="form-card" v-loading="loadingDetail">
      <template #header>
        <div class="card-header"><el-icon><Document /></el-icon><span>销退信息</span></div>
      </template>
      <el-form ref="formRef" :model="form" label-width="110px" :disabled="isReadonly" status-icon>
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8" :lg="8">
            <el-form-item label="销退单号">
              <el-input v-model="form.code" placeholder="保存后自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="16" :lg="16">
            <el-form-item label="经销商" required>
              <el-input v-model="form.dealerName" placeholder="选择发货单后自动带出" disabled />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="8">
            <el-form-item label="原发货仓库" required>
              <el-input v-model="form.warehouseName" placeholder="选择发货单后自动带出" disabled />
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6" :md="8" :lg="8">
            <el-form-item label="仓库收货日期" required>
              <el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="isReadonly" />
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6" :md="8" :lg="8">
            <el-form-item label="退货原因" required>
              <el-select v-model="form.reasonCode" placeholder="请选择" style="width:100%" :disabled="isReadonly" @change="onReasonCodeChange">
                <el-option v-for="r in reasonOptions" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6" :md="8" :lg="8">
            <el-form-item label="退货金额">
              <span class="amount-text">¥{{ Number(form.finalAmount || form.amountInclTax || 0).toFixed(2) }}</span>
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6" :md="8" :lg="8">
            <el-form-item label="状态">
              <el-tag :type="statusTag(form.status)">{{ statusFmt(form.status) }}</el-tag>
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

    <el-card shadow="never" class="lines-card">
      <template #header>
        <div class="card-header">
          <el-icon><Goods /></el-icon>
          <span>销退明细</span>
          <div class="spacer" />
          <el-tag type="info" size="small">共 {{ form.lines.length }} 行</el-tag>
        </div>
      </template>
      <el-table v-if="form.lines.length" :data="form.lines" border size="small" stripe>
        <el-table-column label="发货行" width="70" align="center">
          <template #default="{ row }">{{ row.lineNo != null ? row.lineNo : '-' }}</template>
        </el-table-column>
        <el-table-column label="订单行" width="70" align="center">
          <template #default="{ row }">{{ row.orderLineNo != null ? row.orderLineNo : '-' }}</template>
        </el-table-column>
        <el-table-column label="产品编码" prop="productCode" min-width="130" show-overflow-tooltip />
        <el-table-column label="产品名称" prop="productName" min-width="170" show-overflow-tooltip />
        <el-table-column label="规格" prop="productSpec" min-width="110" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="60" />
        <el-table-column label="批号" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input v-if="!isReadonly" v-model="row.batchNo" size="small" :disabled="isReadonly" placeholder="批号" />
            <span v-else>{{ row.batchNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="序列号" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input v-if="!isReadonly" v-model="row.serialNo" size="small" :disabled="isReadonly" placeholder="序列号(如有)" />
            <span v-else>{{ row.serialNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发货数" prop="shippedQty" width="78" align="right" />
        <el-table-column label="已退数" prop="returnedQty" width="78" align="right" />
        <el-table-column label="可退数" width="78" align="right">
          <template #default="{ row }">{{ returnableQty(row) }}</template>
        </el-table-column>
        <el-table-column label="平摊单价" width="105" align="right">
          <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="行总价" width="110" align="right">
          <template #default="{ row }"><b>¥{{ lineTotal(row).toFixed(2) }}</b></template>
        </el-table-column>
        <el-table-column label="本次退货数" width="115" align="right">
          <template #header>
            <span>本次退货数</span><span class="required-mark">*</span>
          </template>
          <template #default="{ row }">
            <el-input-number
              v-model="row.qty"
              :min="0"
              :max="isReadonly ? Math.max(Number(row.qty || 0), Number(returnableQty(row))) : Number(returnableQty(row))"
              :step="1"
              :precision="0"
              :controls="false"
              :disabled="isReadonly"
              size="small"
              style="width:100%"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="!isReadonly" label="操作" width="70" fixed="right">
          <template #default="{ $index }">
            <el-button size="small" type="danger" link @click="removeLine($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="选择原发货单后自动带入可退货明细" :image-size="80" />
      <div v-if="form.lines.length" class="return-summary">
        <span>退货行数：{{ form.lines.length }}</span>
        <span>汇总退货金额：<b class="amount-text">¥{{ returnTotal.toFixed(2) }}</b></span>
      </div>
    </el-card>

    <el-card v-if="isReadonly && logs.length" shadow="never" class="logs-card">
      <template #header>操作日志</template>
      <el-timeline>
        <el-timeline-item v-for="h in logs" :key="h.id || h.atTime" :timestamp="h.atTime">
          <el-tag size="small">{{ h.username || '系统' }}</el-tag>
          <b style="margin-left:8px">{{ h.action }}</b>
          <div class="muted">{{ h.changes || '' }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-dialog v-model="pickerVisible" title="选择原发货单" width="640px" top="10vh" append-to-body destroy-on-close class="shipment-picker-dialog">
      <div class="shipment-picker">
        <el-form :inline="true" :model="pickerQuery" class="picker-form" @submit.prevent>
          <el-form-item label="发货时间">
            <el-date-picker
              v-model="pickerDateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="经销商">
            <el-select
              v-model="pickerQuery.dealerId"
              filterable
              remote
              clearable
              reserve-keyword
              :remote-method="onSearchDealer"
              :loading="dealerLoading"
              placeholder="经销商名称"
              style="width: 160px"
            >
              <el-option v-for="d in dealerOptions" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="批号">
            <el-input v-model="pickerQuery.batchNo" placeholder="产品批号" clearable style="width: 130px" @keyup.enter="searchShipments" />
          </el-form-item>
          <el-form-item label="产品">
            <el-input v-model="pickerQuery.productCode" placeholder="产品编码/名称" clearable style="width: 150px" @keyup.enter="searchShipments" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="searchShipments">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetPickerQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table
          :data="shipmentList"
          v-loading="shipmentLoading"
          border
          stripe
          size="small"
          highlight-current-row
          height="200"
          @row-dblclick="onPickShipment"
        >
          <el-table-column label="发货单号" prop="code" width="150" show-overflow-tooltip />
          <el-table-column label="销售订单" prop="orderCode" width="140" show-overflow-tooltip />
          <el-table-column label="经销商" prop="dealerName" min-width="140" show-overflow-tooltip />
          <el-table-column label="发货仓库" prop="warehouseName" width="120" show-overflow-tooltip />
          <el-table-column label="发货日期" prop="salesDate" width="100" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag size="small" type="success">{{ statusFmt(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="onPickShipment(row)">选择</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="picker-tip">提示：双击行可快速选择；选择后将自动带入经销商、仓库及可退货明细</div>
      </div>
    </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Link, Document, Goods, Search, RefreshLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getOperationLogs } from '@/api/crud'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const logs = ref([])
const saving = ref(false)
const submitting = ref(false)
const loadingDetail = ref(false)
const submitError = ref('')

const pickerVisible = ref(false)
const pickerQuery = reactive({ dealerId: null, keyword: '', batchNo: '', productCode: '' })
const pickerDateRange = ref([])
const shipmentList = ref([])
const shipmentLoading = ref(false)
const dealerOptions = ref([])
const dealerLoading = ref(false)
let dealerTimer = null

const sourceSalesOut = ref(null)

const reasonOptions = [
  { value: 'NORMAL', label: '常规退货' },
  { value: 'CONTAMINATED', label: '术前污染' },
  { value: 'QUALITY', label: '质量问题' },
  { value: 'NEAR_EXPIRY', label: '近效期退货' },
  { value: 'DAMAGED', label: '运输破损' },
  { value: 'OTHER', label: '其他' }
]

const form = reactive({
  id: null,
  code: '',
  status: 'DRAFT',
  sourceSalesOutId: null,
  sourceSalesOutCode: '',
  refSalesOutId: null,
  refSalesOutCode: '',
  dealerId: null,
  dealerName: '',
  warehouseId: null,
  warehouseName: '',
  expectedDate: todayStr(),
  reasonCode: '',
  reason: '',
  remark: '',
  finalAmount: 0,
  amountInclTax: 0,
  lines: []
})

const isEdit = computed(() => !!route.params.id)
const isViewMode = computed(() => route.query.mode === 'view' || route.query.readonly === '1' || route.meta.readonly === true)
const isReadonly = computed(() => isViewMode.value || ['COMPLETED','RECEIVING','APPROVED','PENDING_APPROVAL','CANCELLED'].includes(form.status))
const canCreateRedOut = computed(() => isReadonly.value && ['APPROVED','RECEIVING'].includes(form.status) && !!form.id)
const creatingRedOut = ref(false)

function todayStr() {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

function statusFmt(s) {
  return ({
    DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '已审批', REJECTED: '已驳回',
    RECEIVING: '收货中', COMPLETED: '已完成', CANCELLED: '已取消',
    SHIPPED: '已发货', PARTIAL_SHIPPED: '部分发货', CONFIRMED: '已确认',
    COMPLETED_OUT: '已完成', PARTIAL_OUTBOUND: '部分出库'
  })[s] || s || '-'
}
function statusTag(s) {
  return ({ DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'primary', REJECTED: 'danger', RECEIVING: 'warning', COMPLETED: 'success', CANCELLED: 'danger' })[s] || ''
}

function normalizeReasonCode(code) {
  if (code === 'CONTAMINATED') return 'PRE_OP_CONTAMINATION'
  if (code === 'QUALITY') return 'QUALITY_ISSUE'
  return code || ''
}
function onReasonCodeChange(code) {
  const found = reasonOptions.find(r => r.value === code)
  form.reason = found ? found.label : ''
}

function returnableQty(row) {
  const shipped = Number(row.shippedQty || row.qty || 0)
  const returned = Number(row.returnedQty || 0)
  const r = shipped - returned
  return r > 0 ? r : 0
}
function lineTotal(row) {
  const price = Number(row.unitPrice || 0)
  if (isReadonly.value) {
    const saved = Number(row.finalAmount != null ? row.finalAmount : (row.subtotal != null ? row.subtotal : NaN))
    if (Number.isFinite(saved)) return Math.round(saved * 100) / 100
    return Math.round(price * Number(row.qty || 0) * 100) / 100
  }
  const qty = Math.min(Number(row.qty || 0), Number(returnableQty(row)))
  return Math.round(price * qty * 100) / 100
}
const returnTotal = computed(() => form.lines.reduce((sum, row) => sum + lineTotal(row), 0))

function removeLine(i) {
  form.lines.splice(i, 1)
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
  } finally { creatingRedOut.value = false }
}

function openShipmentPicker() {
  pickerVisible.value = true
  if (shipmentList.value.length === 0) searchShipments()
}

function resetPickerQuery() {
  pickerQuery.dealerId = null
  pickerQuery.keyword = ''
  pickerQuery.batchNo = ''
  pickerQuery.productCode = ''
  pickerDateRange.value = []
  dealerOptions.value = []
  searchShipments()
}

async function onSearchDealer(q) {
  if (!q || q.length < 1) { dealerOptions.value = []; return }
  dealerLoading.value = true
  if (dealerTimer) clearTimeout(dealerTimer)
  dealerTimer = setTimeout(async () => {
    try {
      const res = await request({ url: '/api/lookups/dealers', method: 'get', params: { keyword: q, limit: 20 } })
      dealerOptions.value = res?.data || []
    } catch { dealerOptions.value = [] }
    finally { dealerLoading.value = false }
  }, 250)
}

async function searchShipments() {
  shipmentLoading.value = true
  try {
    const params = {
      dealerId: pickerQuery.dealerId || undefined,
      keyword: pickerQuery.keyword || undefined,
      batchNo: pickerQuery.batchNo || undefined,
      productCode: pickerQuery.productCode || undefined
    }
    if (pickerDateRange.value && pickerDateRange.value.length === 2) {
      params.startDate = pickerDateRange.value[0]
      params.endDate = pickerDateRange.value[1]
    }
    const res = await request({ url: '/api/sales-returns/shipped-outs', method: 'get', params })
    shipmentList.value = res?.data || []
  } catch (e) {
    ElMessage.error('查询发货单失败：' + (e?.message || ''))
    shipmentList.value = []
  } finally {
    shipmentLoading.value = false
  }
}

async function onPickShipment(row) {
  if (!row || !row.id) return
  if (form.lines.length > 0 || form.dealerId) {
    try {
      await ElMessageBox.confirm(
        '更换原发货单将清空当前已录入的经销商、仓库和销退明细，是否继续？',
        '更换发货单确认',
        { confirmButtonText: '确认更换', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
  }
  pickerVisible.value = false
  await loadShippedOutLines(row)
}

async function loadShippedOutLines(row) {
  loadingDetail.value = true
  try {
    const res = await request({ url: `/api/sales-returns/shipped-outs/${row.id}/lines`, method: 'get' })
    const d = res?.data
    if (!d) throw new Error('发货单可退明细为空')
    sourceSalesOut.value = { ...row, ...d }
    form.sourceSalesOutId = d.id
    form.sourceSalesOutCode = d.code || row.code
    form.dealerId = d.dealerId || row.dealerId
    form.dealerName = row.dealerName || ''
    form.warehouseId = d.warehouseId || row.warehouseId
    form.warehouseName = row.warehouseName || ''
    if (!form.expectedDate) form.expectedDate = todayStr()
    form.lines = (d.lines || []).map(l => ({
      sourceOutLineId: l.sourceOutLineId || l.id,
      lineNo: l.lineNo,
      orderLineNo: l.orderLineNo,
      productId: l.productId,
      productCode: l.productCode,
      productName: l.productName,
      productSpec: l.productSpec,
      unit: l.unit,
      batchNo: l.batchNo || '',
      serialNo: l.serialNo || '',
      isSerialManaged: l.isSerialManaged,
      qty: Number(l.returnableQty || l.qty || 0),
      shippedQty: Number(l.shippedQty || 0),
      returnedQty: Number(l.returnedQty || 0),
      unitPrice: Number(l.unitPrice || 0),
      taxRate: l.taxRate != null ? Number(l.taxRate) : 0.13
    })).filter(l => l.qty > 0)
    ElMessage.success(`已带入 ${form.dealerName} / ${form.warehouseName}，共 ${form.lines.length} 行可退明细`)
  } catch (e) {
    submitError.value = e?.response?.data?.message || e.message || '加载发货单明细失败'
    ElMessage.error(submitError.value)
    sourceSalesOut.value = null
    form.sourceSalesOutId = null
    form.lines = []
  } finally {
    loadingDetail.value = false
  }
}

async function onSave(targetStatus) {
  if (saving.value || submitting.value) return
  await doSave(targetStatus)
}

async function doSave(targetStatus) {
  const sourceId = form.sourceSalesOutId || form.refSalesOutId
  if (!sourceId) { ElMessage.error('请选择原发货单'); return }
  if (!form.dealerId) { ElMessage.error('请选择经销商'); return }
  if (!form.warehouseId) { ElMessage.error('请选择原发货仓库'); return }
  if (!form.expectedDate) { ElMessage.error('请选择仓库收货日期'); return }
  if (!form.reasonCode) { ElMessage.error('请选择退货原因'); return }
  if (!form.lines.length) { ElMessage.error('请至少添加一条销退明细'); return }
  const invalid = form.lines.find(l => !l.qty || Number(l.qty) <= 0 || !Number.isInteger(Number(l.qty)))
  if (invalid) { ElMessage.error(`产品「${invalid.productCode || ''}」退货数量必须是大于 0 的整数`); return }
  const over = form.lines.find(l => Number(l.qty) > Number(returnableQty(l)))
  if (over) { ElMessage.error(`产品「${over.productCode || ''}」退货数量不能大于可退数量 ${returnableQty(over)}`); return }
  submitError.value = ''
  if (targetStatus === 'PENDING_APPROVAL') submitting.value = true; else saving.value = true
  try {
    const payload = {
      sourceSalesOutId: sourceId,
      sourceSalesOutCode: form.sourceSalesOutCode,
      refSalesOutId: sourceId,
      refSalesOutCode: form.sourceSalesOutCode,
      dealerId: form.dealerId,
      dealerName: form.dealerName,
      warehouseId: form.warehouseId,
      warehouseName: form.warehouseName,
      expectedDate: form.expectedDate,
      reasonCode: form.reasonCode,
      reason: form.reason || reasonOptions.find(r => r.value === form.reasonCode)?.label || '',
      remark: form.remark,
      lines: form.lines.map(l => ({
        sourceOutLineId: l.sourceOutLineId,
        productId: l.productId,
        productCode: l.productCode,
        productName: l.productName,
        qty: Number(l.qty),
        unitPrice: Number(l.unitPrice || 0),
        finalAmount: Number(l.finalAmount || l.subtotal || 0),
        taxRate: l.taxRate != null ? Number(l.taxRate) : 0.13,
        batchNo: l.batchNo || '',
        serialNo: l.serialNo || ''
      }))
    }
    let res
    if (isEdit.value) {
      res = await request({ url: `/api/sales-returns/${form.id}`, method: 'put', data: payload, skipDuplicate: true })
    } else {
      res = await request({ url: '/api/sales-returns', method: 'post', data: payload, skipDuplicate: true })
    }
    if (res && res.code && res.code !== 0 && res.code !== 200) throw new Error(res.message || '保存失败')
    const savedId = res?.data?.id || form.id
    if (targetStatus === 'PENDING_APPROVAL' && savedId) {
      try { await request({ url: `/api/sales-returns/${savedId}/submit`, method: 'post', data: {} }) }
      catch (e) {
        const msg = e?.response?.data?.message || e?.message || '提交审批失败'
        submitError.value = '草稿已保存，但提交审批失败：' + msg
        ElMessage.error(submitError.value)
        form.id = savedId
        form.status = 'DRAFT'
        router.replace('/sales-return-edit/' + savedId)
        return
      }
    }
    ElMessage.success(targetStatus === 'PENDING_APPROVAL' ? '已提交审批' : '暂存成功')
    router.push('/m/sales-returns')
  } catch (e) {
    submitError.value = e?.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
    submitting.value = false
  }
}

async function loadForEdit() {
  if (!route.params.id) return
  loadingDetail.value = true
  try {
    const res = await request({ url: `/api/sales-returns/${route.params.id}`, method: 'get' })
    try {
      const lr = await getOperationLogs('sales_return', route.params.id, 'salesReturn')
      logs.value = Array.isArray(lr?.data) ? lr.data : []
    } catch (e) { logs.value = [] }
    const d = res?.data
    if (!d) throw new Error('未找到销退单')
    Object.assign(form, {
      id: d.id, code: d.code, status: d.status,
      sourceSalesOutId: d.sourceSalesOutId || d.refSalesOutId || null,
      sourceSalesOutCode: d.sourceSalesOutCode || d.refSalesOutCode || '',
      refSalesOutId: d.refSalesOutId || d.sourceSalesOutId || null,
      refSalesOutCode: d.refSalesOutCode || d.sourceSalesOutCode || '',
      dealerId: d.dealerId, dealerName: d.dealerName || '',
      warehouseId: d.warehouseId, warehouseName: d.warehouseName || '',
      expectedDate: d.expectedDate || todayStr(),
      reasonCode: normalizeReasonCode(d.reasonCode) || (d.returnReason ? reasonOptions.find(r => r.label === d.returnReason)?.value || '' : ''),
      reason: d.reason || d.returnReason || '', remark: d.remark || '',
      finalAmount: Number(d.finalAmount || 0), amountInclTax: Number(d.amountInclTax || d.finalAmount || 0),
      lines: Array.isArray(d.lines) ? d.lines.map(l => ({ ...l, unitPrice: Number(l.unitPrice || 0), finalAmount: Number(l.finalAmount || l.subtotal || 0), batchNo: l.batchNo || '', serialNo: l.serialNo || '' })) : []
    })
    if (form.sourceSalesOutId) {
      sourceSalesOut.value = {
        id: form.sourceSalesOutId,
        code: form.sourceSalesOutCode,
        dealerName: form.dealerName,
        warehouseName: form.warehouseName,
        status: d.sourceSalesOutStatus || '',
        orderCode: d.sourceOrderCode || d.refOrderCode || '',
        salesDate: d.sourceSalesOutDate || d.refSalesOutDate || ''
      }
    }
  } catch (e) {
    submitError.value = e?.response?.data?.message || e.message
  } finally {
    loadingDetail.value = false
  }
}

function resetForm() {
  form.id = null
  form.code = ''
  form.status = 'DRAFT'
  form.sourceSalesOutId = null
  form.sourceSalesOutCode = ''
  form.refSalesOutId = null
  form.refSalesOutCode = ''
  form.dealerId = null
  form.dealerName = ''
  form.warehouseId = null
  form.warehouseName = ''
  form.expectedDate = todayStr()
  form.reasonCode = ''
  form.reason = ''
  form.remark = ''
  form.lines = []
  sourceSalesOut.value = null
  submitError.value = ''
  pickerQuery.dealerId = null
  pickerQuery.keyword = ''
  pickerQuery.batchNo = ''
  pickerQuery.productCode = ''
  pickerDateRange.value = []
}

onMounted(() => { if (isEdit.value) loadForEdit(); else resetForm() })
// When navigating between edit/new without unmounting, reset or reload accordingly
watch(() => route.params.id, (newId) => {
  if (newId) loadForEdit()
  else resetForm()
})
watch(pickerVisible, (v) => { if (!v) { /* keep last query for reopen */ } })
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
.source-info { font-size: 13px; }
.return-summary { display:flex;justify-content:flex-end;gap:24px;padding:12px 4px 0;color:#606266;font-size:13px }
.return-summary .amount-text { font-size:16px }
.source-card :deep(.el-card__body) { padding: 8px 14px; }
.compact-source-card :deep(.el-card__header){ padding: 8px 14px; }
.source-info-inline { display:flex;flex-wrap:wrap;gap:10px 22px;align-items:center;font-size:13px }
.source-info-inline b { color:#606266;font-weight:600 }
.source-empty { display: flex; align-items: center; gap: 8px; padding: 4px 0; color: #909399; }
.source-empty-text { font-size: 13px; }
.shipment-picker .picker-form { margin-bottom: 8px; }
.shipment-picker-dialog .el-dialog__body { padding-top: 12px; padding-bottom: 12px; }
.shipment-picker-dialog .picker-form .el-form-item { margin-bottom: 8px; margin-right: 8px; }
.shipment-picker .picker-tip { margin-top: 8px; font-size: 12px; color: #909399; }
.required-mark { color: var(--el-color-danger); margin-left: 2px; }
.el-form-item { margin-bottom: 14px; }
.amount-text { font-weight: 600; color: var(--el-color-danger); }
@media (max-width: 768px) { .page-actions { flex-wrap: wrap; } }
</style>
