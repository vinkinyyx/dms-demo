<template>
  <el-card shadow="never">
    <div class="toolbar">
      <span class="title">审批监控（管理员）</span>
      <el-select v-model="status" placeholder="状态" clearable style="width:160px;margin-left:16px;" @change="reload">
        <el-option v-for="s in INSTANCE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <div class="spacer" />
      <el-button @click="reload">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="标题" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="业务类型" width="120"><template #default="{ row }">{{ BUSINESS_LABELS[row.businessType] || row.businessType }}</template></el-table-column>
      <el-table-column label="发起人" width="120" prop="submitterName" />
      <el-table-column label="当前节点" width="140" prop="currentNodeName" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><el-tag :type="INSTANCE_STATUS_TYPE[row.status] || 'info'">{{ INSTANCE_STATUS_LABELS[row.status] || row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="发起时间" width="170"><template #default="{ row }">{{ formatTime(row.startedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }"><el-button size="small" @click="openDetail(row)">详情/干预</el-button></template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
    <ApprovalDetailDrawer v-model="drawerVisible" :instance-id="drawerId" @refresh="reload" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminInstances } from '@/api/approval'
import ApprovalDetailDrawer from './ApprovalDetailDrawer.vue'
import { BUSINESS_LABELS, INSTANCE_STATUS, INSTANCE_STATUS_LABELS, INSTANCE_STATUS_TYPE, formatTime } from './dict'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref(null)
const drawerVisible = ref(false)
const drawerId = ref(null)

async function reload() {
  loading.value = true
  try {
    const res = await adminInstances({ page: page.value, size: size.value, status: status.value || undefined })
    rows.value = (res.data && res.data.list) || []
    total.value = (res.data && res.data.total) || 0
  } finally { loading.value = false }
}
function onPage(p) { page.value = p; reload() }
function openDetail(row) { drawerId.value = row.id; drawerVisible.value = true }
onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 14px; }
.title { font-weight: 600; font-size: 15px; }
.spacer { flex: 1; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>