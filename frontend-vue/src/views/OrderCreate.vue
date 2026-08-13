<template>
  <div class="order-create">
    <div class="main-content">
      <el-row :gutter="20">
        <el-col :span="10" class="left-col">
          <el-card class="info-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <el-icon><Document /></el-icon>
                <span>基本信息</span>
              </div>
            </template>
            <el-form :model="form" label-width="100px" size="large">
              <el-form-item :label="cfg.partyLabel" required>
                <ResourcePicker v-model="form.partyId" :resource="cfg.partyResource"
                  :placeholder="'请选择' + cfg.partyLabel" @pick="onPartyPick" />
              </el-form-item>
              <el-form-item label="订单类型" required>
                <el-select v-model="form.orderType" style="width:100%">
                  <el-option v-for="o in cfg.orderTypes" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="入库仓库" v-if="mode === 'purchase'" required>
                <ResourcePicker v-model="form.warehouseId" resource="warehouses" placeholder="选择入库仓库" />
              </el-form-item>
              <el-form-item v-else label="发货仓库">
                <ResourcePicker v-model="form.warehouseId" resource="warehouses" placeholder="选择发货仓库" />
              </el-form-item>
              <el-form-item label="下单日期">
                <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
              <el-form-item label="期望到货">
                <el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
              <el-form-item label="订单备注">
                <el-input v-model="form.remark" type="textarea" :rows="4" placeholder="请输入订单备注信息" />
              </el-form-item>
            </el-form>
          </el-card>

          <el-card class="overview-card" shadow="hover" v-if="overview">
            <template #header>
              <div class="card-header">
                <el-icon><OfficeBuilding /></el-icon>
                <span>{{ mode === 'sales' ? '经销商' : '供应商' }}概览</span>
              </div>
            </template>
            <div class="ov">
              <div class="ov-row"><span class="ov-label">编码</span><span>{{ overview.dealerCode || overview.code || '-' }}</span></div>
              <div class="ov-row"><span class="ov-label">名称</span><span>{{ overview.dealerName || overview.name || '-' }}</span></div>
              <div v-if="mode === 'sales'" class="ov-row"><span class="ov-label">级别</span><span>{{ overview.level || '-' }}</span></div>
              <div v-if="mode === 'sales'" class="ov-row"><span class="ov-label">GSP资质</span><span>{{ overview.gspStatus || '-' }}</span></div>
              <el-divider v-if="mode === 'sales' && overview.creditLimit" content-position="left" style="margin:12px 0">授信额度</el-divider>
              <div v-if="mode === 'sales' && overview.creditLimit" class="ov-row"><span class="ov-label">总额度</span><span>¥ {{ Number(overview.creditLimit || 0).toLocaleString() }}</span></div>
              <div v-if="mode === 'sales' && overview.creditLimit" class="ov-row"><span class="ov-label">已使用</span><span>¥ {{ Number(overview.pendingAmount || 0).toLocaleString() }}</span></div>
              <div v-if="mode === 'sales' && overview.creditLimit">
                <el-progress :percentage="creditUsedPct" :status="creditUsedPct > 90 ? 'exception' : ''" :stroke-width="8" />
                <div class="ov-row" style="margin-top:8px"><span class="ov-label">剩余额度</span><span class="ov-highlight">¥ {{ Number(overview.remainingCredit || 0).toLocaleString() }}</span></div>
              </div>
            </div>
          </el-card>

          <el-card class="summary-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <el-icon><Calculator /></el-icon>
                <span>金额汇总</span>
              </div>
            </template>
            <div class="amount-preview">
              <div>产品数量：<b>{{ validLines.length }}</b></div>
              <div>不含税金额：<b>¥ {{ amountExclTax.toFixed(2) }}</b></div>
              <div>税额：<b>¥ {{ amountTax.toFixed(2) }}</b></div>
              <div class="amount-total">含税金额：<b>¥ {{ amountInclTax.toFixed(2) }}</b></div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="14" class="right-col">
          <el-card class="products-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <el-icon><Goods /></el-icon>
                <span>产品明细</span>
                <el-button size="small" type="primary" style="margin-left:auto" @click="addLine">
                  <el-icon><Plus /></el-icon>添加产品
                </el-button>
              </div>
            </template>
            <el-table :data="lines" border size="small" class="products-table">
              <el-table-column label="产品" min-width="200">
                <template #default="{ row }">
                  <ResourcePicker v-model="row.productId" resource="products" placeholder="选择产品" @pick="(p) => onProductPick(row, p)" />
                </template>
              </el-table-column>
              <el-table-column label="可用库存" width="120">
                <template #default="{ row }">
                  <span v-if="row.stock" :class="{ 'stock-warn': row.qty > row.stock.totalQty }">
                    可用 {{ row.stock.totalQty }}
                    <span v-if="row.stock.expiringQty > 0" class="stock-exp">·临期 {{ row.stock.expiringQty }}</span>
                  </span>
                  <span v-else class="stock-none">—</span>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="100">
                <template #default="{ row }">
                  <el-input-number v-model="row.qty" :min="1" :controls="false" style="width:100%" @change="calc" />
                </template>
              </el-table-column>
              <el-table-column label="单价" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :controls="false" style="width:100%" @change="calc" />
                </template>
              </el-table-column>
              <el-table-column label="税率" width="80">
                <template #default="{ row }">
                  <el-input-number v-model="row.taxRate" :min="0" :max="1" :step="0.01" :controls="false" style="width:100%" @change="calc" />
                </template>
              </el-table-column>
              <el-table-column label="小计" width="110">
                <template #default="{ row }">¥ {{ ((row.qty || 0) * (row.unitPrice || 0) * (1 + (row.taxRate || 0))).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ $index }">
                  <el-button size="small" type="danger" link @click="removeLine($index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!lines.length" class="empty-state">
              <el-empty description="暂无产品，请点击上方添加产品" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div class="bottom-bar">
      <div class="bar-content">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>取消
        </el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          <el-icon><Check /></el-icon>保存订单
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import ResourcePicker from '@/components/ResourcePicker.vue'
import { inventoryByProduct, dealerOverview, createOrder, createPurchaseOrder, supplierOverview } from '@/api/order'

