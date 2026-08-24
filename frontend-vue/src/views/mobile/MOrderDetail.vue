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
        <van-cell title="经销商" :value="dealerDisplay" />
        <van-cell title="仓库" :value="order.warehouseName || '-'" />
        <van-cell title="订单日期" :value="fmtDate(order.orderDate || order.createdAt)" />
        <van-cell v-if="order.expectedDate" title="期望到货" :value="fmtDate(order.expectedDate)" />
        <van-cell title="创建时间" :value="fmtDate(order.createdAt)" />
        <van-cell v-if="order.remark" title="备注" :value="order.remark" />
      </van-cell-group>

      <van-cell-group inset title="产品明细" style="margin-top:10px">
        <div v-if="lines.length">
          <van-cell
            v-for="(line, idx) in lines" :key="idx"
            :title="lineTitle(line)"
            :label="'数量 ' + (line.qty || 0) + ' × 单价 ¥' + Number(line.unitPrice || 0).toFixed(2)"
            :value="'¥ ' + Number((line.qty || 0) * (line.unitPrice || 0)).toFixed(2)"
          />
          <van-cell title="合计" title-class="total-title" :value="'¥ ' + totalAmount" value-class="total-value" />
        </div>
        <van-empty v-else description="暂无明细" />
      </van-cell-group>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getResource } from '@/api/crud'
import request from '@/utils/request'
import { statusText } from '@/utils/dict'

const route = useRoute()
const id = route.params.id
const loading = ref(true)
const order = ref(null)
const lines = ref([])
const productMap = ref({})
const dealerName = ref('')

const orderTypeLabel = computed(() => {
  const t = order.value?.orderType
  if (!t) return '-'
  const map = { SALES: '销售订单', PURCHASE: '采购订单', NORMAL: '常规', SHORTAGE: '缺货补料', CUSTOM: '定制', EMERGENCY: '应急', URGENT: '紧急采购' }
  return map[t] || t
})

const dealerDisplay = computed(() => order.value?.dealerName || dealerName.value || '-')

const totalAmount = computed(() => lines.value.reduce((s, l) => s + (Number(l.qty || 0) * Number(l.unitPrice || 0)), 0).toFixed(2))

function lineTitle(line) {
  const name = line.productName || productMap.value[line.productId]?.name || line.productCode || ''
  const code = line.productCode || productMap.value[line.productId]?.code || ''
  const spec = line.spec || productMap.value[line.productId]?.spec || ''
  const label = [code, name].filter(Boolean).join(' ')
  return spec ? `${label} / ${spec}` : label
}

function fmtDate(v) {
  if (!v) return '-'
  return String(v).substring(0, 19).replace('T', ' ')
}

onMounted(async () => {
  try {
    const res = await getResource('/api/orders', id)
    const payload = res.data || null
    if (payload) {
      order.value = payload.order || payload
      const rawLines = payload.lines || payload.items || []
      lines.value = rawLines
      const dealerId = order.value?.dealerId
      if (dealerId) {
        try {
          const d = await getResource('/api/dealers', dealerId)
          dealerName.value = d.data?.name || d.data?.dealerName || ''
        } catch (e) { /* ignore */ }
      }
      const productIds = [...new Set(rawLines.map(l => l.productId).filter(Boolean))]
      if (productIds.length) {
        try {
          const { data } = await request({ url: '/api/lookups/products', method: 'get', params: { limit: 500 } })
          const list = Array.isArray(data) ? data : (data?.list || [])
          const map = {}
          list.forEach(p => { map[p.id] = { name: p.name || p.nameCn, code: p.code, spec: p.spec } })
          productMap.value = map
        } catch (e) { /* ignore */ }
      }
    }
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
.total-title { font-weight: 600; }
.total-value { color: var(--dms-color-danger); font-weight: 700; }
</style>