<template>
  <div class="lines-editor">
    <el-table :data="modelValue" border size="small">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column v-for="col in visibleCols" :key="colKey(col, $index)" :label="colTitle(col)" :width="col.width" :min-width="col.minWidth">
        <template #header>
          <span>{{ colTitle(col) }}</span>
          <span v-if="col.required" class="required-mark">*</span>
        </template>
        <template #default="{ row }">
          <el-select
            v-if="colType(col) === 'select'"
            v-model="row[colField(col)]"
            :placeholder="col.placeholder || colTitle(col)"
            clearable
            filterable
            :multiple="col.multiple === true"
            :multiple-limit="col.multipleLimit"
            :collapse-tags="col.multiple === true"
            :collapse-tags-tooltip="col.multiple === true"
            style="width:100%"
          >
            <el-option v-for="opt in (col.options || [])" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <ResourcePicker
            v-else-if="colType(col) === 'resource' || colType(col) === 'picker'"
            v-model="row[colField(col)]"
            :resource="col.resource || col.picker"
            :multiple="col.multiple"
            :placeholder="col.placeholder || colTitle(col)"
            :disabled="col.disabledOnEdit && row.id"
            :display-value="row[col.displayKey]"
            @pick="(p) => onResourcePick(row, col, p)"
          />
          <el-input
            v-else-if="colType(col) === 'textarea'"
            v-model="row[colField(col)]"
            type="textarea"
            :rows="2"
            :placeholder="col.placeholder"
          />
          <el-input-number
            v-else-if="colType(col) === 'number'"
            v-model="row[colField(col)]"
            :min="col.min ?? 0"
            :max="col.max"
            :step="col.step ?? 1"
            :precision="col.precision"
            :controls="false"
            style="width:100%"
          />
          <el-switch
            v-else-if="colType(col) === 'switch' || colType(col) === 'boolean'"
            v-model="row[colField(col)]"
            :active-value="col.activeValue ?? true"
            :inactive-value="col.inactiveValue ?? false"
          />
          <el-date-picker
            v-else-if="colType(col) === 'date'"
            v-model="row[colField(col)]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
          <el-input v-else v-model="row[colField(col)]" :placeholder="col.placeholder || colTitle(col)" :readonly="col.readonly" />
        </template>
      </el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="70" fixed="right">
        <template #default="{ $index }">
          <el-button size="small" type="danger" link @click="removeLine($index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button v-if="!readonly" size="small" type="primary" plain @click="addLine" style="margin-top: 10px">
      <el-icon><Plus /></el-icon>添加明细行
    </el-button>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import ResourcePicker from './ResourcePicker.vue'

const props = defineProps({
  modelValue: { type: Array, required: true },
  columns: { type: Array, default: () => [] },
  field: { type: Object, default: null },
  context: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false },
  scene: { type: String, default: 'default' }
})
const emit = defineEmits(['update:modelValue'])

const resolvedColumns = computed(() => {
  if (Array.isArray(props.columns) && props.columns.length) return props.columns
  if (props.field && Array.isArray(props.field.columns) && props.field.columns.length) return props.field.columns
  if (props.field && Array.isArray(props.field.cols) && props.field.cols.length) return props.field.cols
  return []
})

const visibleCols = computed(() => {
  return resolvedColumns.value.filter(c => {
    if (!c || c.hidden) return false
    if (Array.isArray(c.scenes) && c.scenes.length && !c.scenes.includes(props.scene)) return false
    if (typeof c.showIf === 'function') {
      try { if (!c.showIf(props.context || {})) return false } catch (_) { /* ignore */ }
    }
    if (Array.isArray(c.showWhen) && c.showWhen.length === 2) {
      if ((props.context || {})[c.showWhen[0]] !== c.showWhen[1]) return false
    }
    return true
  })
})

const internalSync = ref(false)

function colField(c) { return c.field || c.k || c.prop }
function colTitle(c) { return c.title || c.label || c.l || '' }
function colType(c) { return c.type || (c.picker ? 'picker' : 'text') }
function colKey(c, idx) { return c.field || c.k || c.prop || ('col-' + idx) }

function addLine() {
  const blank = {}
  for (const c of resolvedColumns.value) {
    const f = colField(c)
    if (!f) continue
    if (c.value !== undefined) blank[f] = c.value
    else if (c.defaultValue !== undefined) blank[f] = c.defaultValue
    else {
      const t = colType(c)
      if (t === 'number') blank[f] = 0
      else if (t === 'switch' || t === 'boolean') blank[f] = false
      else blank[f] = t === 'select' ? '' : ''
    }
  }
  const next = [...(props.modelValue || []), blank]
  internalSync.value = true
  emit('update:modelValue', next)
}

function removeLine(i) {
  const next = props.modelValue.slice()
  next.splice(i, 1)
  internalSync.value = true
  emit('update:modelValue', next)
}

function onResourcePick(row, col, picked) {
  if (!picked) return
  if (col.onPick) col.onPick(row, picked)
  const raw = picked.raw || picked.row || picked
  if (col.resource === 'products' || col.picker === 'products') {
    if (raw) {
      const field = colField(col)
      if (field && field.endsWith('ProductId')) {
        const prefix = field.slice(0, -'ProductId'.length)
        if (raw.code) row[prefix + 'ProductCode'] = raw.code
        if (raw.nameCn || raw.name) row[prefix + 'ProductName'] = raw.nameCn || raw.name
        if (raw.spec) row[prefix + 'ProductSpec'] = raw.spec
      }
      if (raw.code && !row.productCode) row.productCode = raw.code
      if ((raw.nameCn || raw.name) && !row.productName) row.productName = raw.nameCn || raw.name
      if (raw.spec && !row.productSpec) row.productSpec = raw.spec
      if (raw.unit && !row.unit) row.unit = raw.unit
      if (raw.price != null && row.unitPrice == null) row.unitPrice = raw.price
    }
  }
  if (col.bindFields) {
    for (const [target, source] of Object.entries(col.bindFields)) {
      const v = source.split('.').reduce((o, k) => (o ? o[k] : undefined), raw)
      if (v !== undefined) row[target] = v
    }
  }
}
</script>

<style scoped>
.lines-editor { width: 100%; }
.required-mark { color: var(--el-color-danger); margin-left: 2px; }
</style>
