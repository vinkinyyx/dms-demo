<template>
  <div class="roles-page">
    <div class="panel-title">角色管理</div>
    <div class="page-toolbar">
      <el-input v-model="keyword" placeholder="角色名称/编码" clearable style="width: 240px" @keyup.enter="loadRoles">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="loadRoles"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="onReset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增角色</el-button>
    </div>

    <el-table :data="filteredRoles" v-loading="loading" border stripe size="small">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column prop="code" label="角色编码" width="180" />
      <el-table-column prop="name" label="角色名称" width="180" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">{{ row.type === 'system' ? '系统角色' : '自定义角色' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.status === 'active' ? 'success' : 'danger'">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="openPermissions(row)">权限设置</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑角色' : '新增角色'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="角色编码"><el-input v-model="form.code" :disabled="!!editingId" placeholder="如 sales_manager" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="角色类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="自定义角色" value="custom" />
            <el-option label="系统角色" value="system" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permVisible" :title="`权限设置 - ${currentRole?.name || ''}`" width="860px" top="6vh">
      <div class="perm-toolbar">
        <el-input v-model="permKeyword" placeholder="搜索菜单/按钮/接口" clearable style="width: 280px" />
        <el-checkbox v-model="checkAll" :indeterminate="indeterminate" @change="toggleAll">全选可见项</el-checkbox>
      </div>
      <el-tree ref="permTreeRef" :data="permTree" show-checkbox node-key="code" :default-checked-keys="selectedPerms"
        :filter-node-method="filterPermNode" v-loading="permLoading" class="perm-tree" />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPerm" @click="savePermissions">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getRolePermissions, setRolePermissions } from '@/api/admin'

const roles = ref([])
const loading = ref(false)
const keyword = ref('')
const formVisible = ref(false)
const editingId = ref(null)
const form = ref({ code: '', name: '', type: 'custom', description: '' })

const permVisible = ref(false)
const permLoading = ref(false)
const savingPerm = ref(false)
const currentRole = ref(null)
const resources = ref([])
const selectedPerms = ref([])
const permKeyword = ref('')
const checkAll = ref(false)
const permTreeRef = ref(null)

const typeLabel = { menu: '菜单', button: '按钮', api: '接口' }
const filteredRoles = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return roles.value
  return roles.value.filter(r => [r.code, r.name].some(v => String(v || '').toLowerCase().includes(k)))
})

async function loadRoles() {
  loading.value = true
  try {
    const res = await request.get('/api/roles')
    roles.value = res.data || []
  } finally {
    loading.value = false
  }
}

function onReset() { keyword.value = ''; loadRoles() }
function openCreate() {
  editingId.value = null
  form.value = { code: '', name: '', type: 'custom', description: '' }
  formVisible.value = true
}
function openEdit(row) {
  editingId.value = row.id
  form.value = { code: row.code, name: row.name, type: row.type || 'custom', description: row.description || '' }
  formVisible.value = true
}
async function saveRole() {
  if (!form.value.code || !form.value.name) {
    ElMessage.warning('请填写角色编码和名称')
    return
  }
  if (editingId.value) {
    await request.put('/api/roles/' + editingId.value, { name: form.value.name, type: form.value.type, description: form.value.description })
  } else {
    await request.post('/api/roles', form.value)
  }
  ElMessage.success('保存成功')
  formVisible.value = false
  loadRoles()
}

const permTree = computed(() => {
  const map = new Map()
  resources.value.forEach((r) => map.set(r.code, { ...r, label: `${r.name} [${typeLabel[r.type] || r.type}]`, children: [] }))
  const roots = []
  map.forEach((node) => {
    const parentCode = parentCodeOf(node.code, node.type)
    const parent = map.get(parentCode)
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})
function parentCodeOf(code, type) {
  if (type === 'menu') return null
  const parts = code.split(':')
  if (parts.length <= 1) return null
  return parts[0] + ':view'
}
const indeterminate = computed(() => {
  const keys = visibleLeafCodes()
  const hit = keys.filter(k => selectedPerms.value.includes(k)).length
  return hit > 0 && hit < keys.length
})
function visibleLeafCodes() {
  const k = permKeyword.value.trim().toLowerCase()
  return resources.value
    .filter(r => !k || `${r.code} ${r.name} ${r.type}`.toLowerCase().includes(k))
    .filter(r => r.type !== 'menu')
    .map(r => r.code)
}
function toggleAll(val) {
  const set = new Set(selectedPerms.value)
  visibleLeafCodes().forEach((code) => val ? set.add(code) : set.delete(code))
  selectedPerms.value = [...set]
}
function filterPermNode(value, data) {
  if (!value) return true
  return `${data.code} ${data.name} ${data.type}`.toLowerCase().includes(value.toLowerCase())
}
watch(permKeyword, (v) => permTreeRef.value && permTreeRef.value.filter(v))

async function openPermissions(row) {
  currentRole.value = row
  permVisible.value = true
  permLoading.value = true
  selectedPerms.value = []
  try {
    const res = await getRolePermissions(row.id)
    resources.value = res.data.resources || []
    selectedPerms.value = res.data.selectedCodes || []
    await nextTick()
    permTreeRef.value?.setCheckedKeys(selectedPerms.value, false)
  } finally {
    permLoading.value = false
  }
}
async function savePermissions() {
  savingPerm.value = true
  try {
    const codes = permTreeRef.value.getCheckedKeys(false).concat(permTreeRef.value.getHalfCheckedKeys())
    await setRolePermissions(currentRole.value.id, codes)
    ElMessage.success('权限已保存，相关用户重新登录后生效')
    permVisible.value = false
  } finally {
    savingPerm.value = false
  }
}

onMounted(loadRoles)
</script>

<style scoped>
.roles-page { background: var(--dms-bg-container); border-radius: 4px; padding: 16px; box-shadow: 0 1px 3px rgb(0 0 0 / .1); }
.panel-title { margin-bottom: 16px; border-bottom: 1px solid var(--dms-border-1); padding-bottom: 8px; font-size: 1rem; color: var(--dms-color-primary); font-weight: 500; }
.page-toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.spacer { flex: 1; }
.perm-toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.perm-tree { max-height: 560px; overflow: auto; border: 1px solid var(--dms-border-2); border-radius: 4px; padding: 8px; }
</style>
