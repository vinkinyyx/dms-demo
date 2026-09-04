<template>
  <div class="contract-workspace">
    <el-card shadow="never">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="合同编号/名称" clearable style="width: 200px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="合同分类">
          <el-select v-model="query.category" placeholder="合同分类" clearable style="width: 140px">
            <el-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px" @change="reload">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button type="primary" v-has="'contract:create'" @click="goCreate"><el-icon><Plus /></el-icon>新建合同</el-button>
          <el-button @click="doExport"><el-icon><Download /></el-icon>导出</el-button>
          <el-button @click="doExportAsync" :loading="exportingAsync">异步导出</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="合同编号" width="190"><template #default="{ row }"><el-link type="primary" @click="goDetail(row.id)">{{ row.code }}</el-link></template></el-table-column>
        <el-table-column prop="name" label="合同名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ appTypeLabel(row.applicationType) }}</template>
        </el-table-column>
        <el-table-column prop="dealerName" label="经销商" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.dealerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ row.signedAmount != null ? Number(row.signedAmount).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="200">
          <template #default="{ row }">{{ formatDate(row.validFrom) }} ~ {{ formatDate(row.validTo) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).tag" size="small">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="goDetail(row.id)">查看</el-button>
              <template v-if="rowActions(row).length <= 2">
                <el-button v-for="a in rowActions(row)" :key="a.key" v-has="a.perm" link :type="a.type" @click="a.on(row.id)">{{ a.label }}</el-button>
              </template>
              <el-dropdown v-else trigger="click" @command="(cmd)=>cmd.on(row.id)">
                <el-button link type="primary">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="a in rowActions(row)" :key="a.key" v-has="a.perm" :command="a">{{ a.label }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="(p) => { query.page = p; load() }"
        @size-change="(s) => { query.size = s; query.page = 1; load() }"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import { listContracts, submitContract, withdrawContract, deleteContract, terminateContract, exportContracts } from './api'
import { CATEGORY_OPTIONS, APP_TYPE_OPTIONS, STATUS_OPTIONS, categoryLabel, appTypeLabel, statusMeta } from './dict'
import request from '@/utils/request'
import { formatDate } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, status: '', keyword: '', category: '' })

async function load() {
  loading.value = true
  try {
    const params = {}
    Object.keys(query).forEach((k) => {
      const v = query[k]
      if (v !== null && v !== undefined && v !== '') params[k] = v
    })
    const res = await listContracts(params)
    const data = res.data || res
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
function reload() { query.page = 1; load() }
function reset() { query.keyword = ''; query.category = ''; query.status = ''; reload() }
function goCreate() { router.push('/contracts/new') }
function goEdit(id) { router.push('/contracts/' + id + '/edit') }
function goDetail(id) { router.push('/contracts/' + id) }

async function doSubmit(id) {
  await ElMessageBox.confirm('提交后合同将进入审批流程并锁定编辑，确认提交？', '提示', { type: 'warning' })
  await submitContract(id)
  ElMessage.success('已提交审批')
  load()
}
async function doWithdraw(id) {
  await ElMessageBox.confirm('确认撤回该合同的审批？', '提示', { type: 'warning' })
  await withdrawContract(id)
  ElMessage.success('已撤回')
  load()
}
async function doTerminate(id) {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('请输入合同终止原因', '合同终止', {
      confirmButtonText: '提交终止审批', cancelButtonText: '取消', type: 'warning',
      inputValidator: (v) => (v && v.trim()) ? true : '终止原因必填'
    })
    reason = r.value
  } catch { return }
  try {
    await terminateContract(id, { reason })
    ElMessage.success('已提交终止审批')
    load()
  } catch (e) { ElMessage.error('终止失败: ' + (e?.response?.data?.message || e?.message || e)) }
}

const exportingAsync = ref(false)
async function doExportAsync() {
  exportingAsync.value = true
  try {
    await request({ url: '/api/contracts/actions/export-async', method: 'post', params: query })
    ElMessage.success('导出任务已提交，请在"导入导出任务"中查看并下载')
  } catch (e) {
    ElMessage.error('提交失败: ' + (e?.message || e))
  } finally { exportingAsync.value = false }
}
async function doExport() {
  loading.value = true
  try {
    const blob = await exportContracts(query)
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'contracts_' + new Date().toISOString().slice(0,10) + '.xlsx')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('已导出当前筛选结果')
  } catch (e) {
    ElMessage.error('导出失败: ' + (e?.message || e))
  } finally {
    loading.value = false
  }
}
async function doDelete(id) {
  await ElMessageBox.confirm('确认删除该草稿合同？', '提示', { type: 'warning' })
  await deleteContract(id)
  ElMessage.success('已删除')
  load()
}
function rowActions(row){
  const a=[]
  if(row.status==='draft'||row.status==='rejected') a.push({key:'edit',label:'编辑',type:'primary',perm:null,on:(id)=>goEdit(id)})
  if(row.status==='draft') a.push({key:'submit',label:'提交',type:'warning',perm:null,on:(id)=>doSubmit(id)})
  if(row.status==='draft') a.push({key:'delete',label:'删除',type:'danger',perm:null,on:(id)=>doDelete(id)})
  if(row.status==='pending') a.push({key:'withdraw',label:'撤回',type:'info',perm:null,on:(id)=>doWithdraw(id)})
  if(row.status==='effective') a.push({key:'terminate',label:'终止合同',type:'danger',perm:'contract:submit',on:(id)=>doTerminate(id)})
  return a
}
onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.row-actions { display: flex; gap: 6px; align-items: center; }
</style>