<template>
  <div class="m-page-scroll">
    <van-nav-bar title="移动审批" />
    <van-tabs v-model:active="active" @change="reload(1)" sticky>
      <van-tab title="待我审批" name="todo" :badge="active === 'todo' ? '' : ''" />
      <van-tab title="我已审批" name="done" />
      <van-tab title="我发起的" name="submitted" />
      <van-tab title="抄送我的" name="cc" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div class="m-card-list" v-if="rows.length">
          <div v-for="r in rows" :key="r.id" class="m-ord" role="button" tabindex="0" :aria-label="(active === 'todo' ? '去审批 ' : '查看审批 ') + (r.businessCode || r.title || ('审批#' + r.id))" @click="open(r)" @keydown.enter="open(r)" @keydown.space.prevent="open(r)">
            <div class="ot">
              <span class="no">{{ r.businessCode || r.title || ('审批#' + r.id) }}</span>
              <span v-if="active === 'todo'" class="st st-pen"><i></i>待处理</span>
            </div>
            <div class="ol">
              <div class="th"><van-icon name="todo-list-o" /></div>
              <div>
                <div class="pn">{{ bizLabel(r.businessType) }}</div>
                <div class="pm">{{ label(r) }}</div>
              </div>
            </div>
            <div class="of">
              <span class="tot">{{ r.submitterName ? '发起人：' + r.submitterName : '审批流程' }}</span>
              <button class="ob" :class="active === 'todo' ? 'amber' : 'ghost'">
                {{ active === 'todo' ? '去审批' : '查看' }}
              </button>
            </div>
          </div>
        </div>
        <van-empty v-if="!loading && !rows.length" description="暂无审批" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>
<script setup>
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { myTodoTasks, myDoneTasks, mySubmitted, myCc } from '@/api/approval'
import { BUSINESS_LABELS } from '@/views/approval/dict'
const router = useRouter()
const active = ref('todo'); const rows = ref([]); const page = ref(1); const size = ref(20); const total = ref(0)
const loading = ref(false); const finished = ref(false); const refreshing = ref(false); const inFlight = ref(false)
function bizLabel(t) { return BUSINESS_LABELS[t] || t || '审批' }
function label(r) {
  const parts = []
  if (r.nodeName) parts.push(r.nodeName)
  const t = fmt(r.createdAt || r.startedAt)
  if (t) parts.push(t)
  return parts.join(' · ')
}
function fmt(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '' }
async function loadMore() {
  if (inFlight.value) return
  inFlight.value = true; loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    const res = active.value === 'todo' ? await myTodoTasks(params) : active.value === 'done' ? await myDoneTasks(params) : active.value === 'submitted' ? await mySubmitted(params) : await myCc(params)
    const d = res.data || {}
    const list = d.list || []
    rows.value.push(...list)
    total.value = d.total || 0
    if (rows.value.length >= total.value || !list.length) finished.value = true
    page.value++
  } catch (e) { finished.value = true } finally { loading.value = false; refreshing.value = false; inFlight.value = false }
}
function reload(p = 1) { page.value = p; rows.value = []; finished.value = false; loading.value = false; loadMore() }
function onRefresh() { reload(1) }
function open(r) { const id = r.instanceId || r.id; if (id) router.push('/mobile/approvals/' + id) }
let firstEnter = true
onActivated(() => { if (!firstEnter) reload(1); firstEnter = false })
onMounted(() => loadMore())
</script>
<style scoped>
.m-ord { cursor: pointer; }
</style>
