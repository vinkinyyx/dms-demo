<template>
  <div class="profile-page">
    <el-card class="profile-card" shadow="never">
      <template #header><div class="card-header">个人资料与安全</div></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ user.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ user.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="二次验证 (MFA)">
          <el-tag v-if="mfaEnabled" type="success">已启用</el-tag>
          <el-tag v-else type="info">未启用</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <div class="mfa-actions">
        <template v-if="!mfaEnabled">
          <el-button type="primary" :loading="loading" @click="startSetup">启用 MFA</el-button>
          <el-alert v-if="setup" type="info" :closable="false" show-icon title="请使用 Google Authenticator、Microsoft Authenticator 或 1Password 扫描密钥，然后输入 6 位验证码。" style="margin-top:16px" />
          <div v-if="setup" class="mfa-setup">
            <div class="mfa-secret"><span class="label">密钥</span><code>{{ setup.secret }}</code></div>
            <div class="mfa-url"><span class="label">otpauth</span><code class="small">{{ setup.otpAuthUrl }}</code></div>
            <el-input v-model="confirmCode" maxlength="6" placeholder="输入 6 位验证码" style="max-width:240px;margin-top:12px" />
            <div style="margin-top:12px">
              <el-button type="primary" :loading="loading" @click="confirmSetup">确认启用</el-button>
              <el-button @click="setup = null">取消</el-button>
            </div>
          </div>
        </template>
        <template v-else>
          <el-input v-model="disableCode" maxlength="6" placeholder="输入当前 6 位验证码" style="max-width:240px" />
          <el-button type="danger" style="margin-left:12px" :loading="loading" @click="disableMfa">关闭 MFA</el-button>
        </template>
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { mfaSetup, mfaConfirm, mfaDisable } from '@/api/auth'
const userStore = useUserStore()
const user = computed(() => userStore.user || {})
const mfaEnabled = ref(false)
const setup = ref(null)
const confirmCode = ref('')
const disableCode = ref('')
const loading = ref(false)
onMounted(loadStatus)
const isCodeValid = (code) => /^\d{6}$/.test((code || '').trim())
async function loadStatus() {
  try {
    const { data } = await mfaSetup()
    mfaEnabled.value = Boolean(data && data.enabled)
    setup.value = mfaEnabled.value ? null : data
  } catch (error) { setup.value = null }
}
async function startSetup() {
  loading.value = true
  try { const { data } = await mfaSetup(); setup.value = data; mfaEnabled.value = Boolean(data.enabled) }
  finally { loading.value = false }
}
async function confirmSetup() {
  if (!isCodeValid(confirmCode.value)) return ElMessage.warning('请输入 6 位数字验证码')
  loading.value = true
  try {
    await mfaConfirm({ code: confirmCode.value.trim() })
    ElMessage.success('MFA 已启用')
    setup.value = null
    confirmCode.value = ''
    mfaEnabled.value = true
    await loadStatus()
  } finally { loading.value = false }
}
async function disableMfa() {
  if (!isCodeValid(disableCode.value)) return ElMessage.warning('请输入 6 位数字验证码')
  loading.value = true
  try {
    await mfaDisable({ code: disableCode.value.trim() })
    ElMessage.success('MFA 已关闭')
    disableCode.value = ''
    mfaEnabled.value = false
    await loadStatus()
  } finally { loading.value = false }
}
</script>
<style scoped lang="scss">
.profile-page { padding: 4px; }
.profile-card { max-width: 760px; }
.card-header { font-weight: 600; }
.mfa-actions { margin-top: 20px; }
.mfa-setup { margin-top: 12px; padding: 12px; background: var(--dms-bg-page, #f5f7fa); border-radius: 4px; }
.mfa-secret, .mfa-url { margin: 6px 0; word-break: break-all; }
.mfa-url .small { font-size: 12px; }
.label { color: #606266; margin-right: 8px; }
</style>
