<template>
  <div class="m-page-scroll">
    <div class="profile-hero">
      <div class="avatar"><van-icon name="user-circle-o" size="56" color="#fff" /></div>
      <div class="pname">{{ userStore.user?.displayName || userStore.user?.name || userStore.user?.username || userStore.username }}</div>
      <div class="pmeta">
        <span v-if="userTypeLabel" class="ptag">{{ userTypeLabel }}</span>
        <span v-if="userStore.user?.dealerName">{{ userStore.user.dealerName }}</span>
      </div>
    </div>

    <div class="m-quick-mini">
      <router-link to="/mobile/messages" class="qm"><van-icon name="envelop-o" /><span>消息中心</span></router-link>
      <router-link to="/mobile/approvals" class="qm"><van-icon name="todo-list-o" /><span>我的审批</span></router-link>
      <router-link to="/mobile/dashboard" class="qm"><van-icon name="bar-chart-o" /><span>我的业绩</span></router-link>
      <a class="qm" @click="onSwitchPc"><van-icon name="desktop-o" /><span>电脑版</span></a>
    </div>

    <div class="m-section">账号信息</div>
    <div class="m-info-card">
      <div class="info-row"><span class="k">账号</span><span class="v">{{ userStore.user?.username || '-' }}</span></div>
      <div class="info-row"><span class="k">角色</span><span class="v">{{ userStore.user?.roleName || userStore.user?.role || userTypeLabel || '-' }}</span></div>
      <div class="info-row"><span class="k">经销商</span><span class="v">{{ userStore.user?.dealerName || (userStore.user?.dealerId ? '#' + userStore.user.dealerId : '-') }}</span></div>
      <div class="info-row"><span class="k">手机</span><span class="v">{{ userStore.user?.phone || '-' }}</span></div>
      <div class="info-row"><span class="k">邮箱</span><span class="v">{{ userStore.user?.email || '-' }}</span></div>
    </div>

    <div style="padding: 24px 13px 30px;">
      <van-button block round plain type="danger" @click="onLogout">退出登录</van-button>
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
.profile-hero {
  background: var(--dms-m-head-gradient);
  color: #fff;
  padding: calc(30px + var(--dms-mobile-safe-top, 0px)) 20px 46px;
  text-align: center;
  position: relative;
  overflow: hidden;
}
.profile-hero::after {
  content: ''; position: absolute; right: -40px; top: -50px; width: 160px; height: 160px;
  border-radius: 50%; background: rgba(255,255,255,.10);
}
.avatar { position: relative; z-index: 1; }
.pname { position: relative; z-index: 1; font-size: 20px; font-weight: 800; margin-top: 10px; }
.pmeta { position: relative; z-index: 1; margin-top: 6px; font-size: 12px; opacity: .9; display: flex; gap: 8px; align-items: center; justify-content: center; flex-wrap: wrap; }
.ptag { background: var(--dms-m-amber); color: #fff; border-radius: 99px; padding: 2px 10px; font-size: 10px; font-weight: 800; }
.m-quick-mini {
  display: grid; grid-template-columns: repeat(4, 1fr);
  background: #fff; border-radius: 16px; margin: -26px 13px 0; padding: 16px 4px;
  box-shadow: 0 2px 12px rgba(46,107,168,.10); position: relative; z-index: 2;
}
.m-quick-mini .qm {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  font-size: 10.5px; color: var(--dms-text-2, #1f2d3d); text-decoration: none;
  -webkit-tap-highlight-color: transparent;
}
.m-quick-mini .qm .van-icon { font-size: 22px; color: var(--dms-m-navy); }
.m-quick-mini .qm:active { opacity: .6; }
.m-info-card { background: #fff; border-radius: 14px; margin: 0 13px; padding: 4px 14px; box-shadow: 0 2px 12px rgba(46,107,168,.10); }
.info-row { display: flex; align-items: center; padding: 13px 0; border-bottom: 1px solid #f0f3f8; font-size: 13px; }
.info-row:last-child { border-bottom: none; }
.info-row .k { color: var(--dms-text-4, #74839a); width: 70px; flex: none; }
.info-row .v { color: var(--dms-text-1, #1f2d3d); font-weight: 600; text-align: right; flex: 1; word-break: break-all; }
</style>
