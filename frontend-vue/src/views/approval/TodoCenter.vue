<template>
  <div class="todo-page">
    <el-tabs v-model="activeTab" @tab-change="reload">
      <el-tab-pane label="我的待办" name="todo" />
      <el-tab-pane label="我已处理" name="done" />
      <el-tab-pane label="我发起的" name="submitted" />
      <el-tab-pane label="抄送我的" name="cc" />
    </el-tabs>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row)">{{ row.title || row.businessCode || ('审批#' + (row.instanceId || row.id)) }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="业务类型" width="120">
        <template #default="{ row }">{{ businessLabel(row.businessType) }}</template>
      </el-table-column>
      <el-table-column v-if="activeTab === 'todo' || activeTab === 'done'" label="当前节点" prop="nodeName" width="140" />
      <el-table-column v-if="activeTab === 'todo' || activeTab === 'done'" label="审批人" prop="assigneeName" width="120" />
      <el-table-column v-else label="发起人" prop="submitterName" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt || row.startedAt) }}</template>
      </el-table-column>
      <el-table-column v-if="activeTab === 'todo'" label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="actApprove(row)">同意</el-button>
          <el-button size="small" type="danger" @click="actReject(row)">驳回</el-button>
          <el-button size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
      <el-table-column v-else label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="page"
      :page-size="size"
      @current-change="onPage"
    />

    <el-dialog v-model="commentVisible" title="审批意见" width="460px">
      <el-input v-model="commentText" type="textarea" :rows="3" placeholder="请输入审批意见（可选）" />
      <template #footer>
        <el-button @click="commentVisible = false">取消</el-button>
        <el-button type="primary" @click="submitComment">确认</el-button>
      </template>
    </el-dialog>

    <ApprovalDetailDrawer v-model="drawerVisible" :instance-id="drawerId" @refresh="reload" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  myTodoTasks, myDoneTasks, mySubmitted, myCc, approveTask, rejectTask
} from '@/api/approval'
import ApprovalDetailDrawer from './ApprovalDetailDrawer.vue'
import { BUSINESS_LABELS, INSTANCE_STATUS_LABELS, TASK_STATUS_LABELS, formatTime } from './dict'

const activeTab = ref('todo')
const rows = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)

const commentVisible = ref(false)
const commentText = ref('')
let pendingAction = null

const drawerVisible = ref(false)
const drawerId = ref(null)

function businessLabel(t) { return BUSINESS_LABELS[t] || t || '-' }
function statusLabel(s) {
  if (activeTab.value === 'todo' || activeTab.value === 'done') return TASK_STATUS_LABELS[s] || s
  return INSTANCE_STATUS_LABELS[s] || s
}
function statusType(s) {
  if (s === 'APPROVED' || s === 'PASSED') return 'success'
  if (s === 'REJECTED' || s === 'TERMINATED' || s === 'VOID' || s === 'CANCELLED') return 'danger'
  if (s === 'RUNNING' || s === 'PENDING') return 'warning'
  return 'info'
}

async function reload() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    let res
    if (activeTab.value === 'todo') res = await myTodoTasks(params)
    else if (activeTab.value === 'done') res = await myDoneTasks(params)
    else if (activeTab.value === 'submitted') res = await mySubmitted(params)
    else res = await myCc(params)
    rows.value = (res.data && res.data.list) || []
    total.value = (res.data && res.data.total) || 0
  } finally {
    loading.value = false
  }
}

function onPage(p) { page.value = p; reload() }

function openDetail(row) {
  const iid = row.instanceId || row.id
  if (!iid) { ElMessage.warning('缺少实例编号'); return }
  drawerId.value = iid
  drawerVisible.value = true
}

function actApprove(row) {
  pendingAction = () => approveTask(row.id, commentText.value)
  commentText.value = ''
  commentVisible.value = true
}
function actReject(row) {
  ElMessageBox.confirm('确认驳回该审批？', '提示', { type: 'warning' })
    .then(() => {
      pendingAction = () => rejectTask(row.id, commentText.value)
      commentText.value = ''
      commentVisible.value = true
    })
    .catch(() => {})
}
async function submitComment() {
  if (!pendingAction) return
  try {
    await pendingAction()
    ElMessage.success('操作成功')
    commentVisible.value = false
    reload()
  } catch (e) { /* handled by interceptor */ }
}

onMounted(reload)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
</style>