<template>
  <div class="m-order-create">
    <van-nav-bar title="下销售订单" left-arrow @click-left="$router.back()" />

    <van-cell-group inset title="基本信息" style="margin-top:10px">
      <van-field
        readonly clickable is-link
        label="经销商" required
        :model-value="form.dealerId ? (dealerMap[form.dealerId] || form.dealerId) : ''"
        placeholder="选择经销商"
        @click="openDealerPicker"
      />
      <van-field
        readonly clickable is-link
        label="订单类型"
        :model-value="orderTypeLabel"
        @click="showOrderTypePicker = true"
      />
      <van-field
        readonly clickable is-link
        label="期望日期"
        :model-value="form.expectedDate || '不指定'"
        @click="showDatePicker = true"
      />
      <van-field
        readonly clickable is-link
        label="整单折扣"
        :model-value="headerDiscountLabel"
        @click="openHeaderDiscount"
      />
      <van-field
        v-model="form.remark"
        label="备注"
        type="textarea" rows="2" autosize
        placeholder="选填"
      />
    </van-cell-group>

    <van-cell-group inset title="订单明细" style="margin-top:10px">
      <div v-for="(line, idx) in form.lines" :key="line.tempId" class="line-card" :class="{ 'is-child': line.lineLevel==='CHILD', 'is-parent': line.lineLevel==='PARENT' }">
        <div class="line-head">
          <span class="line-no">
            <template v-if="line.lineLevel==='CHILD'">└─ 子件</template>
            <template v-else-if="line.lineLevel==='PARENT'">#{{ idx+1 }} BOM母件</template>
            <template v-else>#{{ idx+1 }}</template>
            <van-tag v-if="line.isGift" plain type="danger" size="mini">赠品</van-tag>
          </span>
          <van-button v-if="canDelete(line)" size="mini" type="danger" plain @click="removeLine(idx)">删除</van-button>
        </div>
        <van-field
          v-if="canPick(line)"
          readonly clickable is-link
          label="产品"
          :model-value="line.productId ? productLabel(line) : ''"
          :placeholder="form.dealerId ? '选择产品' : '请先选择经销商'"
          :disabled="!form.dealerId"
          @click="openProductPicker(idx)"
        />
        <div v-else class="readonly-product">
          <span class="rp-code">{{ line.productCode }}</span>
          <span class="rp-name">{{ line.productName }}</span>
          <span v-if="line.productSpec" class="rp-spec">/ {{ line.productSpec }}</span>
        </div>
        <div v-if="line.productId" class="line-row">
          <div class="line-cell">
            <div class="cell-l">数量</div>
            <van-stepper v-model="line.qty" :min="1" integer input-width="48px" :disabled="!canEditQty(line)" @change="onQtyChange(line)" />
          </div>
          <div class="line-cell">
            <div class="cell-l">含税单价</div>
            <div class="ro-val">¥ {{ num(line.standardPriceInclTax).toFixed(2) }}</div>
          </div>
        </div>
        <div v-if="line.productId && line.lineLevel!=='PARENT' && !line.isGift" class="line-row">
          <div class="line-cell">
            <div class="cell-l">行折扣类型</div>
            <van-field readonly clickable is-link :model-value="lineDiscountTypeLabel(line)" placeholder="无" @click="openLineDiscount(line, 'type')" />
          </div>
          <div class="line-cell" v-if="line.lineDiscountType">
            <div class="cell-l">{{ line.lineDiscountType==='PERCENT' ? '折扣率(%)' : '折扣金额' }}</div>
            <van-field v-model.number="line.lineDiscountValue" type="number" placeholder="0.00" @update:model-value="schedulePreview" />
          </div>
        </div>
        <div v-if="line.productId" class="line-sub">
          <span v-if="line.lineLevel==='PARENT'">标准金额 ¥ {{ standardAmount(line).toFixed(2) }}</span>
          <span v-else>最终金额 ¥ {{ num(line.finalAmount).toFixed(2) }}</span>
          <span v-if="line.isGift" class="muted">赠品</span>
        </div>
      </div>
      <div style="padding:10px 16px;">
        <van-button block plain icon="plus" :disabled="!form.dealerId" @click="addLine">添加产品</van-button>
      </div>
    </van-cell-group>

    <van-cell-group inset title="金额合计" style="margin-top:10px">
      <van-cell title="产品项数" :value="editableRoots.length + ' 项'" />
      <van-cell title="不含税金额" :value="'¥ ' + num(form.amountExclTax).toFixed(2)" />
      <van-cell title="税额" :value="'¥ ' + num(form.taxAmount).toFixed(2)" />
      <van-cell title="整单折扣" :value="'- ¥ ' + num(form.discountAmount).toFixed(2)" />
      <van-cell title-class="total-title" value-class="total-value" title="含税合计" :value="'¥ ' + num(form.finalAmount).toFixed(2)" />
    </van-cell-group>

    <div v-if="error" class="err-banner">{{ error }}</div>

    <div class="submit-bar">
      <van-button block round type="primary" :loading="submitting" @click="submitOrder">提交订单</van-button>
    </div>

    <van-popup v-model:show="showDealerPicker" position="bottom" round>
      <van-picker
        :columns="dealerColumns" :model-value="[form.dealerId || '']"
        @confirm="onDealerConfirm" @cancel="showDealerPicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showOrderTypePicker" position="bottom" round>
      <van-picker
        :columns="orderTypeColumns" :model-value="[form.orderType]"
        @confirm="onOrderTypeConfirm" @cancel="showOrderTypePicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        :model-value="datePickerValue"
        @confirm="onDateConfirm" @cancel="showDatePicker = false" title="选择期望日期"
      />
    </van-popup>
    <van-popup v-model:show="showHeaderDiscount" position="bottom" round>
      <van-cell-group inset>
        <van-field label="折扣类型">
          <template #input>
            <van-radio-group v-model="headerDiscountDraft.type" direction="horizontal">
              <van-radio name="">无</van-radio>
              <van-radio name="PERCENT">百分比</van-radio>
              <van-radio name="AMOUNT">固定金额</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-if="headerDiscountDraft.type" :label="headerDiscountDraft.type==='PERCENT' ? '折扣率(%)' : '折扣金额'" v-model.number="headerDiscountDraft.value" type="number" placeholder="0.00" />
        <div style="padding:12px 16px;display:flex;gap:8px">
          <van-button block plain @click="showHeaderDiscount=false">取消</van-button>
          <van-button block type="primary" @click="confirmHeaderDiscount">确定</van-button>
        </div>
      </van-cell-group>
    </van-popup>
    <van-popup v-model:show="showLineDiscountPicker" position="bottom" round>
      <van-picker
        :columns="lineDiscountTypeColumns" :model-value="[activeLine?.lineDiscountType || '']"
        @confirm="onLineDiscountTypeConfirm" @cancel="showLineDiscountPicker=false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showProductPicker" position="bottom" round :style="{ height: '70%' }">
      <van-nav-bar title="选择产品" :left-arrow="false">
        <template #right>
          <van-button size="small" type="primary" @click="showProductPicker=false">关闭</van-button>
        </template>
      </van-nav-bar>
      <van-search v-model="productKeyword" placeholder="搜索编码 / 名称 / 规格" @search="reloadProducts" />
      <van-list :loading="loadingProducts" :finished="finishedProducts" :finished-text="productOptions.length ? '没有更多了' : '暂无产品'" @load="loadProducts">
        <van-cell
          v-for="p in productOptions" :key="p.value"
          :title="p.label" :label="(p.spec || '') + (p.unit ? ' / ' + p.unit : '')"
          clickable @click="onProductPick(p)"
        />
      </van-list>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { lookup } from '@/api/crud'
