<template>
  <div class="pricing-panel">
    <!-- 促销文案：醒目逐条展示，不可漏 -->
    <div v-if="messages.length" class="promo-banner">
      <div class="promo-title"><van-icon name="gift-o" /> 本单优惠</div>
      <div v-for="(m, i) in messages" :key="i" class="promo-line">{{ m }}</div>
    </div>

    <van-cell-group inset title="计价方式" style="margin-top:10px">
      <van-field label="计价方式" readonly>
        <template #input>
          <van-radio-group v-model="mode" direction="horizontal" @change="onModeChange">
            <van-radio name="NORMAL">普通折扣</van-radio>
            <van-radio name="FIXED_PRICE">整单一口价</van-radio>
            <van-radio name="ZERO_ORDER">整单0金额</van-radio>
            <van-radio name="VOUCHER">代金券</van-radio>
          </van-radio-group>
        </template>
      </van-field>

      <van-field
        v-if="mode === 'FIXED_PRICE'"
        label="整单成交价"
        required
      >
        <template #input>
          <van-field v-model.number="fixedPrice" type="number" placeholder="输入整单成交价 ¥" border="false" @update:model-value="emitChange" />
        </template>
      </van-field>

      <van-field
        v-if="mode === 'VOUCHER'"
        readonly clickable is-link
        label="代金券"
        :model-value="voucherLabel"
        placeholder="选择可用代金券"
        @click="showVoucher = true"
      />

      <van-field
        v-if="mode === 'NORMAL'"
        readonly clickable is-link
        label="整单折扣"
        :model-value="headerLabel"
        placeholder="无（可设折扣/加价）"
        @click="openHeader"
      />
    </van-cell-group>

    <div v-if="mode !== 'NORMAL'" class="mutex-tip">
      <van-icon name="info-o" />
      {{ mode === 'VOUCHER' ? '使用代金券后，产品按原价销售，行折扣/整单折扣/促销均不可用。' : '该计价方式下，产品按原价销售，所有行折扣、整单折扣、促销均不可用。' }}
    </div>

    <!-- 金额明细 -->
    <van-cell-group inset title="金额明细" style="margin-top:10px">
      <van-cell title="原价合计（含税）" :value="'¥ ' + money(result.amountInclTax)" />
      <van-cell v-if="num(result.productDiscountTotal)" title="产品全局折扣" :value="'- ¥ ' + money(result.productDiscountTotal)" />
      <van-cell v-if="num(result.promoDiscountTotal)" title="促销优惠" :value="'- ¥ ' + money(result.promoDiscountTotal)" />
      <van-cell v-if="num(result.lineDiscountTotal) < 0" title="行折扣" :value="'- ¥ ' + money(-num(result.lineDiscountTotal))" />
      <van-cell v-else-if="num(result.lineDiscountTotal) > 0" title="行加价（高开）" :value="'+ ¥ ' + money(result.lineDiscountTotal)" />
      <van-cell v-if="num(result.dealerDiscountTotal)" title="客户全局折扣" :value="'- ¥ ' + money(result.dealerDiscountTotal)" />
      <van-cell v-if="num(result.headerDiscountTotal) < 0" title="整单折扣" :value="'- ¥ ' + money(-num(result.headerDiscountTotal))" />
      <van-cell v-else-if="num(result.headerDiscountTotal) > 0" title="整单加价（高开）" :value="'+ ¥ ' + money(result.headerDiscountTotal)" />
      <van-cell v-if="mode === 'FIXED_PRICE'" title="一口价成交" :value="'¥ ' + money(fixedPrice)" />
      <van-cell v-if="num(result.voucherAmount)" title="代金券抵扣" :value="'- ¥ ' + money(result.voucherAmount)" />
      <van-cell title="税额" :value="'¥ ' + money(result.taxAmount)" />
      <van-cell title="不含税金额" :value="'¥ ' + money(result.amountExclTax)" />
      <van-cell
        title-class="total-title" value-class="total-value"
        :title="mode === 'VOUCHER' ? '实付金额' : '含税合计'"
        :value="'¥ ' + money(payable)"
      />
    </van-cell-group>

    <!-- 整单折扣弹层 -->
    <van-popup v-model:show="showHeader" position="bottom" round>
      <van-cell-group>
        <van-field label="折扣类型">
          <template #input>
            <van-radio-group v-model="headerDraft.type" direction="horizontal">
              <van-radio name="">无</van-radio>
              <van-radio name="PERCENT">百分比</van-radio>
              <van-radio name="AMOUNT">固定金额</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-if="headerDraft.type" label="方向">
          <template #input>
            <van-radio-group v-model="headerDraft.direction" direction="horizontal">
              <van-radio name="REDUCE">折扣（减）</van-radio>
              <van-radio name="ADD">加价（高开）</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field
          v-if="headerDraft.type"
          :label="headerDraft.type === 'PERCENT' ? '折扣率(%)' : '金额'"
        >
          <template #input>
            <van-field v-model.number="headerDraft.value" type="number" :placeholder="headerDraft.type === 'PERCENT' ? '如 90 表示9折' : '0.00'" border="false" />
          </template>
        </van-field>
        <div style="padding:12px 16px;display:flex;gap:8px">
          <van-button block plain @click="showHeader = false">取消</van-button>
          <van-button block type="primary" @click="confirmHeader">确定</van-button>
        </div>
      </van-cell-group>
    </van-popup>

    <VoucherPicker
      v-model:show="showVoucher"
      @select="onVoucherSelect"
      :dealer-id="dealerId"
      :amount="num(result.amountInclTax)"
      :product-ids="productIds"
      v-model="voucherIdInner"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { showToast } from 'vant'
