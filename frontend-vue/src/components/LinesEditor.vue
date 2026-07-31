<template>
  <el-form-item :label="field.label || '明细'" :required="field.required" class="lines-item">
    <div class="lines-box">
      <el-table :data="rows" border size="small" style="width:100%">
        <el-table-column v-for="c in cols" :key="c.k" :min-width="c.w || 140">
          <template #header>
            <span v-if="c.required" class="line-req">*</span>{{ c.l }}
          </template>
          <template #default="{ row }">
            <ResourcePicker v-if="c.type === 'picker' || c.picker" v-model="row[c.k]"
              :resource="c.picker || 'products'" :placeholder="c.l" @pick="(p) => onPick(row, c, p)" />
            <el-input-number v-else-if="c.type === 'number'" v-model="row[c.k]" :controls="false" :min="c.min ?? 0" style="width:100%" />
            <el-input v-else v-model="row[c.k]" :placeholder="c.l" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button size="small" type="danger" link @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" type="primary" plain style="margin-top:8px" @click="addRow">
        <el-icon><Plus /></el-icon>添加明细行
      </el-button>
    </div>
  </el-form-item>
</template>

<script setup>
import { ref, watch } from 'vue'
import ResourcePicker from '@/components/ResourcePicker.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  field: { type: Object, required: true }
})
const emit = defineEmits(['update:modelValue'])

const defaultCols = [
  { k: 'productId', l: '产品', type: 'picker', picker: 'products' },
  { k: 'qty', l: '数量', type: 'number' },
  { k: 'unitPrice', l: '单价', type: 'number' },
  { k: 'taxRate', l: '税率', type: 'number' }
]
const cols = props.field.cols
  ? props.field.cols.map((c) => ({ k: c.k, l: c.l, type: c.type, picker: c.picker, w: c.w }))
  : defaultCols

const rows = ref(props.modelValue.length ? [...props.modelValue] : [{}])

watch(rows, (v) => {
  emit('update:modelValue', v.filter((r) => r.productId).map((r, i) => ({ seq: i + 1, ...r })))
}, { deep: true })

function addRow() { rows.value.push({}) }
function removeRow(i) { rows.value.splice(i, 1) }
function onPick(row, c, p) {
  if (c.picker === 'products') {
    // v3.7.4: 产品切换时清空本行的数量与单价
    row.qty = null
    row.unitPrice = p && p.row && p.row.price != null ? p.row.price : null
  }
}
</script>

<style scoped>
.line-req { color: #f56c6c; margin-right: 4px; font-weight: bold; }
.lines-item :deep(.el-form-item__content) { display: block; }
.lines-box { width: 100%; }
</style>
