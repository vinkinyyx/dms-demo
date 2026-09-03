<template>
  <div class="auth-detail-page" v-loading="loading">
    <el-page-header @back="$router.push('/authorizations')" content="授权详情" style="margin-bottom:12px" />
    <el-card v-if="detail.id">
      <template #header>
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-weight:600">AUTH-{{ detail.id }}</span>
          <el-tag :type="tagType(detail.status)" size="small">{{ detail.statusLabel || detail.status }}</el-tag>
          <span style="flex:1"></span>
          <el-button v-if="canTerminate" v-has="'authorization:create'" type="danger" @click="doTerminate">发起终止</el-button>
          <el-button v-if="canRenew" v-has="'authorization:create'" type="warning" @click="doRenew">续约</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="经销商">{{ detail.dealerName || detail.dealerId }}</el-descriptions-item>
        <el-descriptions-item label="授权类型">{{ detail.authType }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ detail.validFrom }} ~ {{ detail.validTo }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.source }}</el-descriptions-item>
        <el-descriptions-item label="授权产品线" :span="2">{{ detail.productLineNames || '—' }}</el-descriptions-item>
        <el-descriptions-item label="授权终端医院" :span="2">
          <el-tag v-for="t in detail.authorizedTerminals || []" :key="t.id" size="small" style="margin:2px">{{ t.name }}</el-tag>
          <span v-if="!(detail.authorizedTerminals && detail.authorizedTerminals.length)">—</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuthorization, terminateAuthorization, renewAuthorization } from './api'

const route = useRoute()
const router = useRouter()
const detail = ref({})
const loading = ref(false)

const canTerminate = computed(() => ['active', 'not_started'].includes(detail.value.status))
const canRenew = computed(() => ['active', 'not_started', 'expired'].includes(detail.value.status))

function tagType(s) {
  return { active: 'success', not_started: 'info', pending_approval: 'warning',
    terminate_pending: 'warning', expired: 'info', terminated: 'danger',
    rejected: 'danger', draft: 'info' }[s] || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await getAuthorization(route.params.id)
    detail.value = res?.data || {}
  } catch (e) {
    ElMessage.error('加载失败: ' + (e?.message || e))
  } finally { loading.value = false }
}

async function doTerminate() {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('请输入终止原因', '授权终止', {
      confirmButtonText: '提交终止审批', cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim()) ? true : '终止原因必填'
    })
    reason = r.value
  } catch { return }
  try {
    await terminateAuthorization(detail.value.id, { reason })
    ElMessage.success('已提交终止审批')
    load()
  } catch (e) { ElMessage.error('终止失败: ' + (e?.response?.data?.message || e?.message || e)) }
}

async function doRenew() {
  try {
    const { value } = await ElMessageBox.prompt('请输入续约新的截止日期 (YYYY-MM-DD)', '授权续约', {
      confirmButtonText: '提交续约审批', cancelButtonText: '取消',
      inputValue: detail.value.validTo,
      inputValidator: (v) => /^\d{4}-\d{2}-\d{2}$/.test((v || '').trim()) ? true : '日期格式应为 YYYY-MM-DD'
    })
    await renewAuthorization(detail.value.id, { validTo: value.trim() })
    ElMessage.success('续约已提交审批')
    load()
  } catch (e) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error('续约失败: ' + (e?.response?.data?.message || e?.message || e))
  }
}

onMounted(load)
</script>
