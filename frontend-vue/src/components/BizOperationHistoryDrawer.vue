<template>
  <el-drawer v-model="visible" :title="title" size="520px" destroy-on-close>
    <el-timeline v-loading="loading">
      <el-timeline-item
        v-for="item in rows"
        :key="item.id"
        :timestamp="item.atTime || item.createdAt"
        placement="top"
        :type="dotType(item.action)"
      >
        <div class="hist-card">
          <div class="hist-head">
            <el-tag size="small" :type="tagType(item.action)">{{ item.actionLabel || item.action }}</el-tag>
            <span class="hist-user">{{ item.userName || item.username || '-' }}</span>
          </div>
          <div v-if="item.remark || item.path" class="hist-remark">{{ item.remark || item.path }}</div>
          <div v-if="item.detail" class="hist-detail">
            <el-collapse>
              <el-collapse-item title="变更详情" :name="item.id">
                <pre class="code">{{ formatDetail(item.detail) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </el-timeline-item>
      <el-empty v-if="!loading && rows.length === 0" description="暂无操作记录" />
    </el-timeline>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import request from '@/utils/request'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  resourceType: { type: String, default: '' },
  resourceId: { type: [String, Number], default: '' },
  title: { type: String, default: '操作记录' }
})
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const rows = ref([])
const loading = ref(false)

watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v && props.resourceType && props.resourceId) load()
})
watch(visible, (v) => emit('update:modelValue', v))

async function load() {
  loading.value = true
  try {
    const { data } = await request({
      url: '/api/operation-logs',
      method: 'get',
      params: { resourceType: props.resourceType, resourceId: props.resourceId }
    })
    rows.value = Array.isArray(data) ? data : (data?.list || [])
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function tagType(action) {
  const a = String(action || '').toUpperCase()
  if (a.includes('APPROVE') || a.includes('RECEIPT') || a.includes('OUT') || a === 'EXECUTE') return 'success'
  if (a.includes('REJECT') || a.includes('CANCEL')) return 'danger'
  if (a === 'CREATE' || a === 'SUBMIT') return 'primary'
  return 'info'
}
function dotType(action) {
  const a = String(action || '').toUpperCase()
  if (a.includes('REJECT') || a.includes('CANCEL')) return 'danger'
  if (a.includes('APPROVE')) return 'success'
  return 'primary'
}
function formatDetail(detail) {
  if (!detail) return ''
  if (typeof detail === 'string') {
    try { return JSON.stringify(JSON.parse(detail), null, 2) } catch { return detail }
  }
  return JSON.stringify(detail, null, 2)
}
</script>

<style scoped>
.hist-card { padding: 4px 0; }
.hist-head { display: flex; align-items: center; gap: 8px; }
.hist-user { color: #606266; font-size: 13px; }
.hist-remark { margin-top: 6px; color: #909399; font-size: 13px; }
.hist-detail { margin-top: 6px; }
.code { white-space: pre-wrap; word-break: break-all; font-size: 12px; max-height: 260px; overflow: auto; margin: 0; }
</style>
