<template>
  <div class="contract-workspace">
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="reload">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="draft">草稿</el-radio-button>
          <el-radio-button label="pending">审批中</el-radio-button>
          <el-radio-button label="effective">已生效</el-radio-button>
          <el-radio-button label="rejected">已驳回</el-radio-button>
          <el-radio-button label="terminated">已终止</el-radio-button>
          <el-radio-button label="expired">已到期</el-radio-button>
        </el-radio-group>
        <div class="filters">
          <el-input v-model="query.keyword" placeholder="合同编号/名称" clearable style="width: 200px" @keyup.enter="reload" />
          <el-select v-model="query.category" placeholder="合同分类" clearable style="width: 140px">
            <el-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button type="success" @click="goCreate"><el-icon><Plus /></el-icon>新建合同</el-button>
          <el-button @click="doExport"><el-icon><Download /></el-icon>导出</el-button>
          <el-button @click="doExportAsync" :loading="exportingAsync">异步导出</el-button>
        </div>
      </div>

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
              <el-button v-if="rowActions(row).length <= 2" v-for="a in rowActions(row)" :key="a.key" link :type="a.type" @click="a.on(row.id)">{{ a.label }}</el-button>
              <el-dropdown v-if="rowActions(row).length > 2" trigger="click" @command="(cmd)=>cmd.on(row.id)">
                <el-button link type="primary">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="a in rowActions(row)" :key="a.key" :command="a">{{ a.label }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        :page-sizes="[10, 20, 50]"
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
import { listContracts, submitContract, withdrawContract, deleteContract, exportContracts } from './api'
import { CATEGORY_OPTIONS, APP_TYPE_OPTIONS, categoryLabel, appTypeLabel, statusMeta } from './dict'
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
    const res = await listContracts(query)
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
  if(row.status==='draft'||row.status==='rejected') a.push({key:'edit',label:'编辑',type:'primary',on:(id)=>goEdit(id)})
  if(row.status==='draft') a.push({key:'submit',label:'提交',type:'warning',on:(id)=>doSubmit(id)})
  if(row.status==='draft') a.push({key:'delete',label:'删除',type:'danger',on:(id)=>doDelete(id)})
  if(row.status==='pending') a.push({key:'withdraw',label:'撤回',type:'info',on:(id)=>doWithdraw(id)})
  return a
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; }
.pager { margin-top: 16px; justify-content: flex-end; }
.row-actions { display: flex; gap: 6px; align-items: center; }
</style>