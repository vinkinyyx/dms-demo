<template>
  <div class="m-home">
    <div class="m-hero">
      <div class="m-hero-row">
        <DmsLogo :size="28" inverse />
        <div class="m-hero-hi">
          你好，{{ userStore.username }}
          <span class="m-hero-date">{{ today }}</span>
        </div>
        <router-link to="/mobile/messages" class="m-hero-action" aria-label="消息">
          <van-badge :content="messageBadge" :show="!!messageBadge">
            <van-icon name="bell" />
          </van-badge>
        </router-link>
      </div>
    </div>

    <div class="m-primary-actions">
      <router-link to="/mobile/smart-order" class="m-primary-btn m-primary-btn--main">
        <van-icon name="chat-o" />智能下单
      </router-link>
      <router-link to="/mobile/orders/create" class="m-primary-btn m-primary-btn--ghost">
        <van-icon name="plus" />下销售订单
      </router-link>
    </div>

    <div class="m-sec-title">本月概览</div>
    <div class="m-stats">
      <div class="m-stat" @click="$router.push('/mobile/dashboard')">
        <div class="m-stat-v">¥ {{ fmtAmount(monthKpi.totalSales) }}</div>
        <div class="m-stat-l">本月销售金额</div>
      </div>
      <div class="m-stat" @click="$router.push('/mobile/dashboard')">
        <div class="m-stat-v">{{ monthKpi.totalOrders || 0 }}</div>
        <div class="m-stat-l">本月订单数</div>
      </div>
    </div>

    <div class="m-sec-title">常用功能</div>
    <div class="m-quick-grid">
      <router-link v-for="q in quicks" :key="q.key" :to="q.to" class="m-quick-item">
        <div class="m-quick-ic">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="q.icon" />
        </div>
        <div class="m-quick-l">{{ q.label }}</div>
      </router-link>
    </div>

    <div class="m-sec-title">最近订单</div>
    <div class="m-list-card">
      <van-cell
        v-for="o in recentOrders" :key="o.id"
        :title="o.code"
        :label="(o.dealerName || '-') + ' · ' + (o.createdAt || '').substring(0, 10)"
        is-link
        @click="$router.push('/mobile/orders/' + o.id)"
      >
        <template #value>
          <div class="m-amt">¥ {{ o.finalAmount || 0 }}</div>
          <van-tag :type="statusTagType(o.status)" size="mini">{{ statusText(o.status) }}</van-tag>
        </template>
      </van-cell>
      <van-empty v-if="!loadingRecent && !recentOrders.length" description="暂无订单" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useUserStore } from '@/store/user'
import { listSalesOrders } from '@/api/order'
import { getKpi } from '@/api/dashboard'
import { statusText, statusTagType } from '@/utils/dict'
import request from '@/utils/request'

const userStore = useUserStore()
const today = new Date().toLocaleDateString('zh-CN')

const monthKpi = reactive({})
const recentOrders = ref([])
const loadingRecent = ref(false)
const messageBadge = ref(0)

const ICONS = {
  surgery: '<path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="M9 13h2l1-2 2 4 1-2h2"/>' ,
  orders: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 13h6M9 17h4"/>',
  approvals: '<path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>',
  dashboard: '<path d="M3 3v18h18"/><path d="M7 14l3-3 3 3 5-6"/>',
  messages: '<path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8z"/>',
  profile: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>'
}
const quicks = [
  { key: 'surgery',   label: '手术报台', to: '/mobile/surgery-reports' },
  { key: 'orders',    label: '我的订单', to: '/mobile/orders' },
  { key: 'approvals', label: '移动审批', to: '/mobile/approvals' },
  { key: 'dashboard', label: '我的业绩', to: '/mobile/dashboard' },
  { key: 'messages',  label: '消息中心', to: '/mobile/messages' },
  { key: 'profile',   label: '我的',     to: '/mobile/profile' }
].map(q => ({ ...q, icon: ICONS[q.key] }))

function fmtAmount(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  try { Object.assign(monthKpi, (await getKpi({ period: 'month' })).data || {}) } catch (e) { /* ignore */ }
  try {
    const unread = await request({ url: '/api/notifications/unread-count', method: 'get' })
    messageBadge.value = Number(unread?.data?.count || 0)
  } catch (e) { /* ignore */ }
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
.m-home { padding-bottom: var(--dms-spacing-4); }
.m-stat { cursor: pointer; -webkit-tap-highlight-color: transparent; }
.m-stat:active { opacity: .7; }
</style>
