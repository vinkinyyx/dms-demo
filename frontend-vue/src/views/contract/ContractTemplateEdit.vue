<template>
  <div class="template-edit">
    <el-page-header @back="$router.back()" :content="isEdit ? '编辑合同模板' : '新建合同模板'" class="header" />
    <el-card shadow="never" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模板名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="绑定分类" prop="category">
              <el-select v-model="form.category" style="width:100%">
                <el-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-tag :type="templateStatusMeta(form.status).tag">{{ templateStatusMeta(form.status).label }}</el-tag>
              <span v-if="isEdit" style="margin-left:12px;color:var(--dms-text-4)">版本 V{{ form.version || 1 }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">Word 模板</el-divider>
        <el-upload :http-request="customUpload" :show-file-list="false" accept=".docx" :before-upload="beforeUpload">
          <el-button type="primary" plain>上传 Word(.docx)</el-button>
          <template #tip>
            <div class="el-upload__tip">
              上传后自动识别内容控件与 ${占位符}。当前文件：
              <el-link v-if="originalName" :href="'/api/files/' + form.originalFileId + '/download'" target="_blank" type="primary">{{ originalName }}</el-link>
              <span v-else>未上传</span>
            </div>
          </template>
        </el-upload>

        <el-divider content-position="left">
          可填字段（{{ form.fields.length }}）
          <el-button size="small" @click="addField" style="margin-left:12px">新增字段</el-button>
        </el-divider>
        <el-table :data="form.fields" size="small" border>
          <el-table-column label="排序" width="70">
            <template #default="{ $index }">
              <el-input-number v-model="form.fields[$index].sort" :controls="false" size="small" style="width:60px" />
            </template>
          </el-table-column>
          <el-table-column label="字段Key" width="180">
            <template #default="{ row }"><el-input v-model="row.key" size="small" /></template>
          </el-table-column>
          <el-table-column label="标签" width="160">
            <template #default="{ row }"><el-input v-model="row.label" size="small" /></template>
          </el-table-column>
          <el-table-column label="类型" width="140">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small">
                <el-option v-for="o in FIELD_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="分组" width="140">
            <template #default="{ row }"><el-input v-model="row.group" size="small" /></template>
          </el-table-column>
          <el-table-column label="必填" width="70" align="center">
            <template #default="{ row }"><el-checkbox v-model="row.required" /></template>
          </el-table-column>
          <el-table-column label="审批可见" width="90" align="center">
            <template #default="{ row }"><el-checkbox v-model="row.approvalVisible" /></template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button link type="danger" @click="form.fields.splice($index,1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <div class="footer-actions">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="info" @click="save(false)">保存草稿</el-button>
        <el-button v-if="form.status === 'draft'" type="primary" @click="save(true)">保存并发布</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import request from '@/utils/request'
import { createTemplate, getTemplate, publishTemplate, updateTemplate } from './api'
import { CATEGORY_OPTIONS, FIELD_TYPE_OPTIONS, templateStatusMeta } from './dict'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref(null)
const originalName = ref('')
const isEdit = computed(() => !!route.params.id && route.params.id !== 'new')
const headers = { Authorization: 'Bearer ' + (getToken() || '') }

const form = reactive({
  name: '', category: '', originalFileId: null, status: 'draft', version: 1,
  fields: []
})
const rules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择绑定分类', trigger: 'change' }]
}

function beforeUpload(file) {
  if (!file.name.toLowerCase().endsWith('.docx')) { ElMessage.error('仅支持 .docx'); return false }
  if (file.size > 50 * 1024 * 1024) { ElMessage.error('文件不能超过 50MB'); return false }
  return true
}
async function customUpload({ file }) {
  try {
    const fd = new FormData()
    fd.append('file', file)
    const r = await request({ url: '/api/contract-templates/upload-and-parse', method: 'post', data: fd, headers: { 'Content-Type': 'multipart/form-data' } })
    const d = r.data || r
    form.originalFileId = d.fileId
    originalName.value = d.originalName
    const fields = d.fields || []
    if (fields.length) {
      const existing = new Map(form.fields.map((f) => [f.key, f]))
      for (const f of fields) {
        if (!existing.has(f.key)) form.fields.push({ ...f, required: false, approvalVisible: true, group: f.group || '基本信息' })
      }
      ElMessage.success('识别到 ' + fields.length + ' 个字段')
    } else {
      ElMessage.info('未识别到可填字段，可手动新增')
    }
  } catch (e) {
    ElMessage.error('上传或识别失败')
  }
}

function addField() {
  form.fields.push({
    key: 'field_' + (form.fields.length + 1),
    label: '新字段', type: 'text', required: false, approvalVisible: true,
    group: '基本信息', sort: form.fields.length + 1
  })
}

async function load() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getTemplate(route.params.id)
    const d = res.data || res
    Object.assign(form, {
      name: d.name, category: d.category, originalFileId: d.originalFileId,
      status: d.status, version: d.version, fields: d.fields || []
    })
    if (d.originalFileId) {
      // 文件名在列表中不返回，仅展示文件ID
      originalName.value = '模板文件 #' + d.originalFileId
    }
  } finally { loading.value = false }
}

async function save(publish) {
  await formRef.value.validate()
  const payload = {
    name: form.name, category: form.category, originalFileId: form.originalFileId, fields: form.fields
  }
  let id
  if (isEdit.value) {
    await updateTemplate(route.params.id, payload)
    id = route.params.id
  } else {
    const res = await createTemplate(payload)
    id = (res.data || res).id
  }
  if (publish) {
    await publishTemplate(id)
    ElMessage.success('已发布')
  } else {
    ElMessage.success('已保存草稿')
  }
  router.push('/contracts/templates')
}
onMounted(load)
</script>

<style scoped>
.header { margin-bottom: 12px; }
.footer-actions { text-align: right; margin-top: 16px; }
</style>