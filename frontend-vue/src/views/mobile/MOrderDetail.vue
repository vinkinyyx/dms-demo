<template>
  <div>
    <van-nav-bar title="订单详情" left-arrow @click-left="$router.back()" />
    <div v-if="loading" class="loading"><van-loading type="spinner" /></div>
    <div v-else-if="!order" class="empty"><van-empty description="订单不存在" /></div>
    <div v-else class="detail-body">
      <div class="status-bar" :class="'st-' + (order.status || '').toLowerCase()">
        <div class="st-text">{{ statusText(order.status) }}</div>
        <div class="st-code">{{ order.code }}</div>
      </div>

      <van-cell-group inset title="基本信息" style="margin-top:10px">
        <van-cell title="单号" :value="order.code || '-'" />
        <van-cell title="订单类型" :value="orderTypeLabel" />
        <van-cell title="经销商" :value="order.dealerName || '-'" />
        <van-cell v-if="order.warehouseName" title="仓库" :value="order.warehouseName" />
        <van-cell v-if="order.expectedDate" title="期望到货" :value="fmtDate(order.expectedDate)" />
        <van-cell title="创建时间" :value="fmtDate(order.createdAt)" />
        <van-cell v-if="order.submittedAt" title="提交时间" :value="fmtDate(order.submittedAt)" />
        <van-cell v-if="order.approvedAt" title="审批时间" :value="fmtDate(order.approvedAt)" />
        <van-cell v-if="order.remark" title="备注" :value="order.remark" />
      </van-cell-group>

      <van-cell-group inset title="产品明细" style="margin-top:10px">
        <div v-if="lineTree.length" class="line-list">
          <div
            v-for="line in lineTree"
            :key="line.id || line.tempId"
            class="line-card"
            :class="{ 'is-parent': line.lineLevel === 'PARENT', 'is-child': line.lineLevel === 'CHILD', 'is-gift': line.isGift }"
          >
            <div class="line-head">
              <span class="line-title">
                <span v-if="line.lineLevel === 'CHILD'" class="child-prefix">└─ </span>
                {{ line.productCode }} {{ line.productName }}
              </span>
              <van-tag v-if="line.lineLevel === 'PARENT'" plain type="warning" size="mini">BOM母件</van-tag>
              <van-tag v-if="line.isGift" plain type="danger" size="mini">赠品</van-tag>
            </div>
            <div v-if="line.productSpec" class="line-spec">规格：{{ line.productSpec }}</div>
            <div class="line-grid line-grid-3">
              <div class="g"><label>数量</label><b>{{ line.qty || 0 }}</b></div>
              <div class="g"><label>单价</label><b>¥{{ money(line.standardPriceInclTax || line.unitPrice) }}</b></div>
              <div class="g"><label>金额</label><b>¥{{ money(lineLineSubtotal(line)) }}</b></div>
            </div>
            <div v-if="hasDiscount(line)" class="line-promos">
              <span v-if="Number(line.productDiscountAmount || 0) > 0" class="lp lp-promo">产品优惠 -¥{{ money(line.productDiscountAmount) }}</span>
              <span v-if="Number(line.lineDiscountAmount || 0) > 0" class="lp lp-line">行折扣 -¥{{ money(line.lineDiscountAmount) }}</span>
              <span v-if="Number(line.promoDiscountAmount || 0) > 0" class="lp lp-promo">促销优惠 -¥{{ money(line.promoDiscountAmount) }}</span>
              <span v-if="Number(line.headerDiscountAmount || 0) > 0" class="lp lp-header">整单折扣分摊 -¥{{ money(line.headerDiscountAmount) }}</span>
            </div>
            <div class="line-final">
              <span>小计</span>
              <b>¥{{ money(line.finalAmount || (Number(line.qty || 0) * Number(line.standardPriceInclTax || line.unitPrice || 0))) }}</b>
            </div>
            <div v-if="line.children && line.children.length" class="child-list">
              <div
                v-for="child in line.children"
                :key="child.id || child.tempId"
                class="line-card is-child"
              >
                <div class="line-head">
                  <span class="line-title"><span class="child-prefix">└─ </span>{{ child.productCode }} {{ child.productName }}</span>
                  <van-tag v-if="child.isGift" plain type="danger" size="mini">赠品</van-tag>
                </div>
                <div v-if="child.productSpec" class="line-spec">规格：{{ child.productSpec }}</div>
                <div class="line-grid line-grid-3">
                  <div class="g"><label>数量</label><b>{{ child.qty || 0 }}</b></div>
                  <div class="g"><label>单价</label><b>¥{{ money(child.standardPriceInclTax || child.unitPrice) }}</b></div>
                  <div class="g"><label>金额</label><b>¥{{ money(lineLineSubtotal(child)) }}</b></div>
                </div>
                <div v-if="hasDiscount(child)" class="line-promos">
                  <span v-if="Number(child.productDiscountAmount || 0) > 0" class="lp lp-promo">产品优惠 -¥{{ money(child.productDiscountAmount) }}</span>
                  <span v-if="Number(child.lineDiscountAmount || 0) > 0" class="lp lp-line">行折扣 -¥{{ money(child.lineDiscountAmount) }}</span>
                  <span v-if="Number(child.promoDiscountAmount || 0) > 0" class="lp lp-promo">促销优惠 -¥{{ money(child.promoDiscountAmount) }}</span>
                  <span v-if="Number(child.headerDiscountAmount || 0) > 0" class="lp lp-header">整单折扣分摊 -¥{{ money(child.headerDiscountAmount) }}</span>
                </div>
                <div class="line-final">
                  <span>小计</span>
                  <b>¥{{ money(child.finalAmount || (Number(child.qty || 0) * Number(child.standardPriceInclTax || child.unitPrice || 0))) }}</b>
                </div>
              </div>
            </div>
          </div>
        </div>
        <van-empty v-else description="暂无明细" />
      </van-cell-group>

      <van-cell-group v-if="promoMessages.length" inset title="命中促销" style="margin-top:10px">
        <div v-for="(msg, i) in promoMessages" :key="i" class="promo-hit">
          <van-icon name="gift-o" class="promo-hit-ic" />
          <span>{{ msg }}</span>
        </div>
      </van-cell-group>

      <van-cell-group inset title="优惠明细" style="margin-top:10px">
        <van-cell title="商品总额" :value="'¥' + money(order.amountInclTax)" />
        <van-cell v-if="totalProductDiscount > 0" title="产品优惠">
          <template #value><span class="val-discount">-¥{{ money(totalProductDiscount) }}</span></template>
        </van-cell>
        <van-cell v-if="totalLineDiscount > 0" title="行折扣">
          <template #value><span class="val-discount">-¥{{ money(totalLineDiscount) }}</span></template>
        </van-cell>
        <van-cell v-if="totalPromoDiscount > 0" title="促销优惠">
          <template #value><span class="val-discount">-¥{{ money(totalPromoDiscount) }}</span></template>
        </van-cell>
        <van-cell v-if="Number(order.voucherAmount || 0) > 0" title="代金券抵扣">
          <template #value><span class="val-discount">-¥{{ money(order.voucherAmount) }}</span></template>
        </van-cell>
        <van-cell v-if="Number(order.discountAmount || 0) > 0" title="整单折扣/优惠合计">
          <template #value><span class="val-discount">-¥{{ money(order.discountAmount) }}</span></template>
        </van-cell>
      </van-cell-group>

      <div class="pay-bar">
        <span class="pay-label">应付金额</span>
        <span class="pay-amount">¥{{ money(order.finalAmount) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getSalesOrder } from '@/api/order'
import { statusText } from '@/utils/dict'

const route = useRoute()
const id = route.params.id
const loading = ref(true)
const order = ref(null)

const ORDER_TYPE_LABEL = { SALES: '销售订单', NORMAL: '销售订单', STANDARD: '销售订单', REPLENISHMENT: '补货订单', PURCHASE: '采购订单', RETURN: '退货订单' }

const orderTypeLabel = computed(() => {
  const t = order.value?.orderType
  if (!t) return '-'
  return ORDER_TYPE_LABEL[t] || t
})

const lineTree = computed(() => {
  const lines = order.value?.lines || []
  return lines
    .filter(l => !l.bomParentLineId)
    .map(l => ({
      ...l,
      children: lines.filter(c => String(c.bomParentLineId) === String(l.id))
    }))
})

function money(v) {
  return Number(v == null ? 0 : v).toFixed(2)
}
// 行折前小计（数量 × 单价），用于展示
function lineLineSubtotal(line) {
  const qty = Number(line.qty || 0)
  const price = Number(line.standardPriceInclTax || line.unitPrice || 0)
  return qty * price
}
// 解析促销命中文案（后端可能返回 JSON 字符串数组或数组）
const promoMessages = computed(() => {
  const raw = order.value?.promoMessages
  if (!raw) return []
  if (Array.isArray(raw)) return raw.filter(Boolean)
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter(Boolean) : [String(raw)]
  } catch (e) {
    return String(raw).split(/\n|；|;/).map(x => x.trim()).filter(Boolean)
  }
})
function sumLines(key) {
  return (order.value?.lines || []).reduce((acc, l) => acc + Math.abs(Number(l[key] || 0)), 0)
}
const totalProductDiscount = computed(() => sumLines('productDiscountAmount'))
const totalLineDiscount = computed(() => sumLines('lineDiscountAmount'))
const totalPromoDiscount = computed(() => sumLines('promoDiscountAmount'))
function rateLabel(r) {
  if (r == null || r === '') return '-'
  const n = Number(r)
  if (isNaN(n)) return String(r)
  return (n * 100).toFixed(n < 1 ? 0 : 0) + '%'
}
function hasDiscount(line) {
  return Number(line.lineDiscountAmount || 0) > 0 ||
    Number(line.promoDiscountAmount || 0) > 0 ||
    Number(line.headerDiscountAmount || 0) > 0
}
function fmtDate(v) {
  if (!v) return '-'
  return String(v).substring(0, 19).replace('T', ' ')
}

