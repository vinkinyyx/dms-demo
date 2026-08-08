<template>
  <div class="m-home">
    <div class="m-header">
      <div class="hi">你好，{{ userStore.username }}</div>
      <div class="date">{{ today }}</div>
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
        <van-icon :name="q.icon" :color="q.color" size="28" />
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
import { listResource } from '@/api/crud'
import { getKpi } from '@/api/dashboard'
import { statusText, statusTagType } from '@/utils/dict'

const router = useRouter()
const userStore = useUserStore()
const today = new Date().toLocaleDateString('zh-CN')

const todayKpi = reactive({})
const monthKpi = reactive({})
const recentOrders = ref([])
const loadingRecent = ref(false)

const quicks = [
  { key: 'order-create', label: '下销售订单', icon: 'orders-o', color: '#1989fa', to: '/mobile/orders/create' },
  { key: 'surgery',      label: '填手术报台', icon: 'edit-o',    color: '#07c160', to: '/mobile/surgery-reports/create' },
  { key: 'orders',       label: '我的订单',   icon: 'list-switching', color: '#ff976a', to: '/mobile/orders' },
  { key: 'dashboard',    label: '我的业绩',   icon: 'chart-trending-o', color: '#2C4B8E', to: '/mobile/dashboard' }
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
    const r = await listResource('/api/orders', { page: 1, size: 5 })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    recentOrders.value = list.slice(0, 5)
  } catch (e) { /* ignore */ }
  finally { loadingRecent.value = false }
}

load()
</script>

<style scoped>
.m-header { background: linear-gradient(135deg, #2C4B8E, #1E3A5F); color: #fff; padding: 24px 20px 32px; }
.hi { font-size: 20px; font-weight: 600; }
.date { opacity: .85; margin-top: 6px; font-size: 13px; }
.kpi-block { background: #fff; margin: -16px 12px 12px; border-radius: 12px; padding: 14px 8px 6px; box-shadow: 0 2px 8px rgba(0,0,0,.05); }
.kpi-title { font-size: 14px; font-weight: 600; color: #323233; margin: 0 6px 8px; }
.kpi-v { font-size: 22px; font-weight: 700; color: #2C4B8E; }
.kpi-v.more { color: #1989fa; font-size: 14px; font-weight: 500; }
.kpi-l { font-size: 12px; color: #969799; margin-top: 4px; }
.quick-grid { margin: 0 8px; background: #fff; border-radius: 12px; }
.quick-l { font-size: 12px; color: #646566; margin-top: 6px; }
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; color: #323233; }
.amt { color: #ee0a24; font-weight: 600; font-size: 13px; }
</style>