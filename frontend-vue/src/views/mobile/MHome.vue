<template>
  <div class="m-home">
    <div class="m-hero">
      <div class="m-hero-row">
        <div class="m-hero-hi">
          <div class="m-hero-greet">{{ greeting }}，{{ userStore.username }}</div>
          <div class="m-hero-who">{{ dealerName || 'MySolMed DMS' }}</div>
        </div>
        <router-link to="/mobile/messages" class="m-hero-action" aria-label="消息">
          <van-badge :content="messageBadge" :show="!!messageBadge">
            <van-icon name="bell" />
          </van-badge>
        </router-link>
      </div>
    </div>

    <div class="m-kpiband m-kpiband-2">
      <router-link class="m-kpi" to="/mobile/orders"><div class="n m-num">{{ kpi.ongoing }}</div><div class="t">进行中订单</div></router-link>
      <router-link class="m-kpi" to="/mobile/approvals"><div class="n m-num">{{ kpi.approval }}</div><div class="t">待我审批</div></router-link>
      <router-link class="m-kpi" to="/mobile/dashboard"><div class="n m-num">¥{{ kpi.monthSales }}</div><div class="t">本月销售额</div></router-link>
    </div>

    <div class="m-section">常用功能</div>
    <div class="m-grid-card">
      <router-link v-for="q in quicks" :key="q.key" :to="q.to" class="qa">
        <div class="ic"><van-icon :name="q.icon" /></div>
        <span>{{ q.label }}</span>
      </router-link>
    </div>

    <div class="m-section">最近业务<router-link class="m-more" to="/mobile/orders">全部 ›</router-link></div>
    <div class="m-card-list">
      <div v-for="o in recentOrders" :key="o.id" class="m-ord" role="button" tabindex="0" :aria-label="'查看订单 ' + o.code" @click="onCardAction(o)" @keydown.enter="onCardAction(o)">
        <div class="ot">
          <span class="no">{{ o.code }}</span>
          <span class="st" :class="statusCls(o.status)"><i></i>{{ statusText(o.status) }}</span>
        </div>
        <div class="ol">
          <div class="th"><van-icon name="orders-o" /></div>
          <div>
            <div class="pn">{{ o.dealerName || '销售订单' }}</div>
            <div class="pm">{{ formatDate(o.createdAt) }} · <b class="m-num">¥{{ fmtAmount(o.finalAmount) }}</b></div>
          </div>
        </div>
        <div class="of">
          <span class="tot">合计<b class="m-num">¥{{ fmtAmount(o.finalAmount) }}</b></span>
          <button type="button" class="ob" :class="o.status === 'PENDING_APPROVAL' ? 'amber' : 'ghost'" @click.stop="onCardAction(o)">
            {{ cardAction(o.status) }}
          </button>
        </div>
      </div>
      <van-empty v-if="!loadingRecent && !recentOrders.length" description="暂无业务" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { listSalesOrders } from '@/api/order'
import { getKpi } from '@/api/dashboard'
import { statusText } from '@/utils/dict'
import { formatDate } from '@/utils/format'
import request from '@/utils/request'

const userStore = useUserStore()
const router = useRouter()

const monthKpi = reactive({})
const recentOrders = ref([])
const loadingRecent = ref(false)
const messageBadge = ref(0)
const approvalTodo = ref(0)

const quicks = [
  { key: 'smart',     label: '智能下单',   to: '/mobile/smart-order',     icon: 'chat-o' },
  { key: 'create',    label: '下销售订单', to: '/mobile/orders/create',   icon: 'description' },
  { key: 'surgery',   label: '手术报台',   to: '/mobile/surgery-reports', icon: 'todo-list-o' },
  { key: 'approvals', label: '移动审批',   to: '/mobile/approvals',       icon: 'passed' },
  { key: 'dashboard', label: '我的业绩',   to: '/mobile/dashboard',       icon: 'bar-chart-o' },
  { key: 'messages',  label: '消息中心',   to: '/mobile/messages',        icon: 'envelop-o' }
]

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
const dealerName = computed(() => userStore.user?.dealerName || '')
const kpi = computed(() => ({
  ongoing: Number(monthKpi.totalOrders || recentOrders.value.length || 0),
  approval: approvalTodo.value,
  monthSales: fmtCompact(monthKpi.totalSales)
}))
function fmtCompact(v) {
  const n = Number(v || 0)
  return n >= 10000 ? (n / 10000).toFixed(1) + '万' : n.toLocaleString('zh-CN')
}

const STATUS_CLS = {
  DRAFT: 'st-info', SUBMITTED: 'st-pen', PENDING_APPROVAL: 'st-pen',
  APPROVED: 'st-ok', CONFIRMED: 'st-ok', REJECTED: 'st-rej',
  CANCELLED: 'st-rej', SHIPPED: 'st-info', PARTIAL_SHIPPED: 'st-info',
  RECEIVED: 'st-ok', COMPLETED: 'st-ok', RECEIVING: 'st-info'
}
function statusCls(s) { return STATUS_CLS[s] || 'st-info' }
function cardAction(s) {
  if (s === 'PENDING_APPROVAL' || s === 'SUBMITTED') return '去审批'
  return '查看'
}
function onCardAction(o) {
  if (o.status === 'PENDING_APPROVAL' || o.status === 'SUBMITTED') { router.push('/mobile/approvals'); return }
  router.push('/mobile/orders/' + o.id)
}

function fmtAmount(v) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  try { Object.assign(monthKpi, (await getKpi({ period: 'month' })).data || {}) } catch (e) { /* ignore */ }
  try {
    const todo = await request({ url: '/api/approval/tasks/my-todo', method: 'get', params: { page: 1, size: 1 } })
    approvalTodo.value = Number(todo?.data?.total || 0)
  } catch (e) { /* ignore */ }
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

let firstEnter = true
onActivated(() => { if (!firstEnter) load(); firstEnter = false })
load()
</script>

<style scoped>
.m-kpi{text-decoration:none;color:inherit;}
.m-home { padding-bottom: 20px; }
.m-hero { position: relative; overflow: hidden; }
.m-hero::after {
  content: ''; position: absolute; right: -50px; top: -60px;
  width: 180px; height: 180px; border-radius: 50%; background: rgba(255,255,255,.10); pointer-events: none;
}
.m-hero-row { position: relative; z-index: 1; display: flex; align-items: flex-start; gap: 10px; }
.m-hero-hi { flex: 1; min-width: 0; }
.m-hero-greet { font-size: 12px; opacity: .88; }
.m-hero-who { font-size: 19px; font-weight: 800; margin-top: 3px; display: flex; align-items: center; }
.m-hero-action { position: relative; z-index: 1; color: #fff; font-size: 22px; line-height: 1; padding: 4px; }
.m-kpi { cursor: pointer; }
.m-ord { cursor: pointer; }
</style>
