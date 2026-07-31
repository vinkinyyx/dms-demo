<template>
  <div class="m-home">
    <div class="m-header">
      <div class="hi">你好，{{ userStore.username }}</div>
      <div class="date">{{ today }}</div>
    </div>
    <van-grid :column-num="2" :border="false" class="kpi-grid">
      <van-grid-item v-for="k in kpis" :key="k.key">
        <div class="kpi-v">{{ k.value }}</div>
        <div class="kpi-l">{{ k.label }}</div>
      </van-grid-item>
    </van-grid>

    <div class="sec-title">快捷入口</div>
    <van-grid :column-num="4" :border="false" class="quick-grid">
      <van-grid-item v-for="q in quicks" :key="q.key" :to="q.to" clickable>
        <van-icon :name="q.icon" :color="q.color" size="28" />
        <div class="quick-l">{{ q.label }}</div>
      </van-grid-item>
    </van-grid>

    <div class="sec-title">最近订单</div>
    <van-cell-group inset>
      <van-cell v-for="o in recentOrders" :key="o.id" :title="o.code" :label="o.dealerName || o.supplierName"
        :value="'¥ ' + (o.finalAmount || 0)" is-link @click="$router.push('/mobile/orders')" />
      <van-empty v-if="!recentOrders.length" description="暂无订单" />
    </van-cell-group>

    <div style="margin:20px 16px;">
      <van-button block plain type="danger" @click="logout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { listResource } from '@/api/crud'
import { systemStats } from '@/api/admin'

const router = useRouter()
const userStore = useUserStore()
const today = new Date().toLocaleDateString('zh-CN')
const stats = ref({})
const recentOrders = ref([])

const kpis = computed(() => [
  { key: 'orders', label: '订单总数', value: stats.value.orders ?? '-' },
  { key: 'inventoryRecords', label: '库存记录', value: stats.value.inventoryRecords ?? '-' },
  { key: 'products', label: '产品数量', value: stats.value.products ?? '-' },
  { key: 'promotions', label: '促销规则', value: stats.value.promotions ?? '-' }
])

const quicks = [
  { key: 'order-create', label: '下订单', icon: 'orders-o', color: '#1989fa', to: '/mobile/orders/create' },
  { key: 'surgery', label: '手术报台', icon: 'edit-o', color: '#07c160', to: '/mobile/surgery-reports/create' },
  { key: 'trace', label: '订单追溯', icon: 'search-o', color: '#ff976a', to: '/mobile/report-order-trace' },
  { key: 'dashboard', label: '仪表盘', icon: 'chart-trending-o', color: '#2C4B8E', to: '/mobile/dashboard' }
]

async function load() {
  try { stats.value = (await systemStats()).data || {} } catch (e) { /* ignore */ }
  try {
    const r = await listResource('/api/orders', { page: 1, size: 5 })
    const d = r.data
    recentOrders.value = Array.isArray(d) ? d : (d.list || d.records || [])
  } catch (e) { /* ignore */ }
}
function logout() { userStore.reset(); router.replace('/mobile/login') }
load()
</script>

<style scoped>
.m-header { background: linear-gradient(135deg, #2C4B8E, #1E3A5F); color: #fff; padding: 24px 20px; }
.hi { font-size: 20px; font-weight: 600; }
.date { opacity: .85; margin-top: 6px; font-size: 13px; }
.kpi-grid { margin-top: -20px; }
.kpi-v { font-size: 22px; font-weight: 700; color: #2C4B8E; }
.kpi-l { font-size: 12px; color: #969799; margin-top: 4px; }
.quick-grid { margin: 0 8px; }
.quick-l { font-size: 12px; color: #646566; margin-top: 6px; }
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; }
</style>
