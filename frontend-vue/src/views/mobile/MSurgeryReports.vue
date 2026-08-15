<template>
  <div>
    <van-nav-bar :left-arrow="false">
      <template #title>
        <span class="nav-title"><SurgeryIcon :size="22" bg="#52c41a" /><span>手术报台</span></span>
      </template>
      <template #right>
        <van-icon name="plus" size="20" @click="$router.push('/mobile/surgery-reports/create')" />
      </template>
    </van-nav-bar>

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <van-cell-group
        inset
        v-for="r in list" :key="r.id"
        style="margin-top:10px"
        is-link
        @click="$router.push('/mobile/surgery-reports/' + r.id)"
      >
        <van-cell
          :title="r.code"
          :label="'医院：' + (r.terminalName || '-') + ' · 经销商：' + (r.dealerName || '-')"
        >
          <template #value>
            <div class="date">{{ formatDate(r.surgeryDate) }}</div>
            <van-tag :type="statusTagType(r.status)" size="mini">{{ statusText(r.status) }}</van-tag>
          </template>
        </van-cell>
      </van-cell-group>
    </van-list>
    <van-empty v-if="finished && !list.length">
      <template #image><SurgeryIcon :size="72" bg="#52c41a" /></template>
      <p>暂无报台，点击右下角新建</p>
    </van-empty>

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
  } catch (e) { finished.value = true } finally { loading.value = false }
}
</script>

<style scoped>
.date { color: var(--dms-text-4); font-size: 12px; }
.fab-wrap { position: fixed; right: 16px; bottom: 80px; z-index: 10; }
.nav-title { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; }
</style>