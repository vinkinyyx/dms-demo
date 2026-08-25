<template>
  <div class="m-home">
    <div class="m-header">
      <div class="m-header-row">
        <DmsLogo :size="30" class="m-header-logo" inverse />
        <div class="hi">你好，{{ userStore.username }}</div>
        <div class="date">{{ today }}</div>
      </div>
    </div>

    <div class="kpi-block">
      <div class="kpi-title">今日业绩</div>
      <van-grid :column-num="2" :border="false" gutter="8" class="kpi-grid">
        <van-grid-item>
          <div class="kpi-v">¥ {{ fmtAmount(todayKpi.totalSales) }}</div>
          <div class="kpi-l">今日销售金额</div>
        </van-grid-item>
        <van-grid-item>
          <div class="kpi-v">{{ todayKpi.totalOrders || 0 }}</div>
          <div class="kpi-l">今日订单数</div>
        </van-grid-item>
      </van-grid>
    </div>

    <div class="kpi-block">
      <div class="kpi-title">本月业绩</div>
      <van-grid :column-num="2" :border="false" gutter="8" class="kpi-grid">
        <van-grid-item>
          <div class="kpi-v">¥ {{ fmtAmount(monthKpi.totalSales) }}</div>
          <div class="kpi-l">本月销售金额</div>
        </van-grid-item>
        <van-grid-item>
          <div class="kpi-v">{{ monthKpi.totalOrders || 0 }}</div>
          <div class="kpi-l">本月订单数</div>
        </van-grid-item>
        <van-grid-item>
          <div class="kpi-v">{{ monthKpi.totalSurgeries || 0 }}</div>
          <div class="kpi-l">本月报台数</div>
        </van-grid-item>
        <van-grid-item @click="$router.push('/mobile/dashboard')">
          <div class="kpi-v more">查看趋势 ›</div>
          <div class="kpi-l">业绩详情</div>
        </van-grid-item>
      </van-grid>
    </div>

    <div class="sec-title">快捷入口</div>
    <van-grid :column-num="4" :border="false" class="quick-grid">
      <van-grid-item v-for="q in quicks" :key="q.key" :to="q.to" clickable>
        <SurgeryIcon v-if="q.key === 'surgery'" :size="30" bg="#52c41a" />
        <van-icon v-else :name="q.icon" :color="q.color" size="28" />
        <div class="quick-l">{{ q.label }}</div>
      </van-grid-item>
    </van-grid>

    <div class="sec-title">最近订单</div>
    <van-cell-group inset>
      <van-cell
        v-for="o in recentOrders" :key="o.id"
        :title="o.code"
        :label="o.dealerName || '-'"
        is-link
        @click="$router.push('/mobile/orders/' + o.id)"
      >
        <template #value>
          <div class="amt">¥ {{ o.finalAmount || 0 }}</div>
          <van-tag :type="statusTagType(o.status)" size="mini">{{ statusText(o.status) }}</van-tag>
        </template>
      </van-cell>
      <van-empty v-if="!loadingRecent && !recentOrders.length" description="暂无订单" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { listSalesOrders } from '@/api/order'
import { getKpi } from '@/api/dashboard'
import { statusText, statusTagType } from '@/utils/dict'
import SurgeryIcon from '@/components/SurgeryIcon.vue'

const router = useRouter()
const userStore = useUserStore()
const today = new Date().toLocaleDateString('zh-CN')

const todayKpi = reactive({})
const monthKpi = reactive({})
const recentOrders = ref([])
const loadingRecent = ref(false)

const quicks = [
  { key: 'order-create', label: '下销售订单', icon: 'orders-o', color: '#1677ff', to: '/mobile/orders/create' },
  { key: 'surgery',      label: '填手术报台', icon: 'edit-o',    color: '#52c41a', to: '/mobile/surgery-reports/create' },
  { key: 'orders',       label: '我的订单',   icon: 'list-switching', color: '#fa8c16', to: '/mobile/orders' },
  { key: 'dashboard',    label: '我的业绩',   icon: 'chart-trending-o', color: '#1677ff', to: '/mobile/dashboard' },
  { key: 'scan-receive', label: '扫码收货', icon: 'logistics', color: '#722ed1', to: '/mobile/scan-receive' },
  { key: 'scan-inv', label: '库存扫码', icon: 'search', color: '#13c2c2', to: '/mobile/scan-inventory' },
  { key: 'stocktake', label: '库存盘点', icon: 'todo-list-o', color: '#eb2f96', to: '/mobile/scan-inventory' },
  { key: 'approvals', label: '移动审批', icon: 'balance-list-o', color: '#fa541c', to: '/mobile/approvals' }
]

function fmtAmount(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  try { Object.assign(todayKpi, (await getKpi({ period: 'today' })).data || {}) } catch (e) { /* ignore */ }
  try { Object.assign(monthKpi, (await getKpi({ period: 'month' })).data || {}) } catch (e) { /* ignore */ }
  loadingRecent.value = true
  try {
    const r = await listSalesOrders({ page: 1, size: 5 })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    recentOrders.value = list.slice(0, 5)
  } catch (e) { /* ignore */ }
  finally { loadingRecent.value = false }
}

load()
</script>

<style scoped>
.m-header { background: linear-gradient(135deg, var(--dms-color-primary), var(--dms-blue-700)); color: var(--dms-text-inverse); padding: 24px 20px 32px; }
.m-header-row { display: flex; align-items: center; gap: 12px; }
.m-header-logo { filter: drop-shadow(0 2px 6px rgba(0,0,0,.22)); flex: 0 0 auto; }
.m-header .hi { flex: 1; font-size: 20px; font-weight: 600; }
.hi { font-size: 20px; font-weight: 600; }
.date { opacity: .85; margin-top: 6px; font-size: 13px; }
.kpi-block { background: var(--dms-bg-container); margin: -16px 12px 12px; border-radius: 12px; padding: 14px 8px 6px; box-shadow: 0 2px 8px rgba(0,0,0,.05); }
.kpi-title { font-size: 14px; font-weight: 600; color: var(--dms-text-2); margin: 0 6px 8px; }
.kpi-v { font-size: 22px; font-weight: 700; color: var(--dms-color-primary); }
.kpi-v.more { color: var(--dms-color-primary); font-size: 14px; font-weight: 500; }
.kpi-l { font-size: 12px; color: var(--dms-text-4); margin-top: 4px; }
.quick-grid { margin: 0 8px; background: var(--dms-bg-container); border-radius: 12px; }
.quick-l { font-size: 12px; color: var(--dms-text-3); margin-top: 6px; }
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; color: var(--dms-text-2); }
.amt { color: var(--dms-color-danger); font-weight: 600; font-size: 13px; }
</style>