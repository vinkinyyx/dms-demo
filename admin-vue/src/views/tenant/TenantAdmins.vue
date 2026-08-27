<template>
  <div class="tenant-admins">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="租户">
          <el-select v-model="query.tenantId" clearable filterable placeholder="全部租户" style="width: 260px">
            <el-option-group label="厂商租户">
              <el-option v-for="tenant in manufacturers" :key="tenant.id" :label="tenant.code + ' / ' + tenant.name" :value="tenant.id" />
            </el-option-group>
            <el-option-group label="经销商租户">
              <el-option v-for="tenant in dealers" :key="tenant.id" :label="tenant.code + ' / ' + tenant.name" :value="tenant.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="用户名/姓名" style="width: 180px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>创建租户管理员</template>
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef" style="max-width: 760px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="所属租户" prop="tenantId"><el-select v-model="form.tenantId" filterable placeholder="选择租户" style="width:100%"><el-option-group label="厂商租户"><el-option v-for="tenant in manufacturers" :key="tenant.id" :label="tenant.code + ' / ' + tenant.name" :value="tenant.id" /></el-option-group><el-option-group label="经销商租户"><el-option v-for="tenant in dealers" :key="tenant.id" :label="tenant.code + ' / ' + tenant.name" :value="tenant.id" /></el-option-group></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item></el-col>
        </el-row>
        <el-button type="primary" :loading="creating" @click="createAdmin">创建</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>租户管理员列表</template>
      <el-table v-loading="loading" :data="table" border stripe size="small">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="所属租户" min-width="220"><template #default="{ row }"><div>{{ row.tenantName || '-' }}</div><div class="muted">{{ row.tenantCode || row.tenantId }}</div></template></el-table-column>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="改密" width="90"><template #default="{ row }"><el-tag v-if="row.mustChangePassword" type="warning">待修改</el-tag><span v-else>-</span></template></el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="170" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="view(row)">查看</el-button>
            <el-dropdown trigger="click" @command="(cmd) => onCommand(cmd, row)">
              <el-button link type="primary">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="reset">重置密码</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'active'" command="disable" divided>停用</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, sizes, prev, pager, next" :total="total"
        :current-page="query.page" :page-size="query.size" :page-sizes="[20,50,100]"
        @current-change="onPageChange" @size-change="onSizeChange" />
    </el-card>

    <el-drawer v-model="detailVisible" title="租户管理员详情" size="460px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="所属租户">{{ detail.tenantName || '-' }}<span class="muted" style="margin-left:6px">{{ detail.tenantCode || '' }}</span></el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status === 'active' ? '启用' : '停用' }}</el-descriptions-item>
        <el-descriptions-item label="待改密">{{ detail.mustChangePassword ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="最近登录">{{ detail.lastLoginAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { createTenantAdmin, disableTenantAdmin, listDealers, listManufacturers, listTenantAdmins, resetTenantAdminPassword } from '@/api/admin'

const loading = ref(false)
const creating = ref(false)
const table = ref([])
const total = ref(0)
const manufacturers = ref([])
const dealers = ref([])
const formRef = ref()
const query = reactive({ page: 1, size: 20, tenantId: null, keyword: '' })
const form = reactive({ tenantId: null, username: '', password: '', name: '' })
const detailVisible = ref(false)
const detail = ref(null)
const rules = { tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }], username: [{ required: true, message: '请输入用户名', trigger: 'blur' }], name: [{ required: true, message: '请输入姓名', trigger: 'blur' }], password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }] }

async function loadTenants() {
  const params = { page: 1, size: 200 }
  const [manufacturerRes, dealerRes] = await Promise.all([listManufacturers(params), listDealers(params)])
  manufacturers.value = manufacturerRes.data?.list || []
  dealers.value = dealerRes.data?.list || []
}
async function loadList() {
  loading.value = true
  try {
    const res = await listTenantAdmins(query)
    table.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function onSearch() { query.page = 1; loadList() }
function resetQuery() { Object.assign(query, { page: 1, size: 20, tenantId: null, keyword: '' }); loadList() }
function onPageChange(page) { query.page = page; loadList() }
function onSizeChange(size) { query.size = size; query.page = 1; loadList() }
function view(row) { detail.value = row; detailVisible.value = true }
function onCommand(cmd, row) {
  if (cmd === 'disable') disable(row)
  else if (cmd === 'reset') reset(row)
}
async function createAdmin() {
  await formRef.value.validate()
  creating.value = true
  try {
    await createTenantAdmin(form)
    ElMessage.success('创建成功')
    Object.assign(form, { tenantId: null, username: '', password: '', name: '' })
    formRef.value?.resetFields()
    await loadList()
  } finally { creating.value = false }
}
async function disable(row) {
  await ElMessageBox.confirm(`确定停用租户管理员 ${row.username}？`, '确认', { type: 'warning', title: '确认' })
  await disableTenantAdmin(row.id)
  ElMessage.success('已停用')
  loadList()
}
async function reset(row) {
  const { value } = await ElMessageBox.prompt(`请输入 ${row.username} 的新密码`, '重置密码', { inputType: 'password', inputValidator: (v) => (v && v.length >= 6) || '密码至少 6 位' })
  await resetTenantAdminPassword(row.id, value)
  ElMessage.success('密码已重置')
}
onMounted(async () => { await loadTenants(); await loadList() })
</script>

<style scoped>
.tenant-admins { display: flex; flex-direction: column; gap: 12px; }
.muted { color: var(--dms-text-4); font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>