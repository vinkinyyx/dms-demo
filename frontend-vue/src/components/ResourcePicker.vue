<template>
  <div class="resource-picker">
    <el-input v-model="displayText" readonly :disabled="disabled" :placeholder="(disabled ? '' : '点击选择 · ') + placeholder" @click="disabled ? null : open()">
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
            <el-tag v-if="c.k === 'isBom' && (row.isBom||row.isBundle)" size="small" type="warning" effect="plain">BOM组套</el-tag>
            <el-tag v-else-if="c.k === 'status'" :type="statusTagType(row[c.k])" size="small">{{ statusText(row[c.k]) }}</el-tag>
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
  extraParams: { type: Object, default: () => ({}) },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'pick'])

const PICKER_META = {
  dealers: { title: '\u9009\u62e9\u7ecf\u9500\u5546', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'name', l: '\u540d\u79f0' }, { k: 'level', l: '\u7b49\u7ea7' }, { k: 'status', l: '\u72b6\u6001' }] },
  products: { title: '\u9009\u62e9\u4ea7\u54c1', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'nameCn', l: '\u540d\u79f0' }, { k: 'spec', l: '\u89c4\u683c' }, { k: 'unit', l: '\u5355\u4f4d' }, { k: 'currentPrice', l: '\u4ef7\u683c' }, { k: 'isBom', l: '\u7c7b\u578b' }] },
  'product-lines': { title: '\u9009\u62e9\u4ea7\u54c1\u5c42\u6b21', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'name', l: '\u540d\u79f0' }, { k: 'level', l: '\u5c42\u7ea7' }, { k: 'status', l: '\u72b6\u6001' }] },
  hospitals: { title: '\u9009\u62e9\u533b\u9662/\u79d1\u5ba4', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'name', l: '\u540d\u79f0' }, { k: 'level', l: '\u7c7b\u578b' }] },
  suppliers: { title: '\u9009\u62e9\u4f9b\u5e94\u5546', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'name', l: '\u540d\u79f0' }, { k: 'contactPerson', l: '\u8054\u7cfb\u4eba' }, { k: 'status', l: '\u72b6\u6001' }] },
  warehouses: { title: '\u9009\u62e9\u4ed3\u5e93', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'name', l: '\u540d\u79f0' }, { k: 'type', l: '\u7c7b\u578b' }] },
  categories: { title: '\u9009\u62e9\u5206\u7c7b', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'name', l: '\u540d\u79f0' }] },
  regions: { title: '\u9009\u62e9\u533a\u57df', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'name', l: '\u540d\u79f0' }, { k: 'level', l: '\u7b49\u7ea7' }] },
  contracts: { title: '\u9009\u62e9\u5408\u540c', cols: [{ k: 'code', l: '\u7f16\u7801' }, { k: 'category', l: '\u7c7b\u578b' }, { k: 'status', l: '\u72b6\u6001' }] },
  orders: { title: '\u9009\u62e9\u8ba2\u5355', cols: [{ k: 'code', l: '\u5355\u636e\u53f7' }, { k: 'type', l: '\u7c7b\u578b' }, { k: 'amount', l: '\u91d1\u989d' }, { k: 'status', l: '\u72b6\u6001' }] },
  'sales-outs': { title: '\u9009\u62e9\u539f\u53d1\u8d27\u5355', cols: [{ k: 'code', l: '\u53d1\u8d27\u5355\u53f7' }, { k: 'dealerName', l: '\u7ecf\u9500\u5546' }, { k: 'warehouseName', l: '\u4ed3\u5e93' }, { k: 'amount', l: '\u91d1\u989d' }, { k: 'status', l: '\u72b6\u6001' }] },
  'purchase-orders': { title: '\u9009\u62e9\u91c7\u8d2d\u8ba2\u5355', cols: [{ k: 'code', l: '\u91c7\u8d2d\u5355\u53f7' }, { k: 'status', l: '\u72b6\u6001' }] }
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
// 当父组件清空 modelValue（如新建表单 reset）时，同步清空输入框显示，避免残留旧名称
watch(() => props.modelValue, (v) => {
  if (v === '' || v === null || v === undefined) displayText.value = ''
})

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
.resource-picker { width: 100%; }
.picker-search { margin-bottom: 12px; }
.clear-btn { cursor: pointer; }
.picker-pager { margin-top: 10px; display: flex; justify-content: flex-end; }
</style>

