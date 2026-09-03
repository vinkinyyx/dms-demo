<template>
  <div class="m-order-create">
    <van-nav-bar title="下销售订单" left-arrow @click-left="$router.back()" />

    <van-cell-group inset title="基本信息" style="margin-top:10px">
      <van-field
        readonly
        :clickable="!isDealerLocked"
        :is-link="!isDealerLocked"
        label="客户" required
        :model-value="dealerDisplayName"
        placeholder="选择客户"
        @click="openDealerPicker"
      />
      <van-field
        readonly clickable is-link
        label="送货地址" required
        :model-value="addressLabel"
        placeholder="选择送货地址"
        @click="onPickAddress"
      />
      <van-field
        readonly clickable is-link
        label="期望日期"
        :model-value="form.expectedDate || '不指定'"
        @click="showDatePicker = true"
      />
      <van-field
        v-model="form.remark"
        label="备注"
        type="textarea" rows="2" autosize
        placeholder="选填"
      />
    </van-cell-group>

    <van-cell-group inset title="订单明细" style="margin-top:10px">
      <div v-for="(line, idx) in displayLines" :key="line.key" class="line-card" :class="{ 'is-child': line.lineLevel === 'CHILD', 'is-gift': line.gift }">
        <div class="line-head">
          <span class="line-no">
            <template v-if="line.lineLevel === 'CHILD'">└─ 子件</template>
            <template v-else>#{{ idx + 1 }}</template>
            <van-tag v-if="line.gift" plain type="danger" size="mini">赠品</van-tag>
            <van-tag v-else-if="line.lineZero" plain type="warning" size="mini">0金额</van-tag>
            <van-tag v-else-if="line.promoType === 'QTY_DISCOUNT'" plain type="success" size="mini">促销打折</van-tag>
            <van-tag v-else-if="line.promoType" plain type="success" size="mini">促销</van-tag>
          </span>
          <van-button v-if="line.editable" size="mini" type="danger" plain @click="removeLine(line)">删除</van-button>
        </div>

        <van-field
          v-if="line.editable && !line.productId"
          readonly clickable is-link
          label="产品"
          model-value=""
          :placeholder="form.dealerId ? '选择产品' : '请先选择客户'"
          :disabled="!form.dealerId"
          @click="openProductPicker(line)"
        />
        <div v-else class="readonly-product">
          <span class="rp-code">{{ line.productCode }}</span>
          <span class="rp-name">{{ line.productName }}</span>
          <span v-if="line.productSpec" class="rp-spec">/ {{ line.productSpec }}</span>
        </div>

        <div v-if="line.productId && !line.gift" class="line-row">
          <div class="line-cell">
            <div class="cell-l">数量（整数）</div>
            <van-stepper
              v-if="line.editable"
              v-model="line.qty"
              :min="1"
              :step="1"
              integer
              input-width="48px"
              @change="onQtyChange(line)"
            />
            <div v-else class="ro-val">{{ num(line.qty) }}</div>
          </div>
          <div class="line-cell">
            <div class="cell-l">含税单价</div>
            <div class="ro-val">¥ {{ num(line.unitPriceInclTax || line.standardPriceInclTax).toFixed(2) }}</div>
          </div>
        </div>

        <template v-if="line.editable && !line.gift && pricing.pricingMode === 'NORMAL'">
          <div class="line-row">
            <div class="line-cell">
              <div class="cell-l">行折扣类型</div>
              <van-field readonly clickable is-link :model-value="lineDiscountTypeLabel(line)" placeholder="无" @click="openLineDiscountType(line)" />
            </div>
            <div class="line-cell" v-if="line.lineDiscountType && line.promoType !== 'QTY_DISCOUNT'">
              <div class="cell-l">方向</div>
              <van-field readonly clickable is-link :model-value="line.lineDiscountDirection === 'ADD' ? '加价' : '折扣'" @click="openLineDirection(line)" />
            </div>
          </div>
          <div class="line-row" v-if="line.lineDiscountType && line.promoType !== 'QTY_DISCOUNT'">
            <div class="line-cell">
              <div class="cell-l">{{ line.lineDiscountType === 'PERCENT' ? '折扣率(%)' : '折扣金额(¥)' }}</div>
              <van-field v-model.number="line.lineDiscountValue" type="number" placeholder="0.00" @update:model-value="schedulePreview" />
            </div>
          </div>
          <div v-if="line.promoType === 'QTY_DISCOUNT'" class="line-hint">该产品已命中促销打折，不可再设置行折扣。</div>
          <div class="line-row" v-if="!line.promoType">
            <div class="line-cell zero-cell">
              <span class="cell-l" style="margin:0">本行 0 金额</span>
              <van-switch v-model="line.lineZero" size="20" @change="onLineZeroChange(line)" />
            </div>
          </div>
          <div v-if="line.lineZero" class="line-hint">该行为 0 金额，产品折扣/促销/行折扣不可用。</div>
        </template>

        <div v-if="line.productId" class="line-sub">
          <span v-if="line.lineLevel === 'CHILD'">子件成交 ¥ {{ money(line.finalAmount) }}</span>
          <span v-else>
            原价 ¥ {{ money(line.standardAmount) }}
            <template v-if="num(line.finalAmount) !== num(line.standardAmount)"> → 成交 ¥ {{ money(line.finalAmount) }}</template>
          </span>
        </div>
      </div>

      <div style="padding:10px 16px;">
        <van-button block plain icon="plus" :disabled="!form.dealerId || lockedMode" @click="addLine">添加产品</van-button>
      </div>
    </van-cell-group>

    <PricingPanel
      :result="preview"
      :messages="promoMessages"
      :dealer-id="form.dealerId"
      :product-ids="productIds"
      :pricing-mode="pricing.pricingMode"
      :fixed-price-val="pricing.fixedPrice"
      :voucher-id-val="pricing.voucherId"
      :voucher-label-text="voucherLabelText"
      :header-type="pricing.headerDiscountType"
      :header-value="pricing.headerDiscountValue"
      :header-direction="pricing.headerDiscountDirection"
      @change="onPricingChange"
      @voucher-select="onVoucherSelect"
    />

    <div v-if="error" class="err-banner">{{ error }}</div>

    <div class="submit-bar">
      <van-button block round type="primary" :loading="submitting" @click="submitOrder">
        提交订单 · ¥ {{ payableText }}
      </van-button>
    </div>

    <van-popup v-model:show="showDealerPicker" position="bottom" round>
      <van-picker
        :columns="dealerColumns"
        :model-value="[form.dealerId]"
        @confirm="onDealerConfirm"
        @cancel="showDealerPicker = false"
        show-toolbar
      />
    </van-popup>

    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        v-model="datePickerValue"
        :min-date="minDate"
        :max-date="maxDate"
        title="选择期望日期"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showLineDiscountType" position="bottom" round>
      <van-picker
        :columns="lineDiscountTypeColumns"
        :model-value="[activeLine?.lineDiscountType || '']"
        @confirm="onLineDiscountTypeConfirm"
        @cancel="showLineDiscountType = false"
        show-toolbar
      />
    </van-popup>

    <van-popup v-model:show="showLineDirection" position="bottom" round>
      <van-picker
        :columns="lineDirectionColumns"
        :model-value="[activeLine?.lineDiscountDirection || 'REDUCE']"
        @confirm="onLineDirectionConfirm"
        @cancel="showLineDirection = false"
        show-toolbar
      />
    </van-popup>

    <van-popup v-model:show="showProductPicker" position="bottom" round :style="{ height: '70%' }">
      <van-nav-bar title="选择产品" :left-arrow="false">
        <template #right>
          <van-button size="small" type="primary" @click="showProductPicker = false">关闭</van-button>
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

    <AddressPicker
      v-model:show="showAddressPicker"
      :dealer-id="form.dealerId"
      v-model="selectedAddress"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { showToast, showFailToast, showConfirmDialog } from 'vant'
