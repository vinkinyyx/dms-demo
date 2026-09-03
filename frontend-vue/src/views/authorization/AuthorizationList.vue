<template>
  <div class="auth-list-page">
    <el-alert
      :title="enforce ? '授权与下单已挂钩：经销商无有效授权（产品线+终端医院+有效期）时不能下单/出库' : '授权与下单解耦：当前可直接下单，不受授权限制'"
      :type="enforce ? 'error' : 'info'"
      :closable="false"
      show-icon
      style="margin-bottom:12px">
      <template #default>
        <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <span>{{ enforce ? '✅ 授权管控已开启' : '⏸️ 授权管控已关闭' }}</span>
          <el-switch :model-value="enforce" :loading="switchLoading" active-text="挂钩下单" inactive-text="解耦" @change="onToggleEnforce" />
          <el-button link type="primary" :loading="switchLoading" @click="onToggleEnforce(!enforce)">{{ enforce ? '关闭管控' : '开启管控' }}</el-button>
        </div>
      </template>
    </el-alert>

    <el-form :inline="true" size="small" @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部状态" style="width:140px" @change="reload">
          <el-option v-for="s in STATUS_OPTS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="经销商">
        <el-input v-model="query.dealerName" placeholder="经销商名称" clearable style="width:180px" @keyup.enter="reload" />
      </el-form-item>
      <el-form-item label="合同编号">
        <el-input v-model="query.code" placeholder="合同编号" clearable style="width:160px" @keyup.enter="reload" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="success" v-has="'authorization:create'" @click="goCreate">新增授权</el-button>
        <el-button @click="doExport">导出</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="经销商" prop="dealerName" min-width="160" show-overflow-tooltip />
      <el-table-column label="授权产品线" prop="productLineNames" min-width="160" show-overflow-tooltip />
      <el-table-column label="授权终端医院" prop="terminalNames" min-width="200" show-overflow-tooltip />
      <el-table-column label="有效期" width="200">
        <template #default="{ row }">{{ row.validFrom }} ~ {{ row.validTo }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">查看</el-button>
          <el-button v-if="canTerminate(row)" v-has="'authorization:create'" link type="danger" @click="doTerminate(row)">终止</el-button>
          <el-button v-if="canRenew(row)" v-has="'authorization:create'" link type="warning" @click="doRenew(row)">续约</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display:flex;justify-content:flex-end;margin-top:12px">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="reload"
        @current-change="reload" />
    </div>

    <!-- 续约弹窗 -->
    <el-dialog v-model="renewVisible" title="授权续约" width="420px">
      <el-form label-width="90px">
        <el-form-item label="原授权">
          <span>AUTH-{{ renewRow?.id }}（{{ renewRow?.dealerName }}）</span>
        </el-form-item>
        <el-form-item label="新有效期">
          <el-date-picker
            v-model="renewRange" type="daterange" value-format="YYYY-MM-DD"
            range-separator="至" start-placeholder="开始" end-placeholder="结束"
            style="width:100%" :default-time="null" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRenew">提交续约审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listAuthorizations, getOrderEnforce, setOrderEnforce,
  terminateAuthorization, renewAuthorization, exportAuthorizations
} from './api'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const enforce = ref(false)
const switchLoading = ref(false)
const saving = ref(false)

const STATUS_OPTS = [
  { value: 'pending_approval', label: '审批中' },
  { value: 'terminate_pending', label: '终止审批中' },
  { value: 'not_started', label: '未开始' },
  { value: 'active', label: '生效中' },
  { value: 'expired', label: '已到期' },
  { value: 'terminated', label: '已终止' },
  { value: 'rejected', label: '已驳回' },
  { value: 'draft', label: '草稿' }
]

const query = reactive({ page: 1, size: 20, status: '', dealerName: '', code: '' })

const renewVisible = ref(false)
const renewRow = ref(null)
const renewRange = ref(null)

function statusTag(s) {
  return { active: 'success', not_started: 'info', pending_approval: 'warning',
    terminate_pending: 'warning', expired: 'info', terminated: 'danger',
    rejected: 'danger', draft: 'info' }[s] || 'info'
}
function canTerminate(row) { return row.status === 'active' || row.status === 'not_started' }
function canRenew(row) { return ['active', 'not_started', 'expired'].includes(row.status) }

async function loadEnforce() {
  try { const res = await getOrderEnforce(); enforce.value = !!res?.data?.enforced } catch (e) { /* ignore */ }
}
async function onToggleEnforce(val) {
  const target = typeof val === 'boolean' ? val : !enforce.value
  try {
    await ElMessageBox.confirm(
      target ? '开启后，经销商下单/出库若无有效授权将被拦截，确认开启？' : '关闭后授权与下单解耦，可直接下单，确认关闭？',
      '授权-下单挂钩开关', { type: 'warning' })
  } catch { return }
  switchLoading.value = true
  try {
    await setOrderEnforce(target)
    enforce.value = target
    ElMessage.success(target ? '已开启授权管控' : '已关闭授权管控')
  } catch (e) {
    ElMessage.error('设置失败: ' + (e?.message || e))
  } finally { switchLoading.value = false }
}

async function reload() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.status) params.status = query.status
    if (query.dealerName) params.dealerName = query.dealerName
    if (query.code) params.code = query.code
    const res = await listAuthorizations(params)
    const data = res?.data || {}
    rows.value = data.list || data.records || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载失败: ' + (e?.message || e))
  } finally { loading.value = false }
}
function reset() { query.status = ''; query.dealerName = ''; query.code = ''; query.page = 1; reload() }
function goCreate() { router.push('/authorizations/new') }
function goDetail(id) { router.push('/authorizations/' + id) }

async function doTerminate(row) {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('请输入终止原因', '授权终止', {
      confirmButtonText: '提交终止审批', cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim()) ? true : '终止原因必填'
    })
    reason = r.value
  } catch { return }
  try {
    await terminateAuthorization(row.id, { reason })
    ElMessage.success('已提交终止审批')
    reload()
  } catch (e) { ElMessage.error('终止失败: ' + (e?.response?.data?.message || e?.message || e)) }
}

function doRenew(row) {
  renewRow.value = row
  renewRange.value = [row.validTo, '']
  renewVisible.value = true
}
async function submitRenew() {
  if (!renewRange.value || !renewRange.value[0] || !renewRange.value[1]) {
    ElMessage.warning('请选择新的有效期区间'); return
  }
  saving.value = true
  try {
    await renewAuthorization(renewRow.value.id, { validFrom: renewRange.value[0], validTo: renewRange.value[1] })
    ElMessage.success('续约已提交审批')
    renewVisible.value = false
    reload()
  } catch (e) {
    ElMessage.error('续约失败: ' + (e?.response?.data?.message || e?.message || e))
  } finally { saving.value = false }
}

async function doExport() {
  try {
    const blob = await exportAuthorizations()
    const url = window.URL.createObjectURL(new Blob([blob]))
    const a = document.createElement('a')
    a.href = url; a.download = '授权列表.xlsx'; a.click()
    window.URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error('导出失败: ' + (e?.message || e)) }
}

onMounted(() => { loadEnforce(); reload() })
</script>
