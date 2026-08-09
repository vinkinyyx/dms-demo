<template>
  <div class="contract-detail" v-loading="loading">
    <el-page-header @back="$router.back()" :content="detail ? detail.name : '合同详情'" class="header">
      <template #extra>
        <el-tag :type="statusMeta(detail && detail.status).tag">{{ statusMeta(detail && detail.status).label }}</el-tag>
      </template>
    </el-page-header>

    <el-card shadow="never" v-if="detail">
      <el-descriptions title="基础信息" :column="3" border size="small">
        <el-descriptions-item label="合同编号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="合同名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ categoryLabel(detail.category) }}</el-descriptions-item>
        <el-descriptions-item label="申请类型">{{ appTypeLabel(detail.applicationType) }}</el-descriptions-item>
        <el-descriptions-item label="经销商">{{ detail.dealerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="甲方">{{ detail.vendorParty || '-' }}</el-descriptions-item>
        <el-descriptions-item label="乙方">{{ detail.dealerParty || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签订城市">{{ detail.signCity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ formatDate(detail.validFrom) }} ~ {{ formatDate(detail.validTo) }}</el-descriptions-item>
        <el-descriptions-item label="目标金额">{{ detail.targetAmount != null ? Number(detail.targetAmount).toLocaleString() : '-' }}</el-descriptions-item>
        <el-descriptions-item label="签约金额">{{ detail.signedAmount != null ? Number(detail.signedAmount).toLocaleString() : '-' }}</el-descriptions-item>
        <el-descriptions-item label="结算周期">{{ detail.settlementCycle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="付款条款">{{ detail.paymentTerms || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail.ownerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.ownerPhone || '-' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="dynamicFields.length">
        <el-divider content-position="left">合同条款</el-divider>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item v-for="f in dynamicFields" :key="f.key" :label="f.label" :span="f.type === 'textarea' ? 3 : 1">
            {{ detail.formData && detail.formData[f.key] != null ? detail.formData[f.key] : '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>

      <el-divider content-position="left">附件与成稿</el-divider>
      <el-upload action="/api/files/upload" :headers="headers" :data="{ bizType: 'contract-annex' }"
        :show-file-list="false" :on-success="onUpload" :before-upload="beforeUpload" v-if="canEdit">
        <el-button type="primary" plain size="small">上传附件</el-button>
      </el-upload>
      <el-table :data="detail.attachments || []" size="small" style="margin-top: 12px">
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="category" label="类别" width="120" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ row.sizeBytes ? (row.sizeBytes/1024).toFixed(1) + ' KB' : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-link type="primary" :href="row.fileUrl" target="_blank">下载</el-link>
            <el-button v-if="canEdit" link type="danger" @click="removeAtt(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px" v-if="detail.sourceFileId">
        <el-link type="success" :href="'/api/files/' + detail.sourceFileId + '/download'" target="_blank">
          下载合同成稿（Word）
        </el-link>
      </div>

      <el-divider content-position="left">审批与留痕</el-divider>
      <el-timeline>
        <el-timeline-item v-for="r in detail.revisions || []" :key="r.id" :timestamp="formatDate(r.createdAt)" placement="top">
          <el-tag size="small">{{ actionLabel(r.action) }}</el-tag>
          <span style="margin-left: 8px">{{ r.operatorName || '系统' }}</span>
          <div v-if="r.comment" style="color: #606266; margin-top: 4px">{{ r.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <div class="footer-actions" v-if="detail">
      <el-button v-if="detail.status === 'draft' || detail.status === 'rejected'" type="primary" @click="$router.push('/contracts/' + detail.id + '/edit')">编辑</el-button>
      <el-button v-if="detail.status === 'draft'" type="warning" @click="doSubmit">提交审批</el-button>
      <el-button v-if="detail.status === 'pending'" @click="doWithdraw">撤回</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken } from '@/utils/auth'
import { getContract, submitContract, withdrawContract, addContractAttachment, deleteContractAttachment } from './api'
import { categoryLabel, appTypeLabel, statusMeta } from './dict'
import { formatDate } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref(null)
const headers = { Authorization: 'Bearer ' + (getToken() || '') }
const canEdit = computed(() => detail.value && (detail.value.status === 'draft' || detail.value.status === 'rejected'))
const dynamicFields = computed(() => {
  if (!detail.value || !detail.value.template || !Array.isArray(detail.value.template.fields)) return []
  return [...detail.value.template.fields].sort((a, b) => (a.sort || 0) - (b.sort || 0))
})

async function load() {
  loading.value = true
  try {
    const res = await getContract(route.params.id)
    detail.value = res.data || res
  } finally {
    loading.value = false
  }
}
function beforeUpload(file) {
  if (file.size > 50 * 1024 * 1024) { ElMessage.error('文件不能超过 50MB'); return false }
  return true
}
async function onUpload(res) {
  if (res && res.code === 0) {
    const f = res.data
    await addContractAttachment(route.params.id, { fileId: f.fileId, fileName: f.originalName, sizeBytes: f.sizeBytes })
    ElMessage.success('上传成功')
    load()
  } else {
    ElMessage.error((res && res.message) || '上传失败')
  }
}
async function removeAtt(id) {
  await deleteContractAttachment(route.params.id, id)
  ElMessage.success('已删除')
  load()
}
async function doSubmit() {
  await ElMessageBox.confirm('提交后合同将进入审批流程，确认提交？', '提示', { type: 'warning' })
  await submitContract(route.params.id)
  ElMessage.success('已提交')
  load()
}
async function doWithdraw() {
  await ElMessageBox.confirm('确认撤回审批？', '提示', { type: 'warning' })
  await withdrawContract(route.params.id)
  ElMessage.success('已撤回')
  load()
}
function actionLabel(a) {
  return ({ submit: '提交', approve: '通过', reject: '驳回', withdraw: '撤回', terminate: '终止' })[a] || a
}
onMounted(load)
</script>

<style scoped>
.header { margin-bottom: 12px; }
.footer-actions { text-align: right; margin-top: 16px; }
</style>