<template>
  <div class="template-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="filters.businessType" placeholder="业务类型" clearable style="width:160px;" @change="reload">
          <el-option v-for="b in BUSINESS_TYPES" :key="b.value" :label="b.label" :value="b.value" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:140px;" @change="reload">
          <el-option v-for="s in TEMPLATE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="名称/编码" clearable style="width:200px;" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="onCreate">新建审批流</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">{{ BUSINESS_LABELS[row.businessType] || row.businessType }}</template>
        </el-table-column>
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="TEMPLATE_STATUS_TYPE[row.status] || 'info'">{{ TEMPLATE_STATUS_LABELS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="驳回策略" width="180">
          <template #default="{ row }">{{ REJECT_POLICY_LABELS[row.rejectPolicy] || row.rejectPolicy }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <template v-for="(a, idx) in rowActions(row)" :key="a.key">
                <el-button v-if="idx < 2" size="small" :type="a.type" @click="a.on(row)">{{ a.label }}</el-button>
              </template>
              <el-dropdown v-if="rowActions(row).length > 2" trigger="click" @command="(cmd)=>cmd.on(row)">
                <el-button size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown><el-dropdown-menu>
                  <el-dropdown-item v-for="a in rowActions(row).slice(2)" :key="a.key" :command="a">{{ a.label }}</el-dropdown-item>
                </el-dropdown-menu></template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
    </el-card>

    <el-drawer v-model="editVisible" :title="form.id ? '编辑审批流' : '新建审批流'" size="1100px">
      <el-form :model="form" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="业务类型" required><el-select v-model="form.businessType" style="width:100%;" :disabled="!!form.id"><el-option v-for="b in BUSINESS_TYPES" :key="b.value" :label="b.label" :value="b.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="编码"><el-input v-model="form.code" placeholder="留空自动生成" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" :max="9999" controls-position="right" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="驳回策略" required><el-select v-model="form.rejectPolicy" style="width:100%;"><el-option v-for="r in REJECT_POLICIES" :key="r.value" :label="r.label" :value="r.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="超时(小时)"><el-input-number v-model="form.timeoutHours" :min="0" controls-position="right" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">生效条件</el-divider>
        <div class="cond-block">
          <el-radio-group v-model="form.conditionConfig.logic" size="small">
            <el-radio-button value="AND">全部满足(AND)</el-radio-button>
            <el-radio-button value="OR">任一满足(OR)</el-radio-button>
          </el-radio-group>
          <el-table :data="form.conditionConfig.rules" border size="small" style="margin-top:8px;">
            <el-table-column label="字段"><template #default="{ row }"><el-select v-model="row.field"><el-option v-for="f in CONDITION_FIELDS" :key="f.value" :label="f.label" :value="f.value" /></el-select></template></el-table-column>
            <el-table-column label="运算符" width="130"><template #default="{ row }"><el-select v-model="row.operator"><el-option v-for="o in CONDITION_OPERATORS" :key="o.value" :label="o.label" :value="o.value" /></el-select></template></el-table-column>
            <el-table-column label="值"><template #default="{ row }"><el-input v-model="row.value" :placeholder="isNumericField(row.field) ? '如 10000' : '输入值'" /></template></el-table-column>
            <el-table-column label="操作" width="80"><template #default="{ $index }"><el-button size="small" link type="danger" @click="form.conditionConfig.rules.splice($index, 1)">删除</el-button></template></el-table-column>
          </el-table>
          <el-button size="small" style="margin-top:8px;" @click="addRule">+ 添加条件</el-button>
          <div class="tip">留空条件表示该业务类型全部单据匹配。配置“低于某金额自动通过”：新建一个无审批节点、条件为金额区间的审批流并提高优先级。</div>
        </div>

        <el-divider content-position="left">审批节点（图形化配置）</el-divider>
        <ApprovalFlowEditor v-model="form.nodes" />
        <div class="tip">不添加审批节点即“自动审批通过”。点击画布上的节点可编辑审批人、多人审批方式、超时等。</div>

        <el-divider content-position="left">完成抄送</el-divider>
        <div v-for="(a, ai) in form.finishCcs" :key="ai" class="assignee-row"><AssigneePicker v-model="form.finishCcs[ai]" /><el-button size="small" link type="danger" @click="form.finishCcs.splice(ai, 1)">移除</el-button></div>
        <el-button size="small" @click="form.finishCcs.push({ assigneeType: 'USER', refId: null, displayName: null })">+ 添加抄送人</el-button>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" @click="onSave">保存草稿</el-button></template>
    </el-drawer>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listTemplates, getTemplate, createTemplate, updateTemplate,
  publishTemplate, disableTemplate, newTemplateVersion
} from '@/api/approval'
import { ArrowDown } from '@element-plus/icons-vue'
import AssigneePicker from './AssigneePicker.vue'
import ApprovalFlowEditor from './ApprovalFlowEditor.vue'
import {
  BUSINESS_TYPES, BUSINESS_LABELS, TEMPLATE_STATUS, TEMPLATE_STATUS_LABELS, TEMPLATE_STATUS_TYPE,
  REJECT_POLICIES, REJECT_POLICY_LABELS, CONDITION_FIELDS, CONDITION_OPERATORS, formatTime
} from './dict'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = ref({ businessType: null, status: null, keyword: null })
const editVisible = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return {
    id: null, businessType: 'SALES_ORDER', name: '', code: '', priority: 100,
    rejectPolicy: 'RETURN_TO_SUBMITTER', timeoutHours: 24, description: '',
    conditionConfig: { logic: 'AND', rules: [] },
    nodes: [], finishCcs: []
  }
}