import { lookup } from '@/api/crud'
import { previewV43, createSalesOrderV43, submitSalesOrderV43 } from '@/api/mobileV43'
import PricingPanel from './components/PricingPanel.vue'
import AddressPicker from './components/AddressPicker.vue'

const router = useRouter()
const userStore = useUserStore()

let seq = 1
const num = v => Number(v || 0)
const money = v => num(v).toFixed(2)

function makeLine(p = {}) {
  return {
    key: 't' + (seq++),
    editable: true,
    productId: p.productId ?? p.id ?? null,
    productCode: p.code || p.productCode || '',
    productName: p.nameCn || p.name || p.productName || '',
    productSpec: p.spec || p.productSpec || '',
    qty: 1,
    lineDiscountType: '',
    lineDiscountValue: 0,
    lineDiscountDirection: 'REDUCE',
    lineZero: false,
    standardPriceInclTax: 0,
    standardAmount: 0,
    finalAmount: 0,
    unitPriceInclTax: 0,
    promoType: '',
    gift: false,
    lineLevel: 'NORMAL'
  }
}

const form = reactive({
  dealerId: null,
  dealerName: '',
  expectedDate: '',
  remark: '',
  lines: []
})

const pricing = reactive({
  pricingMode: 'NORMAL',
  fixedPrice: null,
  voucherId: null,
  headerDiscountType: '',
  headerDiscountValue: 0,
  headerDiscountDirection: 'REDUCE'
})

