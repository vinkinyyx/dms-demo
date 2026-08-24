<template>
  <div class="m-order-create">
    <van-nav-bar title="下销售订单" left-arrow @click-left="$router.back()" />

    <van-cell-group inset title="基本信息" style="margin-top:10px">
      <van-field
        readonly clickable is-link
        label="经销商" required
        :model-value="form.dealerId ? (dealerMap[form.dealerId] || form.dealerId) : ''"
        placeholder="选择经销商（您负责的）"
        @click="openDealerPicker"
      />
      <van-field
        readonly clickable is-link
        label="仓库"
        :model-value="form.warehouseId ? (warehouseMap[form.warehouseId] || form.warehouseId) : ''"
        placeholder="选择发货仓库（可选）"
        @click="openWarehousePicker"
      />
      <van-field
        readonly clickable is-link
        label="订单类型"
        :model-value="orderTypeLabel"
        placeholder="选择订单类型"
        @click="openOrderTypePicker"
      />
      <van-field
        readonly clickable is-link
        label="订单日期"
        :model-value="form.orderDate"
        placeholder="选择订单日期"
        @click="openDatePicker = true"
      />
      <van-field
        v-model="form.remark"
        label="备注"
        type="textarea" rows="2" autosize
        placeholder="选填"
      />
    </van-cell-group>

    <van-cell-group inset title="产品明细" style="margin-top:10px">
      <div v-for="(line, idx) in lines" :key="idx" class="line-card">
        <div class="line-head">
          <span class="line-no">#{{ idx + 1 }}</span>
          <van-button size="mini" type="danger" plain @click="removeLine(idx)" v-if="lines.length > 1">删除</van-button>
        </div>
        <van-field
          readonly clickable is-link
          label="产品"
          :model-value="line.productId ? productLabel(line) : ''"
          :placeholder="form.dealerId ? '选择产品（按授权过滤）' : '请先选择经销商'"
          :disabled="!form.dealerId"
          @click="openProductPicker(idx)"
        />
        <div v-if="line.productId" class="line-row">
          <div class="line-cell">
            <div class="cell-l">数量</div>
            <van-stepper v-model="line.qty" :min="1" input-width="50px" />
          </div>
          <div class="line-cell">
            <div class="cell-l">单价</div>
            <van-field v-model.number="line.unitPrice" type="number" placeholder="0.00" />
          </div>
          <div class="line-cell">
            <div class="cell-l">税率</div>
            <van-field v-model.number="line.taxRate" type="number" placeholder="0.13" />
          </div>
        </div>
        <div v-if="line.productId" class="line-sub">
          <span>小计 ¥ {{ ((line.qty || 0) * (line.unitPrice || 0) * (1 + (line.taxRate || 0))).toFixed(2) }}</span>
          <span v-if="line.stock != null">可用库存 {{ line.stock.totalQty || 0 }}</span>
        </div>
      </div>
      <div style="padding: 10px 16px;">
        <van-button block plain icon="plus" @click="addLine">添加产品</van-button>
      </div>
    </van-cell-group>

    <van-cell-group inset title="金额合计" style="margin-top:10px">
      <van-cell title="产品数量" :value="validLines.length + ' 项'" />
      <van-cell title="不含税金额" :value="'¥ ' + amountExcl.toFixed(2)" />
      <van-cell title="税额" :value="'¥ ' + amountTax.toFixed(2)" />
      <van-cell title-class="total-title" value-class="total-value" title="含税合计" :value="'¥ ' + amountIncl.toFixed(2)" />
    </van-cell-group>

    <div class="submit-bar">
      <van-button block round type="primary" :loading="submitting" @click="submit">提交订单</van-button>
    </div>

    <!-- 选择器弹层 -->
    <van-popup v-model:show="showDealerPicker" position="bottom" round>
      <van-picker
        :columns="dealerColumns" :model-value="[form.dealerId || '']"
        @confirm="onDealerConfirm" @cancel="showDealerPicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showWarehousePicker" position="bottom" round>
      <van-picker
        :columns="warehouseColumns" :model-value="[form.warehouseId || '']"
        @confirm="onWarehouseConfirm" @cancel="showWarehousePicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showOrderTypePicker" position="bottom" round>
      <van-picker
        :columns="orderTypeColumns" :model-value="[form.orderType]"
        @confirm="onOrderTypeConfirm" @cancel="showOrderTypePicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showProductPicker" position="bottom" round :style="{ height: '70%' }">
      <van-nav-bar title="选择产品" :left-arrow="false">
        <template #right>
          <van-button size="small" type="primary" @click="showProductPicker = false">关闭</van-button>
        </template>
      </van-nav-bar>
      <van-search v-model="productKeyword" placeholder="搜索编码 / 名称 / 规格" />
      <van-list :loading="loadingProducts" :finished="finishedProducts" :finished-text="productOptions.length ? '没有更多了' : '该经销商暂无授权产品'" @load="loadProducts">
        <van-cell
          v-for="p in productOptions" :key="p.value"
          :title="p.label" :label="(p.spec || '') + (p.unit ? ' / ' + p.unit : '')"
          clickable @click="onProductPick(p)"
        />
      </van-list>
    </van-popup>
    <van-popup v-model:show="openDatePicker" position="bottom" round>
      <van-date-picker
        :model-value="datePickerValue"
        @confirm="onDateConfirm" @cancel="openDatePicker = false" title="选择订单日期"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { lookup } from '@/api/crud'
