<template>
  <div class="m-layout">
    <div class="m-content">
      <router-view v-slot="{ Component }">
        <keep-alive :max="6">
          <component :is="Component" :key="$route.fullPath" />
        </keep-alive>
      </router-view>
    </div>
    <van-tabbar route>
      <van-tabbar-item to="/mobile/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/mobile/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item to="/mobile/surgery-reports">
        <template #icon>
          <SurgeryIcon :size="22" class="tab-surgery-icon" />
        </template>
        报台
      </van-tabbar-item>
      <van-tabbar-item to="/mobile/approvals" icon="todo-list-o" :badge="approvalBadge || ''">审批</van-tabbar-item>
      <van-tabbar-item to="/mobile/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import SurgeryIcon from '@/components/SurgeryIcon.vue'

const route = useRoute()

const approvalBadge = ref(0)

async function loadBadges() {
  try {
    const todo = await request({ url: '/api/approval/tasks/my-todo', method: 'get', params: { page: 1, size: 1 } })
    approvalBadge.value = Number(todo?.data?.total || 0)
  } catch (e) {
    // ignore badge load errors
  }
}

onMounted(loadBadges)
// 路由切换（含审批完成返回、tab 切换）时刷新角标，保证数字及时更新
watch(() => route.path, (p, old) => { if (p !== old) loadBadges() })
</script>

<style scoped>
.m-layout {
  min-height: 100vh;
  background: var(--dms-bg-page);
  padding-bottom: var(--dms-mobile-tabbar-height-safe);
}
.m-content {
  min-height: 100vh;
}
.tab-surgery-icon {
  color: inherit;
}
</style>
