<template>
  <div>
    <el-input v-model="displayText" readonly :placeholder="'点击选择 · ' + placeholder" @click="open">
      <template #suffix>
        <el-icon v-if="modelValue" class="clear-btn" @click.stop="clear"><CircleClose /></el-icon>
        <el-icon v-else><Search /></el-icon>
      </template>
    </el-input>

    <el-dialog v-model="visible" :title="meta.title" width="640px" append-to-body>
      <div class="picker-search">
        <el-input v-model="keyword" placeholder="输入编码或名称搜索..." clearable @input="onSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <el-table :data="list" v-loading="loading" height="360" @row-click="onPick" highlight-current-row>
        <el-table-column v-for="c in meta.cols" :key="c.k" :prop="c.k" :label="c.l">
          <template #default="{ row }">
            <el-tag v-if="c.k === 'status'" :type="statusTagType(row[c.k])" size="small">{{ statusText(row[c.k]) }}</el-tag>
            <span v-else>{{ fmt(row[c.k], c.k) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { lookup } from '@/api/crud'
import { statusText, statusTagType, fmt } from '@/utils/dict'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  displayValue: { type: String, default: '' },
  resource: { type: String, required: true },
  placeholder: { type: String, default: '' },
  extraParams: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue', 'pick'])

const PICKER_META = {
  dealers: { title: '选择经销商', cols: [{ k: 'code', l: '编码' }, { k: 'name', l: '名称' }, { k: 'level', l: '级别' }, { k: 'status', l: '状态' }] },
  products: { title: '选择产品', cols: [{ k: 'code', l: '编码' }, { k: 'name', l: '名称' }, { k: 'spec', l: '规格' }, { k: 'unit', l: '单位' }, { k: 'price', l: '单价' }] },
  hospitals: { title: '选择医院/终端', cols: [{ k: 'code', l: '编码' }, { k: 'name', l: '名称' }, { k: 'level', l: '等级' }] },
  warehouses: { title: '选择仓库', cols: [{ k: 'code', l: '编码' }, { k: 'name', l: '名称' }, { k: 'type', l: '类型' }] },
  categories: { title: '选择分类', cols: [{ k: 'code', l: '编码' }, { k: 'name', l: '名称' }] },
  regions: { title: '选择区域', cols: [{ k: 'code', l: '编码' }, { k: 'name', l: '名称' }, { k: 'level', l: '级别' }] },
  contracts: { title: '选择合同', cols: [{ k: 'code', l: '编号' }, { k: 'category', l: '分类' }, { k: 'status', l: '状态' }] },
  orders: { title: '选择订单', cols: [{ k: 'code', l: '订单号' }, { k: 'type', l: '类型' }, { k: 'amount', l: '金额' }, { k: 'status', l: '状态' }] },
  'purchase-orders': { title: '选择采购单', cols: [{ k: 'code', l: '采购单号' }, { k: 'status', l: '状态' }] }
}

const visible = ref(false)
const loading = ref(false)
const keyword = ref('')
const list = ref([])
const displayText = ref(props.displayValue || '')
let timer = null

watch(() => props.displayValue, (v) => { displayText.value = v || '' })

function open() {
  visible.value = true
  load()
}
function onSearch() {
  if (timer) clearTimeout(timer)
  timer = setTimeout(load, 350)
}
async function load() {
  loading.value = true
  try {
    const params = { limit: 50, ...props.extraParams }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await lookup(props.resource, params)
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}
function onPick(row) {
  const value = row.value != null ? row.value : row.id
  const label = row.label || ((row.code || '') + ' · ' + (row.name || ''))
  displayText.value = label
  emit('update:modelValue', value)
  emit('pick', { value, label, row })
  visible.value = false
}
function clear() {
  displayText.value = ''
  emit('update:modelValue', '')
  emit('pick', null)
}

const meta = PICKER_META[props.resource] || { title: '选择', cols: [{ k: 'code', l: '编码' }, { k: 'name', l: '名称' }] }
</script>

<style scoped>
.picker-search { margin-bottom: 12px; }
.clear-btn { cursor: pointer; }
</style>
