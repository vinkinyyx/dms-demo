<template>
  <div class="m-page-scroll">
    <van-nav-bar title="我的业绩" />
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="m-perf-card">
        <div class="pl">本月销售额（元）</div>
        <div class="pv">¥{{ fmtAmount(monthKpi.totalSales) }}</div>
        <div class="pt">订单 {{ monthKpi.totalOrders || 0 }} 单 · 环比数据见 PC 端</div>
        <div class="m-bars" v-if="salesTrend.length">
          <div
            v-for="(t, i) in salesTrend.slice(-7)" :key="t.month"
            class="b" :class="{ cur: i === salesTrend.slice(-7).length - 1 }"
          >
            <i :style="{ height: barPct(t.amount) + '%' }"></i>
            <span>{{ shortMonth(t.month) }}</span>
          </div>
        </div>
      </div>

      <div class="m-section">经销商排行（本月）</div>
      <div class="m-rank-card" v-if="topDealers.length">
        <div v-for="(d, idx) in topDealers.slice(0, 6)" :key="idx" class="m-rank-row">
          <span class="rk" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
          <div>
            <div class="rn">{{ d.name }}</div>
            <div class="rm">本月交易额</div>
          </div>
          <span class="rv">¥{{ fmtCompact(d.value) }}</span>
        </div>
      </div>
      <van-empty v-else description="暂无排行数据" />

      <div class="m-section">订单状态分布（本月）</div>
      <div class="m-rank-card" v-if="orderFunnel.length">
        <div v-for="(s, idx) in orderFunnel" :key="idx" class="m-rank-row">
          <span class="rk">{{ idx + 1 }}</span>
          <div class="rn">{{ s.name }}</div>
          <span class="rv">{{ s.value }} 单</span>
        </div>
      </div>
      <van-empty v-else description="暂无状态数据" />

      <div style="height: 20px"></div>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { getKpi, getSalesTrend, getTopDealers, getOrderFunnel } from '@/api/dashboard'

const monthKpi = reactive({})
const salesTrend = ref([])
const topDealers = ref([])
const orderFunnel = ref([])
const refreshing = ref(false)

const maxAmount = computed(() => Math.max(1, ...salesTrend.value.map(t => Number(t.amount) || 0)))
function barPct(v) { return Math.max(4, Math.round((Number(v || 0) / maxAmount.value) * 100)) }
function shortMonth(m) { return String(m || '').replace(/^\d{4}-/, '').replace(/^0/, '') + '月' }
function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtCompact(v) {
  const n = Number(v || 0)
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return n.toLocaleString('zh-CN')
}

async function loadAll() {
  try { Object.assign(monthKpi, (await getKpi({ period: 'month' })).data || {}) } catch (e) { /* ignore */ }
  try { salesTrend.value = (await getSalesTrend()).data || [] } catch (e) { /* ignore */ }
  try {
    const r = await getTopDealers({ period: 'month' })
    const d = r.data || []
    topDealers.value = d.map(x => ({ name: x.name, value: x.value }))
  } catch (e) { /* ignore */ }
  try {
    const r = await getOrderFunnel({ period: 'month' })
    const d = r.data || []
    const statusNameMap = { DRAFT: '草稿', SUBMITTED: '已提交', PENDING_APPROVAL: '审批中', APPROVED: '已审批', REJECTED: '已驳回', COMPLETED: '已完成', CANCELLED: '已取消', SHIPPED: '已发货', PARTIAL_SHIPPED: '部分发货' }
    orderFunnel.value = d.map(x => ({ name: statusNameMap[x.name] || x.name, value: x.value }))
  } catch (e) { /* ignore */ }
}

async function onRefresh() {
  await loadAll()
  refreshing.value = false
  showToast('已刷新')
}

onMounted(loadAll)
</script>
