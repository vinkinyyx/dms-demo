<template>
  <div class="page">
    <el-alert type="info" :closable="false" class="tip"
      title="此处控制系统【定时自动发送】的邮件，修改后实时生效、无需重启服务。业务操作触发的即时邮件（如审批流转通知）不受影响。" />

    <el-card v-loading="loading" class="card">
      <template #header><span class="card-title">定时邮件开关</span></template>
      <div v-for="item in switches" :key="item.key" class="switch-row" :class="{ master: item.key === masterKey }">
        <div class="switch-info">
          <div class="switch-label">
            {{ item.label }}
            <el-tag v-if="item.key === masterKey" type="danger" size="small" effect="dark">总开关</el-tag>
          </div>
          <div class="switch-desc">{{ item.description }}</div>
          <div class="switch-meta">
            <el-tag :type="item.enabled ? 'success' : 'info'" size="small">
              {{ item.enabled ? '当前：发送' : '当前：已停止' }}
            </el-tag>
            <span class="meta-text">定时任务：{{ scheduleText(item.key) }}</span>
          </div>
        </div>
        <el-switch
          :model-value="item.value"
          :disabled="item.key !== masterKey && !masterEnabled"
          :loading="savingKey === item.key"
          size="large"
          @change="(v) => onToggle(item, v)" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMailSwitches, updateMailSwitch } from '@/api/admin'

const masterKey = 'mail.schedule.enabled'
const switches = ref([])
const loading = ref(false)
const savingKey = ref('')

const masterEnabled = computed(() => {
  const m = switches.value.find(s => s.key === masterKey)
  return m ? !!m.value : true
})

function scheduleText(key) {
  if (key === masterKey) return '控制下方所有定时邮件'
  if (key === 'mail.schedule.report.enabled') return '每日 08:00'
  if (key === 'mail.schedule.approval.enabled') return '每日 09:00'
  return ''
}

async function load() {
  loading.value = true
  try {
    const res = await getMailSwitches()
    switches.value = (res.data || []).map(s => ({ ...s, value: !!s.value }))
  } finally {
    loading.value = false
  }
}

async function onToggle(item, value) {
  if (item.key === masterKey && !value) {
    try {
      await ElMessageBox.confirm(
        '关闭总开关后，系统将停止所有定时自动邮件（报表订阅、审批提醒）。确定关闭？',
        '关闭总开关确认', { type: 'warning', confirmButtonText: '确定关闭', cancelButtonText: '取消' })
    } catch {
      return
    }
  }
  savingKey.value = item.key
  try {
    const res = await updateMailSwitch(item.key, value)
    switches.value = (res.data || []).map(s => ({ ...s, value: !!s.value }))
    ElMessage.success(`「${item.label}」已${value ? '开启' : '关闭'}`)
  } catch (e) {
    ElMessage.error('保存失败，请稍后重试')
  } finally {
    savingKey.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.page { max-width: 820px; }
.tip { margin-bottom: 16px; }
.card-title { font-weight: 600; }
.switch-row {
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px; padding: 18px 4px; border-bottom: 1px solid var(--el-border-color-lighter);
}
.switch-row:last-child { border-bottom: none; }
.switch-row.master { background: var(--el-color-danger-light-9); border-radius: 6px; padding-left: 12px; padding-right: 12px; margin-bottom: 8px; }
.switch-info { flex: 1; }
.switch-label { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); display: flex; align-items: center; gap: 8px; }
.switch-desc { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 6px; line-height: 1.5; }
.switch-meta { display: flex; align-items: center; gap: 12px; margin-top: 8px; }
.meta-text { font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
