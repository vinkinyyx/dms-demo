<template>
  <div class="dealer-sub-table">
    <div class="sub-toolbar">
      <span class="sub-title">收货地址</span>
      <el-button type="primary" size="small" v-has="['dealer_address:create','dealer:edit']" @click="openAdd">
        <el-icon><Plus /></el-icon>新增地址
      </el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column label="默认" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="addressName" label="地址名称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="contactName" label="收货人" width="100" />
      <el-table-column prop="phone" label="收货电话" width="130" />
      <el-table-column label="所在地区" min-width="160">
        <template #default="{ row }">{{ [row.province, row.city, row.district].filter(Boolean).join(' ') }}</template>
      </el-table-column>
      <el-table-column prop="address" label="详细地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="postalCode" label="邮编" width="90" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" v-has="['dealer_address:edit','dealer:edit']" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" size="small" v-if="!row.isDefault" v-has="['dealer_address:edit','dealer:edit']" @click="onSetDefault(row)">设为默认</el-button>
          <el-button link type="danger" size="small" v-has="['dealer_address:delete','dealer:edit']" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑收货地址' : '新增收货地址'" width="560px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="地址名称" required><el-input v-model="form.addressName" placeholder="如：总部仓库、门店一店" maxlength="50" /></el-form-item>
        <el-form-item label="收货人" required><el-input v-model="form.contactName" maxlength="50" /></el-form-item>
        <el-form-item label="收货电话" required><el-input v-model="form.phone" maxlength="20" /></el-form-item>
        <el-form-item label="省/市/区">
          <el-input v-model="form.province" placeholder="省" style="width:32%" />&nbsp;
          <el-input v-model="form.city" placeholder="市" style="width:32%" />&nbsp;
          <el-input v-model="form.district" placeholder="区/县" style="width:32%" />
        </el-form-item>
        <el-form-item label="详细地址" required><el-input v-model="form.address" type="textarea" :rows="2" maxlength="200" /></el-form-item>
        <el-form-item label="邮政编码"><el-input v-model="form.postalCode" maxlength="10" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:160px">
            <el-option label="启用" value="active" /><el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
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
  listAllDealerAddresses, createDealerAddress, updateDealerAddress,
  deleteDealerAddress, setDefaultDealerAddress
} from '@/api/registration'

const props = defineProps({ dealerId: { type: [Number, String], required: true } })

const loading = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref(null)
const emptyForm = () => ({
  addressName: '', contactName: '', phone: '', province: '', city: '', district: '',
  address: '', postalCode: '', status: 'active'
})
const form = reactive(emptyForm())

async function load() {
  if (!props.dealerId) return
  loading.value = true
  try {
    const res = await listAllDealerAddresses(props.dealerId)
    rows.value = (res.data || []).slice().sort((a, b) => Number(b.isDefault || 0) - Number(a.isDefault || 0))
  } finally { loading.value = false }
}

function openAdd() { editing.value = null; Object.assign(form, emptyForm()); dialogVisible.value = true }
function openEdit(row) {
  editing.value = row
  Object.assign(form, emptyForm(), {
    addressName: row.addressName || '', contactName: row.contactName || '', phone: row.phone || '',
    province: row.province || '', city: row.city || '', district: row.district || '',
    address: row.address || '', postalCode: row.postalCode || '', status: row.status || 'active'
  })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.addressName.trim()) { ElMessage.warning('请填写地址名称'); return }
  if (!form.contactName.trim()) { ElMessage.warning('请填写收货人'); return }
  if (!form.phone.trim()) { ElMessage.warning('请填写收货电话'); return }
  if (!form.address.trim()) { ElMessage.warning('请填写详细地址'); return }
  const payload = { ...form, dealerId: Number(props.dealerId) }
  saving.value = true
  try {
    if (editing.value) await updateDealerAddress(editing.value.id, payload)
    else await createDealerAddress(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally { saving.value = false }
}

function onSetDefault(row) {
  ElMessageBox.confirm('确认将「' + (row.addressName || '该地址') + '」设为默认收货地址？', '提示', { type: 'warning' })
    .then(async () => { await setDefaultDealerAddress(row.id); ElMessage.success('已设为默认'); load() }).catch(() => {})
}
function onDelete(row) {
  ElMessageBox.confirm('确认删除地址「' + (row.addressName || '') + '」？', '删除确认', { type: 'warning' })
    .then(async () => { await deleteDealerAddress(row.id); ElMessage.success('已删除'); load() }).catch(() => {})
}

defineExpose({ load, refresh: load })
load()
</script>

<style scoped>
.sub-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.sub-title { font-weight: 600; font-size: 14px; }
</style>
