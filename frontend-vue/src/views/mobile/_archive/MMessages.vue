<template>
  <div>
    <van-nav-bar title="消息通知" />
    <van-cell-group inset style="margin-top:10px">
      <van-cell v-for="m in list" :key="m.id" :title="m.title" :label="m.content">
        <template #value>
          <van-badge v-if="!m.isRead" dot />
          <span class="time">{{ (m.atTime || '').substring(5, 16).replace('T', ' ') }}</span>
        </template>
      </van-cell>
      <van-empty v-if="!list.length" description="暂无消息" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { notifications } from '@/api/admin'

const list = ref([])

async function load() {
  try {
    const d = (await notifications({ page: 1, size: 30 })).data
    list.value = Array.isArray(d) ? d : (d.list || d.records || [])
  } catch (e) { /* ignore */ }
}
load()
</script>

<style scoped>
.time { color: #969799; font-size: 12px; margin-left: 8px; }
</style>
