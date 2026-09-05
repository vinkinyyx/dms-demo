<template>
  <div class="m-page-scroll">
    <van-nav-bar title="销售订单" :left-arrow="false" />

    <van-search
      v-model="keyword"
      shape="round"
      placeholder="搜索订单号 / 产品"
      @search="onSearch"
      @clear="onSearch"
    />

    <div class="m-filter-chips">
      <button type="button"
        v-for="f in filters" :key="f.value"
        class="chip" :class="{ on: activeStatus === f.value }"
        :aria-pressed="activeStatus === f.value"
        @click="onFilter(f.value)"
      >{{ f.label }}<b v-if="f.value === ''">{{ totalCount }}</b></button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div class="m-card-list" v-if="list.length">
          <router-link v-for="o in list" :key="o.id" class="m-ord" :to="'/mobile/orders/' + o.id">
            <div class="ot">
              <span class="no">{{ o.code }}</span>
              <span class="st" :class="statusCls(o.status)"><i></i>{{ statusText(o.status) }}</span>
            </div>
            <div class="ol">
              <div class="th"><van-icon name="orders-o" /></div>
              <div>
                <div class="pn">{{ o.dealerName || '销售订单' }}</div>
                <div class="pm">{{ formatDate(o.createdAt) }}{{ o.itemCount ? ' · 共 ' + o.itemCount + ' 项' : '' }}</div>
              </div>
            </div>
            <div class="of">
              <span class="tot">合计<b class="m-num">¥{{ fmtAmount(o.finalAmount) }}</b></span>
              <span class="ob ghost" aria-hidden="true">查看 ›</span>
            </div>
          </router-link>
        </div>
        <van-empty v-if="finished && !list.length" description="暂无销售订单" />
      </van-list>
    </van-pull-refresh>

    <div class="fab-wrap">
      <van-button round type="primary" icon="plus" aria-label="新建销售订单" @click="$router.push('/mobile/orders/create')">下销售订单</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listSalesOrders } from '@/api/order'
import { statusText } from '@/utils/dict'
import { formatDate } from '@/utils/format'

const list = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const keyword = ref('')
const activeStatus = ref('')
const totalCount = ref('')
const inFlight = ref(false)
let page = 1
const pageSize = 20

const filters = [
  { label: '全部', value: '' },
  { label: '待审批', value: 'PENDING_APPROVAL' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' }
]

const STATUS_CLS = {
  DRAFT: 'st-info', SUBMITTED: 'st-pen', PENDING_APPROVAL: 'st-pen',
  APPROVED: 'st-ok', CONFIRMED: 'st-ok', REJECTED: 'st-rej',
  CANCELLED: 'st-rej', SHIPPED: 'st-info', PARTIAL_SHIPPED: 'st-info',
  RECEIVED: 'st-ok', COMPLETED: 'st-ok', RECEIVING: 'st-info'
}
function statusCls(s) { return STATUS_CLS[s] || 'st-info' }
function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function onLoad() {
  if (inFlight.value) return
  inFlight.value = true
  loading.value = true
  try {
    const params = { page, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (activeStatus.value) params.status = activeStatus.value
    const r = await listSalesOrders(params)
    const d = r.data
    const rows = Array.isArray(d) ? d : (d.list || d.records || [])
    if (d.total !== undefined) totalCount.value = d.total
    list.value.push(...rows)
    page++
    if (rows.length < pageSize) finished.value = true
  } catch (e) { finished.value = true } finally { loading.value = false; inFlight.value = false; refreshing.value = false }
}

function reset() {
  list.value = []
  page = 1
  finished.value = false
  onLoad()
}
function onSearch() { reset() }
function onFilter(v) { activeStatus.value = v; reset() }
function onRefresh() { reset() }

onMounted(() => onLoad())
</script>

<style scoped>
.fab-wrap { position: fixed; right: 16px; bottom: calc(var(--dms-mobile-tabbar-height, 50px) + 16px); z-index: 10; }
.m-filter-chips { display: flex; gap: 8px; padding: 0 13px 10px; overflow-x: auto; }
.m-filter-chips .chip {
  flex: none; font-size: 12px; font-weight: 600; color: var(--dms-text-3, #74839a);
  background: #fff; border: none; border-radius: 999px; padding: 6px 14px; box-shadow: 0 2px 8px rgba(46,107,168,.08);
  -webkit-tap-highlight-color: transparent; cursor: pointer; line-height: 1.4;
}
.m-filter-chips .chip b { margin-left: 5px; font-weight: 800; color: var(--dms-m-navy); }
.m-filter-chips .chip.on { background: var(--dms-m-navy); color: #fff; }
.m-filter-chips .chip.on b { color: #fff; }
.m-ord { display: block; text-decoration: none; color: inherit; cursor: pointer; }
</style>