import { createOrder, previewOrder, submitSalesOrder } from '@/api/order'
import request from '@/utils/request'

const router = useRouter()

let seq = 1
const num = v => Number(v || 0)
function todayStr() {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

function makeLine(p = {}) {
  return {
    tempId: 't' + (seq++),
    productId: p.id || p.productId || null,
    productCode: p.code || p.productCode || '',
    productName: p.nameCn || p.name || p.productName || '',
    productSpec: p.spec || p.productSpec || '',
    unit: p.unit || p.unitType || 'EA',
    qty: Number(p.qty || 1),
    standardPriceInclTax: num(p.currentPrice ?? p.price ?? p.standardPriceInclTax),
    taxRate: num(p.taxRate),
    standardAmount: num(p.standardAmount),
    lineDiscountType: p.lineDiscountType || '',
    lineDiscountValue: num(p.lineDiscountValue),
    promoDiscountAmount: num(p.promoDiscountAmount),
    finalAmount: num(p.finalAmount),
    amountExclTax: num(p.amountExclTax),
    taxAmount: num(p.taxAmount),
    lineLevel: p.lineLevel || 'NORMAL',
    isBom: !!(p.isBundle || p.isBom),
    isGift: !!p.isGift,
    bomVersion: p.bomVersion || null,
    bomGroupNo: p.bomGroupNo || null,
    componentQty: num(p.componentQty),
    children: Array.isArray(p.children) ? p.children : []
  }
}

const form = reactive({
  id: null,
  status: 'DRAFT',
  dealerId: null,
  orderType: 'SALES',
  expectedDate: '',
  headerDiscountType: '',
  headerDiscountValue: 0,
  remark: '',
  lines: [],
  amountInclTax: 0,
  discountAmount: 0,
  finalAmount: 0,
  taxAmount: 0,
  amountExclTax: 0
})

const error = ref('')
const submitting = ref(false)
const showDealerPicker = ref(false)
const showOrderTypePicker = ref(false)
const showDatePicker = ref(false)
const showHeaderDiscount = ref(false)
const showLineDiscountPicker = ref(false)
const showProductPicker = ref(false)

const dealerOptions = ref([])
const dealerMap = computed(() => Object.fromEntries(dealerOptions.value.map(d => [d.value, d.label])))
const dealerColumns = computed(() => [
  { text: '请选择', value: '' },
  ...dealerOptions.value.map(d => ({ text: d.label, value: d.value }))
])

const orderTypeOptions = [
  { value: 'SALES', label: '销售订单' },
  { value: 'REPLENISHMENT', label: '补货订单' }
]
const orderTypeColumns = orderTypeOptions.map(o => ({ text: o.label, value: o.value }))
const orderTypeLabel = computed(() => (orderTypeOptions.find(o => o.value === form.orderType) || {}).label || '-')

const datePickerValue = ref(todayStr().split('-'))

const headerDiscountDraft = reactive({ type: '', value: 0 })
const headerDiscountLabel = computed(() => {
  if (!form.headerDiscountType) return '无'
  if (form.headerDiscountType === 'PERCENT') return `百分比 ${num(form.headerDiscountValue).toFixed(2)}%`
  return `固定金额 ¥${num(form.headerDiscountValue).toFixed(2)}`
})

const lineDiscountTypeColumns = [
  { text: '无', value: '' },
  { text: '比例(%)', value: 'PERCENT' },
  { text: '固定金额', value: 'AMOUNT' }
]
const activeLine = ref(null)

const productOptions = ref([])
const productKeyword = ref('')
const loadingProducts = ref(false)
const finishedProducts = ref(false)
let productPage = 1
let activeLineIdx = -1

const flatLines = computed(() => flatten(form.lines))
const editableRoots = computed(() => form.lines.filter(l => !l.isGift && l.lineLevel !== 'CHILD'))

function flatten(a) {
  return a.flatMap(x => [x, ...(Array.isArray(x.children) ? flatten(x.children) : [])])
}
function standardAmount(line) {
  if (line.lineLevel === 'PARENT') {
    return (line.children || []).reduce((s, c) => s + num(c.standardAmount), 0)
  }
  return num(line.standardAmount)
}
function productLabel(line) {
  const parts = [line.productCode, line.productName].filter(Boolean).join(' ')
  return line.productSpec ? `${parts} / ${line.productSpec}` : parts
}
function lineDiscountTypeLabel(line) {
  if (line.lineDiscountType === 'PERCENT') return `比例 ${num(line.lineDiscountValue).toFixed(2)}%`
  if (line.lineDiscountType === 'AMOUNT') return `金额 ¥${num(line.lineDiscountValue).toFixed(2)}`
  return '无'
}
function canPick(line) { return !line.isGift && line.lineLevel !== 'CHILD' }
function canEditQty(line) { return !line.isGift && line.lineLevel !== 'CHILD' }
function canDelete(line) { return !line.isGift && line.lineLevel !== 'CHILD' }

function openDealerPicker() { showDealerPicker.value = true }
function onDealerConfirm({ selectedOptions }) {
  form.dealerId = selectedOptions[0]?.value || null
  showDealerPicker.value = false
  form.lines = []
  schedulePreview()
}
function onOrderTypeConfirm({ selectedOptions }) {
  form.orderType = selectedOptions[0]?.value || 'SALES'
  showOrderTypePicker.value = false
  schedulePreview()
}
function onDateConfirm({ selectedValues }) {
  form.expectedDate = selectedValues.join('-')
  datePickerValue.value = selectedValues
  showDatePicker.value = false
}
function openHeaderDiscount() {
  headerDiscountDraft.type = form.headerDiscountType || ''
  headerDiscountDraft.value = num(form.headerDiscountValue)
  showHeaderDiscount.value = true
}
function confirmHeaderDiscount() {
  form.headerDiscountType = headerDiscountDraft.type || ''
  form.headerDiscountValue = headerDiscountDraft.type ? num(headerDiscountDraft.value) : 0
  showHeaderDiscount.value = false
  schedulePreview()
}
function openLineDiscount(line) {
  activeLine.value = line
  showLineDiscountPicker.value = true
}
function onLineDiscountTypeConfirm({ selectedOptions }) {
  if (activeLine.value) {
    activeLine.value.lineDiscountType = selectedOptions[0]?.value || ''
    if (!activeLine.value.lineDiscountType) activeLine.value.lineDiscountValue = 0
  }
  showLineDiscountPicker.value = false
  schedulePreview()
}

function addLine() {
  if (!form.dealerId) { showToast('请先选择经销商'); return }
  form.lines.push(makeLine())
}
function removeLine(i) {
  form.lines.splice(i, 1)
  schedulePreview()
}
function onQtyChange(row) {
  if (row.children) {
    row.children.forEach(c => {
      c.qty = num(row.qty) * num(c.componentQty)
    })
  }
  schedulePreview()
}

async function loadDealers() {
  try {
    const r = await lookup('dealers', { limit: 200 })
    dealerOptions.value = (r.data || []).map(d => ({ value: d.id, label: (d.code ? d.code + ' · ' : '') + d.name }))
  } catch (e) { /* ignore */ }
}

function openProductPicker(idx) {
  if (!form.dealerId) { showToast('请先选择经销商'); return }
  activeLineIdx = idx
  reloadProducts()
  showProductPicker.value = true
}
function reloadProducts() {
  productOptions.value = []
  productPage = 1
  finishedProducts.value = false
  loadProducts()
}
async function loadProducts() {
  if (!form.dealerId) { finishedProducts.value = true; return }
  loadingProducts.value = true
  try {
    const r = await lookup('products', {
      page: productPage, size: 30,
      keyword: productKeyword.value || undefined
    })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    productOptions.value.push(...list.map(p => ({
      value: p.id,
      label: (p.code ? p.code + ' · ' : '') + (p.nameCn || p.name || ''),
      code: p.code,
      name: p.nameCn || p.name,
      spec: p.spec || '',
      unit: p.unit || p.unitType || 'EA',
      isBom: !!(p.isBundle || p.isBom)
    })))
    productPage++
    if (list.length < 30) finishedProducts.value = true
  } catch (e) { finishedProducts.value = true } finally { loadingProducts.value = false }
}

function activeNow(p) {
  if (!p) return false
  if (String(p.status || '').toLowerCase() !== 'active') return false
  const now = Date.now()
  const from = p.validFrom ? new Date(p.validFrom).getTime() : null
  const to = p.validTo ? new Date(p.validTo).getTime() : null
  if (from && from > now) return false
  if (to && to < now) return false
  return true
}
function pickPrice(rows, partnerId) {
  const d = (rows || []).filter(activeNow).find(p => String(p.partnerId || '') === String(partnerId || ''))
  if (d) return d
  const g = (rows || []).filter(activeNow)
  return g.find(p => Number(p.partnerId) === 0) || g.find(p => p.partnerId == null || p.partnerId === '') || null
}

async function loadPrice(r, bomParentProductId) {
  if (!form.dealerId || !r.productId) return
  const base = { productId: r.productId, priceScope: 'SALE', includeComponents: true, size: 100 }
  const params = bomParentProductId
    ? { ...base, partnerType: 'DEALER', partnerId: form.dealerId, bomParentProductId, priceContext: 'BOM_COMPONENT' }
    : { ...base, partnerType: 'DEALER', partnerId: form.dealerId, priceContext: 'STANDALONE' }
  const res = await request({ url: '/api/product-prices', params }).catch(() => null)
  const list = res?.data?.list || res?.data?.records || (Array.isArray(res?.data) ? res.data : [])
  const p = bomParentProductId
    ? list.find(x => String(x.productId) === String(r.productId) && String(x.bomParentProductId || '') === String(bomParentProductId))
    : pickPrice(list.filter(x => x.priceContext === 'STANDALONE' || !x.priceContext), form.dealerId)
  if (p) {
    r.standardPriceInclTax = num(p.salesPrice)
    r.taxRate = num(p.taxRate)
    r.priceResolved = num(p.salesPrice) > 0
  } else {
    r.standardPriceInclTax = 0
    r.taxRate = 0
    r.priceResolved = false
  }
}

async function onProductPick(p) {
  if (activeLineIdx < 0) return
  const row = form.lines[activeLineIdx]
  Object.assign(row, makeLine({
    id: p.value,
    code: p.code,
    nameCn: p.name,
    spec: p.spec,
    unit: p.unit,
    qty: row.qty || 1,
    tempId: row.tempId
  }))
  showProductPicker.value = false
  const res = await request({ url: `/api/product-bundles/product/${p.value}/active` }).catch(() => null)
  const b = res?.data?.data ?? res?.data
  const nowActive = !b?.validFrom && !b?.validTo ? true : (() => {
    const now = new Date()
    const from = b.validFrom ? new Date(b.validFrom) : null
    const to = b.validTo ? new Date(b.validTo) : null
    return (!from || from <= now) && (!to || to >= now)
  })()
  if (!b?.lines?.length || b.versionStatus !== 'active' || !nowActive) {
    row.lineLevel = 'NORMAL'
    row.children = []
    row.isBom = false
    await loadPrice(row)
    schedulePreview()
    return
  }
  row.lineLevel = 'PARENT'
  row.isBom = true
  row.bomVersion = b.bomVersion
  row.standardPriceInclTax = 0
  row.standardAmount = 0
  row.finalAmount = 0
  row.children = b.lines
    .filter(c => String(c.childProductId) !== String(p.value))
    .map(c => makeLine({
      id: c.childProductId,
      code: c.childProductCode,
      nameCn: c.childProductName,
      spec: c.childProductSpec,
      qty: num(c.quantity) * num(row.qty),
      componentQty: num(c.quantity),
      lineLevel: 'CHILD'
    }))
  await Promise.all(row.children.map(c => loadPrice(c, p.value)))
  schedulePreview()
}

let previewTimer = null
let previewToken = 0
function schedulePreview() {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(runPreview, 300)
}
function buildPreviewPayload(applyPromotions) {
  return {
    applyPromotions: !!applyPromotions,
    orderType: form.orderType,
    dealerId: form.dealerId,
    expectedDate: form.expectedDate || null,
    headerDiscountType: form.headerDiscountType || null,
    headerDiscountValue: form.headerDiscountType ? num(form.headerDiscountValue) : null,
    lines: editableRoots.value.map(l => ({
      productId: l.productId,
      qty: num(l.qty),
      lineDiscountType: l.lineDiscountType || null,
      lineDiscountValue: l.lineDiscountType ? num(l.lineDiscountValue) : null,
      bomVersion: l.bomVersion || null,
      bomGroupNo: l.bomGroupNo || null,
      childDiscounts: (l.children || [])
        .filter(c => c.lineDiscountType)
        .map(c => ({
          productId: c.productId,
          lineDiscountType: c.lineDiscountType || null,
          lineDiscountValue: c.lineDiscountType ? num(c.lineDiscountValue) : 0
        }))
    }))
  }
}
function mapPreviewLine(l, current) {
  return {
    ...makeLine({ ...l, qty: num(l.qty) }),
    tempId: current?.tempId || 't' + (seq++),
    productCode: l.productCode || current?.productCode || '',
    productName: l.productName || current?.productName || '',
    productSpec: l.productSpec || current?.productSpec || '',
    lineLevel: l.lineLevel || 'NORMAL',
    isGift: l.isGift === true || l.gift === true,
    isBom: l.lineLevel === 'PARENT',
    bomVersion: l.bomVersion || current?.bomVersion || null,
    bomGroupNo: l.bomGroupNo || current?.bomGroupNo || null,
    componentQty: num(l.componentQty),
    children: []
  }
}
function applyPreview(data) {
  const roots = editableRoots.value
  let rootIndex = 0
  let currentParent = null
  const next = []
  ;(data.lines || []).forEach(l => {
    if (l.isGift || l.gift) { next.push(mapPreviewLine(l)); return }
    const row = mapPreviewLine(l, l.lineLevel === 'CHILD' ? null : roots[rootIndex++])
    if (l.lineLevel === 'PARENT') {
      currentParent = row
      next.push(row)
    } else if (l.lineLevel === 'CHILD' && currentParent && l.bomGroupNo === currentParent.bomGroupNo) {
      currentParent.children.push(row)
    } else {
      currentParent = null
      next.push(row)
    }
  })
  form.lines = next
  form.amountInclTax = num(data.amountInclTax)
  form.discountAmount = num(data.discountAmount)
  form.finalAmount = num(data.finalAmount)
  form.taxAmount = num(data.taxAmount)
  form.amountExclTax = num(data.amountExclTax)
}
async function runPreview() {
  if (!form.dealerId) {
    form.finalAmount = 0
    form.taxAmount = 0
    form.amountExclTax = 0
    form.discountAmount = 0
    return null
  }
  const token = ++previewToken
  try {
    const { data } = await previewOrder(buildPreviewPayload(false))
    if (token === previewToken) {
      applyPreview(data)
      return data
    }
    return null
  } catch (e) {
    if (token === previewToken) error.value = e?.response?.data?.message || e?.message || ''
    return null
  }
}
async function refreshPromotions() {
  if (!form.dealerId) return null
  const token = ++previewToken
  try {
    const { data } = await previewOrder(buildPreviewPayload(true))
    if (token === previewToken) { applyPreview(data); return data }
    return null
  } catch (e) {
    if (token === previewToken) error.value = e?.response?.data?.message || e?.message || ''
    return null
  }
}

async function submitOrder() {
  if (submitting.value) return
  error.value = ''
  if (!form.dealerId) { showToast('请选择经销商'); return }
  if (!editableRoots.value.length) { showToast('请至少添加一项产品'); return }
  if (editableRoots.value.some(l => !l.productId || num(l.qty) <= 0)) { showToast('请完善产品和数量'); return }
  await refreshPromotions().catch(() => null)
  const chargeable = flatLines.value.filter(l => !l.isGift && l.lineLevel !== 'PARENT')
  const missing = chargeable.filter(l => !num(l.standardPriceInclTax))
  if (missing.length) {
    const names = missing.map(l => l.productCode || l.productName || `#${l.productId}`).join('、')
    error.value = `以下产品没有有效销售价格：${names}`
    showToast.fail(error.value)
    return
  }
  try {
    await showConfirmDialog({ title: '确认提交', message: `合计 ¥${num(form.finalAmount).toFixed(2)}，确认提交？` })
  } catch (e) { return }
  submitting.value = true
  try {
    const payload = buildPreviewPayload(true)
    payload.remark = form.remark
    const res = await createOrder(payload)
    const newId = res?.data?.id
    if (newId) {
      await submitSalesOrder(newId).catch(() => null)
      showToast.success('提交成功')
      router.replace('/mobile/orders/' + newId)
    } else {
      showToast.success('已保存')
      router.replace('/mobile/orders')
    }
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '提交失败'
    showToast.fail(error.value)
  } finally {
    submitting.value = false
  }
}

onMounted(loadDealers)
</script>

<style scoped>
.m-order-create { padding-bottom: 100px; }
.line-card { padding: 10px 16px; border-bottom: 1px solid var(--van-gray-2); background:#fff; }
.line-card:last-child { border-bottom: 0; }
.line-card.is-child { background: #fafbfc; padding-left: 28px; }
.line-card.is-parent { background: #fffbe6; }
.line-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.line-no { font-size: 13px; color: var(--van-text-color-2); display:flex; gap:6px; align-items:center; }
.line-row { display: flex; gap: 8px; margin-top: 6px; }
.line-cell { flex: 1; min-width: 0; }
.cell-l { font-size: 12px; color: var(--van-text-color-3); margin-bottom: 4px; }
.ro-val { padding: 8px 0; font-size: 14px; }
.line-sub { display: flex; justify-content: space-between; font-size: 12px; color: var(--van-text-color-2); margin-top: 6px; }
.line-sub .muted { color: var(--van-danger-color); }
.readonly-product { padding: 8px 0; display:flex; gap:6px; flex-wrap:wrap; align-items:center; font-size: 13px; }
.readonly-product .rp-code { color: var(--van-text-color); font-weight: 600; }
.readonly-product .rp-name { color: var(--van-text-color-2); }
.readonly-product .rp-spec { color: var(--van-text-color-3); }
.submit-bar { position: fixed; bottom: 0; left: 0; right: 0; padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom)); background: var(--van-background-2); box-shadow: 0 -2px 8px rgba(0,0,0,.05); z-index: 10; }
.total-title { font-weight: 600; }
.total-value { color: var(--van-danger-color); font-weight: 700; }
.err-banner { margin: 10px 16px; padding: 8px 12px; background: var(--van-danger-color-light); color: var(--van-danger-color); border-radius: 6px; font-size: 13px; }
.m-order-create :deep(.van-field__label) { width: 72px !important; flex: none; font-size: 13px; }
.m-order-create .line-card :deep(.van-field__label) { width: 72px !important; }
</style>
