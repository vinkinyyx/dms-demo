<template>
  <div class="m-page-scroll">
    <van-nav-bar :left-arrow="false">
      <template #title>
        <span class="nav-title"><SurgeryIcon :size="18" bg="rgba(255,255,255,0.18)" /><span>手术报台</span></span>
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div class="m-card-list" v-if="list.length">
          <div v-for="r in list" :key="r.id" class="m-ord" role="button" tabindex="0" :aria-label="'查看手术报台 ' + r.code" @click="$router.push('/mobile/surgery-reports/' + r.id)" @keydown.enter="$router.push('/mobile/surgery-reports/' + r.id)" @keydown.space.prevent="$router.push('/mobile/surgery-reports/' + r.id)">
            <div class="ot">
              <span class="no">{{ r.code }}</span>
              <span class="st" :class="statusCls(r.status)"><i></i>{{ statusText(r.status) }}</span>
            </div>
            <div class="ol">
              <div class="th"><van-icon name="todo-list-o" /></div>
              <div>
                <div class="pn">{{ r.terminalName || '手术报台' }}</div>
                <div class="pm">经销商：{{ r.dealerName || '-' }} · {{ formatDate(r.surgeryDate) }}</div>
              </div>
            </div>
            <div class="of">
              <span class="tot">主刀 / 产品明细</span>
              <button class="ob ghost">查看</button>
            </div>
          </div>
        </div>
        <van-empty v-if="finished && !list.length" description="暂无报台，点击右下角新建" />
      </van-list>
    </van-pull-refresh>

    <div class="fab-wrap">
      <van-button round type="primary" icon="plus" @click="$router.push('/mobile/surgery-reports/create')">新建报台</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { listResource } from '@/api/crud'
import { statusText } from '@/utils/dict'
import { formatDate } from '@/utils/format'
import SurgeryIcon from '@/components/SurgeryIcon.vue'

const list = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
let page = 1
const pageSize = 20

const STATUS_CLS = {
  DRAFT: 'st-info', SUBMITTED: 'st-pen', PENDING_APPROVAL: 'st-pen',
  APPROVED: 'st-ok', CONFIRMED: 'st-ok', REJECTED: 'st-rej', CANCELLED: 'st-rej',
  COMPLETED: 'st-ok'
}
function statusCls(s) { return STATUS_CLS[s] || 'st-info' }

async function onLoad() {
  loading.value = true
  try {
    const r = await listResource('/api/surgery-reports', { page, size: pageSize })
    const d = r.data
    const rows = Array.isArray(d) ? d : (d.list || d.records || [])
    list.value.push(...rows)
    page++
    if (rows.length < pageSize) finished.value = true
  } catch (e) { finished.value = true } finally { loading.value = false; refreshing.value = false }
}

function onRefresh() {
  list.value = []
  page = 1
  finished.value = false
  onLoad()
}
</script>

<style scoped>
.nav-title { display: inline-flex; align-items: center; gap: 6px; font-weight: 700; color: #fff; }
.fab-wrap { position: fixed; right: 16px; bottom: calc(var(--dms-mobile-tabbar-height, 50px) + 16px); z-index: 10; }
.m-ord { cursor: pointer; }
</style>
