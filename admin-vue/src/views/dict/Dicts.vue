<template>
  <div class="dict-page">
    <el-row :gutter="16">
      <el-col :span="9">
        <div class="page-header">
          <span>字典类型</span>
          <div>
            <el-button size="small" @click="refreshCache">刷新缓存</el-button>
            <el-button type="primary" size="small" @click="openType">新建类型</el-button>
          </div>
        </div>
        <el-table :data="types" border stripe size="small" highlight-current-row @row-click="onSelectType">
          <el-table-column prop="code" label="编码" width="160" />
          <el-table-column prop="name" label="名称" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="editType(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="15">
        <div class="page-header">
          <span>{{ currentType ? `条目 - ${currentType.code}` : '请选择字典类型' }}</span>
          <el-button v-if="currentType" type="primary" @click="openItem">新增条目</el-button>
        </div>
        <el-table :data="items" border stripe size="small">
          <el-table-column prop="code" label="编码" width="160" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="seq" label="排序" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="row.status==='active'?'success':'info'">{{ row.status==='active'?'启用':'停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="editItem(row)">编辑</el-button>
              <el-button v-if="row.status==='active'" link type="danger" @click="toggleItem(row,false)">停用</el-button>
              <el-button v-else link type="success" @click="toggleItem(row,true)">启用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>

    <el-dialog v-model="typeDialog" :title="typeForm.id?'编辑类型':'新建类型'" width="420px">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="80px">
        <el-form-item v-if="!typeForm.id" label="编码" prop="code"><el-input v-model="typeForm.code" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="typeForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="typeForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="typeDialog=false">取消</el-button><el-button type="primary" @click="saveType">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="itemDialog" :title="itemForm.id?'编辑条目':'新增条目'" width="420px">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="80px">
        <el-form-item v-if="!itemForm.id" label="编码" prop="code"><el-input v-model="itemForm.code" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="itemForm.name" /></el-form-item>
        <el-form-item label="排序" prop="seq"><el-input-number v-model="itemForm.seq" :min="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="itemDialog=false">取消</el-button><el-button type="primary" @click="saveItem">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDictTypes, createDictType, updateDictType, listDictItems, createDictItem, updateDictItem, enableDictItem, disableDictItem, refreshDictCache } from '@/api/admin'

const types = ref([]); const items = ref([]); const currentType = ref(null)
const typeDialog = ref(false); const typeFormRef = ref()
const typeForm = reactive({ id: null, code: '', name: '', description: '' })
const typeRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}
const itemDialog = ref(false); const itemFormRef = ref()
const itemForm = reactive({ id: null, code: '', name: '', seq: 100 })
const itemRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

async function loadTypes() { const res = await listDictTypes(); types.value = res.data }
async function onSelectType(row) {
  if (!row) return
  currentType.value = row
  const res = await listDictItems(row.code); items.value = res.data
}
function openType() { Object.assign(typeForm, { id: null, code: '', name: '', description: '' }); typeFormRef.value?.resetFields(); typeDialog.value = true }
function editType(row) { Object.assign(typeForm, { id: row.id, code: row.code, name: row.name, description: row.description }); typeDialog.value = true }
async function saveType() {
  await typeFormRef.value.validate()
  if (typeForm.id) await updateDictType(typeForm.id, { name: typeForm.name, description: typeForm.description })
  else await createDictType({ code: typeForm.code, name: typeForm.name, description: typeForm.description })
  ElMessage.success('已保存'); typeDialog.value = false; loadTypes()
}
function openItem() { Object.assign(itemForm, { id: null, code: '', name: '', seq: 100 }); itemFormRef.value?.resetFields(); itemDialog.value = true }
function editItem(row) { Object.assign(itemForm, { id: row.id, code: row.code, name: row.name, seq: row.seq }); itemDialog.value = true }
async function saveItem() {
  await itemFormRef.value.validate()
  if (itemForm.id) await updateDictItem(itemForm.id, { code: itemForm.code, name: itemForm.name, seq: itemForm.seq })
  else await createDictItem(currentType.value.code, { code: itemForm.code, name: itemForm.name, seq: itemForm.seq })
  ElMessage.success('已保存'); itemDialog.value = false; onSelectType(currentType.value)
}
async function toggleItem(row, active) {
  const action = active ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}字典条目 ${row.name}？`, '确认', { type: 'warning', title: '确认' })
  active ? await enableDictItem(row.id) : await disableDictItem(row.id)
  ElMessage.success('已更新'); onSelectType(currentType.value)
}
async function refreshCache() { await refreshDictCache(); ElMessage.success('缓存已刷新') }
onMounted(loadTypes)
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
</style>