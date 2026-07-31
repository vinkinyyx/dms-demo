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
                <el-icon><Plus /></el-icon>新增
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
              @node-drop="handleDrop"
              draggable
              allow-drop
            />
          </div>
        </el-card>
      </el-col>

      <el-col :span="17" class="detail-col">
        <el-card class="detail-card" shadow="hover" v-if="selected">
          <template #header>
            <div class="card-header">
              <el-icon><User /></el-icon>
              <span>岗位详情 - {{ selected.name }}</span>
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
            <el-descriptions-item label="上级岗位">{{ parentName }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ selected.userName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="级别">{{ selected.level || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="selected.status === 'active' ? 'success' : 'danger'">
                {{ selected.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">
            <el-icon><Avatar /></el-icon> 销售账号列表
            <el-button size="small" type="primary" style="float:right; margin-top:-4px" @click="showBindUser">
              <el-icon><Plus /></el-icon>添加账号
            </el-button>
          </el-divider>
          <div v-if="selectedUserList.length" class="tag-list">
            <el-tag
              v-for="item in selectedUserList"
              :key="item.id"
              closable
              @close="removeUser(item.id)"
              size="large"
            >
              {{ item.name }} ({{ item.username }})
            </el-tag>
          </div>
          <el-empty v-else description="暂无绑定销售账号" />

          <el-divider content-position="left">
            <el-icon><OfficeBuilding /></el-icon> 负责经销商
            <el-button size="small" type="primary" style="float:right; margin-top:-4px" @click="showBindDealer">
              <el-icon><Plus /></el-icon>添加经销商
            </el-button>
          </el-divider>
          <div v-if="selectedDealerList.length" class="tag-list">
            <el-tag
              v-for="item in selectedDealerList"
              :key="item.id"
              closable
              @close="removeDealer(item.id)"
              size="large"
            >
              {{ item.name }}
            </el-tag>
          </div>
          <el-empty v-else description="暂无绑定经销商" />

          <el-divider content-position="left" v-if="children.length">
            <el-icon><List /></el-icon> 下级岗位
          </el-divider>
          <el-table v-if="children.length" :data="children" border size="small">
            <el-table-column prop="code" label="编码" width="140" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="userName" label="负责人" width="120" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'active' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="danger" link @click="doDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-empty v-else description="请在左侧选择岗位查看详情" class="empty-detail" />
      </el-col>
    </el-row>

    <el-dialog v-model="formVisible" title="销售岗位" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="岗位编码" required>
          <el-input v-model="form.code" placeholder="如 POS-A01" />
        </el-form-item>
        <el-form-item label="岗位名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="上级岗位">
          <el-select v-model="form.parentId" filterable placeholder="无上级（顶级岗位）" style="width:100%">
            <el-option v-for="p in parentOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-input v-model="form.level" placeholder="如 L1/L2/L3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.userId" filterable placeholder="选择用户" style="width:100%">
            <el-option v-for="u in users" :key="u.id" :label="u.name + '(' + u.username + ')'" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindUserVisible" title="绑定销售账号" width="580px">
      <el-select
        v-model="bindUserIds"
        multiple
        filterable
        placeholder="选择要绑定的销售账号"
        style="width:100%"
        :options="userOptions"
      />
      <template #footer>
        <el-button @click="bindUserVisible = false">取消</el-button>
        <el-button type="primary" @click="doBindUser">绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindDealerVisible" title="绑定经销商" width="600px">
      <el-select
        v-model="bindDealerIds"
        multiple
        filterable
        placeholder="选择要绑定的经销商"
        style="width:100%"
        :options="dealerOptions"
      />
      <template #footer>
        <el-button @click="bindDealerVisible = false">取消</el-button>
        <el-button type="primary" @click="doBindDealer">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  salesPositionsTree,
  salesPositions,
  createSalesPosition,
  updateSalesPosition,
  deleteSalesPosition,
  candidateUsers,
  bindDealers,
  bindUsers,
  getPositionUsers,
  getPositionDealers
} from '@/api/positions'

const treeData = ref([])
const selected = ref(null)
const formVisible = ref(false)
const bindUserVisible = ref(false)
const bindDealerVisible = ref(false)
const bindUserIds = ref([])
const bindDealerIds = ref([])
const users = ref([])
const userOptions = ref([])
const dealerOptions = ref([])
const parentOptions = ref([])
const selectedUserList = ref([])
const selectedDealerList = ref([])

const form = reactive({
  id: null,
  code: '',
  name: '',
  parentId: null,
  level: '',
  status: 'active',
  userId: null
})

const parentName = computed(() => {
  if (!selected.value?.parentId) return '-'
  const found = findNodeInTree(treeData.value, selected.value.parentId)
  return found?.name || '-'
})

const children = computed(() => {
  if (!selected.value) return []
  return (selected.value.children || []).map((c) => ({ ...c }))
})

function findNodeInTree(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children && node.children.length) {
      const found = findNodeInTree(node.children, id)
      if (found) return found
    }
  }
  return null
}

function flattenTree(nodes) {
  let result = []
  for (const node of nodes) {
    result.push({ id: node.id, name: node.name })
    if (node.children && node.children.length) {
      result = result.concat(flattenTree(node.children))
    }
  }
  return result
}

async function loadTree() {
  treeData.value = (await salesPositionsTree()).data || []
  const all = (await salesPositions({ page: 1, size: 1000 })).data || []
  parentOptions.value = all.map((p) => ({ id: p.id, name: p.name }))
}

async function onTreeClick(node) {
  selected.value = node
  const r = await salesPositions({ id: node.id })
  const d = r.data || []
  if (d.length) {
    const detail = d[0]
    selected.value.userName = detail.userName
    selected.value.children = detail.children || []
  }
  await loadSelectedUsers()
  await loadSelectedDealers()
}

async function loadSelectedUsers() {
  if (!selected.value?.id) {
    selectedUserList.value = []
    return
  }
  try {
    const res = await getPositionUsers(selected.value.id)
    selectedUserList.value = res.data || []
  } catch (e) {
    selectedUserList.value = []
  }
}

async function loadSelectedDealers() {
  if (!selected.value?.id) {
    selectedDealerList.value = []
    return
  }
  try {
    const res = await getPositionDealers(selected.value.id)
    selectedDealerList.value = res.data || []
  } catch (e) {
    selectedDealerList.value = []
  }
}

async function loadUsers() {
  users.value = (await candidateUsers()).data || []
}

async function loadUserOptions() {
  userOptions.value = (await candidateUsers()).data || []
}

function showForm(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      code: row.code,
      name: row.name,
      parentId: row.parentId,
      level: row.level,
      status: row.status,
      userId: row.userId
    })
  } else {
    form.id = null
    form.code = ''
    form.name = ''
    form.parentId = selected.value?.id || null
    form.level = ''
    form.status = 'active'
    form.userId = null
  }
  loadUsers()
  formVisible.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  if (form.id) {
    await updateSalesPosition(form.id, form)
  } else {
    await createSalesPosition(form)
  }
  ElMessage.success('保存成功')
  formVisible.value = false
  await loadTree()
  if (selected.value?.id) {
    onTreeClick(selected.value)
  }
}

