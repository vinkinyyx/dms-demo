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
      <div v-if="paginated" class="picker-pager">
        <el-pagination small layout="prev, pager, next, total" :total="total"
          :page-size="pageSize" :current-page="page" @current-change="onPageChange" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
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
// 产品选择器支持服务端分页（每页50条），列出所有产品
const paginated = computed(() => props.resource === 'products')
const pageSize = 50
const page = ref(1)
const total = ref(0)
let timer = null

watch(() => props.displayValue, (v) => { displayText.value = v || '' })

function open() {
  visible.value = true
  page.value = 1
  load()
}
function onSearch() {
  if (timer) clearTimeout(timer)
  page.value = 1
  timer = setTimeout(load, 350)
}
function onPageChange(p) {
  page.value = p
  load()
}
async function load() {
  loading.value = true
  try {
    const params = { limit: 500, ...props.extraParams }
    if (paginated.value) { params.page = page.value; params.size = pageSize }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await lookup(props.resource, params)
    if (paginated.value && res.data && Array.isArray(res.data.list)) {
      list.value = res.data.list
      total.value = res.data.total || 0
    } else {
      list.value = res.data || []
      total.value = list.value.length
    }
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
.picker-pager { margin-top: 10px; display: flex; justify-content: flex-end; }
</style>