import { createOrder } from '@/api/order'

const router = useRouter()

const form = reactive({
  dealerId: '',
  warehouseId: '',
  orderType: 'NORMAL',
  orderDate: new Date().toISOString().split('T')[0],
  remark: ''
})
const lines = ref([{ productId: '', productName: '', spec: '', qty: 1, unitPrice: 0, taxRate: 0.13, stock: null }])

const dealerOptions = ref([])
const dealerMap = computed(() => Object.fromEntries(dealerOptions.value.map(d => [d.value, d.label])))
const dealerColumns = computed(() => [
  { text: '请选择', value: '' },
  ...dealerOptions.value.map(d => ({ text: d.label, value: d.value }))
])

const warehouseOptions = ref([])
const warehouseMap = computed(() => Object.fromEntries(warehouseOptions.value.map(w => [w.value, w.label])))
const warehouseColumns = computed(() => [
  { text: '请选择（可选）', value: '' },
  ...warehouseOptions.value.map(w => ({ text: w.label, value: w.value }))
])

const orderTypeOptions = [
  { value: 'NORMAL', label: '常规' },
  { value: 'SHORTAGE', label: '缺货补料' },
  { value: 'CUSTOM', label: '定制' },
  { value: 'EMERGENCY', label: '应急' }
]
const orderTypeColumns = orderTypeOptions.map(o => ({ text: o.label, value: o.value }))
const orderTypeLabel = computed(() => (orderTypeOptions.find(o => o.value === form.orderType) || {}).label || '-')

const productOptions = ref([])
const productKeyword = ref('')
const loadingProducts = ref(false)
const finishedProducts = ref(false)
let productPage = 1
let activeLineIdx = -1
const showProductPicker = ref(false)

const showDealerPicker = ref(false)
const showWarehousePicker = ref(false)
const showOrderTypePicker = ref(false)
const openDatePicker = ref(false)
const datePickerValue = ref(form.orderDate.split('-'))

const submitting = ref(false)

const validLines = computed(() => lines.value.filter(l => l.productId && l.qty > 0))
const amountExcl = computed(() => validLines.value.reduce((s, l) => s + (l.qty || 0) * (l.unitPrice || 0), 0))
const amountTax  = computed(() => validLines.value.reduce((s, l) => s + (l.qty || 0) * (l.unitPrice || 0) * (l.taxRate || 0), 0))
const amountIncl = computed(() => amountExcl.value + amountTax.value)

function productLabel(line) {
  const t = line.productName || ''
  const spec = line.spec ? ' / ' + line.spec : ''
  return t + spec
}

async function loadDealers() {
  try {
    const r = await lookup('dealers', { limit: 100 })
    dealerOptions.value = (r.data || []).map(d => ({ value: d.id, label: (d.code ? d.code + ' · ' : '') + d.name }))
  } catch (e) { /* ignore */ }
}
async function loadWarehouses() {
  try {
    const r = await lookup('warehouses', { limit: 100 })
    warehouseOptions.value = (r.data || []).map(w => ({ value: w.id, label: w.name }))
  } catch (e) { /* ignore */ }
}

function openDealerPicker() { showDealerPicker.value = true }
function openWarehousePicker() { showWarehousePicker.value = true }
function openOrderTypePicker() { showOrderTypePicker.value = true }
function onDealerConfirm({ selectedOptions }) {
  form.dealerId = selectedOptions[0]?.value || ''
  showDealerPicker.value = false
  // 选了经销商后，清空产品行，让用户重新选择以走授权过滤
  lines.value = [{ productId: '', productName: '', spec: '', qty: 1, unitPrice: 0, taxRate: 0.13, stock: null }]
}
function onWarehouseConfirm({ selectedOptions }) {
  form.warehouseId = selectedOptions[0]?.value || ''
  showWarehousePicker.value = false
}
function onOrderTypeConfirm({ selectedOptions }) {
  form.orderType = selectedOptions[0]?.value || 'NORMAL'
  showOrderTypePicker.value = false
}
function onDateConfirm({ selectedValues }) {
  form.orderDate = selectedValues.join('-')
  datePickerValue.value = selectedValues
  openDatePicker.value = false
}

