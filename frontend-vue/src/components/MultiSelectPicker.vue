<template>
  <el-select v-model="inner" multiple filterable :placeholder="'请选择'" style="width:100%"
    @visible-change="onVisible">
    <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
  </el-select>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { lookup } from '@/api/crud'

const props = defineProps({
  modelValue: { type: [String, Array], default: '' },
  resource: { type: String, required: true }
})
const emit = defineEmits(['update:modelValue'])

const options = ref([])
let loaded = false

const inner = computed({
  get() {
    if (Array.isArray(props.modelValue)) return props.modelValue
    if (typeof props.modelValue === 'string' && props.modelValue) return props.modelValue.split(',').map((s) => (isNaN(s) ? s : Number(s)))
    return []
  },
  set(val) { emit('update:modelValue', val) }
})

async function load() {
  if (loaded) return
  const res = await lookup(props.resource, { limit: 200 })
  options.value = (res.data || []).map((r) => ({
    value: r.value != null ? r.value : r.id,
    label: r.label || ((r.code || '') + ' · ' + (r.name || ''))
  }))
  loaded = true
}
function onVisible(v) { if (v) load() }
watch(() => props.resource, () => { loaded = false; options.value = [] })
</script>
