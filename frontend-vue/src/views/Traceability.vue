<template>
  <div class="trace-page">
    <el-card shadow="never">
      <div class="search-bar">
        <el-radio-group v-model="mode" @change="onModeChange">
          <el-radio-button value="serial">序列号追溯</el-radio-button>
          <el-radio-button value="batch">批次追溯</el-radio-button>
        </el-radio-group>
        <el-input
          ref="inputRef"
          v-model="keyword"
          :placeholder="mode === 'serial' ? '扫描或输入序列号/UDI' : '输入批号'"
          clearable
          style="width:360px"
          @keyup.enter="search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </div>

      <el-empty v-if="!searched" description="请输入序列号或批号进行追溯" />

      <template v-else-if="mode === 'serial' && result">
        <el-descriptions title="序列号信息" :column="3" border size="small" class="block">
          <el-descriptions-item label="序列号">{{ result.serialNo }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag v-if="result.currentStock" type="success">在库</el-tag>
            <el-tag v-else type="info">已出库/无在库记录</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="事件数">{{ result.eventsCount }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="result.currentStock" title="当前库存" :column="4" border size="small" class="block">
          <el-descriptions-item label="仓库">{{ warehouseName(result.currentStock.warehouseId) }}</el-descriptions-item>
          <el-descriptions-item label="批号">{{ result.currentStock.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ result.currentStock.qty }}</el-descriptions-item>
          <el-descriptions-item label="效期">{{ result.currentStock.expDate || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="block">
          <h4>出入库时间线</h4>
          <el-timeline v-if="result.events && result.events.length">
            <el-timeline-item
              v-for="(ev, idx) in result.events"
              :key="idx"
              :timestamp="ev.at"
              :type="eventType(ev.event)"
            >
              <el-tag size="small" :type="eventType(ev.event)">{{ eventLabel(ev.event) }}</el-tag>
              <span class="ev-ref">{{ ev.refCode || ev.refId || '-' }}</span>
              <span class="ev-qty">数量: {{ ev.qty ?? '-' }}</span>
              <span v-if="ev.batchNo" class="ev-batch">批号: {{ ev.batchNo }}</span>
              <span v-if="ev.warehouseId" class="ev-wh">仓库: {{ warehouseName(ev.warehouseId) }}</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="无出入库事件" />
        </div>
      </template>

      <template v-else-if="mode === 'batch' && result">
        <el-descriptions title="批次信息" :column="3" border size="small" class="block">
          <el-descriptions-item label="批号">{{ result.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="事件数">{{ result.eventsCount }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="result.events" border stripe class="block">
          <el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.at) }}</template></el-table-column>
          <el-table-column label="事件" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="eventType(row.event)">{{ eventLabel(row.event) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="serialNo" label="序列号" width="160" />
          <el-table-column align="right" prop="qty" label="数量" width="90" />
          <el-table-column label="仓库">
            <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { formatDateTime } from '@/utils/format'
import { Search } from '@element-plus/icons-vue'
import request from '@/utils/request'

const mode = ref('serial')
const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const result = ref(null)
const warehouseMap = ref({})
const inputRef = ref(null)

onMounted(loadWarehouses)

async function loadWarehouses() {
  try {
    const { data } = await request({ url: '/api/warehouses', method: 'get', params: { size: 1000 } })
    const list = Array.isArray(data) ? data : (data?.list || [])
    list.forEach(w => { warehouseMap.value[w.id] = w.name || w.code })
  } catch (e) { /* ignore */ }
}
function warehouseName(id) { return warehouseMap.value[id] || (id ? '#' + id : '-') }

function onModeChange() { result.value = null; searched.value = false; keyword.value = ''; inputRef.value?.focus() }

async function search() {
  const kw = keyword.value.trim()
  if (!kw) return
  loading.value = true
  searched.value = true
  try {
    const url = mode.value === 'serial' ? '/api/traceability/by-serial' : '/api/traceability/by-batch'
    const param = mode.value === 'serial' ? { serialNo: kw } : { batchNo: kw }
    const { data } = await request({ url, method: 'get', params: param })
    result.value = data
  } finally { loading.value = false }
}

function eventLabel(ev) {
  const map = {
    RECEIPT: '收货入库', SALES_OUT: '销售出库',
    IN: '入库', OUT: '出库', ADJUST: '库存调整', MOVE: '库存移动'
  }
  return map[ev] || ev
}
function eventType(ev) {
  if (ev === 'RECEIPT' || ev === 'IN') return 'success'
  if (ev === 'SALES_OUT' || ev === 'OUT') return 'warning'
  if (ev === 'ADJUST') return 'danger'
  return 'primary'
}
</script>

<style scoped>
.search-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }
.block { margin-top: 16px; }
.block h4 { margin: 0 0 10px; }
.ev-ref { margin-left: 10px; color: #303133; }
.ev-qty, .ev-batch, .ev-wh { margin-left: 14px; color: #909399; font-size: 13px; }
</style>