async function doDelete(row) {
  await ElMessageBox.confirm('确认删除此岗位？删除后不可恢复', '提示', { type: 'warning' })
  await deleteSalesPosition(row.id)
  ElMessage.success('已删除')
  selected.value = null
  await loadTree()
}

function showBindUser() {
  loadUserOptions()
  bindUserIds.value = selectedUserList.value.map(u => u.id)
  bindUserVisible.value = true
}

async function doBindUser() {
  await bindUsers(selected.value.id, { userIds: bindUserIds.value })
  ElMessage.success('绑定成功')
  bindUserVisible.value = false
  await loadSelectedUsers()
}

async function removeUser(userId) {
  const newIds = selectedUserList.value.filter(u => u.id !== userId).map(u => u.id)
  await bindUsers(selected.value.id, { userIds: newIds })
  ElMessage.success('已移除')
  await loadSelectedUsers()
}

function showBindDealer() {
  bindDealerIds.value = selectedDealerList.value.map(d => d.id)
  bindDealerVisible.value = true
}

async function doBindDealer() {
  await bindDealers(selected.value.id, { dealerIds: bindDealerIds.value })
  ElMessage.success('绑定成功')
  bindDealerVisible.value = false
  await loadSelectedDealers()
}

async function removeDealer(dealerId) {
  const newIds = selectedDealerList.value.filter(d => d.id !== dealerId).map(d => d.id)
  await bindDealers(selected.value.id, { dealerIds: newIds })
  ElMessage.success('已移除')
  await loadSelectedDealers()
}

function handleDrop(draggingNode, dropNode, type) {
  console.log('node dropped', draggingNode, dropNode, type)
}

loadTree()
</script>

<style scoped>
.positions-page {
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

.tree-col {
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

.detail-col {
  display: flex;
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

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px 0;
}

.tag-list .el-tag {
  padding: 4px 12px;
  font-size: 14px;
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
