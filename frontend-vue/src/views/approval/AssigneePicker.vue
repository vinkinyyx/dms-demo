<template>
  <div>
    <el-radio-group v-model="kind" size="small" @change="onKindChange" style="margin-bottom:8px;">
      <el-radio-button value="USER">账号</el-radio-button>
      <el-radio-button value="ROLE">角色</el-radio-button>
      <el-radio-button value="SUBMITTER">提交人本人</el-radio-button>
    </el-radio-group>
    <el-select
      v-if="kind !== 'SUBMITTER'"
      v-model="selected"
      filterable
      remote
      clearable
      reserve-keyword
      :remote-method="query"
      :loading="loading"
      :placeholder="kind === 'USER' ? '搜索账号姓名/账号' : '选择角色'"
      style="width:100%;"
      @change="onChange"
    >
      <el-option
        v-for="item in options"
        :key="kind + ':' + item.id"
        :label="item.label"
        :value="item.id"
      />
    </el-select>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue'])

const kind = ref(props.modelValue && props.modelValue.assigneeType ? props.modelValue.assigneeType : 'USER')
const selected = ref(props.modelValue && props.modelValue.refId ? props.modelValue.refId : null)
const options = ref([])
const loading = ref(false)

async function query(keyword) {
  if (kind.value === 'SUBMITTER') return
  loading.value = true
  try {
    if (kind.value === 'USER') {
      const res = await request({ url: '/api/users', method: 'get', params: { keyword, page: 1, size: 20 } })
      const list = (res && res.data && (res.data.list || res.data.records || res.data.content)) || []
      options.value = list.map((u) => ({ id: u.id, label: (u.name || u.username || u.id) + (u.username ? '（' + u.username + '）' : '') }))
    } else {
      const res = await request({ url: '/api/roles', method: 'get' })
      const list = (res && res.data) || []
      options.value = list
        .filter((r) => !keyword || (r.name || '').indexOf(keyword) >= 0 || (r.code || '').indexOf(keyword) >= 0)
        .map((r) => ({ id: r.id, label: r.name + (r.code ? '（' + r.code + '）' : '') }))
    }
  } finally {
    loading.value = false
  }
}

function onKindChange() {
  selected.value = null
  options.value = []
  if (kind.value === 'SUBMITTER') {
    emit('update:modelValue', { assigneeType: 'SUBMITTER', refId: 0, displayName: '提交人本人' })
    return
  }
  emit('update:modelValue', { assigneeType: kind.value, refId: null, displayName: null })
  query('')
}

function onChange(val) {
  const opt = options.value.find((o) => o.id === val)
  emit('update:modelValue', { assigneeType: kind.value, refId: val, displayName: opt ? opt.label : null })
}

onMounted(() => {
  if (kind.value !== 'SUBMITTER') query('')
})
</script>
