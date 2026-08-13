<template>
  <div class="sales-out-edit">
    <div class="page-toolbar">
      <el-button @click="$router.back()"><el-icon><ArrowLeft /></el-icon>返回</el-button>
      <div class="spacer" />
      <el-button v-if="canCreateBatch" type="primary" @click="handleCreateBatch" :loading="createLoading">
        <el-icon><Plus /></el-icon>创建发货单
      </el-button>
      <el-button v-if="canCancelRemaining" type="warning" plain @click="openCancelRemainingDialog">
        <el-icon><CircleClose /></el-icon>取消剩余发货
      </el-button>
    </div>

    <el-card shadow="never">
      <template #header><el-icon><Document /></el-icon>出库单信息</template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="出库单号">{{ salesOut.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经销商">{{ salesOut.dealerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货仓库">{{ salesOut.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源订单">{{ salesOut.sourceOrderCode || (salesOut.sourceOrder?.code) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusTagType(salesOut.status)" size="small">{{ statusText(salesOut.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ formatDateTime(salesOut.shippedAt) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(salesOut.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(salesOut.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ salesOut.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="salesOut.sourceOrder" shadow="never" style="margin-top:14px">
      <template #header>
        <el-icon><Tickets /></el-icon>关联销售订单
        <span style="margin-left:12px;color:var(--dms-text-4);font-weight:normal">来源单据（不可编辑）</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="销售单号">{{ salesOut.sourceOrder.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="销售类型">{{ orderTypeText(salesOut.sourceOrder.orderType) }}</el-descriptions-item>
        <el-descriptions-item label="订单状态"><el-tag :type="statusTagType(salesOut.sourceOrder.status)" size="small">{{ statusText(salesOut.sourceOrder.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="经销商">{{ salesOut.sourceOrder.dealerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货仓库">{{ salesOut.sourceOrder.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望发货日期">{{ formatDate(salesOut.sourceOrder.expectedDate) }}</el-descriptions-item>
        <el-descriptions-item label="含税金额">{{ salesOut.sourceOrder.amountInclTax != null ? salesOut.sourceOrder.amountInclTax : '-' }}</el-descriptions-item>
        <el-descriptions-item label="税额">{{ salesOut.sourceOrder.taxAmount != null ? salesOut.sourceOrder.taxAmount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="实付金额">{{ salesOut.sourceOrder.finalAmount != null ? salesOut.sourceOrder.finalAmount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单人">{{ salesOut.sourceOrder.createdByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatDateTime(salesOut.sourceOrder.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ salesOut.sourceOrder.approvedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间" :span="2">{{ formatDateTime(salesOut.sourceOrder.approvedAt) }}</el-descriptions-item>
        <el-descriptions-item label="销售备注" :span="3">{{ salesOut.sourceOrder.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="soLines.length > 0" shadow="never" style="margin-top:14px">
      <template #header>
        <el-icon><Goods /></el-icon>销售订单产品明细
        <span style="margin-left:12px;color:var(--dms-text-4);font-weight:normal">订单原始数量与待发参考</span>
      </template>
      <el-table :data="soLinesView" border size="small" style="width:100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="行号" width="70" prop="seq" />
        <el-table-column label="产品编码" min-width="140" prop="productCode" />
        <el-table-column label="产品名称" min-width="200" prop="productName" />
        <el-table-column label="订单数量" width="100" prop="qty" />
        <el-table-column label="已发数量" width="100"><template #default="{ row }">{{ shippedByProduct[row.productId] || 0 }}</template></el-table-column>
        <el-table-column label="待发数量" width="100"><template #default="{ row }">{{ remainingByProduct[row.productId] ?? row.qty }}</template></el-table-column>
        <el-table-column label="单价" width="110" prop="unitPrice" />
        <el-table-column label="小计" width="120" prop="subtotal" />
      </el-table>
    </el-card>
    <el-card shadow="never" style="margin-top:14px">
      <template #header>
        <el-icon><Box /></el-icon>发货子单列表
        <span style="margin-left:12px;color:var(--dms-text-4);font-weight:normal">每次发货一张子单，独立保存/确认/取消；批次与序列号必须选择在库合格库存</span>
      </template>
      <el-empty v-if="!salesOut.batches || salesOut.batches.length === 0" description="暂无发货子单，请点击顶部 [创建发货单] 开始" />

      <div v-for="batch in salesOut.batches" :key="batch.id" class="batch-card">
        <div class="batch-head">
          <span class="batch-code">{{ batch.code }}</span>
          <el-tag :type="batchTagType(batch.status)" size="small" style="margin-left:8px">{{ batchStatusText(batch.status) }}</el-tag>
          <span class="batch-meta">创建：{{ formatDateTime(batch.createdAt) }}</span>
          <span v-if="batch.confirmedAt" class="batch-meta">确认：{{ formatDateTime(batch.confirmedAt) }} {{ batch.confirmedByName ? '('+batch.confirmedByName+')' : '' }}</span>
          <span v-if="batch.cancelledAt" class="batch-meta">取消：{{ formatDateTime(batch.cancelledAt) }} {{ batch.cancelReason ? '('+batch.cancelReason+')' : '' }}</span>
        </div>

        <el-table :data="batch.lines || []" border size="small" style="width:100%">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="发货行号" width="90">
            <template #default="{ row }">
              <el-input-number v-if="batch.status === 'DRAFT'" v-model="row.shipLineNo" :controls="false" :min="1" size="small" style="width:100%" />
              <span v-else>{{ row.shipLineNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="应发行" min-width="220">
            <template #default="{ row }">
              <el-select v-if="batch.status === 'DRAFT'" v-model="row.expectedLineId" placeholder="选择订单明细行" filterable size="small" style="width:100%" @change="(val) => onExpectedPick(batch, row, val)">
                <el-option v-for="opt in expectedOptions" :key="opt.id" :label="`行${opt.seq} ${opt.productCode || ''} ${opt.productName || ''} (待发${opt.remaining})`" :value="opt.id" :disabled="opt.remaining <= 0" />
              </el-select>
              <span v-else>{{ row.productName || ('产品'+row.productId) }} <el-tag v-if="row.isSerialManaged" type="warning" size="small">序列号</el-tag></span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <el-input-number v-if="batch.status === 'DRAFT'" v-model="row.qty" :min="0" :controls="false" size="small" style="width:100%" />
              <span v-else>{{ row.qty || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="在库批次(合格)" min-width="240">
            <template #default="{ row }">
              <el-select v-if="batch.status === 'DRAFT' && row.productId" v-model="row.stockBatchId" placeholder="选择合格批次" filterable size="small" style="width:100%" @change="(val) => onStockBatchPick(row, val)">
                <el-option v-for="b in (batchesByProduct[row.productId] || [])" :key="b.id" :label="b.label" :value="b.id" />
              </el-select>
              <span v-else>{{ row.batchNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="序列号" min-width="180">
            <template #default="{ row }">
              <template v-if="batch.status === 'DRAFT' && row.isSerialManaged">
                <el-button v-if="row.stockBatchId" link type="primary" size="small" @click="openSerialDialog(row)">{{ row.serialNo ? '已选: ' + row.serialNo : '选择序列号' }}</el-button>
                <span v-else style="color:var(--dms-text-placeholder)">请先选批次</span>
              </template>
              <span v-else-if="row.isSerialManaged" class="serial-preview">{{ row.serialNo || '-' }}</span>
              <span v-else style="color:var(--dms-text-placeholder)">—</span>
            </template>
          </el-table-column>
          <el-table-column v-if="batch.status === 'DRAFT'" label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button size="small" type="danger" link @click="removeBatchLine(batch, $index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="batch.status === 'DRAFT'" class="batch-actions">
          <el-button size="small" type="primary" plain @click="addBatchLine(batch)"><el-icon><Plus /></el-icon>新增行</el-button>
          <el-button size="small" @click="saveBatch(batch)" :loading="batch._saving">保存明细</el-button>
          <el-button size="small" type="success" @click="confirmBatchAction(batch)" :loading="batch._confirming"><el-icon><Check /></el-icon>确认发货</el-button>
          <el-button size="small" type="danger" plain @click="openCancelBatchDialog(batch)"><el-icon><CircleClose /></el-icon>取消本次</el-button>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="cancelBatchVisible" title="取消本次发货" width="500px">
      <el-form label-width="100px">
        <el-form-item label="子单号"><el-tag>{{ cancelTargetBatch?.code }}</el-tag></el-form-item>
        <el-form-item label="取消原因" required>
          <el-input v-model="cancelBatchReason" type="textarea" :rows="3" placeholder="请填写取消原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelBatchVisible = false">关闭</el-button>
        <el-button type="danger" :loading="cancelBatchLoading" @click="submitCancelBatch">确认取消本次</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelRemainingVisible" title="取消剩余发货" width="500px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px">取消剩余后，父单不再允许新建发货子单；已确认的发货不受影响。</el-alert>
      <el-form label-width="100px">
        <el-form-item label="取消原因" required>
          <el-input v-model="cancelRemainingReason" type="textarea" :rows="3" placeholder="请填写取消原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelRemainingVisible = false">关闭</el-button>
        <el-button type="warning" :loading="cancelRemainingLoading" @click="submitCancelRemaining">确认取消剩余</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="serialDialogVisible" title="选择在库序列号" width="480px">
      <el-empty v-if="!availableSerials.length" description="该批次下无在库序列号" :image-size="60" />
      <el-radio-group v-else v-model="selectedSerialNo" style="width:100%">
        <div v-for="s in availableSerials" :key="s.id" style="margin-bottom:6px">
          <el-radio :value="s.serialNo">{{ s.serialNo }} <span style="color:var(--dms-text-4);font-size:12px">{{ formatTime(s.receivedAt) }}</span></el-radio>
        </div>
      </el-radio-group>
      <template #footer>
        <el-button @click="serialDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applySerial">确认选择</el-button>
      </template>
    </el-dialog>

    <el-card shadow="never" style="margin-top:14px">
      <template #header><el-icon><DataAnalysis /></el-icon>发货汇总</template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="累计应发">{{ totalExpected }}</el-descriptions-item>
        <el-descriptions-item label="累计已发">{{ totalShipped }}</el-descriptions-item>
        <el-descriptions-item label="待发">{{ totalRemaining }}</el-descriptions-item>
        <el-descriptions-item label="已取消">{{ totalCancelled }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:14px">
      <template #header><el-icon><List /></el-icon>已发货记录</template>
      <el-table :data="confirmedBatchLines" border size="small" style="width:100%">
        <el-table-column label="子单号" width="180" prop="batchCode" />
        <el-table-column label="产品编码" width="140" prop="productCode" />
        <el-table-column label="产品名称" min-width="180" prop="productName" />
        <el-table-column label="批次号" width="140" prop="batchNo" />
        <el-table-column label="序列号" width="180" prop="serialNo" />
        <el-table-column label="发货数量" width="100" prop="qty" />
        <el-table-column label="发货时间" width="160"><template #default="{ row }">{{ formatDateTime(row.confirmedAt) }}</template></el-table-column>
        <el-table-column label="发货人" width="120" prop="confirmedByName" />
      </el-table>
      <el-empty v-if="!confirmedBatchLines.length" description="暂无发货记录" :image-size="60" />
    </el-card>

    <el-card shadow="never" style="margin-top:14px">
      <template #header><el-icon><Tickets /></el-icon>操作记录</template>
      <el-table :data="opLogs" border size="small" style="width:100%">
        <el-table-column label="操作时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作人" width="120" prop="operatorName" />
        <el-table-column label="操作" width="120" prop="action" />
        <el-table-column label="备注" prop="remark" />
      </el-table>
      <el-empty v-if="!opLogs.length" description="暂无操作记录" :image-size="60" />
    </el-card>
  </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, CircleClose, Document, Tickets, Box, Check, Goods, DataAnalysis, List } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getDetail, actionResource } from '@/api/crud'
import { statusText, statusTagType } from '@/utils/dict'
import { formatDateTime, formatDate } from '@/utils/format'

const route = useRoute()
const salesOutId = computed(() => route.params.id)

const salesOut = reactive({ batches: [] })
const soLines = ref([])
const opLogs = ref([])
const expectedOptions = ref([])
const batchesByProduct = reactive({})
const createLoading = ref(false)

const cancelBatchVisible = ref(false)
const cancelBatchReason = ref('')
const cancelTargetBatch = ref(null)
const cancelBatchLoading = ref(false)
const cancelRemainingVisible = ref(false)
const cancelRemainingReason = ref('')
const cancelRemainingLoading = ref(false)

const serialDialogVisible = ref(false)
const currentSerialRow = ref(null)
const availableSerials = ref([])
const selectedSerialNo = ref('')

const activeParentStatuses = ['DRAFT', 'APPROVED', 'PARTIAL_SHIPPED']
const canCreateBatch = computed(() => activeParentStatuses.includes(salesOut.status))
const canCancelRemaining = computed(() => activeParentStatuses.includes(salesOut.status))

function formatTime(s) { return formatDateTime(s) }
function orderTypeText(t) { return ({ NORMAL: '常规销售', URGENT: '紧急销售', RETURN: '退货' })[t] || t || '-' }
function batchStatusText(s) { return ({ DRAFT: '草稿', CONFIRMED: '已确认', CANCELLED: '已取消' })[s] || s || '-' }
function batchTagType(s) { return ({ DRAFT: 'warning', CONFIRMED: 'success', CANCELLED: 'info' })[s] || 'default' }

function parseList(res) {
  if (Array.isArray(res)) return res
  if (res?.data && Array.isArray(res.data)) return res.data
  if (res?.data?.list) return res.data.list
  if (res?.data?.records) return res.data.records
  return []
}

const shippedByProduct = computed(() => {
  const m = {}
  for (const l of salesOut.lines || []) m[l.productId] = (m[l.productId] || 0) + Number(l.shippedQty || 0)
  return m
})
const totalExpected = computed(() => (salesOut.lines || []).reduce((s, l) => s + Number(l.expectedQty || 0), 0))
const totalShipped = computed(() => (salesOut.lines || []).reduce((s, l) => s + Number(l.shippedQty || 0), 0))
const totalCancelled = computed(() => (salesOut.lines || []).reduce((s, l) => s + Number(l.cancelledQty || 0), 0))
const totalRemaining = computed(() => (salesOut.lines || []).reduce((s, l) => s + Math.max(0, Number(l.expectedQty || 0) - Number(l.shippedQty || 0) - Number(l.cancelledQty || 0)), 0))

const soLinesView = computed(() => soLines.value)
const remainingByProduct = computed(() => {
  const m = {}
  for (const l of salesOut.lines || []) m[l.productId] = Math.max(0, Number(l.expectedQty || 0) - Number(l.shippedQty || 0) - Number(l.cancelledQty || 0))
  return m
})

const confirmedBatchLines = computed(() => {
  const out = []
  for (const b of salesOut.batches || []) {
    if (b.status !== 'CONFIRMED') continue
    for (const l of b.lines || []) {
      out.push({ batchCode: b.code, productCode: l.productCode, productName: l.productName, batchNo: l.batchNo, serialNo: l.serialNo, qty: l.qty, confirmedAt: formatTime(b.confirmedAt), confirmedByName: b.confirmedByName })
    }
  }
  return out
})

onMounted(() => { loadSalesOut(); loadOpLogs() })

async function loadOpLogs() {
  try {
    const res = await request({ url: `/api/operation-log/list/salesOut/${salesOutId.value}`, method: 'get', params: { size: 200 } })
    const d = res.data || res
    opLogs.value = (d.records || d.list || []).map(x => ({ ...x, createdAt: formatTime(x.createdAt) }))
  } catch (e) { opLogs.value = [] }
}

async function loadSalesOut() {
  try {
    const res = await getDetail('/api/sales-outs', salesOutId.value)
    const d = res.data || {}
    Object.assign(salesOut, d)
    if (!Array.isArray(salesOut.batches)) salesOut.batches = []
    soLines.value = Array.isArray(d.soLines) ? d.soLines : []

    // 应发行下拉：来自 sales_out_lines (expected_qty>0)
    expectedOptions.value = (d.lines || []).map(l => ({
      id: l.id,
      seq: l.seq,
      productId: l.productId,
      productCode: l.productCode,
      productName: l.productName,
      unitPrice: l.unitPrice || 0,
      isSerialManaged: l.isSerialManaged,
      remaining: Math.max(0, Number(l.expectedQty || 0) - Number(l.shippedQty || 0) - Number(l.cancelledQty || 0))
    }))

    // 子单行回填：后端已带 productName/isSerialManaged；初始化编辑态标记
    salesOut.batches.forEach(b => {
      b._saving = false
      b._confirming = false
      ;(b.lines || []).forEach(l => {
        if (l.stockBatchId && l.batchNo) {
          // 已确认行只读；草稿行需要加载批次列表
        }
      })
    })

    // 预加载所有应发行产品的合格批次
    const productIds = [...new Set((d.lines || []).map(l => l.productId).filter(Boolean))]
    await Promise.all(productIds.map(pid => loadBatchesForProduct(pid)))
  } catch (e) {
    ElMessage.error('加载出库单失败')
  }
}

async function loadBatchesForProduct(productId) {
  if (!productId) return
  try {
    const params = new URLSearchParams({ productId: String(productId) })
    if (salesOut.warehouseId) params.append('warehouseId', String(salesOut.warehouseId))
    const res = await request({ url: `/api/inventory/available-batches?${params.toString()}`, method: 'get' })
    batchesByProduct[productId] = parseList(res)
  } catch (e) {
    batchesByProduct[productId] = []
  }
}

async function loadSerialsForRow(row) {
  const warehouseId = row?.warehouseId || salesOut.warehouseId
  if (!row?.productId || !row?.batchNo || !warehouseId) { availableSerials.value = []; return }
  try {
    const params = new URLSearchParams({ productId: String(row.productId), batchNo: row.batchNo, warehouseId: String(warehouseId) })
    const res = await request({ url: `/api/inventory/available-serials?${params.toString()}`, method: 'get' })
    availableSerials.value = parseList(res)
  } catch (e) { availableSerials.value = [] }
}

function nextShipLineNo(batch) {
  const nos = (batch.lines || []).map(x => x.shipLineNo || 0)
  return nos.length ? Math.max(...nos) + 1 : 1
}

function addBatchLine(batch) {
  if (!batch.lines) batch.lines = []
  const first = expectedOptions.value.find(o => o.remaining > 0)
  batch.lines.push({
    shipLineNo: nextShipLineNo(batch),
    expectedLineId: first ? first.id : null,
    productId: first ? first.productId : null,
    productName: first ? first.productName : '',
    productCode: first ? first.productCode : '',
    isSerialManaged: first ? !!first.isSerialManaged : false,
    qty: first && first.isSerialManaged ? 1 : null,
    unitPrice: first ? first.unitPrice : 0,
    stockBatchId: null,
    batchNo: '',
    serialNo: ''
  })
}

function removeBatchLine(batch, idx) { batch.lines.splice(idx, 1) }

function onExpectedPick(batch, row, expectedLineId) {
  const opt = expectedOptions.value.find(o => o.id === expectedLineId)
  if (!opt) return
  row.productId = opt.productId
  row.productName = opt.productName
  row.productCode = opt.productCode
  row.isSerialManaged = !!opt.isSerialManaged
  row.unitPrice = opt.unitPrice
  row.qty = opt.isSerialManaged ? 1 : null
  row.stockBatchId = null
  row.batchNo = ''
  row.serialNo = ''
  if (opt.productId && !batchesByProduct[opt.productId]) loadBatchesForProduct(opt.productId)
}

function onStockBatchPick(row, stockBatchId) {
  if (!stockBatchId) { row.batchNo = ''; row.serialNo = ''; return }
  const list = batchesByProduct[row.productId] || []
  const b = list.find(x => x.id === stockBatchId)
  if (b) {
    row.batchNo = b.batchNo
    if (!row.isSerialManaged) {
      const remain = remainingOfExpected(row.expectedLineId)
      row.qty = Math.min(Number(b.qty || 0), remain) || 1
    } else {
      row.qty = 1
      row.serialNo = ''
    }
  }
}

function remainingOfExpected(expectedLineId) {
  const opt = expectedOptions.value.find(o => o.id === expectedLineId)
  return opt ? opt.remaining : 0
}

function openSerialDialog(row) {
  currentSerialRow.value = row
  selectedSerialNo.value = row.serialNo || ''
  loadSerialsForRow(row)
  serialDialogVisible.value = true
}

function applySerial() {
  if (!selectedSerialNo.value) { ElMessage.warning('请选择一个序列号'); return }
  if (currentSerialRow.value) {
    currentSerialRow.value.serialNo = selectedSerialNo.value
    currentSerialRow.value.qty = 1
  }
  serialDialogVisible.value = false
}

function validateBatch(batch) {
  const lines = (batch.lines || []).filter(l => l.productId || l.qty || l.stockBatchId || l.serialNo)
  if (!lines.length) return '至少填写一条明细'
  for (const l of lines) {
    if (!l.expectedLineId) return '存在未选择订单明细行的行'
    if (!l.qty || Number(l.qty) <= 0) return `产品 ${l.productName || l.productId} 数量必须大于 0`
    if (!l.stockBatchId || !l.batchNo) return `产品 ${l.productName || l.productId} 必须选择在库批次`
    if (l.isSerialManaged && !l.serialNo) return `产品 ${l.productName || l.productId} 是序列号管理，必须选择序列号`
  }
  const nos = lines.map(l => l.shipLineNo)
  if (new Set(nos).size !== nos.length) return '发货行号存在重复'
  return null
}

function buildPayload(batch) {
  return (batch.lines || []).filter(l => l.productId).map(l => ({
    expectedLineId: l.expectedLineId,
    expectedLineSeq: l.seq,
    shipLineNo: l.shipLineNo,
    productId: l.productId,
    warehouseId: salesOut.warehouseId,
    qty: Number(l.qty),
    stockBatchId: l.stockBatchId,
    batchNo: l.batchNo,
    serialNo: l.serialNo,
    unitPrice: l.unitPrice || 0
  }))
}

async function handleCreateBatch() {
  createLoading.value = true
  try {
    await actionResource('/api/sales-outs', salesOutId.value, '/batches', 'post', {})
    ElMessage.success('已创建发货子单')
    await loadSalesOut(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally { createLoading.value = false }
}

async function saveBatch(batch) {
  const err = validateBatch(batch)
  if (err) { ElMessage.warning(err); return }
  batch._saving = true
  try {
    await request({ url: `/api/sales-out-batches/${batch.id}`, method: 'put', data: { lines: buildPayload(batch) } })
    ElMessage.success('明细已保存')
  } catch (e) { /* 拦截器已提示 */ } finally { batch._saving = false }
}

async function confirmBatchAction(batch) {
  const err = validateBatch(batch)
  if (err) { ElMessage.warning(err); return }
  try {
    await ElMessageBox.confirm(`确认发货子单 ${batch.code}？确认后会扣减库存，不可撤销。`, '确认发货', { type: 'warning' })
  } catch { return }
  batch._confirming = true
  try {
    await request({ url: `/api/sales-out-batches/${batch.id}`, method: 'put', data: { lines: buildPayload(batch) } })
    await request({ url: `/api/sales-out-batches/${batch.id}/confirm`, method: 'post' })
    ElMessage.success('发货已确认，库存已出库')
    await loadSalesOut(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally { batch._confirming = false }
}

function openCancelBatchDialog(batch) {
  cancelTargetBatch.value = batch
  cancelBatchReason.value = ''
  cancelBatchVisible.value = true
}
async function submitCancelBatch() {
  if (!cancelBatchReason.value.trim()) { ElMessage.warning('请填写取消原因'); return }
  cancelBatchLoading.value = true
  try {
    await request({ url: `/api/sales-out-batches/${cancelTargetBatch.value.id}/cancel`, method: 'post', data: { reason: cancelBatchReason.value } })
    ElMessage.success('本次发货已取消')
    cancelBatchVisible.value = false
    await loadSalesOut(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally { cancelBatchLoading.value = false }
}

function openCancelRemainingDialog() {
  cancelRemainingReason.value = ''
  cancelRemainingVisible.value = true
}
async function submitCancelRemaining() {
  if (!cancelRemainingReason.value.trim()) { ElMessage.warning('请填写取消原因'); return }
  cancelRemainingLoading.value = true
  try {
    await actionResource('/api/sales-outs', salesOutId.value, '/cancel-remaining', 'post', { reason: cancelRemainingReason.value })
    ElMessage.success('剩余发货已取消')
    cancelRemainingVisible.value = false
    await loadSalesOut(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally { cancelRemainingLoading.value = false }
}
</script>

<style scoped>
.page-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.page-toolbar .spacer { flex: 1; }
.batch-card { border: 1px solid var(--dms-border-2); border-radius: 6px; padding: 10px; margin-top: 12px; }
.batch-head { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.batch-code { font-weight: 600; font-size: 15px; color: var(--dms-text-2); }
.batch-meta { color: var(--dms-text-4); font-size: 12px; }
.batch-actions { margin-top: 10px; display: flex; gap: 8px; }
.serial-preview { font-size: 12px; color: var(--dms-text-3); white-space: pre-wrap; line-height: 1.4; }
</style>
