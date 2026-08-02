<template>
  <div class="positions-page">
    <el-row :gutter="16">
      <el-col :span="7" class="tree-col">
        <el-card class="tree-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Grid /></el-icon>
              <span>组织架构</span>
              <el-button type="primary" size="small" style="margin-left:auto" @click="showForm(null)">
                <el-icon><Plus /></el-icon>新增岗位
              </el-button>
            </div>
          </template>
          <div class="tree-wrapper">
            <el-tree
              :data="treeData"
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              :highlight-current="true"
              :expand-on-click-node="false"
              default-expand-all
              @node-click="onTreeClick"
            />
          </div>
        </el-card>
      </el-col>

      <el-col :span="17" class="detail-col">
        <el-card class="detail-card" shadow="hover" v-if="selected">
          <template #header>
            <div class="card-header">
              <el-icon><User /></el-icon>
              <span>岗位详情 — {{ selected.name }}</span>
              <div class="header-actions">
                <el-button size="small" type="danger" @click="doDelete(selected)">
                  <el-icon><Delete /></el-icon>删除
                </el-button>
                <el-button size="small" type="primary" @click="showForm(selected)">
                  <el-icon><Edit /></el-icon>编辑
                </el-button>
              </div>
            </div>
          </template>

          <el-descriptions :column="3" border size="small" class="info-desc">
            <el-descriptions-item label="岗位编码">{{ selected.code }}</el-descriptions-item>
            <el-descriptions-item label="岗位名称">{{ selected.name }}</el-descriptions-item>
            <el-descriptions-item label="岗位级别">L{{ selected.level || '-' }}</el-descriptions-item>
            <el-descriptions-item label="上级岗位">{{ parentName }}</el-descriptions-item>
            <el-descriptions-item label="区域">{{ selected.region || '-' }}</el-descriptions-item>
            <el-descriptions-item label="排序">{{ selected.sortOrder ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="selected.status === 'active' ? 'success' : 'danger'">
                {{ selected.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="销售人数" :span="2">
              {{ selectedUserList.length }} 人<span v-if="shareSum > 0">，业绩占比合计 {{ shareSumText }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-tabs v-model="activeTab" class="bind-tabs">
            <el-tab-pane label="销售人员" name="users">
              <div class="tab-toolbar">
                <span class="tip">同一岗位下销售业绩占比总和需 ≤ 1；一个销售只能归属一个岗位。</span>
                <el-button type="primary" size="small" @click="openBindUsers">
                  <el-icon><Plus /></el-icon>分配销售账号
                </el-button>
              </div>
              <el-table :data="selectedUserList" border size="small" v-if="selectedUserList.length">
                <el-table-column prop="username" label="登录账号" width="200" />
                <el-table-column prop="name" label="姓名" width="200" />
                <el-table-column label="业绩占比" width="160">
                  <template #default="{ row }">{{ formatRatio(row.shareRatio) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button size="small" type="danger" link @click="removeUser(row.id)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂未分配销售账号" :image-size="80" />
            </el-tab-pane>

            <el-tab-pane label="经销商" name="dealers">
              <div class="tab-toolbar">
                <span class="tip">一个经销商只能归属一个岗位；归属后该岗位及其上级可看到其数据。</span>
                <el-button type="primary" size="small" @click="openBindDealers">
                  <el-icon><Plus /></el-icon>分配经销商
                </el-button>
              </div>
              <el-table :data="selectedDealerList" border size="small" v-if="selectedDealerList.length">
                <el-table-column prop="code" label="经销商编码" width="200" />
                <el-table-column prop="name" label="经销商名称" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button size="small" type="danger" link @click="removeDealer(row.id)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂未分配经销商" :image-size="80" />
            </el-tab-pane>

            <el-tab-pane label="下级岗位" name="children" v-if="children.length">
              <el-table :data="children" border size="small">
                <el-table-column prop="code" label="编码" width="180" />
                <el-table-column prop="name" label="名称" />
                <el-table-column label="级别" width="100">
                  <template #default="{ row }">L{{ row.level }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
                      {{ row.status === 'active' ? '启用' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <el-empty v-else description="请在左侧选择岗位查看详情" class="empty-detail" />
      </el-col>
    </el-row>

    <el-drawer v-model="formVisible" :title="form.id ? '编辑岗位' : '新增岗位'" size="520px" :append-to-body="true">
      <el-form :model="form" label-width="100px" class="pos-form">
        <el-form-item label="岗位编码" required>
          <el-input v-model="form.code" placeholder="如 POS-A01" />
        </el-form-item>
        <el-form-item label="岗位名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="上级岗位">
          <el-select v-model="form.parentId" filterable clearable placeholder="无上级（顶级岗位）" style="width:100%">
            <el-option v-for="p in parentOptions" :key="p.id" :label="p.name" :value="p.id" :disabled="p.id === form.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位级别">
          <el-input-number v-model="form.level" :min="1" :max="6" controls-position="right" />
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="form.region" placeholder="如 east" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="bindUserVisible" title="分配销售账号" width="820px" :append-to-body="true">
      <el-alert type="info" :closable="false" show-icon class="bind-alert">
        勾选要分配到本岗位的销售，并填写每个销售的业绩占比（0~1）。一个销售只能属于一个岗位；已被其他岗位占用的账号会标注且不可勾选。
      </el-alert>
      <el-table ref="userTableRef" :data="userCandidates" border size="small" max-height="420" row-key="id" @selection-change="onUserSelect">
        <el-table-column type="selection" width="48" :selectable="userSelectable" />
        <el-table-column prop="username" label="登录账号" width="160" />
        <el-table-column prop="name" label="姓名" width="140" />
        <el-table-column label="当前归属岗位" width="200">
          <template #default="{ row }">
            <el-tag v-if="row.occupiedByOther" type="warning" size="small">{{ row.boundPositionName }}</el-tag>
            <el-tag v-else-if="row.boundPositionId" type="success" size="small">本岗位</el-tag>
            <span v-else style="color:#909399">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="业绩占比" width="200">
          <template #default="{ row }">
            <el-input-number v-model="row.shareRatio" :min="0" :max="1" :step="0.1" :precision="2"
              controls-position="right" size="small" style="width:100%" :disabled="userSelectable(row) === false" />
          </template>
        </el-table-column>
      </el-table>
      <div class="sum-bar">
        占比合计：<b :class="{ over: selectedShareSum > 1 }">{{ selectedShareSumText }}</b> / 1.00
        <el-tag v-if="selectedShareSum > 1" type="danger" size="small" style="margin-left:8px">超过上限</el-tag>
      </div>
      <template #footer>
        <el-button @click="bindUserVisible = false">取消</el-button>
        <el-button type="primary" :disabled="selectedShareSum > 1" @click="doBindUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindDealerVisible" title="分配经销商" width="760px" :append-to-body="true">
      <el-alert type="info" :closable="false" show-icon class="bind-alert">
        勾选要归属到本岗位的经销商。一个经销商只能归属一个岗位；已被其他岗位占用的经销商会标注且不可勾选。
      </el-alert>
      <el-table ref="dealerTableRef" :data="dealerCandidates" border size="small" max-height="460" row-key="id" @selection-change="onDealerSelect">
        <el-table-column type="selection" width="48" :selectable="dealerSelectable" />
        <el-table-column prop="code" label="经销商编码" width="200" />
        <el-table-column prop="name" label="经销商名称" />
        <el-table-column label="当前归属岗位" width="200">
          <template #default="{ row }">
            <el-tag v-if="row.occupiedByOther" type="warning" size="small">{{ row.boundPositionName }}</el-tag>
            <el-tag v-else-if="row.boundPositionId" type="success" size="small">本岗位</el-tag>
            <span v-else style="color:#909399">未分配</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="bindDealerVisible = false">取消</el-button>
        <el-button type="primary" @click="doBindDealer">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  salesPositionsTree,
  createSalesPosition,
  updateSalesPosition,
  deleteSalesPosition,
  getCandidateUsers,
  getCandidateDealers,
  bindDealers,
  bindUsers,
  getPositionUsers,
  getPositionDealers
} from '@/api/positions'

const treeData = ref([])
const selected = ref(null)
const activeTab = ref('users')
const formVisible = ref(false)
const bindUserVisible = ref(false)
const bindDealerVisible = ref(false)
const parentOptions = ref([])
const selectedUserList = ref([])
const selectedDealerList = ref([])
const userCandidates = ref([])
const dealerCandidates = ref([])
const selectedUsers = ref([])
const selectedDealers = ref([])
const userTableRef = ref(null)
const dealerTableRef = ref(null)

const form = reactive({
  id: null, code: '', name: '', parentId: null, level: 1, region: '', sortOrder: 0, status: 'active'
})

const parentName = computed(() => {
  if (!selected.value?.parentId) return '顶级岗位'
  return findNodeInTree(treeData.value, selected.value.parentId)?.name || '-'
})
const children = computed(() => selected.value?.children || [])
const shareSum = computed(() => selectedUserList.value.reduce((s, u) => s + (Number(u.shareRatio) || 0), 0))
const shareSumText = computed(() => shareSum.value.toFixed(2))
const selectedShareSum = computed(() =>
  selectedUsers.value.reduce((s, u) => s + (Number(u.shareRatio) || 0), 0))
const selectedShareSumText = computed(() => selectedShareSum.value.toFixed(2))

function findNodeInTree(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children?.length) {
      const f = findNodeInTree(node.children, id)
      if (f) return f
    }
  }
  return null
}
function flattenTree(nodes) {
  let r = []
  for (const n of nodes) {
    r.push({ id: n.id, name: n.name })
    if (n.children?.length) r = r.concat(flattenTree(n.children))
  }
  return r
}
function formatRatio(v) {
  const n = Number(v)
  return n ? n.toFixed(2) : '0.00'
}

async function loadTree() {
  treeData.value = (await salesPositionsTree()).data || []
  parentOptions.value = flattenTree(treeData.value)
}
async function onTreeClick(node) {
  selected.value = node
  await Promise.all([loadSelectedUsers(), loadSelectedDealers()])
}
async function loadSelectedUsers() {
  if (!selected.value?.id) { selectedUserList.value = []; return }
  try { selectedUserList.value = (await getPositionUsers(selected.value.id)).data || [] }
  catch { selectedUserList.value = [] }
}
async function loadSelectedDealers() {
  if (!selected.value?.id) { selectedDealerList.value = []; return }
  try { selectedDealerList.value = (await getPositionDealers(selected.value.id)).data || [] }
  catch { selectedDealerList.value = [] }
}

function showForm(row) {
  if (row) {
    Object.assign(form, {
      id: row.id, code: row.code, name: row.name, parentId: row.parentId,
      level: row.level ?? 1, region: row.region || '', sortOrder: row.sortOrder ?? 0, status: row.status || 'active'
    })
  } else {
    Object.assign(form, {
      id: null, code: '', name: '', parentId: selected.value?.id || null,
      level: selected.value ? (selected.value.level || 1) + 1 : 1,
      region: '', sortOrder: 0, status: 'active'
    })
  }
  formVisible.value = true
}
async function save() {
  if (!form.code?.trim() || !form.name?.trim()) {
    ElMessage.warning('请填写岗位编码和名称')
    return
  }
  const payload = { ...form }
  if (payload.id) await updateSalesPosition(payload.id, payload)
  else await createSalesPosition(payload)
  ElMessage.success('保存成功')
  formVisible.value = false
  await loadTree()
}
async function doDelete(row) {
  await ElMessageBox.confirm('确认删除此岗位？删除后其绑定关系也会解除。', '提示', { type: 'warning' })
  await deleteSalesPosition(row.id)
  ElMessage.success('已删除')
  selected.value = null
  await loadTree()
}

async function openBindUsers() {
  bindUserVisible.value = true
  const boundIds = new Set(selectedUserList.value.map((u) => u.id))
  const ratioMap = new Map(selectedUserList.value.map((u) => [u.id, Number(u.shareRatio) || 0]))
  const list = (await getCandidateUsers(selected.value.id)).data || []
  userCandidates.value = list.map((u) => ({
    ...u,
    shareRatio: ratioMap.get(u.id) ?? (Number(u.currentShareRatio) || 0)
  }))
  await nextTick()
  userTableRef.value?.clearSelection()
  userCandidates.value.forEach((row) => {
    if (boundIds.has(row.id) || row.boundPositionId === selected.value.id) {
      userTableRef.value?.toggleRowSelection(row, true)
    }
  })
}
function userSelectable(row) {
  return !row.occupiedByOther
}
function onUserSelect(rows) {
  selectedUsers.value = rows
}
async function doBindUser() {
  const users = userCandidates.value
    .filter((u) => selectedUsers.value.some((s) => s.id === u.id))
    .map((u) => ({ id: u.id, shareRatio: Number(u.shareRatio) || 0 }))
  await bindUsers(selected.value.id, { users })
  ElMessage.success('保存成功')
  bindUserVisible.value = false
  await loadSelectedUsers()
  await loadTree()
}
async function removeUser(userId) {
  const users = selectedUserList.value.filter((u) => u.id !== userId).map((u) => ({ id: u.id, shareRatio: Number(u.shareRatio) || 0 }))
  await bindUsers(selected.value.id, { users })
  ElMessage.success('已移除')
  await loadSelectedUsers()
}

async function openBindDealers() {
  bindDealerVisible.value = true
  const boundIds = new Set(selectedDealerList.value.map((d) => d.id))
  const list = (await getCandidateDealers(selected.value.id)).data || []
  dealerCandidates.value = list
  await nextTick()
  dealerTableRef.value?.clearSelection()
  dealerCandidates.value.forEach((row) => {
    if (boundIds.has(row.id) || row.boundPositionId === selected.value.id) {
      dealerTableRef.value?.toggleRowSelection(row, true)
    }
  })
}
function dealerSelectable(row) {
  return !row.occupiedByOther
}
function onDealerSelect(rows) {
  selectedDealers.value = rows
}
async function doBindDealer() {
  const dealerIds = selectedDealers.value.map((d) => d.id)
  await bindDealers(selected.value.id, { dealerIds })
  ElMessage.success('保存成功')
  bindDealerVisible.value = false
  await loadSelectedDealers()
  await loadTree()
}
async function removeDealer(dealerId) {
  const dealerIds = selectedDealerList.value.filter((d) => d.id !== dealerId).map((d) => d.id)
  await bindDealers(selected.value.id, { dealerIds })
  ElMessage.success('已移除')
  await loadSelectedDealers()
}

loadTree()
</script>

<style scoped>
.positions-page { padding: 16px 0; min-height: calc(100vh - 100px); }
.card-header { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 500; }
.header-actions { margin-left: auto; display: flex; gap: 8px; }
.tree-col, .detail-col { display: flex; }
.tree-card { flex: 1; border-radius: 12px; height: calc(100vh - 140px); display: flex; flex-direction: column; }
.tree-card :deep(.el-card__body) { flex: 1; padding: 16px; overflow: hidden; display: flex; flex-direction: column; }
.tree-wrapper { flex: 1; overflow-y: auto; padding-right: 4px; }
.detail-card { flex: 1; border-radius: 12px; }
.detail-card :deep(.el-card__body) { padding: 20px; }
.empty-detail { margin-top: 100px; }
.info-desc { margin-bottom: 16px; }
.bind-tabs { margin-top: 8px; }
.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.tip { color: #909399; font-size: 13px; }
.bind-alert { margin-bottom: 12px; }
.sum-bar { margin-top: 12px; font-size: 14px; color: #606266; }
.sum-bar b { color: #409eff; }
.sum-bar b.over { color: #f56c6c; }
.pos-form { padding-right: 12px; }
:deep(.el-tree) { font-size: 14px; }
:deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  background-color: #409eff20; color: #409eff; font-weight: 500; border-radius: 6px;
}
</style>