const preview = ref({})
const promoMessages = ref([])
const error = ref('')
const submitting = ref(false)
const selectedAddress = ref(null)
const showAddressPicker = ref(false)

const showDealerPicker = ref(false)
const showDatePicker = ref(false)
const showLineDiscountType = ref(false)
const showLineDirection = ref(false)
const showProductPicker = ref(false)
const isDealerLocked = ref(false)

const dealerOptions = ref([])
const dealerMap = computed(() => Object.fromEntries(dealerOptions.value.map(d => [d.value, d.label])))
const dealerDisplayName = computed(() => {
  if (!form.dealerId) return ''
  return dealerMap.value[form.dealerId] || form.dealerName || ('#' + form.dealerId)
})
const dealerColumns = computed(() => dealerOptions.value.map(d => ({ text: d.label, value: d.value })))

const minDate = new Date()
const maxDate = new Date(new Date().getFullYear() + 2, 11, 31)
const datePickerValue = ref([])

const lineDiscountTypeColumns = [
  { text: '无', value: '' },
  { text: '比例(%)', value: 'PERCENT' },
  { text: '固定金额(¥)', value: 'AMOUNT' }
]
const lineDirectionColumns = [
  { text: '折扣（减）', value: 'REDUCE' },
  { text: '加价（高开）', value: 'ADD' }
]
const activeLine = ref(null)

const productOptions = ref([])
const productKeyword = ref('')
const loadingProducts = ref(false)
const finishedProducts = ref(false)
let productPage = 1
let activeLineRef = null

const lockedMode = computed(() => pricing.pricingMode && pricing.pricingMode !== 'NORMAL')
const editableLines = computed(() => form.lines.filter(l => l.productId && !l.gift))
const productIds = computed(() => editableLines.value.map(l => l.productId).filter(Boolean))

const selectedVoucher = ref(null)
const voucherLabelText = computed(() => {
  const v = selectedVoucher.value
  if (!v) return pricing.voucherId ? '已选券 #' + pricing.voucherId : ''
  return `${v.name || '代金券'} · 面值 ¥${money(v.faceValue)}`
})

const addressLabel = computed(() => {
  const a = selectedAddress.value
  if (!a) return ''
  const region = [a.province, a.city, a.district, a.address].filter(Boolean).join('')
  return `${a.addressName || '收货地址'} · ${a.contactName || ''} ${a.phone || ''} ${region}`.trim()
})

