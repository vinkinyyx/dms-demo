<template>
  <div>
    <van-nav-bar title="销售订单" :left-arrow="false" />

    <van-search
      v-model="keyword"
      placeholder="搜索单号 / 经销商"
      @search="onSearch"
      @clear="onSearch"
    />

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div class="m-list-card" v-if="list.length">
          <van-cell
            v-for="o in list" :key="o.id"
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
        </div>
        <van-empty v-if="finished && !list.length" description="暂无销售订单" />
      </van-list>
    </van-pull-refresh>

    <div class="fab-wrap">
      <van-button round type="primary" icon="plus" @click="$router.push('/mobile/orders/create')">下销售订单</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listSalesOrders } from '@/api/order'
import { statusText, statusTagType } from '@/utils/dict'

const list = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const keyword = ref('')
const inFlight = ref(false)
let page = 1
const pageSize = 20

async function onLoad() {
  if (inFlight.value) return
  inFlight.value = true
  loading.value = true
  try {
    const params = { page, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    const r = await listSalesOrders(params)
    const d = r.data
    const rows = Array.isArray(d) ? d : (d.list || d.records || [])
    list.value.push(...rows)
    page++
    if (rows.length < pageSize) finished.value = true
  } catch (e) { finished.value = true } finally { loading.value = false; inFlight.value = false; refreshing.value = false }
}

function onSearch() {
  list.value = []
  page = 1
  finished.value = false
  onLoad()
}

onMounted(() => onLoad())
</script>

<style scoped>
.fab-wrap { position: fixed; right: 16px; bottom: calc(var(--dms-mobile-tabbar-height) + 16px); z-index: 10; }
</style>