import VoucherPicker from './VoucherPicker.vue'

const props = defineProps({
  result: { type: Object, default: () => ({}) },
  messages: { type: Array, default: () => [] },
  dealerId: { type: [Number, String], default: null },
  productIds: { type: Array, default: () => [] },
  pricingMode: { type: String, default: 'NORMAL' },
  fixedPriceVal: { type: [Number, String], default: null },
  voucherIdVal: { type: [Number, String], default: null },
  voucherLabelText: { type: String, default: '' },
  headerType: { type: String, default: '' },
  headerValue: { type: [Number, String], default: 0 },
  headerDirection: { type: String, default: 'REDUCE' }
})
const emit = defineEmits(['change', 'voucher-select'])

const num = v => Number(v || 0)
const money = v => num(v).toFixed(2)

const mode = ref(props.pricingMode || 'NORMAL')
const fixedPrice = ref(props.fixedPriceVal != null ? num(props.fixedPriceVal) : null)
const voucherIdInner = ref(props.voucherIdVal || null)
const showVoucher = ref(false)
const showHeader = ref(false)
const headerDraft = reactive({ type: '', value: 0, direction: 'REDUCE' })

const headerType = ref(props.headerType || '')
const headerValue = ref(num(props.headerValue))
const headerDirection = ref(props.headerDirection || 'REDUCE')

watch(() => props.pricingMode, v => { if (v) mode.value = v })
watch(() => props.voucherLabelText, () => {})

const payable = computed(() => {
  if (mode.value === 'ZERO_ORDER') return '0.00'
  if (mode.value === 'VOUCHER') return money(props.result.payableAmount != null ? props.result.payableAmount : num(props.result.finalAmount) - num(props.result.voucherAmount))
  if (mode.value === 'FIXED_PRICE') return money(fixedPrice.value || 0)
  return money(props.result.finalAmount)
})

const voucherLabel = computed(() => {
  if (!voucherIdInner.value) return ''
  return props.voucherLabelText || ('已选券 #' + voucherIdInner.value)
})

const headerLabel = computed(() => {
  if (!headerType.value) return '无'
  const dir = headerDirection.value === 'ADD' ? '加价' : '折扣'
  if (headerType.value === 'PERCENT') return `${dir} 百分比 ${num(headerValue.value).toFixed(2)}%`
  return `${dir} ¥${num(headerValue.value).toFixed(2)}`
})

function emitChange() {
  emit('change', {
    pricingMode: mode.value,
    fixedPrice: mode.value === 'FIXED_PRICE' ? num(fixedPrice.value) : null,
    voucherId: mode.value === 'VOUCHER' ? voucherIdInner.value : null,
    headerDiscountType: mode.value === 'NORMAL' && headerType.value ? headerType.value : null,
    headerDiscountValue: mode.value === 'NORMAL' && headerType.value ? num(headerValue.value) : null,
    headerDiscountDirection: mode.value === 'NORMAL' && headerType.value ? headerDirection.value : null
  })
}

function onModeChange() {
  if (mode.value === 'VOUCHER' && !voucherIdInner.value) {
    showVoucher.value = true
  }
  emitChange()
}

watch(voucherIdInner, () => { if (mode.value === 'VOUCHER') emitChange() })

function onVoucherSelect(v) {
  emit('voucher-select', v)
}

function openHeader() {
  headerDraft.type = headerType.value || ''
  headerDraft.value = num(headerValue.value)
  headerDraft.direction = headerDirection.value || 'REDUCE'
  showHeader.value = true
}
function confirmHeader() {
  headerType.value = headerDraft.type || ''
  headerValue.value = headerDraft.type ? num(headerDraft.value) : 0
  headerDirection.value = headerDraft.direction || 'REDUCE'
  if (headerType.value === 'PERCENT') {
    const v = num(headerDraft.value)
    if (headerDirection.value === 'REDUCE' && (v <= 0 || v >= 100)) {
      showToast('折扣百分比需在 0~100 之间')
      return
    }
  } else if (headerType.value === 'AMOUNT') {
    if (num(headerDraft.value) < 0) { showToast('金额不能为负'); return }
  }
  showHeader.value = false
  emitChange()
}
</script>

<style scoped>
.promo-banner { margin: 10px 16px 0; padding: 10px 12px; background: #fff7e8; border: 1px solid #ffd591; border-radius: 8px; }
.promo-title { font-weight: 600; color: #d46b08; font-size: 14px; margin-bottom: 6px; display: flex; align-items: center; gap: 4px; }
.promo-line { font-size: 13px; color: #ad6800; line-height: 1.7; }
.mutex-tip { margin: 10px 16px 0; padding: 8px 12px; background: var(--van-primary-color-light, #ecf5ff); border-radius: 6px; font-size: 12px; color: var(--van-text-color-2); line-height: 1.6; }
.total-title { font-weight: 600; }
.total-value { color: var(--van-danger-color); font-weight: 700; font-size: 16px; }
.pricing-panel :deep(.van-field__label) { width: 96px !important; flex: none; font-size: 13px; }
.pricing-panel :deep(.van-radio) { margin-right: 12px; }
</style>
