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
            <div class="line-grid">
              <div class="g"><label>数量</label><b>{{ line.qty || 0 }}</b></div>
              <div class="g"><label>含税单价</label><b>¥{{ money(line.standardPriceInclTax || line.unitPrice) }}</b></div>
              <div class="g"><label>税率</label><b>{{ rateLabel(line.taxRate) }}</b></div>
              <div class="g"><label>税额</label><b>¥{{ money(line.taxAmount) }}</b></div>
            </div>
            <div v-if="hasDiscount(line)" class="line-discount">
              <span v-if="Number(line.lineDiscountAmount || 0) > 0">行折扣 -¥{{ money(line.lineDiscountAmount) }}</span>
              <span v-if="Number(line.promoDiscountAmount || 0) > 0">促销 -¥{{ money(line.promoDiscountAmount) }}</span>
              <span v-if="Number(line.headerDiscountAmount || 0) > 0">整单折扣分摊 -¥{{ money(line.headerDiscountAmount) }}</span>
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
                <div class="line-grid">
                  <div class="g"><label>数量</label><b>{{ child.qty || 0 }}</b></div>
                  <div class="g"><label>含税单价</label><b>¥{{ money(child.standardPriceInclTax || child.unitPrice) }}</b></div>
                  <div class="g"><label>税率</label><b>{{ rateLabel(child.taxRate) }}</b></div>
                  <div class="g"><label>税额</label><b>¥{{ money(child.taxAmount) }}</b></div>
                </div>
                <div v-if="hasDiscount(child)" class="line-discount">
                  <span v-if="Number(child.lineDiscountAmount || 0) > 0">行折扣 -¥{{ money(child.lineDiscountAmount) }}</span>
                  <span v-if="Number(child.promoDiscountAmount || 0) > 0">促销 -¥{{ money(child.promoDiscountAmount) }}</span>
                  <span v-if="Number(child.headerDiscountAmount || 0) > 0">整单折扣分摊 -¥{{ money(child.headerDiscountAmount) }}</span>
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

      <van-cell-group inset title="金额信息" style="margin-top:10px">
        <van-cell title="含税金额" :value="'¥' + money(order.amountInclTax)" />
        <van-cell v-if="Number(order.discountAmount || 0) > 0" title="整单折扣" :value="'-¥' + money(order.discountAmount)" />
        <van-cell title="不含税金额" :value="'¥' + money(order.amountExclTax)" />
        <van-cell title="税额" :value="'¥' + money(order.taxAmount)" />
        <van-cell title="最终金额" title-class="total-title" :value="'¥' + money(order.finalAmount)" value-class="total-value" />
      </van-cell-group>
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

const ORDER_TYPE_LABEL = { SALES: '销售订单', REPLENISHMENT: '补货订单', PURCHASE: '采购订单' }

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
.status-bar { padding: 16px 20px; color: var(--dms-text-inverse); }
.status-bar .st-text { font-size: 18px; font-weight: 600; }
.status-bar .st-code { font-size: 13px; opacity: .9; margin-top: 4px; }
.st-draft     { background: var(--dms-text-4); }
.st-submitted { background: var(--dms-color-warning); }
.st-approved,
.st-completed,
.st-active    { background: var(--dms-color-success); }
.st-rejected,
.st-cancelled { background: var(--dms-color-danger); }
.st-shipping,
.st-receiving,
.st-partial_received,
.st-partial_shipped,
.st-partial_cancelled { background: var(--dms-color-primary); }
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
.line-grid .g { display: flex; flex-direction: column; font-size: 12px; color: var(--dms-text-4); }
.line-grid .g b { color: var(--dms-text-1); font-weight: 500; font-size: 13px; margin-top: 2px; }
.line-discount { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 6px; font-size: 12px; color: var(--dms-color-danger); }
.line-final { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; font-size: 14px; }
.line-final b { color: var(--dms-color-danger); font-size: 16px; }
.child-list { margin-top: 8px; border-left: 2px solid var(--dms-gray-200); }
.total-title { font-weight: 600; }
.total-value { color: var(--dms-color-danger); font-weight: 700; font-size: 16px; }
</style>
