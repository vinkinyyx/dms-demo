<template>
  <el-container class="admin">
    <el-aside width="180px">
      <el-menu :default-active="active" @select="onSelect">
        <el-menu-item v-for="m in menus" :key="m.key" :index="m.key">
          <el-icon><component :is="m.icon" /></el-icon><span>{{ m.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <!-- 系统概览 -->
      <div v-if="active === 'overview'">
        <el-row :gutter="12">
          <el-col :span="6" v-for="(v, k) in stats" :key="k">
            <el-card shadow="hover" class="stat"><div class="sv">{{ v }}</div><div class="sl">{{ statLabel(k) }}</div></el-card>
          </el-col>
        </el-row>
        <el-card shadow="never" style="margin-top:14px"><template #header>系统健康</template>
          <el-tag :type="healthStatus === 'UP' ? 'success' : 'danger'">{{ healthStatus }}</el-tag>
        </el-card>
      </div>

      <!-- 通用日志表格 -->
      <el-card v-else-if="isTable" shadow="never">
        <template #header>{{ currentMenu.label }}
          <el-input v-if="active !== 'notifications'" v-model="logFilter" placeholder="筛选关键词" clearable size="small"
            style="width:200px;float:right" @keyup.enter="loadTable" />
        </template>
        <el-table :data="tableRows" v-loading="tableLoading" border stripe size="small">
          <el-table-column v-for="c in tableCols" :key="c.k" :prop="c.k" :label="c.l" :width="c.w" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="c.k === 'success'" :type="row[c.k] ? 'success' : 'danger'" size="small">{{ row[c.k] ? '成功' : '失败' }}</el-tag>
              <span v-else>{{ fmt(row[c.k], c.k) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 系统参数 -->
      <el-card v-else-if="active === 'settings'" shadow="never">
        <template #header>系统参数</template>
        <el-table :data="settingRows" border stripe size="small">
          <el-table-column prop="key" label="参数键" width="240" />
          <el-table-column prop="value" label="参数值" />
          <el-table-column prop="description" label="说明" />
        </el-table>
      </el-card>

      <!-- 数据字典 -->
      <el-card v-else-if="active === 'dicts'" shadow="never">
        <template #header>数据字典
          <el-button size="small" type="primary" style="float:right" @click="showDictTypeForm()">新增字典类型</el-button>
        </template>
        <el-row :gutter="12">
          <el-col :span="5">
            <el-card shadow="never" style="min-height:400px">
              <template #header>字典类型</template>
              <el-tree :data="dictTypeTree" node-key="code" :props="{ label: 'name', children: 'items' }"
                :highlight-current="true" @node-click="onDictTypeClick" />
            </el-card>
          </el-col>
          <el-col :span="19">
            <el-card shadow="never">
              <template #header>{{ selectedDictType?.name || '请选择字典类型' }}
                <el-button v-if="selectedDictType" size="small" type="primary" style="float:right" @click="showDictItemForm()">新增字典项</el-button>
              </template>
              <el-table :data="dictItemRows" border stripe size="small">
                <el-table-column prop="itemCode" label="编码" width="140" />
                <el-table-column prop="label" label="标签" width="160" />
                <el-table-column prop="value" label="值" width="160" />
                <el-table-column prop="sortOrder" label="排序" width="80" />
                <el-table-column prop="status" label="状态" width="80">
                  <template #default="{ row }"><el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button size="small" @click="showDictItemForm(row)">编辑</el-button>
                    <el-button size="small" type="danger" @click="deleteDictItem(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </el-card>

      <!-- 租户 -->
      <el-card v-else-if="active === 'tenants'" shadow="never">
        <template #header>租户管理
          <el-button size="small" type="primary" style="float:right" @click="showTenantForm()">新增租户</el-button>
        </template>
        <el-table :data="tenantRows" border stripe size="small">
          <el-table-column prop="code" label="租户代码" width="140" />
          <el-table-column prop="name" label="租户名称" />
          <el-table-column prop="industry" label="行业" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="contactPhone" label="联系电话" width="130" />
          <el-table-column label="创建时间" width="160"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="showTenantForm(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteTenant(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- RBAC 矩阵 -->
      <el-card v-else-if="active === 'rbac'" shadow="never">
        <template #header>RBAC 权限矩阵</template>
        <el-table :data="rbacRows" border size="small">
          <el-table-column prop="permission" label="权限" width="240" fixed />
          <el-table-column v-for="r in rbacRoles" :key="r" :label="r" width="120" align="center">
            <template #default="{ row }"><el-icon v-if="row[r]" color="var(--dms-color-success)"><Select /></el-icon><span v-else style="color:var(--dms-border-2)">—</span></template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 运维 -->
      <el-card v-else-if="active === 'ops'" shadow="never">
        <template #header>系统运维</template>
        <el-space direction="vertical" alignment="flex-start" :size="16">
          <div>缓存状态：<el-tag>{{ cacheInfo }}</el-tag>
            <el-button size="small" type="warning" style="margin-left:12px" @click="doFlush">清空缓存</el-button>
          </div>
          <div>审批超时检查：<el-button size="small" type="primary" @click="doCheckTimeouts">立即检查</el-button>
            <span v-if="timeoutResult" style="margin-left:12px">{{ timeoutResult }}</span>
          </div>
        </el-space>
      </el-card>

      <!-- 全链路操作日志下载 -->
      <el-card v-else-if="active === 'op-logs'" shadow="never">
        <template #header>全链路操作日志</template>
        <el-space direction="vertical" alignment="flex-start" :size="16">
          <div>选择日期：
            <el-date-picker v-model="opLogDate" type="date" value-format="YYYY-MM-DD" :placeholder="opLogDefaultDate" style="width:180px" />
            <el-button size="small" type="primary" style="margin-left:12px" :loading="opLogDownloading" @click="doDownloadOpLog">下载日志</el-button>
            <span style="color:var(--dms-text-4);margin-left:8px;font-size:12px">仅保留最近 7 天</span>
          </div>
          <div v-if="opLogTip" style="color:var(--dms-color-success)">{{ opLogTip }}</div>
        </el-space>
      </el-card>

      <!-- 字典类型表单弹窗 -->
      <el-dialog v-model="dictTypeFormVisible" title="字典类型" width="500px">
        <el-form :model="dictTypeForm" label-width="100px">
          <el-form-item label="类型编码" required><el-input v-model="dictTypeForm.code" placeholder="如 order_status" /></el-form-item>
          <el-form-item label="类型名称" required><el-input v-model="dictTypeForm.name" /></el-form-item>
          <el-form-item label="描述"><el-input v-model="dictTypeForm.description" type="textarea" :rows="2" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dictTypeFormVisible = false">取消</el-button>
          <el-button type="primary" @click="saveDictType">保存</el-button>
        </template>
      </el-dialog>

      <!-- 字典项表单弹窗 -->
      <el-dialog v-model="dictItemFormVisible" title="字典项" width="500px">
        <el-form :model="dictItemForm" label-width="100px">
          <el-form-item label="编码" required><el-input v-model="dictItemForm.itemCode" /></el-form-item>
          <el-form-item label="标签" required><el-input v-model="dictItemForm.label" /></el-form-item>
          <el-form-item label="值" required><el-input v-model="dictItemForm.value" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="dictItemForm.sortOrder" :controls="false" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="dictItemForm.status"><el-option label="启用" value="active" /><el-option label="停用" value="inactive" /></el-select></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dictItemFormVisible = false">取消</el-button>
          <el-button type="primary" @click="saveDictItem">保存</el-button>
        </template>
      </el-dialog>

      <!-- 租户表单弹窗 -->
      <el-dialog v-model="tenantFormVisible" title="租户" width="500px">
        <el-form :model="tenantForm" label-width="100px">
          <el-form-item label="租户代码" required><el-input v-model="tenantForm.code" placeholder="如 DEALER_A" /></el-form-item>
          <el-form-item label="租户名称" required><el-input v-model="tenantForm.name" /></el-form-item>
          <el-form-item label="行业"><el-input v-model="tenantForm.industry" /></el-form-item>
          <el-form-item label="联系人"><el-input v-model="tenantForm.contactName" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="tenantForm.contactPhone" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="tenantForm.status"><el-option label="启用" value="active" /><el-option label="停用" value="inactive" /></el-select></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="tenantFormVisible = false">取消</el-button>
          <el-button type="primary" @click="saveTenant">保存</el-button>
        </template>
      </el-dialog>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fmt, statusText, statusTagType } from '@/utils/dict'
import * as api from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const menus = [
  { key: 'overview', icon: 'DataBoard', label: '系统概览' },
  { key: 'audit-logs', icon: 'Document', label: '审计日志' },
  { key: 'login-logs', icon: 'Key', label: '登录日志' },
  { key: 'notifications', icon: 'Bell', label: '通知消息' },
  { key: 'settings', icon: 'Setting', label: '系统参数' },
  { key: 'dicts', icon: 'Collection', label: '数据字典' },
  { key: 'tenants', icon: 'OfficeBuilding', label: '租户列表' },
  { key: 'rbac', icon: 'Lock', label: 'RBAC矩阵' },
  { key: 'ops', icon: 'Tools', label: '系统运维' },
  { key: 'op-logs', icon: 'Tickets', label: '操作日志下载' }
]

const active = ref('overview')
const currentMenu = computed(() => menus.find((m) => m.key === active.value))
const isTable = computed(() => ['audit-logs', 'login-logs', 'notifications'].includes(active.value))

const stats = ref({})
const healthStatus = ref('-')
const tableRows = ref([]); const tableLoading = ref(false); const logFilter = ref('')
const settingRows = ref([]); const dictGroups = ref({}); const tenantRows = ref([])
const rbacRows = ref([]); const rbacRoles = ref([])
const cacheInfo = ref('-'); const timeoutResult = ref('')

const dictTypeTree = ref([])
const selectedDictType = ref(null)
const dictItemRows = ref([])
const dictTypeFormVisible = ref(false)
const dictTypeForm = reactive({ code: '', name: '', description: '' })
const dictItemFormVisible = ref(false)
const dictItemForm = reactive({ id: null, itemCode: '', label: '', value: '', sortOrder: 1, status: 'active' })
const tenantFormVisible = ref(false)
const tenantForm = reactive({ id: null, code: '', name: '', industry: '', contactName: '', contactPhone: '', status: 'active' })

const TABLE_COLS = {
  'audit-logs': [{ k: 'atTime', l: '时间', w: 160 }, { k: 'username', l: '操作人', w: 120 }, { k: 'action', l: '动作', w: 120 }, { k: 'entityType', l: '实体', w: 120 }, { k: 'entityId', l: '实体ID', w: 100 }, { k: 'ipAddress', l: 'IP', w: 130 }],
  'login-logs': [{ k: 'atTime', l: '时间', w: 160 }, { k: 'username', l: '账号', w: 140 }, { k: 'loginType', l: '方式', w: 100 }, { k: 'success', l: '结果', w: 90 }, { k: 'ipAddress', l: 'IP', w: 140 }, { k: 'failReason', l: '失败原因' }],
  notifications: [{ k: 'atTime', l: '时间', w: 160 }, { k: 'title', l: '标题', w: 200 }, { k: 'content', l: '内容' }, { k: 'channel', l: '通道', w: 100 }, { k: 'isRead', l: '已读', w: 80 }]
}
const tableCols = computed(() => TABLE_COLS[active.value] || [])

const STAT_LABELS = { orders: '订单数', inventoryRecords: '库存记录', products: '产品数', promotions: '促销数', dealers: '经销商', users: '用户数', contracts: '合同数' }
function statLabel(k) { return STAT_LABELS[k] || k }

async function onSelect(key) {
  active.value = key
  if (key === 'overview') loadOverview()
  else if (isTable.value) loadTable()
  else if (key === 'settings') api.settings().then((r) => { settingRows.value = r.data || [] })
  else if (key === 'dicts') loadDictTypes()
  else if (key === 'tenants') api.tenantsBrief().then((r) => { tenantRows.value = r.data || [] })
  else if (key === 'rbac') loadRbac()
  else if (key === 'ops') loadOps()
}

async function loadOverview() {
  try { stats.value = (await api.systemStats()).data || {} } catch (e) { /* ignore */ }
  try { healthStatus.value = (await api.health()).status || (await api.health()).data?.status || 'UP' } catch (e) { healthStatus.value = 'DOWN' }
}
async function loadTable() {
  tableLoading.value = true
  try {
    const params = { page: 1, size: 50 }
    if (logFilter.value) { params.username = logFilter.value; params.action = logFilter.value }
    let r
    if (active.value === 'audit-logs') r = await api.auditLogs(params)
    else if (active.value === 'login-logs') r = await api.loginLogs(params)
    else r = await api.notifications(params)
    const d = r.data
    tableRows.value = Array.isArray(d) ? d : (d.list || d.records || [])
  } finally { tableLoading.value = false }
}
function groupDicts(list) {
  const g = {}
  list.forEach((it) => { const t = it.typeName || it.typeCode || '其它'; (g[t] = g[t] || []).push(it) })
  return g
}
async function loadRbac() {
  const d = (await api.rbacMatrix()).data || {}
  rbacRoles.value = (d.roles || []).map((r) => r.name || r.code)
  const perms = {}
  ;(d.rolePermissions || []).forEach((rp) => {
    const p = rp.permission || rp.permissionCode
    perms[p] = perms[p] || { permission: p }
    perms[p][rp.roleName || rp.role] = true
  })
  rbacRows.value = Object.values(perms)
}
async function loadOps() {
  try { const c = (await api.cacheStatus()).data || {}; cacheInfo.value = JSON.stringify(c).slice(0, 120) } catch (e) { cacheInfo.value = '不可用' }
}
async function doFlush() { await api.cacheFlush(); ElMessage.success('缓存已清空'); loadOps() }
async function doCheckTimeouts() { const r = await api.checkTimeouts(); timeoutResult.value = JSON.stringify(r.data || {}).slice(0, 150); ElMessage.success('检查完成') }

const opLogDate = ref('')
const opLogDefaultDate = new Date().toISOString().slice(0, 10)
const opLogDownloading = ref(false)
const opLogTip = ref('')
async function doDownloadOpLog() {
  const date = opLogDate.value || opLogDefaultDate
  opLogDownloading.value = true
  opLogTip.value = ''
  try {
    const res = await api.opLogsDownload(date)
    const blob = res && res.data ? res.data : res
    if (!blob) {
      ElMessage.warning('下载失败：响应为空')
      return
    }
    if (blob.type && blob.type.indexOf('application/json') >= 0) {
      const txt = await blob.text()
      try {
        const j = JSON.parse(txt)
        ElMessage.error(j.message || '下载失败')
      } catch (e) {
        ElMessage.error('下载失败：' + txt.slice(0, 200))
      }
      return
    }
    if (blob.size === 0) {
      ElMessage.warning('当日没有日志记录')
      return
    }
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'op-log-' + date + '.txt'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    opLogTip.value = '下载完成：op-log-' + date + '.txt'
  } catch (e) {
    ElMessage.error('下载失败：' + (e && e.message ? e.message : '未知错误'))
  } finally {
    opLogDownloading.value = false
  }
}

async function loadDictTypes() {
  const types = (await api.dictTypes()).data || []
  dictTypeTree.value = types.map((t) => ({ code: t.code, name: t.name, description: t.description }))
  selectedDictType.value = null
  dictItemRows.value = []
}
async function onDictTypeClick(node) {
  selectedDictType.value = node
  dictItemRows.value = (await api.dictItems(node.code)).data || []
}
function showDictTypeForm() {
  dictTypeForm.code = ''; dictTypeForm.name = ''; dictTypeForm.description = ''
  dictTypeFormVisible.value = true
}
async function saveDictType() {
  await api.createDictType(dictTypeForm)
  ElMessage.success('字典类型已保存')
  dictTypeFormVisible.value = false
  loadDictTypes()
}
function showDictItemForm(row) {
  if (row) { Object.assign(dictItemForm, { id: row.id, itemCode: row.itemCode, label: row.label, value: row.value, sortOrder: row.sortOrder, status: row.status }) }
  else { dictItemForm.id = null; dictItemForm.itemCode = ''; dictItemForm.label = ''; dictItemForm.value = ''; dictItemForm.sortOrder = 1; dictItemForm.status = 'active' }
  dictItemFormVisible.value = true
}
async function saveDictItem() {
  if (dictItemForm.id) await api.updateDictItem(dictItemForm.id, dictItemForm)
  else await api.createDictItem(selectedDictType.value.code, dictItemForm)
  ElMessage.success('字典项已保存')
  dictItemFormVisible.value = false
  if (selectedDictType.value) await onDictTypeClick(selectedDictType.value)
}
async function deleteDictItem(row) {
  await api.deleteDictItem(row.id)
  ElMessage.success('已删除')
  if (selectedDictType.value) await onDictTypeClick(selectedDictType.value)
}

async function loadTenants() {
  tenantRows.value = (await api.tenants()).data || []
}
function showTenantForm(row) {
  if (row) { Object.assign(tenantForm, { id: row.id, code: row.code, name: row.name, industry: row.industry, contactName: row.contactName, contactPhone: row.contactPhone, status: row.status }) }
  else { tenantForm.id = null; tenantForm.code = ''; tenantForm.name = ''; tenantForm.industry = ''; tenantForm.contactName = ''; tenantForm.contactPhone = ''; tenantForm.status = 'active' }
  tenantFormVisible.value = true
}
async function saveTenant() {
  if (tenantForm.id) await api.updateTenant(tenantForm.id, tenantForm)
  else await api.createTenant(tenantForm)
  ElMessage.success('租户已保存')
  tenantFormVisible.value = false
  loadTenants()
}
async function deleteTenant(row) {
  await api.deleteTenant(row.id)
  ElMessage.success('已删除')
  loadTenants()
}

loadOverview()
</script>

<style scoped>
.admin { height: calc(100vh - 92px); }
.admin .el-aside { background: var(--dms-bg-container); border-radius: 6px; }
.stat { text-align: center; margin-bottom: 12px; }
.sv { font-size: 24px; font-weight: 700; color: var(--dms-color-primary); }
.sl { font-size: 13px; color: var(--dms-text-4); margin-top: 6px; }
</style>