async function reload() {
  loading.value = true
  try {
    const res = await listTemplates({ page: page.value, size: size.value, ...filters.value })
    rows.value = (res.data && res.data.list) || []
    total.value = (res.data && res.data.total) || 0
  } finally { loading.value = false }
}
function onPage(p) { page.value = p; reload() }
function isNumericField(f) { const def = CONDITION_FIELDS.find((x) => x.value === f); return !!(def && def.numeric) }
function addRule() { form.value.conditionConfig.rules.push({ field: 'finalAmount', operator: 'GTE', value: '' }) }

function onCreate() {
  form.value = emptyForm()
  form.value.businessType = filters.value.businessType || 'SALES_ORDER'
  editVisible.value = true
}
async function onEdit(row) {
  const res = await getTemplate(row.id)
  const t = res.data || {}
  form.value = {
    id: t.id, businessType: t.businessType, name: t.name, code: t.code,
    priority: t.priority, rejectPolicy: t.rejectPolicy, timeoutHours: t.timeoutHours,
    description: t.description,
    conditionConfig: t.conditionConfig && t.conditionConfig.rules ? t.conditionConfig : { logic: 'AND', rules: [] },
    nodes: (t.nodes || []).map((n) => ({
      id: n.id, name: n.name, approveMode: n.approveMode,
      allowTransfer: n.allowTransfer, allowAddSign: n.allowAddSign, timeoutHours: n.timeoutHours,
      assignees: (n.assignees || []).map((a) => ({ assigneeType: a.assigneeType, refId: a.refId, displayName: a.displayName })),
      ccs: (n.ccs || []).map((a) => ({ assigneeType: a.assigneeType, refId: a.refId, displayName: a.displayName }))
    })),
    finishCcs: (t.finishCcs || []).map((a) => ({ assigneeType: a.assigneeType, refId: a.refId, displayName: a.displayName }))
  }
  editVisible.value = true
}
function normalizeAssignees(list) {
  return (list || []).filter((a) => a && a.refId).map((a) => ({ assigneeType: a.assigneeType, refId: a.refId, displayName: a.displayName }))
}
async function onSave() {
  if (!form.value.name) { ElMessage.warning('请填写名称'); return }
  if (!form.value.businessType) { ElMessage.warning('请选择业务类型'); return }
  for (const n of form.value.nodes) {
    if (!n.name || !n.name.trim()) { ElMessage.warning('请填写审批节点名称'); return }
    if (!n.assignees || n.assignees.length === 0) { ElMessage.warning('节点「' + n.name + '」需要至少一个审批人'); return }
  }
  const payload = JSON.parse(JSON.stringify(form.value))
  payload.templateType = payload.nodes && payload.nodes.length > 0 ? 'MANUAL' : 'AUTO_APPROVE'
  if (!payload.code || !String(payload.code).trim()) {
    payload.code = 'AF_' + String(payload.businessType || 'FLOW') + '_' + Date.now().toString(36).toUpperCase()
  }
  payload.conditionConfig.rules = payload.conditionConfig.rules
    .filter((r) => r.field && r.operator && r.value !== '' && r.value !== null && r.value !== undefined)
    .map((r) => {
      if (isNumericField(r.field)) r.value = Number(r.value)
      return r
    })
  payload.nodes = payload.nodes.map((n, i) => ({
    id: n.id, nodeOrder: i + 1, name: n.name, approveMode: n.approveMode,
    allowTransfer: n.allowTransfer !== false, allowAddSign: n.allowAddSign !== false,
    timeoutHours: n.timeoutHours, assignees: normalizeAssignees(n.assignees), ccs: normalizeAssignees(n.ccs)
  }))
  payload.finishCcs = normalizeAssignees(payload.finishCcs)
  try {
    if (payload.id) await updateTemplate(payload.id, payload)
    else await createTemplate(payload)
    ElMessage.success('已保存为草稿，发布后生效')
    editVisible.value = false
    reload()
  } catch (e) { /* handled */ }
}
async function onPublish(row) {
  await ElMessageBox.confirm('发布后，新提交的单据将使用此版本审批流。确认发布？', '提示', { type: 'warning' })
  await publishTemplate(row.id)
  ElMessage.success('已发布')
  reload()
}
async function onDisable(row) {
  await ElMessageBox.confirm('停用后，新提交的单据将不再匹配此审批流。确认停用？', '提示', { type: 'warning' })
  await disableTemplate(row.id)
  ElMessage.success('已停用')
  reload()
}
async function onNewVersion(row) {
  await ElMessageBox.confirm('基于该版本创建一个可编辑的新版本草稿？', '提示', { type: 'info' })
  await newTemplateVersion(row.id)
  ElMessage.success('已创建新版本草稿')
  reload()
}
function rowActions(row){
  const a=[{key:'edit',label:'编辑',type:'',on:(r)=>onEdit(r)}]
  if(row.status==='DRAFT'||row.status==='DISABLED') a.push({key:'pub',label:'发布',type:'success',on:(r)=>onPublish(r)})
  if(row.status==='ENABLED') a.push({key:'dis',label:'停用',type:'warning',on:(r)=>onDisable(r)})
  a.push({key:'newver',label:'新版本',type:'',on:(r)=>onNewVersion(r)})
  return a
}
onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.spacer { flex: 1; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.cond-block, .node-card { background: var(--dms-gray-50); border: 1px solid var(--dms-border-2); border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.node-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.node-title { font-weight: 600; }
.assignee-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.tip { color: var(--dms-text-4); font-size: 12px; margin-top: 8px; }
</style>
