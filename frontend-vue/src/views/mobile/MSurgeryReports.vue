<template>
  <div>
    <van-nav-bar :left-arrow="false">
      <template #title>
        <span class="nav-title"><SurgeryIcon :size="20" bg="var(--dms-color-primary)" /><span>手术报台</span></span>
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div class="m-list-card" v-if="list.length">
          <van-cell
            v-for="r in list" :key="r.id"
            :title="r.code"
            :label="'医院：' + (r.terminalName || '-') + ' · 经销商：' + (r.dealerName || '-')"
            is-link
            @click="$router.push('/mobile/surgery-reports/' + r.id)"
          >
            <template #value>
              <div class="m-sub">{{ formatDate(r.surgeryDate) }}</div>
              <van-tag :type="statusTagType(r.status)" size="mini">{{ statusText(r.status) }}</van-tag>
            </template>
          </van-cell>
        </div>
        <van-empty v-if="finished && !list.length">
          <template #image><SurgeryIcon :size="72" bg="var(--dms-color-primary)" /></template>
          <p>暂无报台，点击右下角新建</p>
        </van-empty>
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
import { statusText, statusTagType } from '@/utils/dict'
import { formatDate } from '@/utils/format'
import SurgeryIcon from '@/components/SurgeryIcon.vue'

const list = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
let page = 1
const pageSize = 20

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
.nav-title { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; }
.fab-wrap { position: fixed; right: 16px; bottom: calc(var(--dms-mobile-tabbar-height) + 16px); z-index: 10; }
</style>
