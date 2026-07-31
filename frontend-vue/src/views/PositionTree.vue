<template>
  <div class="position-tree-page">
    <el-row :gutter="16">
      <el-col :span="7" class="tree-col">
        <el-card class="tree-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Grid /></el-icon>
              <span>组织架构</span>
              <el-button type="primary" size="small" style="margin-left:auto" @click="openForm(null)">
                <el-icon><Plus /></el-icon>新增岗位
              </el-button>
            </div>
          </template>
          <div class="tree-wrapper">
            <el-tree
              ref="treeRef"
              :data="treeData"
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              :highlight-current="true"
              :expand-on-click-node="false"
              default-expand-all
              @node-click="onTreeClick"
            >
              <template #default="{ node, data }">
                <span class="tree-node">
                  <span class="tree-node-name">
                    <el-tag v-if="data.level" size="small" :type="levelTagType(data.level)" effect="plain" class="level-tag">
                      L{{ data.level }}
                    </el-tag>
                    <span>{{ data.name }}</span>
                  </span>
                  <span class="tree-node-meta" v-if="data.userCount || data.bindDealerCount">
                    <el-badge v-if="data.userCount" :value="data.userCount" class="meta-badge" type="primary">人</el-badge>
                    <el-badge v-if="data.bindDealerCount" :value="data.bindDealerCount" class="meta-badge" type="success">商</el-badge>
                  </span>
                </span>
              </template>
            </el-tree>
            <el-empty v-if="!treeData.length" description="暂无岗位" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="17" class="detail-col">
        <el-card v-if="selected" class="detail-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><OfficeBuilding /></el-icon>
              <span>岗位详情 — {{ selected.name }}</span>
              <div class="header-actions">
                <el-button size="small" type="primary" @click="openForm(selected)">
                  <el-icon><Edit /></el-icon>编辑
                </el-button>
                <el-button size="small" type="danger" @click="doDelete(selected)">
                  <el-icon><Delete /></el-icon>删除
                </el-button>
              </div>
            </div>
          </template>

          <el-descriptions :column="3" border size="small" class="info-desc">
            <el-descriptions-item label="岗位编码">{{ selected.code }}</el-descriptions-item>
            <el-descriptions-item label="岗位名称">{{ selected.name }}</el-descriptions-item>
            <el-descriptions-item label="岗位级别">L{{ selected.level }}</el-descriptions-item>
            <el-descriptions-item label="上级岗位">{{ parentName }}</el-descriptions-item>
            <el-descriptions-item label="区域">{{ selected.region || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="selected.status === 'active' ? 'success' : 'danger'" size="small">
                {{ selected.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="销售人员">{{ selected.userCount || 0 }} 人</el-descriptions-item>
            <el-descriptions-item label="经销商账号">{{ selected.bindDealerCount || 0 }} 个</el-descriptions-item>
            <el-descriptions-item label="排序">{{ selected.sortOrder || 0 }}</el-descriptions-item>
          </el-descriptions>

          <el-tabs v-model="activeTab" class="detail-tabs">
            <el-tab-pane label="销售人员" name="sales">
              <div class="tab-actions">
                <el-button type="primary" size="small" @click="openBindUser('sales')">
                  <el-icon><Plus /></el-icon>分配销售账号
                </el-button>
                <el-button size="small" @click="loadSalesUsers" :loading="loadingSalesUsers">
                  <el-icon><Refresh /></el-icon>刷新
                </el-button>
              </div>
              <el-table v-if="salesUsers.length" :data="salesUsers" border size="small" style="margin-top: 8px;">
                <el-table-column prop="username" label="账号" width="160" />
                <el-table-column prop="name" label="姓名" width="140" />
                <el-table-column prop="userType" label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" type="primary">销售</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button size="small" type="danger" link @click="removeUser(row)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂未分配销售账号" />
            </el-tab-pane>

            <el-tab-pane label="经销商账号" name="dealer">
              <div class="tab-actions">
                <el-button type="success" size="small" @click="openBindUser('dealer')">
                  <el-icon><Plus /></el-icon>分配经销商账号
                </el-button>
                <el-button size="small" @click="loadDealerAccounts" :loading="loadingDealerAccounts">
                  <el-icon><Refresh /></el-icon>刷新
                </el-button>
              </div>
              <el-table v-if="dealerAccounts.length" :data="dealerAccounts" border size="small" style="margin-top: 8px;">
                <el-table-column prop="username" label="账号" width="160" />
                <el-table-column prop="name" label="姓名" width="140" />
                <el-table-column prop="dealerName" label="所属经销商" />
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button size="small" type="danger" link @click="removeDealerAccount(row)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂未分配经销商账号" />
            </el-tab-pane>

            <el-tab-pane label="下级岗位" name="children" v-if="(selected.children || []).length">
              <el-table :data="selected.children" border size="small">
                <el-table-column prop="code" label="编码" width="140" />
                <el-table-column prop="name" label="名称" />
                <el-table-column label="级别" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">L{{ row.level }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="region" label="区域" width="120" />
                <el-table-column label="销售人员" width="100" prop="userCount" />
                <el-table-column label="经销商" width="100" prop="bindDealerCount" />
                <el-table-column label="操作" width="160">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" link @click="openForm(row)">编辑</el-button>
                    <el-button size="small" type="danger" link @click="doDelete(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <el-empty v-else description="请在左侧选择岗位查看详情" class="empty-detail" />
      </el-col>
    </el-row>

    <!-- 表单弹窗：新增/编辑岗位 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑岗位' : '新增岗位'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="岗位编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 POS-A01" />
        </el-form-item>
        <el-form-item label="岗位名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="上级岗位">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="无（顶级岗位）"
            style="width:100%"
            node-key="id"
            :render-after-expand="false"
          />
        </el-form-item>
        <el-form-item label="岗位级别" required>
          <el-input-number v-model="form.level" :min="1" :max="6" style="width:100%" />
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="form.region" placeholder="如 east/south/all" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :step="10" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配账号弹窗：左右双选 -->
    <el-dialog v-model="bindVisible" :title="bindRole === 'sales' ? '分配销售账号' : '分配经销商账号'" width="760px">
      <div class="bind-transfer">
        <div class="bind-panel">
          <div class="bind-panel-header">
            <span>待分配（{{ candidates.length }}）</span>
            <el-input v-model="candidateKeyword" size="small" placeholder="搜索" clearable style="width:140px" />
          </div>
          <el-table
            ref="candidateTableRef"
            :data="filteredCandidates"
            border
            size="small"
            height="380"
            @selection-change="onCandidateSelectionChange"
          >
            <el-table-column type="selection" width="44" />
            <el-table-column prop="username" label="账号" width="120" />
            <el-table-column prop="name" label="姓名" width="120" />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.userType === 'sales' ? 'primary' : 'success'">
                  {{ row.userType === 'sales' ? '销售' : '经销商' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前岗位">
              <template #default="{ row }">
                <span v-if="row.boundPositionName">{{ row.boundPositionName }}</span>
                <span v-else class="muted">未分配</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="bind-panel">
          <div class="bind-panel-header">
            <span>已选（{{ selectedCandidateIds.length }}）</span>
          </div>
          <el-table :data="selectedCandidateList" border size="small" height="380">
            <el-table-column prop="username" label="账号" width="120" />
            <el-table-column prop="name" label="姓名" width="120" />
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button size="small" type="danger" link @click="removeSelected(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="confirmBind">确认分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Grid,
  Plus,
  Edit,
  Delete,
  OfficeBuilding,
  Refresh
} from '@element-plus/icons-vue'
import {
  salesPositionsTree,
  createSalesPosition,
  updateSalesPosition,
  deleteSalesPosition,
  candidateUsers,
  bindUsers,
  getPositionUsers,
  getPositionDealerAccounts
} from '@/api/positions'

const treeRef = ref(null)
const treeData = ref([])
const selected = ref(null)
const formVisible = ref(false)
const saving = ref(false)
const activeTab = ref('sales')

const form = reactive({
  id: null,
  code: '',
  name: '',
  parentId: null,
  level: 1,
  region: '',
  sortOrder: 0,
  status: 'active'
})

const salesUsers = ref([])
const dealerAccounts = ref([])
const loadingSalesUsers = ref(false)
const loadingDealerAccounts = ref(false)

const bindVisible = ref(false)
const binding = ref(false)
const bindRole = ref('sales') // 'sales' | 'dealer'
const candidates = ref([])
const candidateKeyword = ref('')
const selectedCandidateIds = ref([])
const candidateTableRef = ref(null)

const parentTreeOptions = computed(() => {
  return filterChildren(treeData.value)
})

function filterChildren(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes.map((n) => ({
    id: n.id,
    name: n.name,
    level: n.level,
    children: filterChildren(n.children || [])
  }))
}

function levelTagType(level) {
  const lv = Number(level)
  if (lv <= 1) return 'danger'
  if (lv <= 2) return 'warning'
  if (lv <= 3) return 'primary'
  if (lv <= 4) return 'success'
  return 'info'
}

const parentName = computed(() => {
  if (!selected.value?.parentId) return '— 顶级岗位 —'
  const found = findNodeInTree(treeData.value, selected.value.parentId)
  return found?.name || '-'
})

function findNodeInTree(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children && n.children.length) {
      const f = findNodeInTree(n.children, id)
      if (f) return f
    }
  }
  return null
}

const filteredCandidates = computed(() => {
  const kw = (candidateKeyword.value || '').trim().toLowerCase()
  if (!kw) return candidates.value
  return candidates.value.filter((u) =>
    (u.username || '').toLowerCase().includes(kw) ||
    (u.name || '').toLowerCase().includes(kw) ||
    (u.boundPositionName || '').toLowerCase().includes(kw)
  )
})

const selectedCandidateList = computed(() => {
  return candidates.value.filter((u) => selectedCandidateIds.value.includes(u.id))
})

async function loadTree() {
  const res = await salesPositionsTree()
  treeData.value = res.data || []
  if (selected.value) {
    const updated = findNodeInTree(treeData.value, selected.value.id)
    if (updated) {
      selected.value = { ...selected.value, ...updated }
    } else {
      selected.value = null
    }
  }
}

function onTreeClick(node) {
  selected.value = { ...node, children: node.children || [] }
  activeTab.value = 'sales'
  loadSalesUsers()
  loadDealerAccounts()
}

async function loadSalesUsers() {
  if (!selected.value?.id) {
    salesUsers.value = []
    return
  }
  loadingSalesUsers.value = true
  try {
    const res = await getPositionUsers(selected.value.id, 'sales')
    salesUsers.value = res.data || []
  } catch (e) {
    salesUsers.value = []
  } finally {
    loadingSalesUsers.value = false
  }
}

async function loadDealerAccounts() {
  if (!selected.value?.id) {
    dealerAccounts.value = []
    return
  }
  loadingDealerAccounts.value = true
  try {
    const res = await getPositionDealerAccounts(selected.value.id)
    dealerAccounts.value = res.data || []
  } catch (e) {
    dealerAccounts.value = []
  } finally {
    loadingDealerAccounts.value = false
  }
}

function openForm(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      code: row.code,
      name: row.name,
      parentId: row.parentId || null,
      level: row.level || 1,
      region: row.region || '',
      sortOrder: row.sortOrder || 0,
      status: row.status || 'active'
    })
  } else {
    Object.assign(form, {
      id: null,
      code: '',
      name: '',
      parentId: selected.value?.id || null,
      level: selected.value ? (selected.value.level || 1) + 1 : 1,
      region: selected.value?.region || '',
      sortOrder: 0,
      status: 'active'
    })
  }
  formVisible.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  if (!form.level || form.level < 1 || form.level > 6) {
    ElMessage.warning('级别必须在 1-6 之间')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateSalesPosition(form.id, {
        name: form.name,
        level: form.level,
        region: form.region,
        status: form.status
      })
      ElMessage.success('已更新')
    } else {
      await createSalesPosition({
        code: form.code,
        name: form.name,
        level: form.level,
        parentId: form.parentId || null,
        region: form.region
      })
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await loadTree()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function doDelete(row) {
  await ElMessageBox.confirm(`确认删除岗位「${row.name}」？此操作不可恢复`, '提示', { type: 'warning' })
  try {
    await deleteSalesPosition(row.id)
    ElMessage.success('已删除')
    if (selected.value?.id === row.id) selected.value = null
    await loadTree()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

async function openBindUser(role) {
  if (!selected.value?.id) {
    ElMessage.warning('请先选择岗位')
    return
  }
  bindRole.value = role
  selectedCandidateIds.value = []
  candidateKeyword.value = ''
  try {
    const res = await candidateUsers(role)
    candidates.value = res.data || []
    bindVisible.value = true
    await nextTick()
    // 默认勾选当前已绑定的同角色用户
    const currentIds = role === 'sales'
      ? salesUsers.value.map((u) => u.id)
      : dealerAccounts.value.map((u) => u.id)
    if (candidateTableRef.value && currentIds.length) {
      const rows = candidates.value.filter((u) => currentIds.includes(u.id))
      rows.forEach((r) => {
        candidateTableRef.value.toggleRowSelection(r, true)
      })
    }
  } catch (e) {
    ElMessage.error(e.message || '加载候选账号失败')
  }
}

function onCandidateSelectionChange(rows) {
  selectedCandidateIds.value = rows.map((r) => r.id)
}

function removeSelected(row) {
  if (candidateTableRef.value) {
    candidateTableRef.value.toggleRowSelection(row, false)
  }
  selectedCandidateIds.value = selectedCandidateIds.value.filter((id) => id !== row.id)
}

async function confirmBind() {
  if (!selected.value?.id) return
  binding.value = true
  try {
    await bindUsers(selected.value.id, { userIds: selectedCandidateIds.value })
    ElMessage.success(`已分配 ${selectedCandidateIds.value.length} 个账号`)
    bindVisible.value = false
    if (bindRole.value === 'sales') {
      await loadSalesUsers()
    } else {
      await loadDealerAccounts()
    }
    await loadTree()
  } catch (e) {
    ElMessage.error(e.message || '分配失败')
  } finally {
    binding.value = false
  }
}

async function removeUser(row) {
  if (!selected.value?.id) return
  try {
    const next = salesUsers.value.filter((u) => u.id !== row.id).map((u) => u.id)
    await bindUsers(selected.value.id, { userIds: next })
    ElMessage.success('已移除')
    await loadSalesUsers()
    await loadTree()
  } catch (e) {
    ElMessage.error(e.message || '移除失败')
  }
}

async function removeDealerAccount(row) {
  if (!selected.value?.id) return
  try {
    const next = dealerAccounts.value.filter((u) => u.id !== row.id).map((u) => u.id)
    await bindUsers(selected.value.id, { userIds: next })
    ElMessage.success('已移除')
    await loadDealerAccounts()
    await loadTree()
  } catch (e) {
    ElMessage.error(e.message || '移除失败')
  }
}

onMounted(() => {
  loadTree()
})
</script>

<style scoped>
.position-tree-page {
  padding: 16px 0;
  min-height: calc(100vh - 100px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.header-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.tree-col,
.detail-col {
  display: flex;
}

.tree-card {
  flex: 1;
  border-radius: 12px;
  height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
}

.tree-card :deep(.el-card__body) {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tree-wrapper {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
  font-size: 14px;
}

.tree-node-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.level-tag {
  font-size: 10px;
  height: 18px;
  padding: 0 4px;
}

.tree-node-meta {
  display: flex;
  gap: 4px;
}

.meta-badge :deep(.el-badge__content) {
  font-size: 10px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
}

.detail-card {
  flex: 1;
  border-radius: 12px;
}

.detail-card :deep(.el-card__body) {
  padding: 20px;
}

.empty-detail {
  margin-top: 100px;
}

.info-desc {
  margin-bottom: 16px;
}

.detail-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}

.tab-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.bind-transfer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.bind-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bind-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.muted {
  color: #909399;
  font-size: 12px;
}

:deep(.el-tree) {
  font-size: 14px;
}

:deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  background-color: #409eff20;
  color: #409eff;
  font-weight: 500;
  border-radius: 6px;
}
</style>