const displayLines = computed(() => {
  const out = []
  const previewLines = Array.isArray(preview.value.lines) ? preview.value.lines : []
  const childByGroup = {}
  const giftLines = []
  previewLines.forEach(l => {
    if (l.gift) { giftLines.push(l); return }
    if (l.lineLevel === 'CHILD') {
      const g = l.bomGroupNo != null ? String(l.bomGroupNo) : ''
      ;(childByGroup[g] = childByGroup[g] || []).push(l)
    }
  })
  const rootResults = previewLines.filter(l => !l.gift && l.lineLevel !== 'CHILD')
  form.lines.forEach((line, i) => {
    const r = rootResults[i]
    if (r) {
      line.standardPriceInclTax = num(r.standardPriceInclTax)
      line.standardAmount = num(r.standardAmount)
      line.finalAmount = num(r.finalAmount)
      line.unitPriceInclTax = num(r.unitPriceInclTax)
      line.promoType = r.promoType || ''
    }
    out.push(line)
    const groupNo = r && r.bomGroupNo != null ? String(r.bomGroupNo) : null
    const children = (groupNo != null && childByGroup[groupNo]) || []
    children.forEach(c => out.push({
      key: line.key + '-c-' + c.productId,
      editable: false,
      productId: c.productId,
      productCode: c.productCode,
      productName: c.productName,
      productSpec: c.productSpec,
      qty: num(c.qty),
      finalAmount: num(c.finalAmount),
      unitPriceInclTax: num(c.unitPriceInclTax),
      standardAmount: num(c.standardAmount),
      lineLevel: 'CHILD',
      gift: false,
      promoType: '',
      lineZero: false
    }))
  })
  giftLines.forEach((g, gi) => out.push({
    key: 'gift-' + gi + '-' + g.productId,
    editable: false,
    productId: g.productId,
    productCode: g.productCode,
    productName: g.productName,
    productSpec: g.productSpec,
    qty: num(g.qty),
    finalAmount: 0,
    unitPriceInclTax: 0,
    standardAmount: 0,
    lineLevel: 'NORMAL',
    gift: true,
    promoType: 'GIFT',
    lineZero: false
  }))
  return out
})

const payableText = computed(() => {
  const r = preview.value
  if (pricing.pricingMode === 'ZERO_ORDER') return '0.00'
  if (pricing.pricingMode === 'VOUCHER') return money(r.payableAmount != null ? r.payableAmount : num(r.finalAmount) - num(r.voucherAmount))
  if (pricing.pricingMode === 'FIXED_PRICE') return money(pricing.fixedPrice || 0)
  return money(r.finalAmount)
})

function lineDiscountTypeLabel(line) {
  if (line.promoType === 'QTY_DISCOUNT') return '促销打折（不可改）'
  if (line.lineDiscountType === 'PERCENT') return `比例 ${num(line.lineDiscountValue).toFixed(2)}%`
  if (line.lineDiscountType === 'AMOUNT') return `金额 ¥${money(line.lineDiscountValue)}`
  return '无'
}

function onPickAddress() {
  if (!form.dealerId) { showToast('请先选择客户'); return }
  showAddressPicker.value = true
}

function openDealerPicker() {
  if (isDealerLocked.value) return
  showDealerPicker.value = true
}
function onDealerConfirm({ selectedOptions }) {
  const val = selectedOptions?.[0]?.value
  if (val == null) { showToast('请选择客户'); return }
  form.dealerId = val
  form.dealerName = selectedOptions[0]?.text || ''
  showDealerPicker.value = false
  selectedAddress.value = null
  form.lines = []
  preview.value = {}
  promoMessages.value = []
  schedulePreview()
}

function onDateConfirm({ selectedValues }) {
  form.expectedDate = selectedValues.join('-')
  datePickerValue.value = selectedValues
  showDatePicker.value = false
}

