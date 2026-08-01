<template>
  <div class="receipt-edit">
    <div class="page-toolbar">
      <el-button @click="$router.back()"><el-icon><ArrowLeft /></el-icon>返回</el-button>
      <div class="spacer" />
      <el-button v-if="canCreateBatch" type="primary" @click="handleCreateBatch" :loading="createLoading">
        <el-icon><Plus /></el-icon>创建收货单
      </el-button>
      <el-button v-if="canCancelRemaining" type="warning" plain @click="openCancelRemainingDialog">
        <el-icon><CircleClose /></el-icon>取消剩余收货
      </el-button>
    </div>

    <el-card shadow="never">
      <template #header><el-icon><Document /></el-icon>收货单信息</template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="收货单号">{{ receipt.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ receipt.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源采购单">{{ receipt.sourcePoCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(receipt.status)" size="small">{{ statusText(receipt.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ receipt.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ receipt.updatedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="receipt.sourcePo" shadow="never" style="margin-top:14px">
      <template #header>
        <el-icon><Tickets /></el-icon>关联采购订单
        <span style="margin-left:12px;color:#909399;font-weight:normal">来源单据（不可编辑）</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="采购单号">{{ receipt.sourcePo.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="采购类型">{{ orderTypeText(receipt.sourcePo.orderType) }}</el-descriptions-item>
        <el-descriptions-item label="采购状态"><el-tag :type="statusTagType(receipt.sourcePo.status)" size="small">{{ statusText(receipt.sourcePo.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ receipt.sourcePo.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入库仓库">{{ receipt.sourcePo.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望到货">{{ receipt.sourcePo.expectedDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="含税金额">{{ receipt.sourcePo.amountInclTax != null ? receipt.sourcePo.amountInclTax : '-' }}</el-descriptions-item>
        <el-descriptions-item label="税额">{{ receipt.sourcePo.taxAmount != null ? receipt.sourcePo.taxAmount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="实付金额">{{ receipt.sourcePo.finalAmount != null ? receipt.sourcePo.finalAmount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单人">{{ receipt.sourcePo.createdByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ receipt.sourcePo.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ receipt.sourcePo.approvedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间" :span="2">{{ receipt.sourcePo.approvedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="采购备注" :span="3">{{ receipt.sourcePo.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="receipt.poLines && receipt.poLines.length > 0" shadow="never" style="margin-top:14px">
      <template #header>
        <el-icon><Goods /></el-icon>采购订单产品明细
        <span style="margin-left:12px;color:#909399;font-weight:normal">可收货数量（采购数量 - 已收）</span>
      </template>
      <el-table :data="receipt.poLines" border size="small" style="width:100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="PO 行号" width="80" prop="seq" />
        <el-table-column label="产品编码" min-width="140" prop="productCode" />
        <el-table-column label="产品名称" min-width="180" prop="productName" />
        <el-table-column label="采购数量" width="100" prop="qty">
          <template #default="{ row }">{{ row.qty || 0 }}</template>
        </el-table-column>
        <el-table-column label="已收数量" width="100" prop="receivedQty">
          <template #default="{ row }">
            <el-tag type="success" size="small">{{ row.receivedQty || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="待收数量" width="100">
          <template #default="{ row }">
            <el-tag :type="(Number(row.qty||0) - Number(row.receivedQty||0)) > 0 ? 'warning' : 'success'" size="small">
              {{ Number(row.qty||0) - Number(row.receivedQty||0) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="100" prop="unitPrice">
          <template #default="{ row }">{{ row.unitPrice || 0 }}</template>
        </el-table-column>
        <el-table-column label="序列号管理" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isSerialManaged" type="warning" size="small">是</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top:14px">
      <template #header>
        <el-icon><Box /></el-icon>收货子单列表
        <span style="margin-left:12px;color:#909399;font-weight:normal">每次收货一张子单，独立确认/取消</span>
      </template>
      <el-empty v-if="!receipt.batches || receipt.batches.length === 0" description="暂无子单，请点击顶部 [创建收货单] 开始" />

      <div v-for="batch in receipt.batches" :key="batch.id" class="batch-card">
        <div class="batch-head">
          <span class="batch-code">{{ batch.code }}</span>
          <el-tag :type="batchTagType(batch.status)" size="small" style="margin-left:8px">{{ batchStatusText(batch.status) }}</el-tag>
          <span class="batch-meta">创建 {{ batch.createdAt || '-' }}</span>
          <span v-if="batch.confirmedAt" class="batch-meta">确认 {{ batch.confirmedAt }}</span>
          <span v-if="batch.cancelledAt" class="batch-meta">取消 {{ batch.cancelledAt }} {{ batch.cancelReason ? '('+batch.cancelReason+')' : '' }}</span>
        </div>

        <el-table :data="batch.lines || []" border size="small" style="width:100%">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="收货行号" width="90">
            <template #default="{ row }">
              <el-input-number v-if="batch.status === 'DRAFT'" v-model="row.receiptLineNo" :controls="false" :min="1" size="small" style="width:100%" />
              <span v-else>{{ row.receiptLineNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="PO 行号" width="80" prop="poLineSeq">
            <template #default="{ row }">{{ row.poLineSeq || '-' }}</template>
          </el-table-column>
          <el-table-column label="产品" min-width="240">
            <template #default="{ row }">
              <el-select v-if="batch.status === 'DRAFT'" v-model="row.poLineId" placeholder="选择采购订单明细行"
                filterable size="small" style="width:100%" @change="(val) => onPoLinePick(row, val)">
                <el-option v-for="pl in poLines" :key="pl.id" :label="`行${pl.seq} ${pl.productCode || ''} ${pl.productName || ''}`" :value="pl.id" />
              </el-select>
              <span v-else>{{ row.productName || ('产品'+row.productId) }} <el-tag v-if="row.isSerialManaged" type="warning" size="small">序列号</el-tag></span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <el-input-number v-if="batch.status === 'DRAFT' && !row.isSerialManaged" v-model="row.qty" :min="0" :controls="false" size="small" style="width:100%" />
              <el-tag v-else-if="batch.status === 'DRAFT' && row.isSerialManaged" type="info" size="small" style="width:100%;text-align:center">{{ row.qty || 0 }} 件</el-tag>
              <span v-else>{{ row.qty || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次号" width="160">
            <template #default="{ row }">
              <el-input v-if="batch.status === 'DRAFT'" v-model="row.batchNo" placeholder="必填" size="small" />
              <span v-else>{{ row.batchNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="序列号" min-width="260">
            <template #default="{ row }">
              <div v-if="batch.status === 'DRAFT' && row.isSerialManaged" class="serial-cell">
                <el-input v-model="row.serialNos" type="textarea" :rows="2"
                  :placeholder="`每行/逗号/空格分隔，共 ${snCount(row).total} 个` + (snCount(row).dup ? '（含重复）' : '')"
                  :class="{ 'serial-warn': snCount(row).dup }" @paste="onSerialPaste(row, $event)" @input="onSerialInput(row)" />
                <el-button size="small" link type="primary" @click="openSerialPaste(row)">粘贴序列号</el-button>
              </div>
              <span v-else-if="row.isSerialManaged" class="serial-preview">{{ row.serialNos || '-' }}</span>
              <span v-else style="color:#c0c4cc">—</span>
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
          <el-button size="small" type="success" @click="confirmBatchAction(batch)" :loading="batch._confirming">
            <el-icon><Check /></el-icon>确认收货
          </el-button>
          <el-button size="small" type="danger" plain @click="openCancelBatchDialog(batch)">
            <el-icon><CircleClose /></el-icon>取消本次
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 取消本次子单 -->
    <el-dialog v-model="cancelBatchVisible" title="取消本次收货" width="500px">
      <el-form label-width="100px">
        <el-form-item label="子单号">
          <el-tag>{{ cancelTargetBatch?.code }}</el-tag>
        </el-form-item>
        <el-form-item label="取消原因" required>
          <el-input v-model="cancelBatchReason" type="textarea" :rows="3" placeholder="请填写取消原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelBatchVisible = false">关闭</el-button>
        <el-button type="danger" :loading="cancelBatchLoading" @click="submitCancelBatch">确认取消本次</el-button>
      </template>
    </el-dialog>

    <!-- 取消剩余收货 -->
    <el-dialog v-model="cancelRemainingVisible" title="取消剩余收货" width="500px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px">
        取消剩余后，父单不再允许新建子单；已确认的子单不受影响。
      </el-alert>
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

    <el-card shadow="never" style="margin-top:14px">
      <template #header>
        <el-icon><Tickets /></el-icon>操作记录
      </template>
      <el-table :data="opLogs" border size="small" style="width:100%">
        <el-table-column label="时间" width="170" prop="createdAt" />
        <el-table-column label="操作人" width="120" prop="operatorName" />
        <el-table-column label="操作" width="120" prop="action" />
        <el-table-column label="备注" prop="remark" />
      </el-table>
      <el-empty v-if="!opLogs.length" description="暂无操作记录" :image-size="60" />
    </el-card>

    <el-dialog v-model="serialPasteVisible" title="粘贴序列号" width="520px" append-to-body>
      <el-alert type="info" :closable="false" style="margin-bottom:10px"
        title="支持换行、逗号、分号、空格、Tab 分隔；系统会自动去重并统计数量。" />
      <el-input v-model="serialPasteText" type="textarea" :rows="8"
        placeholder="请粘贴序列号，例如：&#10;SN001,SN002,SN003&#10;或每行一个" />
      <div style="margin-top:8px;color:#606266;font-size:13px">
        识别到 <b>{{ serialPasteParsed.length }}</b> 个序列号
        <span v-if="serialPasteDup" style="color:#e6a23c">（已自动去重 {{ serialPasteDup }} 个）</span>
      </div>
      <template #footer>
        <el-button @click="serialPasteVisible = false">取消</el-button>
        <el-button type="primary" @click="applySerialPaste">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, CircleClose, Document, Tickets, Box, Check } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getDetail, actionResource } from '@/api/crud'
import { statusText, statusTagType } from '@/utils/dict'

const route = useRoute()
const receiptId = computed(() => route.params.id)

const receipt = reactive({ batches: [] })
const poLines = ref([])
const opLogs = ref([])
async function loadOpLogs() {
  try {
    const res = await request({ url: `/api/operation-log/list/receipt/${receiptId.value}`, method: 'get', params: { size: 200 } })
    const d = res.data || res
    opLogs.value = (d.records || d.list || []).map(x => ({ ...x, createdAt: (x.createdAt || '').substring(0,19).replace('T',' ') }))
  } catch (e) { opLogs.value = [] }
}

const createLoading = ref(false)

const cancelBatchVisible = ref(false)
const cancelBatchReason = ref('')
const cancelTargetBatch = ref(null)
const cancelBatchLoading = ref(false)

const cancelRemainingVisible = ref(false)
const cancelRemainingReason = ref('')
const cancelRemainingLoading = ref(false)

const canCreateBatch = computed(() => ['DRAFT', 'APPROVED', 'PARTIAL_RECEIVED'].includes(receipt.status))
const canCancelRemaining = computed(() => ['DRAFT', 'APPROVED', 'PARTIAL_RECEIVED'].includes(receipt.status))

onMounted(() => { loadReceipt(); loadOpLogs() })

async function loadReceipt() {
  try {
    const res = await getDetail('/api/receipts', receiptId.value)
    const d = res.data || {}
    Object.assign(receipt, d)
    if (!Array.isArray(receipt.batches)) receipt.batches = []
    poLines.value = Array.isArray(d.poLines) ? d.poLines : []
    // 初始化每个 batch line 的 isSerialManaged/productName (若后端未回填)
    receipt.batches.forEach(b => {
      b._saving = false; b._confirming = false
      ;(b.lines || []).forEach(l => {
        if (l.isSerialManaged == null && l.productId) {
          const pl = poLines.value.find(x => x.productId === l.productId)
          if (pl) { l.isSerialManaged = pl.isSerialManaged; l.productName = l.productName || pl.productName; l.productCode = l.productCode || pl.productCode }
        }
      })
    })
  } catch (e) {
    ElMessage.error('加载收货单失败')
  }
}

function batchStatusText(s) {
  const map = { DRAFT: '草稿', CONFIRMED: '已确认', CANCELLED: '已取消' }
  return map[s] || s || '-'
}
function batchTagType(s) {
  const map = { DRAFT: 'warning', CONFIRMED: 'success', CANCELLED: 'info' }
  return map[s] || 'default'
}
function orderTypeText(t) {
  const map = { NORMAL: '常规采购', URGENT: '紧急采购', RETURN: '退货' }
  return map[t] || t || '-'
}

async function handleCreateBatch() {
  createLoading.value = true
  try {
    await actionResource('/api/receipts', receiptId.value, '/batches', 'post', {})
    ElMessage.success('已创建收货子单')
    await loadReceipt(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally {
    createLoading.value = false
  }
}

function addBatchLine(batch) {
  if (!batch.lines) batch.lines = []
  const nextNo = batch.lines.length ? Math.max(...batch.lines.map(x => x.receiptLineNo || 0)) + 1 : 1
  batch.lines.push({ receiptLineNo: nextNo, poLineId: null, poLineSeq: null, productId: null, productName: '', productCode: '', isSerialManaged: false, qty: null, batchNo: '', serialNos: '' })
}
function removeBatchLine(batch, idx) {
  batch.lines.splice(idx, 1)
}
function onPoLinePick(row, poLineId) {
  const pl = poLines.value.find(x => x.id === poLineId)
  if (!pl) return
  row.poLineId = pl.id
  row.poLineSeq = pl.seq
  row.productId = pl.productId
  row.productName = pl.productName
  row.productCode = pl.productCode
  row.isSerialManaged = pl.isSerialManaged
  // 切换产品清空数量/批次/序列号
  row.qty = null
  row.batchNo = ''
  row.serialNos = ''
}

// ---------- 序列号批量粘贴 ----------
const serialPasteVisible = ref(false)
const serialPasteText = ref('')
let serialPasteTarget = null

function parseSerials(text) {
  const list = String(text || '').split(/[\r\n,;\s\t]+/).map(x => x.trim()).filter(Boolean)
  const uniq = []
  const seen = new Set()
  let dup = 0
  for (const sn of list) {
    if (seen.has(sn)) { dup++; continue }
    seen.add(sn)
    uniq.push(sn)
  }
  return { list: uniq, total: list.length, dup }
}

function snCount(row) { return parseSerials(row.serialNos) }

function onSerialInput(row) {
  // 序列号产品：数量自动等于序列号个数，保持一致
  if (row.isSerialManaged) {
    const { list } = parseSerials(row.serialNos)
    if (list.length) row.qty = list.length
  }
}

function onSerialPaste(row, e) {
  // 拦截粘贴：解析剪贴板内容，规范化为每行一个
  try {
    const text = (e.clipboardData || window.clipboardData).getData('text')
    if (!text) return
    const { list } = parseSerials(text)
    if (list.length > 1) {
      e.preventDefault()
      // 合并已有值与新粘贴
      const merged = parseSerials((row.serialNos || '') + '\n' + list.join('\n'))
      row.serialNos = merged.list.join('\n')
      row.qty = merged.list.length
    }
  } catch (_) { /* 浏览器不支持时走默认粘贴 */ }
}

function openSerialPaste(row) {
  serialPasteTarget = row
  serialPasteText.value = row.serialNos || ''
  serialPasteVisible.value = true
}
const serialPasteParsed = computed(() => parseSerials(serialPasteText.value).list)
const serialPasteDup = computed(() => parseSerials(serialPasteText.value).dup)

function applySerialPaste() {
  if (!serialPasteTarget) return serialPasteVisible = false
  const { list } = parseSerials(serialPasteText.value)
  serialPasteTarget.serialNos = list.join('\n')
  if (list.length) serialPasteTarget.qty = list.length
  serialPasteVisible.value = false
}
// ----------------------------------

function validateBatch(batch) {
  const lines = (batch.lines || []).filter(l => l.productId || l.qty || l.batchNo || l.serialNos)
  if (!lines.length) return '至少填写一条明细'
  for (const l of lines) {
    if (!l.productId) return '存在未选择产品的行'
    if (!l.qty || Number(l.qty) <= 0) return `产品 ${l.productName || l.productId} 数量必须大于 0`
    if (!l.batchNo || !l.batchNo.trim()) return `产品 ${l.productName || l.productId} 批次号必填`
    if (l.isSerialManaged) {
      const sns = (l.serialNos || '').split(/[\r\n,;\s]+/).map(s => s.trim()).filter(Boolean)
      if (sns.length === 0) return `产品 ${l.productName || l.productId} 为序列号管理，序列号必填`
      if (sns.length !== Number(l.qty)) return `产品 ${l.productName || l.productId} 序列号数量(${sns.length})与数量(${l.qty})不一致`
    }
  }
  // 收货行号唯一
  const nos = lines.map(l => l.receiptLineNo)
  if (new Set(nos).size !== nos.length) return '收货行号存在重复'
  return null
}

async function saveBatch(batch) {
  const err = validateBatch(batch)
  if (err) { ElMessage.warning(err); return }
  batch._saving = true
  try {
    const payload = {
      lines: (batch.lines || []).filter(l => l.productId).map(l => ({
        productId: l.productId, poLineId: l.poLineId, poLineSeq: l.poLineSeq,
        receiptLineNo: l.receiptLineNo, qty: l.qty, batchNo: l.batchNo, serialNos: l.serialNos
      }))
    }
    await request({ url: `/api/receipt-batches/${batch.id}`, method: 'put', data: payload })
    ElMessage.success('明细已保存')
  } catch (e) { /* 拦截器已提示 */ } finally {
    batch._saving = false
  }
}

async function confirmBatchAction(batch) {
  const err = validateBatch(batch)
  if (err) { ElMessage.warning(err); return }
  try {
    await ElMessageBox.confirm(`确认收货子单 ${batch.code} ？确认后会写入库存，不可撤销。`, '确认收货', { type: 'warning' })
  } catch { return }
  batch._confirming = true
  try {
    // 先自动保存
    const payload = {
      lines: (batch.lines || []).filter(l => l.productId).map(l => ({
        productId: l.productId, poLineId: l.poLineId, poLineSeq: l.poLineSeq,
        receiptLineNo: l.receiptLineNo, qty: l.qty, batchNo: l.batchNo, serialNos: l.serialNos
      }))
    }
    await request({ url: `/api/receipt-batches/${batch.id}`, method: 'put', data: payload })
    await request({ url: `/api/receipt-batches/${batch.id}/confirm`, method: 'post' })
    ElMessage.success('收货已确认，库存已入库（待检 Q）')
    await loadReceipt(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally {
    batch._confirming = false
  }
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
    await request({ url: `/api/receipt-batches/${cancelTargetBatch.value.id}/cancel`, method: 'post', data: { reason: cancelBatchReason.value } })
    ElMessage.success('本次收货已取消')
    cancelBatchVisible.value = false
    await loadReceipt(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally {
    cancelBatchLoading.value = false
  }
}

function openCancelRemainingDialog() {
  cancelRemainingReason.value = ''
  cancelRemainingVisible.value = true
}
async function submitCancelRemaining() {
  if (!cancelRemainingReason.value.trim()) { ElMessage.warning('请填写取消原因'); return }
  cancelRemainingLoading.value = true
  try {
    await actionResource('/api/receipts', receiptId.value, '/cancel-remaining', 'post', { reason: cancelRemainingReason.value })
    ElMessage.success('剩余收货已取消')
    cancelRemainingVisible.value = false
    await loadReceipt(); await loadOpLogs()
  } catch (e) { /* 拦截器已提示 */ } finally {
    cancelRemainingLoading.value = false
  }
}
</script>

<style scoped>
.page-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.page-toolbar .spacer { flex: 1; }
.batch-card { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; margin-top: 12px; }
.batch-head { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.batch-code { font-weight: 600; font-size: 15px; color: #303133; }
.batch-meta { color: #909399; font-size: 12px; }
.batch-actions { margin-top: 10px; display: flex; gap: 8px; }
.serial-preview { font-size: 12px; color: #606266; white-space: pre-wrap; line-height: 1.4; }
.serial-cell { display: flex; flex-direction: column; gap: 4px; }
.serial-cell .el-button { align-self: flex-start; padding: 0; }
.serial-warn :deep(.el-textarea__inner) { border-color: #e6a23c; }
</style>