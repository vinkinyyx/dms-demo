<template>
  <el-card shadow="never">
    <div class="toolbar">
      <span class="title">审批委托（全局）</span>
      <div class="spacer" />
      <el-button type="primary" @click="openCreate">新建委托</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px;">
      启用后，在有效期内所有发给委托人的审批任务将自动转交给受托人处理。
    </el-alert>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="委托人" width="160">
        <template #default="{ row }">{{ userName(row.delegatorId) }}</template>
      </el-table-column>
      <el-table-column label="受托人" width="160">
        <template #default="{ row }">{{ userName(row.delegateeId) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" width="170"><template #default="{ row }">{{ formatTime(row.startsAt) }}</template></el-table-column>
      <el-table-column label="结束时间" width="170"><template #default="{ row }">{{ formatTime(row.endsAt) }}</template></el-table-column>
      <el-table-column prop="reason" label="原因" min-width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '生效中' : '已停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.enabled" size="small" link type="danger" @click="onDisable(row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新建委托" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="委托人">
          <el-select v-model="form.delegatorId" filterable remote :remote-method="queryUsers" :loading="loadingUsers" placeholder="搜索委托人" style="width:100%;">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.name + '（' + u.username + '）'" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="受托人">
          <el-select v-model="form.delegateeId" filterable remote :remote-method="queryUsers" :loading="loadingUsers" placeholder="搜索受托人" style="width:100%;">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.name + '（' + u.username + '）'" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期"><el-date-picker v-model="range" type="datetimerange" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width:100%;" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { listDelegations, createDelegation, disableDelegation } from '@/api/approval'
import { formatTime } from './dict'

const loading = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const form = ref({ delegatorId: null, delegateeId: null, reason: '' })
const range = ref([])
const userOptions = ref([])
const loadingUsers = ref(false)
const userCache = ref({})

async function reload() {
  loading.value = true
  try {
    const res = await listDelegations({ page: 1, size: 100 })
    rows.value = (res.data && res.data.list) || []
    const ids = new Set()
    rows.value.forEach((r) => { ids.add(r.delegatorId); ids.add(r.delegateeId) })
    await ensureUsers(Array.from(ids))
  } finally { loading.value = false }
}
async function ensureUsers(ids) {
  const missing = ids.filter((id) => !userCache.value[id])
  for (const id of missing) {
    try {
      const res = await request({ url: '/api/users/' + id, method: 'get' })
      if (res.data) userCache.value[id] = res.data.name || res.data.username
    } catch { userCache.value[id] = '用户#' + id }
  }
}
function userName(id) { return userCache.value[id] || ('用户#' + id) }

async function queryUsers(q) {
  loadingUsers.value = true
  try {
    const res = await request({ url: '/api/users', method: 'get', params: { keyword: q, page: 1, size: 20 } })
    userOptions.value = (res.data && res.data.list) || []
  } finally { loadingUsers.value = false }
}
function openCreate() {
  form.value = { delegatorId: null, delegateeId: null, reason: '' }
  range.value = []
  queryUsers('')
  dialogVisible.value = true
}
async function onSubmit() {
  if (!form.value.delegatorId || !form.value.delegateeId) { ElMessage.warning('请选择委托人和受托人'); return }
  if (form.value.delegatorId === form.value.delegateeId) { ElMessage.warning('委托人和受托人不能相同'); return }
  if (!range.value || range.value.length !== 2) { ElMessage.warning('请选择有效期'); return }
  await createDelegation({
    delegatorId: form.value.delegatorId,
    delegateeId: form.value.delegateeId,
    startsAt: range.value[0], endsAt: range.value[1],
    reason: form.value.reason
  })
  ElMessage.success('已创建')
  dialogVisible.value = false
  reload()
}
async function onDisable(row) {
  await ElMessageBox.confirm('确认停用该委托？', '提示', { type: 'warning' })
  await disableDelegation(row.id)
  ElMessage.success('已停用')
  reload()
}
onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 14px; }
.title { font-weight: 600; font-size: 15px; }
.spacer { flex: 1; }
</style>