<template>
  <div class="m-page-scroll">
    <van-nav-bar title="消息中心" right-text="全部已读" @click-right="readAll" />
    <van-tabs v-model:active="active" @change="reload(1)" sticky>
      <van-tab title="全部" name="" />
      <van-tab title="未读" name="unread" />
      <van-tab title="审批" name="APPROVAL" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div class="m-msg-list" v-if="rows.length">
          <div v-for="n in rows" :key="n.id" class="m-msg-item" role="button" tabindex="0" :aria-label="'查看消息 ' + n.title" @click="open(n)" @keydown.enter="open(n)" @keydown.space.prevent="open(n)">
            <div class="mi" :class="iconCls(n)"><van-icon :name="iconName(n)" /></div>
            <div class="mc">
              <div class="mt">{{ n.title }}<span v-if="!n.isRead" class="dot"></span></div>
              <div class="md">{{ n.body }}</div>
            </div>
            <div class="mtime">{{ fmt(n.createdAt) }}</div>
          </div>
        </div>
        <van-empty v-if="!loading && !rows.length" description="暂无消息" />
      </van-list>
    </van-pull-refresh>

    <van-popup v-model:show="detailShow" position="bottom" round :style="{ maxHeight: '70%' }">
      <div class="msg-detail" v-if="current">
        <div class="md-head">
          <div class="mi" :class="iconCls(current)"><van-icon :name="iconName(current)" /></div>
          <div class="md-title">{{ current.title }}</div>
        </div>
        <div class="md-time">{{ fmtFull(current.createdAt) }}</div>
        <div class="md-body">{{ current.body }}</div>
        <van-button v-if="current.refType === 'APPROVAL' && current.refId" block round type="primary" class="md-btn" @click="goApproval">查看审批详情</van-button>
      </div>
    </van-popup>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import request from '@/utils/request'
import { markNotificationRead, markAllNotificationsRead } from '@/api/notification'
const router = useRouter()
const active = ref(''); const rows = ref([]); const page = ref(1); const size = ref(20); const total = ref(0)
const detailShow = ref(false); const current = ref(null)
const loading = ref(false); const finished = ref(false); const refreshing = ref(false); const inFlight = ref(false)
function fmt(v) { return v ? String(v).replace('T', ' ').slice(5, 16) : '' }
function fmtFull(v) { return v ? String(v).replace('T', ' ').slice(0, 19) : '' }
function iconName(n) {
  const t = (n.refType || n.type || '').toUpperCase()
  if (t.includes('APPROVAL')) return 'passed'
  if (t.includes('INVENTORY') || t.includes('STOCK')) return 'balance-o'
  if (t.includes('ORDER') || t.includes('SHIP')) return 'orders-o'
  return 'bell'
}
function iconCls(n) {
  const t = (n.refType || n.type || '').toUpperCase()
  if (t.includes('APPROVAL')) return 'g'
  if (t.includes('INVENTORY') || t.includes('STOCK')) return 'b'
  if (!n.isRead) return 'a'
  return ''
}
async function loadMore() {
  if (inFlight.value) return
  inFlight.value = true; loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (active.value === 'unread') params.isRead = false
    else if (active.value) params.type = active.value
    const { data } = await request({ url: '/api/notifications', method: 'get', params })
    const d = data || {}
    const list = d.list || d.records || d.content || []
    rows.value.push(...list)
    total.value = d.total || 0
    if (rows.value.length >= total.value || !list.length) finished.value = true
    page.value++
  } catch (e) { finished.value = true } finally { loading.value = false; refreshing.value = false; inFlight.value = false }
}
function reload(p = 1) { page.value = p; rows.value = []; finished.value = false; loading.value = false; loadMore() }
function onRefresh() { reload(1) }
async function open(n) {
  current.value = n
  detailShow.value = true
  if (!n.isRead) {
    try { await markNotificationRead(n.id); n.isRead = true } catch (e) { /* ignore */ }
  }
}
function goApproval() {
  const id = current.value && current.value.refId
  detailShow.value = false
  if (id) router.push('/mobile/approvals/' + id)
}
async function readAll() {
  try { await markAllNotificationsRead(); showSuccessToast('已全部已读'); reload(1) }
  catch (e) { showFailToast((e && e.message) || '操作失败') }
}
onMounted(() => loadMore())
</script>
<style scoped>
.m-msg-item { cursor: pointer; }
.msg-detail { padding: 20px 18px calc(20px + env(safe-area-inset-bottom)); }
.md-head { display: flex; align-items: center; gap: 12px; }
.md-head .mi { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0; }
.md-title { font-size: 16px; font-weight: 700; line-height: 1.4; }
.md-time { font-size: 12px; color: var(--dms-text-4); margin: 10px 0 12px; }
.md-body { font-size: 14px; line-height: 1.7; color: var(--dms-text-2); white-space: pre-wrap; word-break: break-word; }
.md-btn { margin-top: 18px; }
</style>
