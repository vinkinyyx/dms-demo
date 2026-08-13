<template>
  <div>
    <van-nav-bar title="订单详情" left-arrow @click-left="$router.back()" />
    <div v-if="loading" class="loading"><van-loading type="spinner" /></div>
    <div v-else-if="!data" class="empty"><van-empty description="订单不存在" /></div>
    <div v-else class="detail-body">
      <div class="status-bar" :class="'st-' + (data.status || '').toLowerCase()">
        <div class="st-text">{{ statusText(data.status) }}</div>
        <div class="st-code">{{ data.code }}</div>
      </div>

      <van-cell-group inset title="基本信息" style="margin-top:10px">
        <van-cell title="单号" :value="data.code" />
        <van-cell title="订单类型" :value="orderTypeLabel" />
        <van-cell title="经销商" :value="data.dealerName || '-'" />
        <van-cell title="仓库" :value="data.warehouseName || '-'" />
        <van-cell title="订单日期" :value="(data.orderDate || data.createdAt || '').toString().substring(0, 10)" />
        <van-cell v-if="data.expectedDate" title="期望到货" :value="data.expectedDate" />
        <van-cell title="创建时间" :value="fmtDate(data.createdAt)" />
        <van-cell v-if="data.remark" title="备注" :value="data.remark" />
      </van-cell-group>

      <van-cell-group inset title="产品明细" style="margin-top:10px">
        <div v-if="lines.length">
          <van-cell
            v-for="(line, idx) in lines" :key="idx"
            :title="(line.productName || line.productCode) + (line.spec ? ' / ' + line.spec : '')"
            :label="'数量 ' + (line.qty || 0) + ' × 单价 ¥' + (line.unitPrice || 0)"
            :value="'¥ ' + ((line.qty || 0) * (line.unitPrice || 0)).toFixed(2)"
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
import { statusText } from '@/utils/dict'

const route = useRoute()
const id = route.params.id
const loading = ref(true)
const data = ref(null)

const lines = computed(() => data.value?.lines || data.value?.items || [])
const orderTypeLabel = computed(() => {
  const t = data.value?.orderType
  if (!t) return '-'
  const map = { NORMAL: '常规', SHORTAGE: '缺货补料', CUSTOM: '定制', EMERGENCY: '应急', URGENT: '紧急采购' }
  return map[t] || t
})
const totalAmount = computed(() => lines.value.reduce((s, l) => s + (Number(l.qty || 0) * Number(l.unitPrice || 0)), 0).toFixed(2))

function fmtDate(v) {
  if (!v) return '-'
  return String(v).substring(0, 19).replace('T', ' ')
}

onMounted(async () => {
  try {
    const res = await getResource('/api/orders', id)
    data.value = res.data || null
  } catch (e) {
    data.value = null
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