function openLineDiscountType(line) {
  if (line.promoType === 'QTY_DISCOUNT') return
  activeLine.value = line
  showLineDiscountType.value = true
}
function onLineDiscountTypeConfirm({ selectedOptions }) {
  const line = activeLine.value
  if (line) {
    line.lineDiscountType = selectedOptions?.[0]?.value || ''
    if (!line.lineDiscountType) { line.lineDiscountValue = 0; line.lineDiscountDirection = 'REDUCE' }
  }
  showLineDiscountType.value = false
  schedulePreview()
}
function openLineDirection(line) {
  activeLine.value = line
  showLineDirection.value = true
}
function onLineDirectionConfirm({ selectedOptions }) {
  if (activeLine.value) activeLine.value.lineDiscountDirection = selectedOptions?.[0]?.value || 'REDUCE'
  showLineDirection.value = false
  schedulePreview()
}
function onLineZeroChange(line) {
  if (line.lineZero) {
    line.lineDiscountType = ''
    line.lineDiscountValue = 0
  }
  schedulePreview()
}
function onQtyChange(line) {
  const v = num(line.qty)
  if (!Number.isInteger(v) || v < 1) {
    line.qty = Math.max(1, Math.floor(v) || 1)
    showToast('数量必须为大于 0 的整数')
  }
  schedulePreview()
}

function onVoucherSelect(v) {
  selectedVoucher.value = v
}

function onPricingChange(state) {
  pricing.pricingMode = state.pricingMode
  pricing.fixedPrice = state.fixedPrice
  pricing.voucherId = state.voucherId
  pricing.headerDiscountType = state.headerDiscountType || ''
  pricing.headerDiscountValue = state.headerDiscountValue || 0
  pricing.headerDiscountDirection = state.headerDiscountDirection || 'REDUCE'
  // 互斥：非普通模式清空行折扣/行0
  if (pricing.pricingMode !== 'NORMAL') {
    form.lines.forEach(l => {
      l.lineDiscountType = ''
      l.lineDiscountValue = 0
      l.lineDiscountDirection = 'REDUCE'
      l.lineZero = false
    })
  }
  schedulePreview()
}

function addLine() {
  if (!form.dealerId) { showToast('请先选择客户'); return }
  if (lockedMode.value) { showToast('一口价/整单0/代金券模式不可编辑明细'); return }
  form.lines.push(makeLine())
}
function removeLine(line) {
  form.lines = form.lines.filter(l => l.key !== line.key)
  schedulePreview()
}

async function loadDealers() {
  try {
    const r = await lookup('dealers', { limit: 200 })
    dealerOptions.value = (r.data || []).map(d => ({ value: d.id, label: (d.code ? d.code + ' · ' : '') + d.name }))
  } catch (e) { /* ignore */ }
  if (isDealerLocked.value && form.dealerId) {
    const matched = dealerOptions.value.find(d => String(d.value) === String(form.dealerId))
    if (matched) form.dealerName = matched.label
  }
}

function openProductPicker(line) {
  if (!form.dealerId) { showToast('请先选择客户'); return }
  activeLineRef = line
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
    const r = await lookup('products', { page: productPage, size: 30, keyword: productKeyword.value || undefined })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    productOptions.value.push(...list.map(p => ({
      value: p.id,
      label: (p.code ? p.code + ' · ' : '') + (p.nameCn || p.name || ''),
      code: p.code,
      name: p.nameCn || p.name,
      spec: p.spec || '',
      unit: p.unit || p.unitType || 'EA'
    })))
    productPage++
    if (list.length < 30) finishedProducts.value = true
  } catch (e) {
    finishedProducts.value = true
  } finally {
    loadingProducts.value = false
  }
}

function onProductPick(p) {
  const target = activeLineRef && !activeLineRef.productId
    ? activeLineRef
    : (form.lines.find(l => l.editable && !l.productId) || null)
  // 同 SKU 不允许拆多行
  if (form.lines.some(l => l.productId && String(l.productId) === String(p.value))) {
    showFailToast(`产品 ${p.code || p.value} 已在订单中，请直接修改其数量，不可重复添加`)
    showProductPicker.value = false
    return
  }
  if (target) {
    Object.assign(target, makeLine({ id: p.value, code: p.code, nameCn: p.name, spec: p.spec }))
  } else {
    const line = makeLine({ id: p.value, code: p.code, nameCn: p.name, spec: p.spec })
    form.lines.push(line)
  }
  showProductPicker.value = false
  schedulePreview()
}

