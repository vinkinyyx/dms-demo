<template>
  <div>
    <van-nav-bar title="我的业绩" />
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="kpi-block">
        <div class="kpi-title">本月销售</div>
        <van-grid :column-num="2" :border="false" gutter="8" class="kpi-grid">
          <van-grid-item>
            <div class="kpi-v">¥ {{ fmtAmount(monthKpi.totalSales) }}</div>
            <div class="kpi-l">销售金额</div>
          </van-grid-item>
          <van-grid-item>
            <div class="kpi-v">{{ monthKpi.totalOrders || 0 }}</div>
            <div class="kpi-l">销售订单数</div>
          </van-grid-item>
        </van-grid>
      </div>

      <div class="sec-title">近 12 月销售趋势</div>
      <van-cell-group inset>
        <div class="trend-body">
          <div v-for="t in salesTrend" :key="t.month" class="trend-row">
            <span class="trend-l">{{ t.month }}</span>
            <div class="trend-bar-wrap">
              <div class="trend-bar" :style="{ width: barPct(t.amount) + '%' }"></div>
            </div>
            <span class="trend-v">¥ {{ fmtAmount(t.amount) }}</span>
          </div>
          <van-empty v-if="!salesTrend.length" description="暂无数据" />
        </div>
      </van-cell-group>

      <div class="sec-title">本月 TOP 经销商</div>
      <van-cell-group inset>
        <van-cell
          v-for="(d, idx) in topDealers" :key="idx"
          :title="d.name"
        >
          <template #icon>
            <van-tag :type="idx < 3 ? 'danger' : 'primary'" style="margin-right:8px">{{ idx + 1 }}</van-tag>
          </template>
          <template #value>
            <span class="amt">¥ {{ fmtAmount(d.value) }}</span>
          </template>
        </van-cell>
        <van-empty v-if="!topDealers.length" description="暂无数据" />
      </van-cell-group>

      <div class="sec-title">订单状态分布（本月）</div>
      <van-cell-group inset>
        <van-cell
          v-for="(s, idx) in orderFunnel" :key="idx"
          :title="s.name"
          :value="s.value"
        />
        <van-empty v-if="!orderFunnel.length" description="none" />
      </van-cell-group>

      <div class="sec-title">更多报表</div>
      <van-cell-group inset>
        <van-cell title="销售业绩排行" is-link @click="goReport('sales-ranking')" />
        <van-cell title="产品销售 TOP10" is-link @click="goReport('product-top10')" />
        <van-cell title="应收款项" is-link @click="goReport('receivables')" />
        <van-cell title="订单追溯" is-link @click="goReport('order-trace')" />
        <van-cell title="手术报台统计" is-link @click="goReport('surgery-stats')" />
        <van-cell title="PC 端报表中心更全" is-link @click="goReport(null)" />
      </van-cell-group>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getKpi, getSalesTrend, getTopDealers, getOrderFunnel } from '@/api/dashboard'

const monthKpi = reactive({})
const salesTrend = ref([])
const topDealers = ref([])
const orderFunnel = ref([])
const refreshing = ref(false)
const router = useRouter()

const maxAmount = computed(() => Math.max(1, ...salesTrend.value.map(t => Number(t.amount) || 0)))
function barPct(v) { return Math.max(2, Math.round((Number(v || 0) / maxAmount.value) * 100)) }
function fmtAmount(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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
    orderFunnel.value = d.map(x => ({ name: x.name, value: x.value }))
  } catch (e) { /* ignore */ }
}

async function onRefresh() {
  await loadAll()
  refreshing.value = false
  showToast('已刷新')
}

function goReport(key) { if (key) router.push({ path: '/reports', query: { key } }); else showToast('PC 端报表中心更全，请登录') }

onMounted(loadAll)
</script>

<style scoped>
.kpi-block { background: var(--dms-bg-container); margin: 10px 12px; border-radius: 12px; padding: 14px 8px; box-shadow: 0 2px 8px rgba(0,0,0,.05); }
.kpi-title { font-size: 14px; font-weight: 600; color: var(--dms-text-2); margin: 0 6px 8px; }
.kpi-v { font-size: 22px; font-weight: 700; color: var(--dms-color-primary); }
.kpi-l { font-size: 12px; color: var(--dms-text-4); margin-top: 4px; }
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; color: var(--dms-text-2); }
.trend-body { padding: 8px 16px 12px; }
.trend-row { display: flex; align-items: center; padding: 6px 0; font-size: 13px; }
.trend-l { width: 64px; color: var(--dms-text-3); }
.trend-bar-wrap { flex: 1; background: var(--dms-gray-100); height: 8px; border-radius: 4px; margin: 0 8px; overflow: hidden; }
.trend-bar { background: linear-gradient(90deg, var(--dms-color-primary), var(--dms-color-primary)); height: 100%; }
.trend-v { color: var(--dms-color-danger); font-weight: 600; min-width: 88px; text-align: right; }
.amt { color: var(--dms-color-danger); font-weight: 600; }
</style>
