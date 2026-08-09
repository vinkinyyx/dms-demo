<template>
  <div class="template-list">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="模板名称/编号" clearable style="width: 220px" @keyup.enter="reload" />
        <el-select v-model="query.category" placeholder="分类" clearable style="width: 160px">
          <el-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="o in TEMPLATE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="success" @click="goCreate"><el-icon><Plus /></el-icon>新建模板</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="模板编号" width="200" />
        <el-table-column prop="name" label="模板名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="绑定分类" width="130">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="字段数" width="90" align="right">
          <template #default="{ row }">{{ (row.fields || []).length }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="templateStatusMeta(row.status).tag" size="small">{{ templateStatusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goEdit(row.id)">编辑</el-button>
            <el-button v-if="row.status === 'draft'" link type="success" @click="doPublish(row.id)">发布</el-button>
            <el-button v-if="row.status === 'published'" link type="warning" @click="doDisable(row.id)">停用</el-button>
            <el-button link type="primary" @click="doNewVersion(row.id)">新建版本</el-button>
            <el-button v-if="row.status !== 'published'" link type="danger" @click="doDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" layout="total, prev, pager, next"
        :total="total" :current-page="query.page" :page-size="query.size"
        @current-change="(p) => { query.page = p; load() }" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listTemplates, publishTemplate, disableTemplate, newTemplateVersion, deleteTemplate } from './api'
import { CATEGORY_OPTIONS, TEMPLATE_STATUS_OPTIONS, categoryLabel, templateStatusMeta } from './dict'
import { formatDate } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', category: '', status: '' })

async function load() {
  loading.value = true
  try {
    const res = await listTemplates(query)
    const d = res.data || res
    list.value = d.list || []
    total.value = d.total || 0
  } finally { loading.value = false }
}
function reload() { query.page = 1; load() }
function reset() { query.keyword = ''; query.category = ''; query.status = ''; reload() }
function goCreate() { router.push('/contracts/templates/new') }
function goEdit(id) { router.push('/contracts/templates/' + id) }
async function doPublish(id) {
  await ElMessageBox.confirm('发布后模板将锁定，不可再直接编辑，确认发布？', '提示', { type: 'warning' })
  await publishTemplate(id)
  ElMessage.success('已发布')
  load()
}
async function doDisable(id) {
  await ElMessageBox.confirm('确认停用该模板？', '提示', { type: 'warning' })
  await disableTemplate(id)
  ElMessage.success('已停用')
  load()
}
async function doNewVersion(id) {
  const res = await newTemplateVersion(id)
  ElMessage.success('已创建新版本草稿')
  router.push('/contracts/templates/' + (res.data || res).id)
}
async function doDelete(id) {
  await ElMessageBox.confirm('确认删除该模板草稿？', '提示', { type: 'warning' })
  await deleteTemplate(id)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>