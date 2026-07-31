<template>
  <div class="mobile-dashboard">
    <van-nav-bar title="仪表盘" />
    <div class="content">
      <van-grid :column-num="2" gutter="10">
        <van-grid-item icon="orders-o" text="今日订单" :badge="stats.todayOrders || 0" @click="$router.push('/mobile/orders/create')" />
        <van-grid-item icon="todo-list-o" text="本月销售" :badge="stats.monthAmount || 0" />
        <van-grid-item icon="balance-o" text="应收账款" :badge="stats.receivable || 0" />
        <van-grid-item icon="chart-trending-o" text="销售趋势" />
      </van-grid>
      <van-cell-group inset title="最近订单" style="margin-top: 20px;">
        <van-cell v-for="o in recentOrders" :key="o.id" :title="o.code" :value="o.status" :label="o.createdAt" />
      </van-cell-group>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const stats = ref({})
const recentOrders = ref([])

onMounted(async () => {
  try {
    const res = await request.get('/api/dashboard/mobile')
    if (res.data?.data) {
      stats.value = res.data.data.stats || {}
      recentOrders.value = res.data.data.recentOrders || []
    }
  } catch (e) {
    console.error('加载仪表盘失败', e)
  }
})
</script>

<style scoped>
.mobile-dashboard { padding: 16px; background: #f5f5f5; min-height: 100vh; }
.content { padding: 0 8px; }
</style>