let previewTimer = null
let previewToken = 0
function schedulePreview() {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(runPreview, 350)
}

function buildPayload() {
  return {
    dealerId: form.dealerId,
    expectedDate: form.expectedDate || null,
    applyPromotions: true,
    pricingMode: pricing.pricingMode || 'NORMAL',
    fixedPrice: pricing.pricingMode === 'FIXED_PRICE' ? num(pricing.fixedPrice) : null,
    voucherId: pricing.pricingMode === 'VOUCHER' ? pricing.voucherId : null,
    headerDiscountType: pricing.pricingMode === 'NORMAL' && pricing.headerDiscountType ? pricing.headerDiscountType : null,
    headerDiscountValue: pricing.pricingMode === 'NORMAL' && pricing.headerDiscountType ? num(pricing.headerDiscountValue) : null,
    headerDiscountDirection: pricing.pricingMode === 'NORMAL' && pricing.headerDiscountType ? pricing.headerDiscountDirection : null,
    lines: editableLines.value.map(l => ({
      productId: l.productId,
      qty: Math.max(1, Math.floor(num(l.qty)) || 1),
      lineZero: pricing.pricingMode === 'NORMAL' ? !!l.lineZero : false,
      lineDiscountType: pricing.pricingMode === 'NORMAL' && l.lineDiscountType && !l.lineZero ? l.lineDiscountType : null,
      lineDiscountValue: pricing.pricingMode === 'NORMAL' && l.lineDiscountType && !l.lineZero ? num(l.lineDiscountValue) : null,
      lineDiscountDirection: pricing.pricingMode === 'NORMAL' && l.lineDiscountType && !l.lineZero ? (l.lineDiscountDirection || 'REDUCE') : null
    }))
  }
}

async function runPreview() {
  if (!form.dealerId || !editableLines.value.length) {
    preview.value = {}
    promoMessages.value = []
    error.value = ''
    return
  }
  const token = ++previewToken
  error.value = ''
  try {
    const { data } = await previewV43(buildPayload())
    if (token === previewToken) {
      preview.value = data || {}
      promoMessages.value = Array.isArray(data?.promotionMessages) ? data.promotionMessages : []
    }
  } catch (e) {
    if (token === previewToken) {
      preview.value = {}
      promoMessages.value = []
      error.value = e?.response?.data?.message || e?.message || '计价失败'
    }
  }
}

