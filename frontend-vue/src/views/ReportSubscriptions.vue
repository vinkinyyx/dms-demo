<template>
  <div class="report-sub">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">报表订阅</span>
        <div style="flex:1" />
        <el-button type="primary" @click="openEdit()">新建订阅</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="订阅名称" min-width="160" />
        <el-table-column prop="reportType" label="报表" width="160" />
        <el-table-column label="频率" width="100">
          <template #default="{ row }">{{ freqLabel(row.cronExpr) }}</template>
        </el-table-column>
        <el-table-column prop="emails" label="收件人" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small">{{ row.active ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
<el-table-column label="上次运行" width="180"><template #default="{ row }">{{ formatDateTime(row.lastRunAt) }}</template></el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.lastStatus" size="small" :type="row.lastStatus==='SUCCESS'?'success':'danger'">{{ row.lastStatus==='SUCCESS'?'成功':'失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="runNow(row)">立即发送</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.active?'warning':'success'" @click="toggle(row)">{{ row.active?'停用':'启用' }}</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无订阅，点击右上角新建</template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="form.id?'编辑订阅':'新建订阅'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="例如：每日销售排行" /></el-form-item>
        <el-form-item label="报表">
          <el-select v-model="form.reportType" style="width:100%">
            <el-option v-for="r in reportTypes" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="频率">
          <el-radio-group v-model="form.cronExpr">
            <el-radio value="DAILY">每日</el-radio>
            <el-radio value="WEEKLY">每周一</el-radio>
            <el-radio value="MONTHLY">每月1日</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="收件人"><el-input v-model="form.emails" placeholder="多个邮箱用逗号分隔" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { formatDateTime } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const rows = ref([]), loading = ref(false)
const dialog = ref(false)
const form = reactive({ id: null, name: '', reportType: 'sales-ranking', cronExpr: 'DAILY', emails: '', active: true, params: '{}' })
const reportTypes = [
  { value: 'sales-ranking', label: '销售排行' },
  { value: 'inventory-turnover', label: '库存周转' },
  { value: 'order-trace', label: '订单追溯' },
  { value: 'sales', label: '销售报表' }
]
function freqLabel(c) { return ({ DAILY: '每日', WEEKLY: '每周', MONTHLY: '每月' })[c] || c }
async function reload() {
  loading.value = true
  try { const { data } = await request({ url: '/api/report-subscriptions', method: 'get' }); rows.value = data || [] }
  finally { loading.value = false }
}
function openEdit(row) {
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, name: '', reportType: 'sales-ranking', cronExpr: 'DAILY', emails: '', active: true, params: '{}' })
  dialog.value = true
}
async function save() {
  if (!form.name || !form.reportType) return ElMessage.warning('请填写名称和报表')
  if (!form.emails) return ElMessage.warning('请填写收件人邮箱')
  await request({ url: '/api/report-subscriptions', method: 'post', data: form })
  ElMessage.success('已保存'); dialog.value = false; reload()
}
async function toggle(row) { await request({ url: `/api/report-subscriptions/${row.id}/toggle`, method: 'post' }); reload() }
async function remove(row) {
  await ElMessageBox.confirm('确认删除该订阅？', '提示', { type: 'warning' })
  await request({ url: `/api/report-subscriptions/${row.id}`, method: 'delete' }); reload()
}
async function runNow(row) {
  await request({ url: `/api/report-subscriptions/${row.id}/run-now`, method: 'post' })
  ElMessage.success('已触发发送'); setTimeout(reload, 1500)
}
onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.title { font-size: 16px; font-weight: 600; }
</style>
