<template>
  <div class="dealer-sub-table">
    <div class="sub-toolbar">
      <span class="sub-title">联系人</span>
      <el-button type="primary" size="small" v-has="['dealer_contact:create','dealer:edit']" @click="openAdd">
        <el-icon><Plus /></el-icon>新增联系人
      </el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column label="默认" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="contactName" label="姓名" min-width="100" />
      <el-table-column prop="position" label="职务" width="110" />
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" v-has="['dealer_contact:edit','dealer:edit']" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" size="small" v-if="!row.isDefault" v-has="['dealer_contact:edit','dealer:edit']" @click="onSetDefault(row)">设为默认</el-button>
          <el-button link type="danger" size="small" v-has="['dealer_contact:delete','dealer:edit']" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑联系人' : '新增联系人'" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="form.contactName" maxlength="50" /></el-form-item>
        <el-form-item label="职务"><el-input v-model="form.position" maxlength="50" /></el-form-item>
        <el-form-item label="电话" required><el-input v-model="form.phone" maxlength="20" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="100" placeholder="name@company.com" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:160px">
            <el-option label="启用" value="active" /><el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listAllDealerContacts, createDealerContact, updateDealerContact,
  deleteDealerContact, setDefaultDealerContact
} from '@/api/registration'

const props = defineProps({ dealerId: { type: [Number, String], required: true } })

const loading = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({ contactName: '', position: '', phone: '', email: '', status: 'active', remark: '' })

async function load() {
  if (!props.dealerId) return
  loading.value = true
  try {
    const res = await listAllDealerContacts(props.dealerId)
    rows.value = (res.data || []).slice().sort((a, b) => Number(b.isDefault || 0) - Number(a.isDefault || 0))
  } finally { loading.value = false }
}

function resetForm() {
  form.contactName = ''; form.position = ''; form.phone = ''; form.email = ''
  form.status = 'active'; form.remark = ''
}
function openAdd() { editing.value = null; resetForm(); dialogVisible.value = true }
function openEdit(row) {
  editing.value = row
  form.contactName = row.contactName || ''
  form.position = row.position || ''
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.status = row.status || 'active'
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function onSave() {
  if (!form.contactName.trim()) { ElMessage.warning('请填写联系人姓名'); return }
  if (!form.phone.trim()) { ElMessage.warning('请填写联系电话'); return }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { ElMessage.warning('邮箱格式不正确'); return }
  const payload = { ...form, dealerId: Number(props.dealerId) }
  saving.value = true
  try {
    if (editing.value) await updateDealerContact(editing.value.id, payload)
    else await createDealerContact(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally { saving.value = false }
}

function onSetDefault(row) {
  ElMessageBox.confirm('确认将「' + (row.contactName || '该联系人') + '」设为默认联系人？', '提示', { type: 'warning' })
    .then(async () => { await setDefaultDealerContact(row.id); ElMessage.success('已设为默认'); load() }).catch(() => {})
}
function onDelete(row) {
  ElMessageBox.confirm('确认删除联系人「' + (row.contactName || '') + '」？', '删除确认', { type: 'warning' })
    .then(async () => { await deleteDealerContact(row.id); ElMessage.success('已删除'); load() }).catch(() => {})
}

defineExpose({ load, refresh: load })
load()
</script>

<style scoped>
.sub-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.sub-title { font-weight: 600; font-size: 14px; }
</style>