async function submitOrder() {
  if (submitting.value) return
  error.value = ''
  if (!form.dealerId) { showFailToast('请选择客户'); return }
  if (!selectedAddress.value) { showFailToast('请选择送货地址'); return }
  if (!editableLines.value.length) { showFailToast('请至少添加一项产品'); return }
  if (editableLines.value.some(l => !l.productId || !Number.isInteger(num(l.qty)) || num(l.qty) <= 0)) {
    showFailToast('请完善产品，数量必须为大于 0 的整数'); return
  }
  if (pricing.pricingMode === 'FIXED_PRICE' && (!num(pricing.fixedPrice) || num(pricing.fixedPrice) <= 0)) {
    showFailToast('请输入整单成交价'); return
  }
  if (pricing.pricingMode === 'VOUCHER' && !pricing.voucherId) {
    showFailToast('请选择代金券，或切换为其他计价方式'); return
  }

  // 提交前强制刷新一次计价，拿到后端最终结果与拦截原因
  await runPreview()
  if (error.value) {
    showFailToast(error.value)
    return
  }

  let amount = num(preview.value.finalAmount)
  if (pricing.pricingMode === 'VOUCHER') amount = num(preview.value.payableAmount != null ? preview.value.payableAmount : num(preview.value.finalAmount))
  if (pricing.pricingMode === 'FIXED_PRICE') amount = num(pricing.fixedPrice)
  if (pricing.pricingMode === 'ZERO_ORDER') amount = 0

  try {
    await showConfirmDialog({ title: '确认提交', message: `实付 ¥${money(amount)}，确认提交订单？` })
  } catch (e) { return }

  submitting.value = true
  try {
    const payload = buildPayload()
    payload.remark = form.remark
    payload.extra = {
      addressId: selectedAddress.value.id,
      shipAddress: {
        addressName: selectedAddress.value.addressName,
        contactName: selectedAddress.value.contactName,
        phone: selectedAddress.value.phone,
        province: selectedAddress.value.province,
        city: selectedAddress.value.city,
        district: selectedAddress.value.district,
        address: selectedAddress.value.address
      }
    }
    const res = await createSalesOrderV43(payload)
    const newId = res?.data?.id
    if (newId) {
      await submitSalesOrderV43(newId).catch((e) => {
        showFailToast(e?.response?.data?.message || '订单已保存但提交失败，可在草稿中重新提交')
      })
      showToast.success('提交成功')
      router.replace('/mobile/orders/' + newId)
    } else {
      showToast.success('已保存')
      router.replace('/mobile/orders')
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '提交失败'
    error.value = msg
    showFailToast(msg)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const u = userStore.user || {}
  const ut = String(u.userType || u.user_type || '').toLowerCase()
  const dealerId = u.dealerId || u.dealer_id
  if ((ut === 'dealer' || ut === 'customer') && dealerId) {
    isDealerLocked.value = true
    form.dealerId = dealerId
    form.dealerName = u.dealerName || u.companyName || ''
  }
  await loadDealers()
  schedulePreview()
})
</script>

<style scoped>
.m-order-create { padding-bottom: 110px; }
.line-card { padding: 10px 16px; border-bottom: 1px solid var(--dms-divider-color); background: var(--dms-bg-container); }
.line-card:last-child { border-bottom: 0; }
.line-card.is-child { background: var(--dms-gray-50); padding-left: 28px; }
.line-card.is-gift { background: #fff7f7; }
.line-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.line-no { font-size: 13px; color: var(--van-text-color-2); display: flex; gap: 6px; align-items: center; }
.line-row { display: flex; gap: 8px; margin-top: 6px; }
.line-cell { flex: 1; min-width: 0; }
.cell-l { font-size: 12px; color: var(--van-text-color-3); margin-bottom: 4px; }
.zero-cell { display: flex; align-items: center; justify-content: space-between; padding: 6px 0; }
.ro-val { padding: 8px 0; font-size: 14px; }
.line-hint { font-size: 12px; color: #d46b08; margin-top: 6px; line-height: 1.5; }
.line-sub { display: flex; justify-content: space-between; font-size: 12px; color: var(--van-text-color-2); margin-top: 6px; gap: 8px; }
.readonly-product { padding: 8px 0; display: flex; gap: 6px; flex-wrap: wrap; align-items: center; font-size: 13px; }
.readonly-product .rp-code { color: var(--van-text-color); font-weight: 600; }
.readonly-product .rp-name { color: var(--van-text-color-2); }
.readonly-product .rp-spec { color: var(--van-text-color-3); }
.submit-bar { position: fixed; bottom: 0; left: 0; right: 0; padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom)); background: var(--van-background-2); box-shadow: 0 -2px 8px rgba(0,0,0,.05); z-index: 10; }
.err-banner { margin: 10px 16px; padding: 8px 12px; background: var(--van-danger-color-light); color: var(--van-danger-color); border-radius: 6px; font-size: 13px; line-height: 1.5; }
.m-order-create :deep(.van-field__label) { width: 76px !important; flex: none; font-size: 13px; }
.m-order-create .line-card :deep(.van-field__label) { width: 76px !important; }
</style>
