<template>
  <div>
    <div class="toolbar">
      <el-select v-model="status" placeholder="发送状态" clearable style="width:160px" @change="reload(1)">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
      </el-select>
      <div style="flex:1" />
      <el-button type="primary" @click="sendTest">发送测试邮件</el-button>
      <el-button @click="reload(1)">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status === 'SUCCESS' ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="toAddress" label="收件人" min-width="180" show-overflow-tooltip />
      <el-table-column prop="subject" label="主题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="bizType" label="类型" width="140" />
      <el-table-column prop="bizId" label="业务ID" width="100" />
      <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
      <el-table-column label="发送时间" width="180">
        <template #default="{ row }">{{ row.sentAt || row.createdAt }}</template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false), rows = ref([]), total = ref(0)
const page = ref(1), size = ref(20), status = ref(null)

async function reload(p = page.value) {
  page.value = p; loading.value = true
  try {
    const params = { page: page.value, size, status: status.value || undefined }
    const { data } = await request({ url: '/api/email-logs', method: 'get', params })
    rows.value = data?.list || []; total.value = data?.total || 0
  } finally { loading.value = false }
}
function onPage(p) { reload(p) }
async function sendTest() {
  const { value } = await ElMessageBox.prompt('请输入收件邮箱', '发送测试邮件', {
    confirmButtonText: '发送', cancelButtonText: '取消',
    inputPattern: /^.+@.+\..+$/, inputErrorMessage: '邮箱格式不正确'
  })
  await request({ url: '/api/email-logs/test', method: 'post', data: { to: value } })
  ElMessage.success('测试邮件已发送')
  reload(1)
}
onMounted(() => reload(1))
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }
</style>
