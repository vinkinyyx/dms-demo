<template>
  <div class="page">
    <div class="toolbar">
      <el-radio-group v-model="tenantType" @change="onTypeChange">
        <el-radio-button label="MANUFACTURER">厂家</el-radio-button>
        <el-radio-button label="DEALER">经销商</el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" placeholder="编码/名称" clearable style="width:220px" @keyup.enter="onSearch" />
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button type="primary" @click="openCreate">新建模板</el-button>
    </div>
    <el-table :data="filteredTemplates" v-loading="loading" border stripe size="small">
      <el-table-column prop="code" label="编码" width="220" />
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column label="租户类型" width="120">
        <template #default="{ row }">{{ tenantTypeMap[row.tenantType] || row.tenantType }}</template>
      </el-table-column>
      <el-table-column label="数据权限" width="160">
        <template #default="{ row }">{{ dataScopeMap[row.dataScope] || row.dataScope }}</template>
      </el-table-column>
      <el-table-column label="权限点数量" width="110">
        <template #default="{ row }">{{ (perms[row.id] || []).length }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="openPerms(row)">权限点</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建角色模板" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="租户类型" prop="tenantType">
          <el-select v-model="form.tenantType"><el-option label="厂家" value="MANUFACTURER" /><el-option label="经销商" value="DEALER" /></el-select>
        </el-form-item>
        <el-form-item label="数据权限" prop="dataScope">
          <el-select v-model="form.dataScope"><el-option label="全部数据" value="ALL_TENANT" /><el-option label="岗位树" value="POSITION_TREE" /><el-option label="自己创建" value="SELF_CREATED" /></el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="saveCreate">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="permVisible" title="维护权限点（菜单 / 接口 / 按钮）" width="760px" top="6vh">
      <div class="perm-toolbar">
        <el-input v-model="permKeyword" placeholder="搜索编码/名称" clearable size="small" style="width:240px" />
        <el-checkbox v-model="selectAllVisible" @change="toggleSelectAllVisible">全选可见</el-checkbox>
        <span class="perm-stat">已选 {{ selectedPerms.length }} / 资源 {{ filteredResources.length }}</span>
      </div>
      <div class="perm-groups">
        <div v-for="(group, gkey) in groupedResources" :key="gkey" class="perm-group">
          <div class="perm-group-title">
            <el-checkbox :model-value="isGroupAllChecked(gkey)" :indeterminate="isGroupIndeterminate(gkey)" @change="(v) => toggleGroup(gkey, v)">
              <strong>{{ groupLabel(gkey) }}</strong> <span class="dim">({{ group.length }})</span>
            </el-checkbox>
          </div>
          <div class="perm-group-body">
            <el-checkbox v-for="r in group" :key="r.code" :label="r.code" v-show="matchesKeyword(r)">
              <span class="perm-code">{{ r.code }}</span>
              <span class="dim">  {{ r.name }}</span>
            </el-checkbox>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="permVisible=false">取消</el-button>
        <el-button type="primary" @click="savePerms">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listRoleTemplates, createRoleTemplate, getTemplatePermissions, setTemplatePermissions, listTemplateResources } from '@/api/admin'

const tenantTypeMap = { MANUFACTURER: '厂家', DEALER: '经销商', ALL_TENANT: '全部' }
const dataScopeMap = { ALL_TENANT: '全部数据', POSITION_TREE: '岗位树', SELF_CREATED: '自己创建' }

const tenantType = ref('MANUFACTURER')
const keyword = ref('')
const templates = ref([]); const loading = ref(false)
const perms = ref({}); const resources = ref([])
const createVisible = ref(false); const permVisible = ref(false)
const formRef = ref()
const form = reactive({ code: '', name: '', tenantType: 'MANUFACTURER', dataScope: 'ALL_TENANT', description: '' })
const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  tenantType: [{ required: true, message: '请选择租户类型', trigger: 'change' }],
  dataScope: [{ required: true, message: '请选择数据权限', trigger: 'change' }]
}
const current = ref(null); const selectedPerms = ref([])

const filteredTemplates = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return templates.value
  return templates.value.filter(t => (t.code || '').toLowerCase().includes(k) || (t.name || '').toLowerCase().includes(k))
})

// === D13: 权限点按 type 分组 + 搜索 ===
const permKeyword = ref('')
const selectAllVisible = ref(false)
function matchesKeyword(r) {
  if (!permKeyword.value) return true
  const k = permKeyword.value.toLowerCase()
  return (r.code || '').toLowerCase().includes(k) || (r.name || '').toLowerCase().includes(k)
}
const filteredResources = computed(() => {
  if (!permKeyword.value) return resources.value
  const k = permKeyword.value.toLowerCase()
  return resources.value.filter((r) => (r.code || '').toLowerCase().includes(k) || (r.name || '').toLowerCase().includes(k))
})
const groupedResources = computed(() => {
  const out = { menu: [], api: [], button: [], other: [] }
  for (const r of filteredResources.value) {
    const t = (r.type || 'other')
    if (!out[t]) out[t] = []
    out[t].push(r)
  }
  return out
})
function groupLabel(g) {
  return ({ menu: '菜单', api: '接口', button: '按钮' })[g] || ('其它(' + g + ')')
}
function isGroupAllChecked(gkey) {
  const arr = groupedResources.value[gkey] || []
  if (!arr.length) return false
  return arr.every((r) => selectedPerms.value.includes(r.code))
}
function isGroupIndeterminate(gkey) {
  const arr = groupedResources.value[gkey] || []
  if (!arr.length) return false
  const c = arr.filter((r) => selectedPerms.value.includes(r.code)).length
  return c > 0 && c < arr.length
}
function toggleGroup(gkey, checked) {
  const arr = groupedResources.value[gkey] || []
  const set = new Set(selectedPerms.value)
  for (const r of arr) {
    if (checked) set.add(r.code); else set.delete(r.code)
  }
  selectedPerms.value = [...set]
}
function toggleSelectAllVisible(v) {
  const set = new Set(selectedPerms.value)
  for (const r of filteredResources.value) {
    if (v) set.add(r.code); else set.delete(r.code)
  }
  selectedPerms.value = [...set]
}

async function load() {
  loading.value = true
  try {
    const res = await listRoleTemplates({ tenantType: tenantType.value })
    templates.value = res.data
    const p = {}
    for (const t of templates.value) {
      try { const pr = await getTemplatePermissions(t.id); p[t.id] = pr.data } catch (e) { p[t.id] = [] }
    }
    perms.value = p
  } finally { loading.value = false }
}
function onTypeChange() { keyword.value = ''; load() }
function onSearch() { /* computed filters; data already loaded */ }
function onReset() { keyword.value = ''; tenantType.value = 'MANUFACTURER'; load() }
function openCreate() {
  Object.assign(form, { code: '', name: '', tenantType: tenantType.value, dataScope: 'ALL_TENANT', description: '' })
  formRef.value?.resetFields()
  createVisible.value = true
}
async function saveCreate() {
  await formRef.value.validate()
  await createRoleTemplate(form); ElMessage.success('已创建'); createVisible.value = false; load()
}
async function openPerms(row) { permKeyword.value = ''; selectAllVisible.value = false;
  current.value = row
  const [res, exist] = await Promise.all([listTemplateResources(row.tenantType), getTemplatePermissions(row.id)])
  resources.value = res.data; selectedPerms.value = exist.data || []; permVisible.value = true
}
async function savePerms() { await setTemplatePermissions(current.value.id, selectedPerms.value); ElMessage.success('已保存'); permVisible.value = false; load() }
onMounted(load)
</script>