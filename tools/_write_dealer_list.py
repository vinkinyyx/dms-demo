from pathlib import Path
p=Path('frontend-vue/src/views/DealerProfileList.vue')
p.write_text('''<template>
  <ListPageLayout
    page-key="dealer-profile"
    :table-columns="tableColumns"
    :fetch-data="fetchDealers"
    :row-actions="rowActions"
    :toolbar-actions="{}"
    :initial-filter="{ keyword: '' }"
  />
</template>

<script setup>
import { useRouter } from 'vue-router'
import ListPageLayout from '@/components/ListPageLayout.vue'
import request from '@/utils/request'

const router = useRouter()

const tableColumns = [
  { prop: 'code', label: '编码', width: 160 },
  { prop: 'name', label: '名称' },
  { prop: 'level', label: '级别', width: 100 },
  { prop: 'status', label: '状态', width: 100, tag: { success: 'active' } },
  { prop: 'createdAt', label: '创建时间', width: 180 }
]

async function fetchDealers(params) {
  return request({ url: '/api/dealers', method: 'get', params })
}

const rowActions = {
  view: (row) => router.push({ name: 'DealerProfile', params: { id: row.id } })
}
</script>
''', encoding='utf-8', newline='\n')
