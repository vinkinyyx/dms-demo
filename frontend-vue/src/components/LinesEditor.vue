<template>
  <el-form-item :label="field.label || '明细'" :required="field.required" class="lines-item">
    <div class="lines-box">
      <div class="lines-toolbar" v-if="field.importable || field.serialPasteSplit">
        <el-upload v-if="field.importable" :show-file-list="false" :auto-upload="false" accept=".xlsx,.xls" :on-change="onImportXlsx">
          <el-button size="small" type="success" plain>
            <el-icon><Upload /></el-icon>批量导入
          </el-button>
        </el-upload>
        <el-button v-if="field.serialPasteSplit" size="small" type="info" plain @click="showPaste = true">
          <el-icon><DocumentCopy /></el-icon>粘贴序列号
        </el-button>
        <el-button size="small" type="primary" plain @click="addRow">
          <el-icon><Plus /></el-icon>添加明细行
        </el-button>
        <el-button size="small" type="warning" plain @click="addSerialRow" v-if="hasSerialCol">
          <el-icon><Plus /></el-icon>序列号批量行
        </el-button>
      </div>
      <el-table :data="rows" border size="small" style="width:100%">
        <el-table-column v-for="c in cols" :key="c.k" :min-width="c.w || 140">
          <template #header>
            <span v-if="c.required" class="line-req">*</span>{{ c.l }}
          </template>
          <template #default="{ row }">
            <ResourcePicker v-if="c.type === 'picker' || c.picker" v-model="row[c.k]"
              :resource="c.picker || 'products'" :placeholder="c.l" @pick="(p) => onPick(row, c, p)" />
            <el-input-number v-else-if="c.type === 'number'" v-model="row[c.k]" :controls="false" :min="c.min ?? 0" style="width:100%" />
            <el-input v-else v-model="row[c.k]" :placeholder="c.placeholder || c.l" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button size="small" type="danger" link @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="!field.importable && !field.serialPasteSplit" size="small" type="primary" plain style="margin-top:8px" @click="addRow">
        <el-icon><Plus /></el-icon>添加明细行
      </el-button>
    </div>
    <el-dialog v-model="showPaste" title="粘贴序列号（支持换行/逗号/分号分隔）" width="600px">
      <el-form label-width="80px">
        <el-form-item label="产品">
          <ResourcePicker v-model="pasteProduct" resource="products" placeholder="选择产品" @pick="onPasteProductPick" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="pasteBatch" placeholder="批次管理产品需填，序列号管理可不填" />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input v-model="pasteText" type="textarea" :rows="8" placeholder="每行一个，或用 , ; 空格 分隔；自动按数量展开为多行" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPaste = false">取消</el-button>
        <el-button type="primary" @click="confirmPaste">展开为明细行</el-button>
      </template>
    </el-dialog>
  </el-form-item>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, DocumentCopy, Plus } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
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
  ? props.field.cols.map((c) => ({ k: c.k, l: c.l, type: c.type, picker: c.picker, w: c.w, required: c.required, placeholder: c.placeholder }))
  : defaultCols

const hasSerialCol = computed(() => cols.some((c) => c.k === 'serialNo'))
const rows = ref(props.modelValue.length ? [...props.modelValue] : [{}])

const showPaste = ref(false)
const pasteText = ref('')
const pasteProduct = ref(null)
const pasteBatch = ref('')

watch(rows, (v) => {
  emit('update:modelValue', v.filter((r) => Object.values(r).some((x) => x !== '' && x != null)).map((r, i) => ({ seq: i + 1, ...r })))
}, { deep: true })

function addRow() { rows.value.push({}) }
function removeRow(i) { rows.value.splice(i, 1) }
function addSerialRow() { rows.value.push({ qty: 1 }) }
function onPick(row, c, p) {
  if (c.picker === 'products') {
    row.qty = null
    row.unitPrice = p && p.row && p.row.price != null ? p.row.price : null
  }
}

function onImportXlsx(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    try {
      const wb = XLSX.read(ev.target.result, { type: 'array' })
      const ws = wb.Sheets[wb.SheetNames[0]]
      const json = XLSX.utils.sheet_to_json(ws, { defval: '' })
      if (!json.length) { ElMessage.warning('文件无数据'); return }
      // 列名映射：产品编码 -> productId, 数量 -> qty, 批次号 -> batchNo, 序列号 -> serialNo, 单价 -> unitPrice
      const headerMap = {
        '产品编码': 'productCode', '产品': 'productName', '数量': 'qty',
        '批次号': 'batchNo', '序列号': 'serialNo', '单价': 'unitPrice'
      }
      const newRows = []
      for (const r of json) {
        const row = {}
        for (const [hk, hv] of Object.entries(r)) {
          const k = headerMap[hk] || hk
          if (['productCode', 'productName', 'qty', 'batchNo', 'serialNo', 'unitPrice'].includes(k)) {
            row[k === 'productCode' || k === 'productName' ? 'productCode' : k] = hv
          }
        }
        if (row.productCode) row.productId = String(row.productCode)
        newRows.push(row)
      }
      rows.value = [...rows.value.filter((r) => Object.values(r).some((v) => v !== '' && v != null)), ...newRows]
      ElMessage.success('已导入 ' + newRows.length + ' 行（产品编码/名称已带入，请手动选择产品带出默认信息）')
    } catch (e) {
      ElMessage.error('解析失败：' + (e.message || e))
    }
  }
  reader.readAsArrayBuffer(file)
}

function onPasteProductPick(p) {
  if (p && p.row) {
    pasteProduct.value = p.row.id
  }
}

function confirmPaste() {
  if (!pasteText.value.trim()) { ElMessage.warning('请粘贴序列号'); return }
  if (!pasteProduct.value) { ElMessage.warning('请选择产品'); return }
  const items = pasteText.value.split(/[\s,;,,\uFF0C;,\uFF1B\n\r]+/).map((s) => s.trim()).filter(Boolean)
  if (!items.length) { ElMessage.warning('未识别到任何序列号'); return }
  for (const sn of items) {
    rows.value.push({ productId: pasteProduct.value, serialNo: sn, batchNo: pasteBatch.value || '', qty: 1 })
  }
  showPaste.value = false
  pasteText.value = ''
  ElMessage.success('已展开 ' + items.length + ' 行')
}
</script>

<style scoped>
.line-req { color: #f56c6c; margin-right: 4px; font-weight: bold; }
.lines-item :deep(.el-form-item__content) { display: block; }
.lines-box { width: 100%; }
.lines-toolbar { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
</style>
