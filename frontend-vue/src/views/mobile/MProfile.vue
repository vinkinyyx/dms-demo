<template>
  <div>
    <div class="profile-header">
      <van-icon name="user-circle-o" size="56" color="#fff" />
      <div class="name">{{ userStore.user?.displayName || userStore.user?.name || userStore.user?.username || userStore.username }}</div>
      <div class="meta" v-if="userTypeLabel">{{ userTypeLabel }}</div>
    </div>

    <van-cell-group inset style="margin-top: -24px;">
      <van-cell title="消息中心" icon="bell" is-link to="/mobile/messages" />
      <van-cell title="我的审批" icon="todo-list-o" is-link to="/mobile/approvals" />
      <van-cell title="切换到电脑版" icon="desktop-o" is-link @click="onSwitchPc" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px;">
      <van-cell title="账号" :value="userStore.user?.username || '-'" />
      <van-cell title="角色" :value="userStore.user?.roleName || userStore.user?.role || userTypeLabel || '-'" />
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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { onMounted } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '@/store/user'
import { setViewPref, clearViewPref } from '@/utils/device'

const USER_TYPE_LABELS = {
  vendor: '厂商',
  manufacturer: '厂商',
  dealer: '经销商',
  dealer_admin: '经销商管理员',
  platform: '平台管理员',
  admin: '管理员'
}

const router = useRouter()
const userStore = useUserStore()

const userTypeLabel = computed(() => {
  const t = userStore.user?.userType
  if (!t) return ''
  return USER_TYPE_LABELS[t] || USER_TYPE_LABELS[String(t).toLowerCase()] || t
})

onMounted(() => {
  if (!userStore.user?.username) userStore.fetchInfo().catch(() => {})
})

function onSwitchPc() {
  // 标记本次会话强制 PC 形态，随后跳到 PC 工作台；守卫据此不再自动弹回移动端
  setViewPref('pc')
  showToast('已切换到电脑版')
  router.push('/home')
}

async function onLogout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号？' })
  } catch (e) { return }
  try {
    await userStore.logout()
  } catch (e) { /* ignore */ }
  clearViewPref()
  showToast('已退出')
  router.replace('/mobile/login')
}
</script>

<style scoped>
.profile-header {
  background: linear-gradient(135deg, var(--dms-color-primary), var(--dms-blue-700));
  color: var(--dms-text-inverse);
  padding: 36px 20px 48px;
  text-align: center;
}
.name { font-size: 20px; font-weight: 600; margin-top: 8px; }
.meta { opacity: .85; font-size: 13px; margin-top: 4px; }
</style>
