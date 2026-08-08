<template>
  <div>
    <div class="profile-header">
      <van-icon name="user-circle-o" size="56" color="#fff" />
      <div class="name">{{ userStore.user?.username || userStore.username }}</div>
      <div class="meta" v-if="userStore.user?.userType">{{ userStore.user.userType }}</div>
    </div>

    <van-cell-group inset style="margin-top: -24px;">
      <van-cell title="账号" :value="userStore.user?.username || '-'" />
      <van-cell title="角色" :value="userStore.user?.role || userStore.userType || '-'" />
      <van-cell title="经销商" :value="userStore.user?.dealerName || (userStore.user?.dealerId ? '#' + userStore.user.dealerId : '-')" />
      <van-cell title="手机" :value="userStore.user?.phone || '-'" />
      <van-cell title="邮箱" :value="userStore.user?.email || '-'" />
    </van-cell-group>

    <div style="margin: 20px 16px;">
      <van-button block plain type="danger" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

async function onLogout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号？' })
  } catch (e) { return }
  try {
    await userStore.logout()
  } catch (e) { /* ignore */ }
  showToast('已退出')
  router.replace('/mobile/login')
}
</script>

<style scoped>
.profile-header {
  background: linear-gradient(135deg, #2C4B8E, #1E3A5F);
  color: #fff;
  padding: 36px 20px 48px;
  text-align: center;
}
.name { font-size: 20px; font-weight: 600; margin-top: 8px; }
.meta { opacity: .85; font-size: 13px; margin-top: 4px; }
</style>