const router = useRouter()
const route = useRoute()
const mode = ref('sales')
const submitting = ref(false)
const form = reactive({ partyId: '', orderType: 'NORMAL', warehouseId: '', orderDate: new Date().toISOString().split('T')[0], expectedDate: '', remark: '' })
const lines = ref([{ productId: '', productName: '', qty: 1, unitPrice: 0, taxRate: 0.13, stock: null }])
const overview = ref(null)
const partyName = ref('')

const MODE_CFG = {
  sales: { partyLabel: '经销商', partyResource: 'dealers', orderTypes: [{ value: 'NORMAL', label: '常规订单' }, { value: 'SHORTAGE', label: '紧急补货' }, { value: 'CUSTOM', label: '定制订单' }, { value: 'EMERGENCY', label: '应急订单' }] },
  purchase: { partyLabel: '供应商', partyResource: 'suppliers', orderTypes: [{ value: 'NORMAL', label: '常规采购' }, { value: 'URGENT', label: '紧急采购' }] }
}
const cfg = computed(() => MODE_CFG[mode.value])
const validLines = computed(() => lines.value.filter((l) => l.productId && l.qty > 0))
const amountExclTax = computed(() => validLines.value.reduce((s, l) => s + (l.qty || 0) * (l.unitPrice || 0), 0))
const amountTax = computed(() => validLines.value.reduce((s, l) => s + (l.qty || 0) * (l.unitPrice || 0) * (l.taxRate || 0), 0))
const amountInclTax = computed(() => amountExclTax.value + amountTax.value)
const creditUsedPct = computed(() => {
  if (!overview.value || !overview.value.creditLimit) return 0
  return Math.min(100, Math.round((Number(overview.value.pendingAmount || 0) / Number(overview.value.creditLimit)) * 100))
})

function resetForm() {
  form.partyId = ''
  form.orderType = 'NORMAL'
  form.warehouseId = ''
  form.orderDate = new Date().toISOString().split('T')[0]
  form.expectedDate = ''
  form.remark = ''
  lines.value = [{ productId: '', productName: '', qty: 1, unitPrice: 0, taxRate: 0.13, stock: null }]
  overview.value = null
  partyName.value = ''
  submitting.value = false
}

