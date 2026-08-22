<template>
  <div class="page">
    <div class="page-header">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="编码/名称" clearable style="width:220px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建经销商租户</el-button>
    </div>
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="code" label="编码" width="160" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="ownerManufacturerId" label="所属厂家" width="280" />
      <el-table-column prop="boundDealerId" label="绑定dealerId" width="130" />
      <el-table-column prop="contactName" label="联系人" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'danger'">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'active'" link type="primary" @click="toggle(row, true)">启用</el-button>
          <el-button v-else link type="danger" @click="toggle(row, false)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
      :page-size="size" :current-page="page" @current-change="onPage" />

    <el-dialog v-model="dialog" title="新建经销商租户" width="560px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="所属厂家租户ID">
          <el-input v-model="form.manufacturerTenantId" placeholder="厂家租户 UUID" />
        </el-form-item>
        <el-form-item label="dealer ID"><el-input v-model.number="form.dealerId" /></el-form-item>
        <el-form-item label="租户编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="租户名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="管理员账号"><el-input v-model="form.adminUsername" /></el-form-item>
        <el-form-item label="管理员密码"><el-input v-model="form.adminPassword" type="password" show-password /></el-form-item>
        <el-form-item label="管理员姓名"><el-input v-model="form.adminName" /></el-form-item>
        <el-form-item label="进销存/库存管理">
          <el-switch v-model="form.inventoryEnabled" />
          <span style="margin-left:10px;color:#909399">关闭后隐藏采购和库存菜单，销售出库由ERP回调</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import { listDealers, createDealer, enableTenant, disableTenant } from '@/api/admin'

const list = ref([]); const total = ref(0); const page = ref(1); const size = ref(20)
const loading = ref(false); const keyword = ref('')
const dialog = ref(false); const saving = ref(false)
const form = reactive({ manufacturerTenantId: '', dealerId: null, code: '', name: '', contactName: '', contactPhone: '', adminUsername: '', adminPassword: '', adminName: '', inventoryEnabled: true })

async function load() {
  loading.value = true
  try {
    const res = await listDealers({ page: page.value, size: size.value, keyword: keyword.value })
    list.value = res.data.list; total.value = res.data.total
  } finally { loading.value = false }
}
function onPage(p) { page.value = p; load() }
function inventoryEnabled(row) { return row.modulesEnabled?.inventoryEnabled !== false }
function openCreate() {
  Object.assign(form, { manufacturerTenantId: '', dealerId: null, code: '', name: '', contactName: '', contactPhone: '', adminUsername: '', adminPassword: '', adminName: '', inventoryEnabled: true })
  dialog.value = true
}
async function save() {
  saving.value = true
  try { await createDealer(form); ElMessage.success('创建成功'); dialog.value = false; load() }
  finally { saving.value = false }
}
async function toggle(row, active) {
  if (active) { await enableTenant(row.id); ElMessage.success('已启用') }
  else {
    const { value } = await ElMessageBox.prompt('请输入停用原因', '停用租户', { confirmButtonText: '确定', cancelButtonText: '取消', inputType: 'textarea' })
    await disableTenant(row.id, value); ElMessage.success('已停用')
  }
  load()
}
onMounted(load)
</script>
<style scoped>.pager { margin-top: 16px; justify-content: flex-end; }</style>