onMounted(async () => {
  try {
    const res = await getSalesOrder(id)
    order.value = res?.data || null
  } catch (e) {
    order.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.loading, .empty { padding: 40px 16px; text-align: center; }
.detail-body { padding-bottom: 20px; }
.status-bar { padding: 16px 20px; color: #fff; background: var(--dms-m-head-gradient, linear-gradient(135deg,#2e6ba8,#5a95d0)); }
.status-bar .st-text { font-size: 18px; font-weight: 600; }
.status-bar .st-code { font-size: 13px; opacity: .9; margin-top: 4px; }
.st-draft, .st-submitted, .st-approved, .st-completed, .st-active,
.st-rejected, .st-cancelled, .st-shipping, .st-receiving,
.st-partial_received, .st-partial_shipped, .st-partial_cancelled,
.st-pending_approval, .st-confirmed { background: var(--dms-m-head-gradient, linear-gradient(135deg,#2e6ba8,#5a95d0)); }
.line-list { padding: 4px 0; }
.line-card { padding: 10px 16px; border-bottom: 1px solid var(--dms-divider-color); }
.line-card:last-child { border-bottom: 0; }
.line-card.is-child { background: var(--dms-gray-50); padding-left: 24px; }
.line-card.is-gift { opacity: .85; }
.line-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.line-title { font-weight: 600; font-size: 14px; flex: 1; }
.child-prefix { color: var(--dms-text-4); }
.line-spec { font-size: 12px; color: var(--dms-text-4); margin-top: 4px; }
.line-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; margin-top: 8px; }
.line-grid.line-grid-3 { grid-template-columns: repeat(3, 1fr); }
.line-grid .g { display: flex; flex-direction: column; font-size: 12px; color: var(--dms-text-4); }
.line-grid .g b { color: var(--dms-text-1); font-weight: 600; font-size: 13px; margin-top: 2px; font-variant-numeric: tabular-nums; }
.line-promos { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.line-promos .lp { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 999px; }
.lp-promo { color: #b45309; background: #fef3c7; }
.lp-line { color: #2e6ba8; background: #e3eefa; }
.lp-header { color: #15803d; background: #dcfce7; }
.line-final { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; font-size: 14px; }
.line-final b { color: var(--dms-m-amber-deep, #b45309); font-size: 16px; }
.promo-hit { display: flex; align-items: flex-start; gap: 8px; padding: 10px 16px; font-size: 12.5px; color: #92400e; line-height: 1.5; }
.promo-hit + .promo-hit { border-top: 1px dashed var(--dms-divider-color, #e3e9f2); }
.promo-hit-ic { color: #d97706; font-size: 16px; margin-top: 1px; flex: none; }
.val-discount { color: var(--dms-m-amber-deep, #b45309); font-weight: 700; font-variant-numeric: tabular-nums; }
.pay-bar {
  margin: 12px 16px 4px; background: var(--dms-m-navy, #2e6ba8); border-radius: 14px;
  padding: 16px 18px; display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 6px 16px rgba(46,107,168,.28);
}
.pay-bar .pay-label { color: #cdddf0; font-size: 13px; }
.pay-bar .pay-amount { color: #fff; font-size: 24px; font-weight: 800; font-variant-numeric: tabular-nums; }
.child-list { margin-top: 8px; border-left: 2px solid var(--dms-gray-200); }
.total-title { font-weight: 600; }
.total-value { color: var(--dms-color-danger); font-weight: 700; font-size: 16px; }
</style>
