<template>
  <div class="contract-prices">
    <div class="cp-toolbar">
      <el-input v-model="keyword" placeholder="产品编码/名称" clearable style="width:220px" @keyup.enter="reload">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width:120px" @change="reload">
        <el-option label="启用" value="active" /><el-option label="停用" value="inactive" />
      </el-select>
      <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="onReset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <div style="flex:1" />
      <el-button type="primary" plain v-has="['contract_price:create','contract:edit']" @click="importVisible = true"><el-icon><Upload /></el-icon>导入</el-button>
      <el-button type="primary" plain @click="onExport"><el-icon><Download /></el-icon>导出</el-button>
      <el-button type="primary" v-has="['contract_price:create','contract:edit']" @click="openAdd"><el-icon><Plus /></el-icon>新增价格</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column prop="productCode" label="产品编码" width="150" />
      <el-table-column prop="productName" label="产品名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="productSpec" label="规格" width="130" show-overflow-tooltip />
      <el-table-column prop="priceInclTax" label="含税单价" width="110" align="right">
        <template #default="{ row }">{{ Number(row.priceInclTax || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="priceExclTax" label="不含税单价" width="110" align="right">
        <template #default="{ row }">{{ Number(row.priceExclTax || 0).toFixed(4) }}</template>
      </el-table-column>
      <el-table-column align="right" prop="taxRate" label="税率" width="80">
        <template #default="{ row }">{{ Math.round(Number(row.taxRate || 0) * 10000) / 100 }}%</template>
      </el-table-column>
      <el-table-column prop="validFrom" label="生效日期" width="110" />
      <el-table-column prop="validTo" label="失效日期" width="110" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" v-has="['contract_price:edit','contract:edit']" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" v-has="['contract_price:delete','contract:edit']" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px;justify-content:flex-end" background small layout="total, sizes, prev, pager, next"
      :total="total" :page-sizes="[20,50,100]" :page-size="size" :current-page="page"
      @current-change="(p) => { page = p; load() }" @size-change="(s) => { size = s; page = 1; load() }" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑合同价' : '新增合同价'" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="产品" required>
          <ResourcePicker v-model="form.productId" :display-value="form.productName" :disabled="!!editing" resource="products"
            placeholder="选择产品" @pick="onPickProduct" />
        </el-form-item>
        <el-form-item label="含税单价" required>
          <el-input-number v-model="form.priceInclTax" :min="0" :precision="4" controls-position="right" style="width:200px" @change="calcExcl" />
        </el-form-item>
        <el-form-item label="税率">
          <el-input-number v-model="form.taxRate" :min="0" :max="1" :precision="4" :step="0.01" controls-position="right" style="width:200px" @change="calcExcl" />
        </el-form-item>
        <el-form-item label="不含税单价">
          <el-input :model-value="form.priceExclTax != null ? Number(form.priceExclTax).toFixed(4) : ''" readonly />
        </el-form-item>
        <el-form-item label="生效日期"><el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="失效日期"><el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:160px">
            <el-option label="启用" value="active" /><el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入合同价清单" width="500px">
      <el-upload ref="uploadRef" :action="importUrl" :headers="uploadHeaders" :on-success="onImportSuccess"
        :on-error="onImportError" :show-file-list="false" accept=".xlsx,.xls" :auto-upload="false" :before-upload="beforeImport">
        <el-button type="primary">选择Excel文件</el-button>
      </el-upload>
      <p style="margin-top:12px;color:var(--el-text-color-secondary)">按产品编码匹配，合同内同一产品仅允许一条价格。</p>
      <el-button size="small" link type="primary" @click="onDownloadTemplate">下载导入模板</el-button>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Upload, Download, Plus } from '@element-plus/icons-vue'
import ResourcePicker from '@/components/ResourcePicker.vue'
import { getToken } from '@/utils/auth'
import {
  listContractPrices, createContractPrice, updateContractPrice,
  deleteContractPrice, importContractPrices, exportContractPrices, downloadContractPriceTemplate
} from '@/api/contractPrice'

