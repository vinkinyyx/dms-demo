<template>
  <van-popup :show="show" position="bottom" round @update:show="$emit('update:show', $event)">
    <van-nav-bar title="选择代金券" :left-arrow="false">
      <template #right>
        <van-button size="small" type="primary" @click="$emit('update:show', false)">关闭</van-button>
      </template>
    </van-nav-bar>
    <van-loading v-if="loading" style="display:flex;justify-content:center;padding:24px" />
    <van-empty v-else-if="!vouchers.length" description="当前订单暂无可用代金券" />
    <div v-else style="max-height:60vh;overflow-y:auto;padding:10px 12px">
      <div
        v-for="v in vouchers"
        :key="v.id"
        class="voucher-card"
        :class="{ active: modelValue === v.id }"
        @click="onPick(v)"
      >
        <div class="v-face">
          <div class="v-face-amt">¥{{ num(v.faceValue).toFixed(2) }}</div>
          <div class="v-face-label">代金券</div>
        </div>
        <div class="v-info">
          <div class="v-name">{{ v.name }}</div>
          <div class="v-meta">
            <span v-if="num(v.minSpend) > 0">满 ¥{{ num(v.minSpend).toFixed(2) }} 可用</span>
            <span v-else>无门槛</span>
            <span>· {{ scopeText(v) }}</span>
          </div>
          <div class="v-date">有效期：{{ fmtDate(v.validFrom) }} ~ {{ fmtDate(v.validTo) }}</div>
        </div>
        <van-icon v-if="modelValue === v.id" name="success" color="var(--dms-color-primary,#1989fa)" size="20" />
      </div>
      <div style="padding:8px 4px 16px;color:var(--van-text-color-3);font-size:12px;line-height:1.6">
        一单仅可使用一张代金券；券抵扣不计入商品单价，退货时不退还券抵扣金额。
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, watch } from 'vue'
import { showToast } from 'vant'
import { availableVouchers } from '@/api/mobileV43'

const props = defineProps({
  show: { type: Boolean, default: false },
  dealerId: { type: [Number, String], default: null },
  amount: { type: [Number, String], default: 0 },
  productIds: { type: Array, default: () => [] },
  modelValue: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:show', 'update:modelValue', 'select'])

const vouchers = ref([])
const loading = ref(false)
const num = v => Number(v || 0)

function scopeText(v) {
  if (v.scopeType === 'PRODUCT') return '指定商品'
  if (v.scopeType === 'CATEGORY') return '指定品类'
  return '全场通用'
}
function fmtDate(d) {
  if (!d) return '长期'
  const dt = new Date(d)
  if (isNaN(dt.getTime())) return String(d).slice(0, 10)
  const mm = String(dt.getMonth() + 1).padStart(2, '0')
  const dd = String(dt.getDate()).padStart(2, '0')
  return `${dt.getFullYear()}-${mm}-${dd}`
}

async function load() {
  if (!props.dealerId) return
  loading.value = true
  try {
    const params = {
      dealerId: props.dealerId,
      amount: num(props.amount) || undefined,
      productIds: (props.productIds || []).filter(Boolean).join(',') || undefined
    }
    const res = await availableVouchers(params)
    vouchers.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    showToast(e?.message || '代金券加载失败')
  } finally {
    loading.value = false
  }
}

function onPick(v) {
  const next = props.modelValue === v.id ? null : v.id
  emit('update:modelValue', next)
  emit('select', next ? v : null)
  emit('update:show', false)
}

watch(() => props.show, (v) => { if (v) load() })
</script>

<style scoped>
.voucher-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid var(--van-gray-2);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
}
.voucher-card.active { border-color: var(--dms-color-primary, #1989fa); box-shadow: 0 0 0 1px var(--dms-color-primary, #1989fa); }
.v-face {
  width: 86px;
  flex: none;
  text-align: center;
  background: linear-gradient(135deg, #e14d4d, #f27c5e);
  color: #fff;
  border-radius: 6px;
  padding: 12px 4px;
}
.v-face-amt { font-size: 20px; font-weight: 700; }
.v-face-label { font-size: 12px; opacity: .9; margin-top: 2px; }
.v-info { flex: 1; min-width: 0; }
.v-name { font-size: 14px; font-weight: 600; color: var(--van-text-color); }
.v-meta { font-size: 12px; color: var(--van-text-color-2); margin-top: 4px; }
.v-date { font-size: 12px; color: var(--van-text-color-3); margin-top: 4px; }
</style>