function openProductPicker(idx) {
  if (!form.dealerId) {
    showToast('请先选择经销商')
    return
  }
  activeLineIdx = idx
  productOptions.value = []
  productPage = 1
  finishedProducts.value = false
  showProductPicker.value = true
  loadProducts()
}

async function loadProducts() {
  loadingProducts.value = true
  try {
    const r = await lookup('products', {
      page: productPage, size: 30,
      keyword: productKeyword.value || undefined,
      dealerId: form.dealerId
    })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    productOptions.value.push(...list.map(p => ({
      value: p.id,
      label: (p.code ? p.code + ' · ' : '') + (p.name || p.nameCn || ''),
      spec: p.spec || '',
      price: p.priceRetail || p.price || 0,
      isSerialManaged: p.isSerialManaged || false
    })))
    productPage++
    if (list.length < 30) finishedProducts.value = true
  } catch (e) { finishedProducts.value = true } finally { loadingProducts.value = false }
}

async function onProductPick(p) {
  if (activeLineIdx < 0) return
  const line = lines.value[activeLineIdx]
  line.productId = p.value
  line.productName = p.label
  line.spec = p.spec
  if (!line.unitPrice) line.unitPrice = Number(p.price || 0)
  // 拉库存
  try {
    const r = await lookup('products', { dealerId: form.dealerId, keyword: p.label.split('·')[0]?.trim() || '' })
    // inventoryByProduct 是后端真实接口，这里用 lookup 简化
  } catch (e) { /* ignore */ }
  showProductPicker.value = false
  activeLineIdx = -1
}

function addLine() {
  lines.value.push({ productId: '', productName: '', spec: '', qty: 1, unitPrice: 0, taxRate: 0.13, stock: null })
}
function removeLine(i) { lines.value.splice(i, 1) }

async function submit() {
  if (!form.dealerId) { showToast('请选择经销商'); return }
  if (!validLines.value.length) { showToast('请至少添加一项有效产品'); return }
  if (validLines.value.some(l => !(l.unitPrice >= 0))) { showToast('请填写单价'); return }

  try {
    await showConfirmDialog({ title: '确认提交', message: `合计 ¥${amountIncl.value.toFixed(2)}，确认提交？` })
  } catch (e) { return }

  submitting.value = true
  try {
    const payload = {
      dealerId: Number(form.dealerId),
      orderType: form.orderType,
      orderDate: form.orderDate,
      remark: form.remark,
      lines: validLines.value.map((l, i) => ({
        seq: i + 1,
        productId: l.productId,
        qty: l.qty,
        unitPrice: l.unitPrice,
        taxRate: l.taxRate
      }))
    }
    if (form.warehouseId) payload.warehouseId = Number(form.warehouseId)
    const res = await createOrder(payload)
    showToast.success('提交成功')
    const newId = res?.data?.id
    if (newId) router.replace('/mobile/orders/' + newId)
    else router.replace('/mobile/orders')
  } catch (e) {
    showToast.fail(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadDealers()
  loadWarehouses()
})
</script>

<style scoped>
.m-order-create { padding-bottom: 80px; }
.line-card { padding: 10px 16px; border-bottom: 1px solid var(--dms-gray-100); }
.line-card:last-child { border-bottom: 0; }
.line-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.line-no { font-size: 13px; color: var(--dms-text-4); }
.line-row { display: flex; gap: 8px; margin-top: 6px; }
.line-cell { flex: 1; }
.cell-l { font-size: 12px; color: var(--dms-text-3); margin-bottom: 4px; }
.line-sub { display: flex; justify-content: space-between; font-size: 12px; color: var(--dms-text-4); margin-top: 6px; }
.submit-bar { position: fixed; bottom: 0; left: 0; right: 0; padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom)); background: var(--dms-bg-container); box-shadow: 0 -2px 8px rgba(0,0,0,.05); z-index: 10; }
.m-order-create :deep(.van-field__label) { width: 60px !important; flex: none; font-size: 13px; }
.m-order-create .line-card :deep(.van-field__label) { width: 50px !important; }
.m-order-create :deep(.van-field__value) { flex: 1 1 auto !important; min-width: 0; overflow: hidden; }
.m-order-create :deep(.van-field__control) { white-space: nowrap !important; overflow: hidden !important; text-overflow: ellipsis !important; font-size: 11px !important; }
.m-order-create :deep(textarea.van-field__control) { white-space: pre-wrap !important; word-break: break-all; overflow: auto !important; font-size: 13px !important; }
.total-title { font-weight: 600; }
.total-value { color: var(--dms-color-danger); font-weight: 700; }
</style>