<template>
  <div>
    <van-nav-bar title="销售订单" :left-arrow="false">
      <template #right>
        <van-icon name="plus" size="20" @click="$router.push('/mobile/orders/create')" />
      </template>
    </van-nav-bar>

    <van-search
      v-model="keyword"
      placeholder="搜索单号 / 经销商"
      @search="onSearch"
      @clear="onSearch"
      background="#f5f7fa"
    />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <van-cell-group
        inset
        v-for="o in list" :key="o.id"
        style="margin-top:10px"
        @click="$router.push('/mobile/orders/' + o.id)"
        is-link
      >
        <van-cell :title="o.code" :label="(o.dealerName || '-') + ' · ' + (o.createdAt || '').substring(0, 10)">
          <template #value>
            <div class="amt">¥ {{ o.finalAmount || 0 }}</div>
            <van-tag :type="statusTagType(o.status)" size="mini">{{ statusText(o.status) }}</van-tag>
          </template>
        </van-cell>
      </van-cell-group>
    </van-list>
    <van-empty v-if="finished && !list.length" description="暂无销售订单" />

    <div class="fab-wrap">
      <van-button round type="primary" icon="plus" @click="$router.push('/mobile/orders/create')">下销售订单</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { listResource } from '@/api/crud'
import { statusText, statusTagType } from '@/utils/dict'

const list = ref([])
const loading = ref(false)
const finished = ref(false)
const keyword = ref('')
let page = 1
const pageSize = 20

async function onLoad() {
  loading.value = true
  try {
    const params = { page, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    const r = await listResource('/api/orders', params)
    const d = r.data
    const rows = Array.isArray(d) ? d : (d.list || d.records || [])
    list.value.push(...rows)
    page++
    if (rows.length < pageSize) finished.value = true
  } catch (e) { finished.value = true } finally { loading.value = false }
}

function onSearch() {
  list.value = []
  page = 1
  finished.value = false
  onLoad()
}
</script>

<style scoped>
.amt { color: #ee0a24; font-weight: 600; }
.fab-wrap { position: fixed; right: 16px; bottom: 80px; z-index: 10; }
</style>