<template>
  <div>
    <el-card shadow="never" class="welcome">
      <h2>欢迎使用 DMS 通用经销商管理系统</h2>
      <p>当前登录：<b>{{ userStore.username }}</b>（{{ userTypeLabel }}）· 租户 {{ userStore.user.tenantId || '-' }}</p>
      <el-tag type="success">Vue3 + Element Plus 版</el-tag>
    </el-card>

    <el-row :gutter="16" class="cards">
      <el-col :span="6" v-for="s in shortcuts" :key="s.key">
        <el-card shadow="hover" class="shortcut" @click="$router.push('/m/' + s.key)">
          <el-icon :size="30" color="#2C4B8E"><component :is="s.icon" /></el-icon>
          <div class="sc-label">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <Dashboard />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import Dashboard from '@/views/Dashboard.vue'

const userStore = useUserStore()
const userTypeLabel = computed(() => (userStore.userType === 'vendor' ? '厂商' : userStore.userType === 'dealer' ? '经销商' : '用户'))

const shortcuts = [
  { key: 'products', icon: 'Goods', label: '产品管理' },
  { key: 'dealers', icon: 'OfficeBuilding', label: '经销商管理' },
  { key: 'orders', icon: 'Sell', label: '销售订单' },
  { key: 'inventory', icon: 'Box', label: '库存查询' }
]
</script>

<style scoped>
.welcome { margin-bottom: 16px; }
.welcome h2 { margin: 0 0 8px; }
.welcome p { color: #666; margin: 0 0 10px; }
.cards { margin-top: 4px; margin-bottom: 16px; }
.shortcut { text-align: center; cursor: pointer; padding: 10px 0; }
.sc-label { margin-top: 10px; font-size: 14px; color: #333; }
</style>
