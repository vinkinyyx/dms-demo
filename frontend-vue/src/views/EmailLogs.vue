<template>
  <el-card shadow="never">
    <div class="toolbar">
      <span class="title">邮件发送日志</span>
      <el-select v-model="status" placeholder="发送状态" clearable style="width:160px;margin-left:16px;" @change="reload">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
      </el-select>
      <div class="spacer" />
      <el-button type="primary" @click="sendTest">发送测试邮件</el-button><el-button @click="reload">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status === 'SUCCESS' ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="toAddress" label="收件人" min-width="180" />
      <el-table-column prop="subject" label="主题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="bizType" label="类型" width="140" />
      <el-table-column prop="bizId" label="业务ID" width="100" />
      <el-table-column prop="errorMessage" label="错误信息" min-width="220" show-overflow-tooltip />
      <el-table-column label="发送时间" width="170">
        <template #default="{ row }">{{ formatTime(row.sentAt || row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listEmailLogs } from '@/api/emailLog'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { formatTime } from './approval/dict'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref(null)

async function reload() {
  loading.value = true
  try {
    const res = await listEmailLogs({ page: page.value, size: size.value, status: status.value || undefined })
    rows.value = (res.data && res.data.list) || []
    total.value = (res.data && res.data.total) || 0
  } finally { loading.value = false }
}
function onPage(p) { page.value = p; reload() }
async function sendTest() {
  const { value } = await ElMessageBox.prompt('请输入收件邮箱', '发送测试邮件', {
    confirmButtonText: '发送', cancelButtonText: '取消', inputPattern: /^.+@.+\..+$/, inputErrorMessage: '邮箱格式不正确'
  })
  const res = await request({ url: '/api/email-logs/test', method: 'post', data: { to: value } })
  const log = res.data || {}
  if (log.status === 'SUCCESS') ElMessage.success('发送成功，请检查收件箱')
  else ElMessage.error('发送失败：' + (log.errorMessage || '未知错误'))
  reload()
}
onMounted(reload)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 14px; }
.title { font-weight: 600; font-size: 15px; }
.spacer { flex: 1; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>