function updateMode() {
  const path = route.path
  if (path.includes('/purchase')) {
    mode.value = 'purchase'
  } else {
    mode.value = 'sales'
  }
  resetForm()
}

onMounted(() => { updateMode() })
watch(() => route.path, updateMode)

function calc() {

}

function addLine() {
  lines.value.push({ productId: '', productName: '', qty: 1, unitPrice: 0, taxRate: 0.13, stock: null })
}

function removeLine(i) {
  lines.value.splice(i, 1)
}

async function onPartyPick(p) {
  overview.value = null
  partyName.value = ''
  if (p && p.value) {
    partyName.value = p.label
    try {
      if (mode.value === 'sales') {
        overview.value = (await dealerOverview(p.value)).data
      } else {
        overview.value = (await supplierOverview(p.value)).data
      }
    } catch (e) { /* ignore */ }
  }
}

async function onProductPick(row, p) {
  if (!p || !p.value) { row.stock = null; row.productName = ''; return }
  row.productName = p.label
  try {
    const data = (await inventoryByProduct(p.value)).data || {}
    row.stock = data
    if (data.productInfo) {
      if (!row.unitPrice) row.unitPrice = Number(data.productInfo.currentPrice || 0)
      if (data.productInfo.taxRate != null) row.taxRate = Number(data.productInfo.taxRate)
    }
  } catch (e) { /* ignore */ }
}

async function submit() {
  if (!form.partyId) { ElMessage.warning('请选择' + cfg.value.partyLabel); return }
  if (mode.value === 'purchase' && !form.warehouseId) { ElMessage.warning('请选择入库仓库'); return }
  if (!validLines.value.length) { ElMessage.warning('请至少添加一条有效明细'); return }
  submitting.value = true
  try {
    const linesPayload = validLines.value.map((l, i) => ({ seq: i + 1, productId: l.productId, qty: l.qty, unitPrice: l.unitPrice, taxRate: l.taxRate }))
    if (mode.value === 'sales') {
      await createOrder({ dealerId: form.partyId, warehouseId: form.warehouseId || undefined, orderType: form.orderType, orderDate: form.orderDate, expectedDate: form.expectedDate || undefined, remark: form.remark, lines: linesPayload })
    } else {
      await createPurchaseOrder({ supplierId: form.partyId, warehouseId: form.warehouseId, orderType: form.orderType, orderDate: form.orderDate, expectedDate: form.expectedDate || undefined, remark: form.remark, lines: linesPayload })
    }
    ElMessage.success('订单创建成功')
    router.push('/m/' + (mode.value === 'sales' ? 'orders' : 'purchase-orders'))
  } catch (e) { /* 拦截器已提示 */ } finally { submitting.value = false }
}
</script>

<style scoped>
.order-create {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 84px);
  padding-bottom: 80px;
}

.main-content {
  flex: 1;
  padding: 16px 0;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.info-card,
.overview-card,
.summary-card,
.products-card {
  border-radius: 12px;
  margin-bottom: 16px;
}

.info-card :deep(.el-card__body) {
  padding: 20px;
}

.overview-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.summary-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.products-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.left-col {
  display: flex;
  flex-direction: column;
}

.right-col {
  display: flex;
  flex-direction: column;
}

.products-table {
  margin-top: 8px;
}

.empty-state {
  padding: 40px 0;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  padding: 16px 0;
  z-index: 100;
}

.bar-content {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-right: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.ov-row {
  display: flex;
  justify-content: space-between;
  margin: 6px 0;
  font-size: 13px;
}

.ov-label {
  color: var(--dms-text-4);
  width: 70px;
  flex-shrink: 0;
}

.ov-highlight {
  color: var(--dms-color-success);
  font-weight: 600;
}

.amount-preview {
  font-size: 14px;
}

.amount-preview div {
  margin: 8px 0;
}

.amount-preview b {
  color: var(--dms-text-3);
}

.amount-preview .amount-total {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--dms-border-2);
  font-size: 16px;
}

.amount-preview .amount-total b {
  color: var(--dms-color-danger);
}

.stock-warn {
  color: var(--dms-color-danger);
  font-weight: 600;
}

.stock-exp {
  color: var(--dms-color-warning);
}

.stock-none {
  color: var(--dms-text-placeholder);
}
</style>
