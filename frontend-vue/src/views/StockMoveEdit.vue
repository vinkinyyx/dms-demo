<template>
  <div class="stock-move-edit">
    <div class="page-toolbar">
      <el-button @click="$router.back()"><el-icon><ArrowLeft /></el-icon>返回</el-button>
      <div class="spacer" />
      <el-button v-if="!readonly" type="primary" :loading="saving" @click="submit">
        <el-icon><Check /></el-icon>保存并生效
      </el-button>
    </div>

    <template v-if="!readonly">
      <el-card shadow="never">
        <template #header>
          <el-icon><Document /></el-icon>{{ isAdjust ? '库存状态调整' : '库存移动' }}
        </template>
        <el-form label-width="100px" size="default">
          <el-form-item label="处理模式" required>
            <el-radio-group v-model="form.moveType" @change="onModeChange">
              <el-radio value="STATUS_ADJUST">仓内状态调整</el-radio>
              <el-radio value="WAREHOUSE_TRANSFER">跨仓移动</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="源仓库" required>
            <ResourcePicker resource="warehouses" v-model="form.fromWarehouseId"
              :display-value="fromWarehouseName" @pick="onFromWarehousePick" placeholder="选择源仓库" />
          </el-form-item>
          <el-form-item v-if="!isAdjust" label="目标仓库" required>
            <ResourcePicker resource="warehouses" v-model="form.toWarehouseId"
              :display-value="toWarehouseName" @pick="onToWarehousePick" placeholder="选择目标仓库" />
          </el-form-item>
          <el-form-item label="备注/原因">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" style="margin-top:14px">
        <template #header>
          <el-icon><Goods /></el-icon>移动明细
          <span style="margin-left:12px;color:var(--dms-text-4);font-weight:normal">物料/批次/序列号均从库存中选择，不可手填</span>
          <div style="float:right">
            <el-button type="primary" size="small" :disabled="!canAddLine" @click="openInventoryPicker">
              <el-icon><Plus /></el-icon>从库存选择
            </el-button>
          </div>
        </template>
        <el-table :data="form.lines" border size="small" style="width:100%">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="产品编码" min-width="120" prop="productCode" />
          <el-table-column label="产品名称" min-width="160" prop="productName" />
          <el-table-column label="批次号" width="120" prop="batchNo">
            <template #default="{ row }">{{ row.batchNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="序列号" width="160">
            <template #default="{ row }">
              <span v-if="row.serialNo">{{ row.serialNo }}</span>
              <el-tag v-else-if="row.isSerialManaged" type="warning" size="small">序列号管理</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="在库数" width="90" prop="onHand" />
          <el-table-column label="当前状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.fromStockStatus)" size="small">{{ statusText(row.fromStockStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="本次数量" width="130">
            <template #default="{ row }">
              <el-input-number v-if="!row.isSerialManaged" v-model="row.qty" :min="1" :max="Number(row.onHand)" :precision="0" size="small" style="width:100%" controls-position="right" />
              <el-tag v-else type="info" size="small">1 件</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="目标状态" width="150">
            <template #default="{ row }">
              <el-select v-model="row.toStockStatus" size="small" style="width:100%">
                <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="form.lines.splice($index,1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="form.lines.length===0" description="请从库存中选择要移动/调整的物料" />
      </el-card>
    </template>

    <template v-else>
      <el-card shadow="never">
        <template #header><el-icon><Document /></el-icon>库存移动单详情</template>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="移动单号">{{ move.code || '-' }}</el-descriptions-item>
          <el-descriptions-item label="移动类型">
            <el-tag :type="move.moveType === 'STATUS_ADJUST' ? 'warning' : 'primary'" size="small">
              {{ move.moveType === 'STATUS_ADJUST' ? '仓内状态调整' : '跨仓移动' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(move.status)" size="small">{{ statusText(move.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="源仓库">{{ move.fromWarehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标仓库">{{ move.toWarehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ move.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="move.fromStockStatus" label="源状态">
            <el-tag :type="statusTagType(move.fromStockStatus)" size="small">{{ statusText(move.fromStockStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="move.toStockStatus" label="目标状态">
            <el-tag :type="statusTagType(move.toStockStatus)" size="small">{{ statusText(move.toStockStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(move.createdAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
      <el-card shadow="never" style="margin-top:14px">
        <template #header><el-icon><Goods /></el-icon>移动明细</template>
        <el-table :data="(move.lines || [])" border size="small">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="产品编码" min-width="120" prop="productCode" />
          <el-table-column label="产品名称" min-width="160" prop="productName" />
          <el-table-column label="批次号" width="120">
            <template #default="{ row }">{{ row.batchNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="序列号" width="160">
            <template #default="{ row }">{{ row.serialNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="数量" width="90" prop="qty" />
          <el-table-column label="源状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTagType(row.fromStockStatus)" size="small">{{ statusText(row.fromStockStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="目标状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTagType(row.toStockStatus)" size="small">{{ statusText(row.toStockStatus) }}</el-tag></template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-dialog v-model="pickerVisible" title="从库存选择" width="920px" append-to-body>
      <div style="display:flex;gap:10px;margin-bottom:12px">
        <el-input v-model="pickerKeyword" placeholder="输入产品编码/名称/批次搜索" clearable style="flex:1" @input="onPickerSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="pickerStockStatus" placeholder="库存状态" clearable style="width:140px" @change="loadInventory">
          <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-button @click="loadInventory">刷新</el-button>
      </div>
      <el-table :data="inventoryList" v-loading="pickerLoading" height="420" border size="small"
        @selection-change="onPickerSelection" ref="invTableRef">
        <el-table-column type="selection" width="45" :selectable="canSelectInv" />
        <el-table-column label="产品编码" min-width="120" prop="productCode" />
        <el-table-column label="产品名称" min-width="150" prop="productName" />
        <el-table-column label="批次号" width="110" prop="batchNo" />
        <el-table-column label="序列号" width="130">
          <template #default="{ row }">{{ row.serialNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="在库数" width="80" prop="qty" />
        <el-table-column label="库存状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.stockStatus)" size="small">{{ statusText(row.stockStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" @click="addSelectedInventory">添加选中（{{ pickerSelection.length }}）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Plus, Check, Document, Goods, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { formatDateTime } from '@/utils/format'
import { getDetail } from '@/api/crud'
import { statusText, statusTagType } from '@/utils/dict'
import ResourcePicker from '@/components/ResourcePicker.vue'

const route = useRoute()
const router = useRouter()

const statusOptions = [
  { value: 'QUALIFIED', label: '合格' },
  { value: 'DEFECTIVE', label: '不合格' },
  { value: 'QUARANTINED', label: '隔离' },
  { value: 'PENDING', label: '待检' }
]

const readonly = computed(() => route.params.id && route.params.id !== 'new')
const isAdjust = computed(() => form.moveType === 'STATUS_ADJUST')
const canAddLine = computed(() => !!form.fromWarehouseId && (isAdjust.value || !!form.toWarehouseId))

const form = reactive({
  moveType: 'WAREHOUSE_TRANSFER',
  fromWarehouseId: null,
  toWarehouseId: null,
  remark: '',
  lines: []
})
const fromWarehouseName = ref('')
const toWarehouseName = ref('')
const saving = ref(false)
const move = ref({})

function onModeChange() {
  form.lines = []
  if (isAdjust.value) {
    form.toWarehouseId = null
    toWarehouseName.value = ''
  }
}
function onFromWarehousePick(p) {
  if (!p) { form.fromWarehouseId = null; fromWarehouseName.value = ''; form.lines = []; return }
  fromWarehouseName.value = p.label
  form.lines = []
}
function onToWarehousePick(p) {
  if (!p) { form.toWarehouseId = null; toWarehouseName.value = ''; return }
  toWarehouseName.value = p.label
}

const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerKeyword = ref('')
const pickerStockStatus = ref('')
const inventoryList = ref([])
const pickerSelection = ref([])
const invTableRef = ref(null)
let pickerTimer = null

function canSelectInv(row) {
  return Number(row.qty) > 0
}
function openInventoryPicker() {
  if (!canAddLine.value) { ElMessage.warning('请先选择源仓库' + (isAdjust.value ? '' : '和目标仓库')); return }
  pickerVisible.value = true
  pickerKeyword.value = ''
  pickerStockStatus.value = ''
  pickerSelection.value = []
  loadInventory()
}
function onPickerSearch() {
  if (pickerTimer) clearTimeout(pickerTimer)
  pickerTimer = setTimeout(loadInventory, 350)
}
async function loadInventory() {
  pickerLoading.value = true
  try {
    const params = { warehouseId: form.fromWarehouseId, page: 1, size: 200 }
    if (pickerKeyword.value.trim()) params.keyword = pickerKeyword.value.trim()
    if (pickerStockStatus.value) params.stockStatus = pickerStockStatus.value
    const res = await request({ url: '/api/inventory', method: 'get', params })
    inventoryList.value = (res.data && res.data.list) || []
  } finally {
    pickerLoading.value = false
  }
}
function onPickerSelection(rows) { pickerSelection.value = rows }
function addSelectedInventory() {
  if (!pickerSelection.value.length) { ElMessage.warning('请至少选择一条库存'); return }
  let added = 0
  for (const inv of pickerSelection.value) {
    if (form.lines.some((l) => l.srcInventoryId === inv.id)) continue
    const serialManaged = !!inv.isSerialManaged || !!inv.serialNo
    form.lines.push({
      srcInventoryId: inv.id,
      productId: inv.productId,
      productCode: inv.productCode,
      productName: inv.productName,
      batchNo: inv.batchNo || null,
      serialNo: inv.serialNo || null,
      isSerialManaged: serialManaged,
      onHand: Number(inv.qty),
      fromStockStatus: inv.stockStatus,
      toStockStatus: isAdjust.value ? 'QUARANTINED' : inv.stockStatus,
      qty: serialManaged ? 1 : 1
    })
    added++
  }
  pickerVisible.value = false
  ElMessage.success('已添加 ' + added + ' 行')
}

async function submit() {
  if (!form.fromWarehouseId) { ElMessage.warning('请选择源仓库'); return }
  if (!isAdjust.value && !form.toWarehouseId) { ElMessage.warning('请选择目标仓库'); return }
  if (!isAdjust.value && form.fromWarehouseId === form.toWarehouseId) { ElMessage.warning('跨仓移动的源仓库与目标仓库不能相同'); return }
  if (!form.lines.length) { ElMessage.warning('请至少添加一条明细'); return }
  for (let i = 0; i < form.lines.length; i++) {
    const l = form.lines[i]
    if (!l.qty || l.qty <= 0) { ElMessage.warning('第 ' + (i + 1) + ' 行数量必须 > 0'); return }
    if (Number(l.qty) > Number(l.onHand)) { ElMessage.warning('第 ' + (i + 1) + ' 行数量超过在库数'); return }
    if (isAdjust.value && l.fromStockStatus === l.toStockStatus) { ElMessage.warning('第 ' + (i + 1) + ' 行源状态与目标状态相同，无需调整'); return }
  }
  saving.value = true
  try {
    const payload = {
      moveType: form.moveType,
      fromWarehouseId: form.fromWarehouseId,
      toWarehouseId: isAdjust.value ? form.fromWarehouseId : form.toWarehouseId,
      remark: form.remark,
      lines: form.lines.map((l) => ({
        srcInventoryId: l.srcInventoryId,
        qty: Number(l.qty),
        fromStockStatus: l.fromStockStatus,
        toStockStatus: l.toStockStatus
      }))
    }
    const res = await request({ url: '/api/stock-moves', method: 'post', data: payload })
    ElMessage.success((res.data && res.data.message) || '保存成功')
    router.push('/m/stock-moves')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function loadMove() {
  try {
    const res = await getDetail('/api/stock-moves', route.params.id)
    move.value = res.data || {}
  } catch (e) {
    ElMessage.error('加载移动单失败')
  }
}

onMounted(() => {
  if (readonly.value) loadMove()
})
</script>

<style scoped>
.stock-move-edit { padding: 4px; }
.page-toolbar { display: flex; align-items: center; margin-bottom: 14px; }
.spacer { flex: 1; }
</style>
