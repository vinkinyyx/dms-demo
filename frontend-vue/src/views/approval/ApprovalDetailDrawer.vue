<template>
  <el-drawer :model-value="modelValue" @update:model-value="(v) => $emit('update:modelValue', v)" title="审批详情" size="640px">
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" class="block">
          <el-descriptions-item label="标题">{{ instance.title }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ businessLabel(instance.businessType) }}</el-descriptions-item>
          <el-descriptions-item label="业务编号">{{ instance.businessCode || instance.businessId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="INSTANCE_STATUS_TYPE[instance.status] || 'info'">{{ INSTANCE_STATUS_LABELS[instance.status] || instance.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发起人">{{ instance.submitterName || instance.submitterId }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ instance.currentNodeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ formatTime(instance.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatTime(instance.finishedAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="block" v-if="summary.header && Object.keys(summary.header).length">
          <div class="section-title">单据摘要</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item v-for="(v,k) in summary.header" :key="k" :label="k">{{ formatVal(v) }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="block" v-if="summary.items && summary.items.length">
          <div class="section-title">产品明细</div>
          <el-table :data="summary.items" size="small" border>
            <el-table-column prop="productCode" label="编码" width="120" />
            <el-table-column prop="productName" label="产品" min-width="160" />
            <el-table-column prop="batchNo" label="批号/序列号" width="140" />
            <el-table-column align="right" prop="qty" label="数量" width="80" />
            <el-table-column align="right" prop="unitPrice" label="单价" width="100" />
            <el-table-column prop="subtotal" label="小计" width="100" />
          </el-table>
        </div>
        <div class="block" v-if="isContract && contractBusiness">
          <div class="section-title">业务信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="合同编号">{{ contractBusiness.code }}</el-descriptions-item>
            <el-descriptions-item label="合同名称">{{ contractBusiness.name }}</el-descriptions-item>
            <el-descriptions-item label="甲方">{{ contractBusiness.vendorParty || '-' }}</el-descriptions-item>
            <el-descriptions-item label="乙方">{{ contractBusiness.dealerParty || '-' }}</el-descriptions-item>
            <el-descriptions-item label="有效期">{{ contractBusiness.validFrom }} ~ {{ contractBusiness.validTo }}</el-descriptions-item>
            <el-descriptions-item label="签约金额">{{ contractBusiness.signedAmount != null ? contractBusiness.signedAmount : '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="section-title" style="margin-top:12px" v-if="contractVisibleFields.length">审批可见字段</div>
          <el-descriptions v-if="contractVisibleFields.length" :column="2" border size="small">
            <el-descriptions-item v-for="f in contractVisibleFields" :key="f.key" :label="f.label">
              {{ contractBusiness.formData && contractBusiness.formData[f.key] != null ? contractBusiness.formData[f.key] : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="block" v-if="myPendingTasks.length">
          <div class="section-title">我的待办</div>
          <el-alert
            v-for="t in myPendingTasks"
            :key="t.id"
            class="task-alert"
            type="warning"
            :closable="false"
            show-icon
          >
            <template #title>
              <div class="task-bar">
                <span>{{ t.nodeName }}（{{ APPROVE_MODE_LABELS[t.approveMode] || t.approveMode }}）</span>
                <span>
                  <el-button size="small" type="primary" @click="act('approve', t)">同意</el-button>
                  <el-button size="small" type="danger" @click="act('reject', t)">驳回</el-button>
                  <el-button size="small" @click="openTransfer(t)">转办</el-button>
                  <el-button size="small" @click="openAddSign(t)">加签</el-button>
                </span>
              </div>
            </template>
          </el-alert>
        </div>

        <div class="block" v-if="isSubmitter && instance.status === 'RUNNING'">
          <el-button size="small" @click="onWithdraw">撤回申请</el-button>
        </div>

        <div class="block" v-if="isAdmin">
          <div class="section-title">管理员干预</div>
          <el-button size="small" @click="openAdminReassign">改派当前任务</el-button>
          <el-button size="small" type="danger" @click="onTerminate">终止审批</el-button>
        </div>

        <div class="block">
          <div class="section-title">审批记录</div>
          <el-timeline>
            <el-timeline-item
              v-for="(r, i) in records"
              :key="i"
              :timestamp="formatTime(r.createdAt)"
              placement="top"
              :type="recordType(r.action)"
            >
              <div class="record-title">
                <strong>{{ r.operatorName || r.operatorId || '系统' }}</strong>
                <el-tag size="small" :type="recordType(r.action)">{{ actionLabel(r.action) }}</el-tag>
                <span v-if="r.nodeName" class="muted">· {{ r.nodeName }}</span>
              </div>
              <div v-if="r.comment" class="muted">{{ r.comment }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </div>

    <el-dialog v-model="commentVisible" title="审批意见" width="420px" append-to-body>
      <el-input v-model="commentText" type="textarea" :rows="3" placeholder="请输入审批意见" />
      <template #footer>
        <el-button @click="commentVisible = false">取消</el-button>
        <el-button type="primary" @click="submitComment">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userPickVisible" :title="userPickTitle" width="420px" append-to-body>
      <el-select v-model="pickedUser" filterable remote :remote-method="queryUsers" :loading="userLoading" placeholder="搜索账号" style="width:100%;">
        <el-option v-for="u in userOptions" :key="u.id" :label="u.name + '（' + u.username + '）'" :value="u.id" />
      </el-select>
      <el-input v-if="needSignType" v-model="signType" style="display:none;" />
      <template #footer>
        <el-button @click="userPickVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUserPick">确认</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import {
  getInstance, approveTask, rejectTask, transferTask, addSignTask,
  withdrawInstance, reassignTask, terminateInstance
} from '@/api/approval'
import { useUserStore } from '@/store/user'
import {
  BUSINESS_LABELS, INSTANCE_STATUS_LABELS, INSTANCE_STATUS_TYPE, APPROVE_MODE_LABELS, formatTime
} from './dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  instanceId: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue', 'refresh'])

const userStore = useUserStore()
const loading = ref(false)
const detail = ref(null)
const instance = computed(() => detail.value ? detail.value.instance : {})
const tasks = computed(() => detail.value ? detail.value.tasks || [] : [])
const records = computed(() => detail.value ? detail.value.records || [] : [])
const isContract = computed(() => instance.value && instance.value.businessType === 'CONTRACT')
const contractBusiness = computed(() => instance.value ? instance.value.businessSnapshot || {} : {})
const contractVisibleFields = ref([])
const summary = ref({})
async function loadContractFields() {
  contractVisibleFields.value = []
  if (isContract.value && instance.value.businessId) {
    try {
      const r = await request({ url: '/api/contracts/' + instance.value.businessId, method: 'get' })
      const d = r.data || r
      if (d.template && Array.isArray(d.template.fields)) {
        contractVisibleFields.value = d.template.fields.filter((f) => f.approvalVisible).sort((a, b) => (a.sort || 0) - (b.sort || 0))
      }
    } catch (e) { /* ignore */ }
  }
}

const myPendingTasks = computed(() => {
  const uid = userStore.user && userStore.user.id
  return tasks.value.filter((t) => t.status === 'PENDING' && Number(t.assigneeId) === Number(uid))
})
const isSubmitter = computed(() => {
  const uid = userStore.user && userStore.user.id
  return instance.value && Number(instance.value.submitterId) === Number(uid)
})
const isAdmin = computed(() => {
  const u = userStore.user || {}
  const perms = new Set([...(u.permissions || []), ...(u.roles || [])])
  return perms.has('approval:admin') || perms.has('*') || u.userType === 'platform'
})

async function load() {
  if (!props.instanceId) return
  loading.value = true
  try {
    const res = await getInstance(props.instanceId)
    detail.value = res.data
    summary.value = {}
    try { const sm = await request({ url: '/api/approval/instances/' + props.instanceId + '/summary' }); summary.value = sm.data || {} } catch (e) {}
    await loadContractFields()
  } finally {
    loading.value = false
  }
}

watch(() => [props.modelValue, props.instanceId], ([v]) => { if (v) load() }, { immediate: true })

function businessLabel(t) { return BUSINESS_LABELS[t] || t || '-' }
function formatVal(v){ return [null,undefined,''].includes(v) ? '-' : String(v) }

const ACTION_LABELS = {
  START: '发起申请', SUBMIT: '提交', APPROVE: '同意', REJECT: '驳回',
  TRANSFER: '转办', ADD_SIGN: '加签', WITHDRAW: '撤回', TERMINATE: '终止',
  DELEGATE: '委托', AUTO_PASS: '自动通过', CC: '抄送', REASSIGN: '改派', SYSTEM: '系统'
}
function actionLabel(a) { return ACTION_LABELS[a] || a }
function recordType(a) {
  if (a === 'APPROVE' || a === 'AUTO_PASS') return 'success'
  if (a === 'REJECT' || a === 'TERMINATED') return 'danger'
  if (a === 'WITHDRAW') return 'info'
  if (a === 'REJECT') return 'danger'
  if (a === 'TERMINATE') return 'danger'
  return 'primary'
}

const commentVisible = ref(false)
const commentText = ref('')
let pendingFn = null
function act(type, task) {
  pendingFn = () => type === 'approve' ? approveTask(task.id, commentText.value) : rejectTask(task.id, commentText.value)
  commentText.value = ''
  commentVisible.value = true
}
async function submitComment() {
  if (!pendingFn) return
  await pendingFn()
  ElMessage.success('操作成功')
  commentVisible.value = false
  load()
  emit('refresh')
}

function onWithdraw() {
  ElMessageBox.confirm('确认撤回该申请？撤回后可回到草稿修改。', '提示', { type: 'warning' })
    .then(async () => {
      await withdrawInstance(props.instanceId, '')
      ElMessage.success('已撤回')
      load(); emit('refresh')
    }).catch(() => {})
}

const userPickVisible = ref(false)
const userPickTitle = ref('选择账号')
const pickedUser = ref(null)
const userOptions = ref([])
const userLoading = ref(false)
const needSignType = ref(false)
const signType = ref('BEFORE')
let userPickFn = null
async function queryUsers(q) {
  userLoading.value = true
  try {
    const res = await request({ url: '/api/users', method: 'get', params: { keyword: q, page: 1, size: 20 } })
    userOptions.value = (res.data && res.data.list) || []
  } finally { userLoading.value = false }
}
function openTransfer(task) {
  userPickTitle.value = '转办给'
  pickedUser.value = null; needSignType.value = false
  queryUsers('')
  userPickVisible.value = true
  userPickFn = (uid) => transferTask(task.id, uid, commentText.value)
}
function openAddSign(task) {
  userPickTitle.value = '加签账号'
  pickedUser.value = null; needSignType.value = true
  ElMessageBox.confirm('选择加签方式：确定为前加签，取消为后加签', '加签方式', {
    confirmButtonText: '前加签', cancelButtonText: '后加签', distinguishCancelAndClose: true, type: 'info'
  }).then(() => { signType.value = 'BEFORE' }).catch(() => { signType.value = 'AFTER' })
  queryUsers('')
  userPickVisible.value = true
  userPickFn = (uid) => addSignTask(task.id, uid, signType.value, commentText.value)
}
function openAdminReassign() {
  const pending = tasks.value.find((t) => t.status === 'PENDING')
  if (!pending) { ElMessage.warning('没有进行中的任务'); return }
  userPickTitle.value = '管理员改派给'
  pickedUser.value = null; needSignType.value = false
  queryUsers('')
  userPickVisible.value = true
  userPickFn = (uid) => reassignTask(pending.id, uid, '管理员改派')
}
async function submitUserPick() {
  if (!pickedUser.value) { ElMessage.warning('请选择账号'); return }
  await userPickFn(pickedUser.value)
  ElMessage.success('操作成功')
  userPickVisible.value = false
  load(); emit('refresh')
}
function onTerminate() {
  ElMessageBox.prompt('请输入终止原因', '终止审批', { confirmButtonText: '确认终止', cancelButtonText: '取消', type: 'warning', inputType: 'textarea' })
    .then(async ({ value }) => {
      await terminateInstance(props.instanceId, value || '管理员终止')
      ElMessage.success('已终止')
      load(); emit('refresh')
    }).catch(() => {})
}
</script>

<style scoped>
.block { margin-top: 16px; }
.section-title { font-weight: 600; margin-bottom: 8px; }
.task-alert { margin-bottom: 8px; }
.task-bar { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.muted { color: var(--dms-text-4); font-size: 12px; margin-top: 2px; }
.record-title { display: flex; align-items: center; gap: 8px; }
</style>