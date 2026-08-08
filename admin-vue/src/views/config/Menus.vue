<template>
  <div class="page">
    <div class="page-header">
      <div class="toolbar">
        <el-radio-group v-model="tenantType" @change="load">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="MANUFACTURER">厂家</el-radio-button>
          <el-radio-button label="DEALER">经销商</el-radio-button>
        </el-radio-group>
      </div>
      <div>
        <el-button @click="refreshCache">刷新缓存</el-button>
        <el-button type="primary" @click="openCreate">新建菜单</el-button>
      </div>
    </div>
    <el-table :data="menus" v-loading="loading" border>
      <el-table-column prop="menuKey" label="Key" width="180" />
      <el-table-column prop="label" label="名称" width="160" />
      <el-table-column prop="parentKey" label="父级" width="140" />
      <el-table-column prop="route" label="路由" />
      <el-table-column prop="permissionCode" label="权限码" width="200" />
      <el-table-column prop="tenantType" label="类型" width="120" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag :type="row.status==='active'?'success':'info'">{{ row.status==='active'?'启用':'停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status==='active'" link type="danger" @click="toggle(row,false)">停用</el-button>
          <el-button v-else link type="success" @click="toggle(row,true)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id?'编辑菜单':'新建菜单'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="Key"><el-input v-model="form.menuKey" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.label" /></el-form-item>
        <el-form-item label="父级Key"><el-input v-model="form.parentKey" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item>
        <el-form-item label="路由"><el-input v-model="form.route" /></el-form-item>
        <el-form-item label="权限码"><el-input v-model="form.permissionCode" /></el-form-item>
        <el-form-item label="租户类型">
          <el-select v-model="form.tenantType"><el-option label="全部" value="ALL" /><el-option label="厂家" value="MANUFACTURER" /><el-option label="经销商" value="DEALER" /></el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listMenus, createMenu, updateMenu, enableMenu, disableMenu, refreshMenuCache } from '@/api/admin'

const tenantType = ref('ALL'); const menus = ref([]); const loading = ref(false)
const dialog = ref(false)
const form = reactive({ id: null, menuKey:'', label:'', parentKey:'', icon:'', route:'', permissionCode:'', tenantType:'ALL', sortOrder:100, visible:true })

async function load() {
  loading.value = true
  try { const res = await listMenus({ tenantType: tenantType.value === 'ALL' ? undefined : tenantType.value }); menus.value = res.data }
  finally { loading.value = false }
}
function openCreate() { Object.assign(form, { id:null, menuKey:'', label:'', parentKey:'', icon:'', route:'', permissionCode:'', tenantType: tenantType.value==='ALL'?'ALL':tenantType.value, sortOrder:100, visible:true }); dialog.value = true }
function openEdit(row) { Object.assign(form, row); dialog.value = true }
async function save() {
  if (form.id) { await updateMenu(form.id, form); ElMessage.success('已更新') }
  else { await createMenu(form); ElMessage.success('已创建') }
  dialog.value = false; load()
}
async function toggle(row, active) { active ? await enableMenu(row.id) : await disableMenu(row.id); ElMessage.success('已更新'); load() }
async function refreshCache() { await refreshMenuCache(); ElMessage.success('缓存已刷新') }
onMounted(load)
</script>