const props = defineProps({ contractId: { type: [Number, String], required: true } })

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const statusFilter = ref('')

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({ productId: '', productName: '', priceInclTax: 0, taxRate: 0.13, priceExclTax: 0, validFrom: '', validTo: '', status: 'active' })

const importVisible = ref(false)
const importUrl = computed(() => `/api/contracts/${props.contractId}/prices/actions/import`)
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + (getToken() || '') }))
const uploadRef = ref(null)
const importing = ref(false)

async function load() {
  if (!props.contractId) return
  loading.value = true
  try {
    const res = await listContractPrices(props.contractId, {
      page: page.value, size: size.value,
      productCode: keyword.value.trim() || undefined,
      status: statusFilter.value || undefined
    })
    const data = res.data || {}
    rows.value = data.list || data.records || []
    total.value = data.total || rows.value.length
  } finally { loading.value = false }
}
function reload() { page.value = 1; load() }
function onReset() { keyword.value = ''; statusFilter.value = ''; reload() }

function resetForm() {
  form.productId = ''; form.productName = ''; form.priceInclTax = 0; form.taxRate = 0.13
  form.priceExclTax = 0; form.validFrom = ''; form.validTo = ''; form.status = 'active'
}
function onPickProduct(p) {
  if (!p) { form.productId = ''; form.productName = ''; return }
  form.productId = p.value
  form.productName = p.label
}
function calcExcl() {
  const incl = Number(form.priceInclTax)
  const rate = Number(form.taxRate)
  if (Number.isFinite(incl) && Number.isFinite(rate)) {
    form.priceExclTax = Math.round((incl / (1 + rate)) * 10000) / 10000
  }
}
function openAdd() { editing.value = null; resetForm(); dialogVisible.value = true }
function openEdit(row) {
  editing.value = row
  form.productId = row.productId
  form.productName = row.productName ? (row.productCode + ' · ' + row.productName) : ''
  form.priceInclTax = Number(row.priceInclTax || 0)
  form.taxRate = Number(row.taxRate || 0.13)
  form.priceExclTax = Number(row.priceExclTax || 0)
  form.validFrom = row.validFrom || ''
  form.validTo = row.validTo || ''
  form.status = row.status || 'active'
  dialogVisible.value = true
}
async function onSave() {
  if (!form.productId) { ElMessage.warning('请选择产品'); return }
  if (!(Number(form.priceInclTax) >= 0)) { ElMessage.warning('含税单价不能小于 0'); return }
  if (form.validFrom && form.validTo && form.validFrom > form.validTo) { ElMessage.warning('生效日期不能晚于失效日期'); return }
  const payload = {
    productId: form.productId, priceInclTax: form.priceInclTax, taxRate: form.taxRate,
    validFrom: form.validFrom || null, validTo: form.validTo || null, status: form.status
  }
  saving.value = true
  try {
    if (editing.value) await updateContractPrice(editing.value.id, payload)
    else await createContractPrice(props.contractId, payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally { saving.value = false }
}
function onDelete(row) {
  ElMessageBox.confirm('确认删除产品「' + (row.productName || row.productCode) + '」的合同价？', '删除确认', { type: 'warning' })
    .then(async () => { await deleteContractPrice(row.id); ElMessage.success('已删除'); load() }).catch(() => {})
}

function onExport() { exportContractPrices(props.contractId).then(() => ElMessage.success('导出成功')).catch(() => ElMessage.error('导出失败')) }
function onDownloadTemplate() { downloadContractPriceTemplate().catch(() => ElMessage.error('模板下载失败')) }
function beforeImport() { importing.value = true; return true }
function submitImport() { if (uploadRef.value) uploadRef.value.submit() }
function onImportSuccess(res) {
  importing.value = false
  importVisible.value = false
  const d = res?.data || res || {}
  if (d.failed) ElMessage.warning('导入完成：成功 ' + (d.success || 0) + ' 条，失败 ' + d.failed + ' 条')
  else ElMessage.success('导入成功')
  load()
}
function onImportError() { importing.value = false; ElMessage.error('导入失败，请检查文件格式') }

defineExpose({ load, refresh: load })
load()
</script>

<style scoped>
.cp-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
</style>
