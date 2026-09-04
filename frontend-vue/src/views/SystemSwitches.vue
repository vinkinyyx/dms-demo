<template>
  <div class="system-switches">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">系统开关</span>
        <el-tag size="small" type="warning">仅系统管理员可操作</el-tag>
        <div style="flex:1" />
        <el-button @click="reload">刷新</el-button>
      </div>

      <el-alert
        title="此处配置影响全系统行为，请谨慎切换。业务管控开关仅对当前租户生效；定时邮件为全局配置。"
        type="info" :closable="false" show-icon style="margin-bottom:16px" />

      <div v-loading="loading">
        <template v-for="grp in groups" :key="grp.key">
          <div class="group-title">
            <el-icon><component :is="grp.icon" /></el-icon>
            <span>{{ grp.title }}</span>
          </div>
          <div class="switch-list">
            <div v-for="item in grp.items" :key="item.key" class="switch-item">
              <div class="switch-main">
                <div class="switch-label">
                  <span>{{ item.label }}</span>
                  <el-tag v-if="item.scope === 'global'" size="small" type="info" effect="plain">全局</el-tag>
                  <el-tag v-else size="small" type="success" effect="plain">本租户</el-tag>
                </div>
                <div class="switch-desc">{{ item.description }}</div>
              </div>
              <el-switch
                :model-value="!!item.enabled"
                :loading="savingKey === item.key"
                :disabled="isDisabled(item)"
                active-text="开启"
                inactive-text="关闭"
                inline-prompt
                @change="(val) => onToggle(item, val)" />
            </div>
          </div>
        </template>

        <StateView v-if="!loading && switches.length === 0" type="empty" text="暂无配置项" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import StateView from '@/components/StateView.vue'

const MAIL_MASTER = 'mail.schedule.enabled'

const loading = ref(false)
const savingKey = ref('')
const switches = ref([])

const groups = computed(() => [
  {
    key: 'tenant', title: '业务管控', icon: 'Lock',
    items: switches.value.filter(s => s.scope === 'tenant')
  },
  {
    key: 'global', title: '定时邮件', icon: 'Message',
    items: switches.value.filter(s => s.scope === 'global')
  }
].filter(g => g.items.length > 0))

function isDisabled(item) {
  if (savingKey.value) return true
  if (item.key !== MAIL_MASTER && item.scope === 'global') {
    const master = switches.value.find(s => s.key === MAIL_MASTER)
    if (master && !master.enabled) return true
  }
  return false
}

async function reload() {
  loading.value = true
  try {
    const res = await request({ url: '/api/system-switches', method: 'get' })
    switches.value = res?.data || []
  } catch (e) {
    ElMessage.error('加载失败: ' + (e?.response?.data?.message || e?.message || e))
  } finally { loading.value = false }
}

async function onToggle(item, val) {
  savingKey.value = item.key
  const previous = !!item.enabled
  item.enabled = val
  try {
    const res = await request({
      url: '/api/system-switches', method: 'post',
      data: { key: item.key, enabled: val }
    })
    if (res?.data) switches.value = res.data
    ElMessage.success('设置已更新')
  } catch (e) {
    item.enabled = previous
    ElMessage.error('设置失败: ' + (e?.response?.data?.message || e?.message || e))
  } finally { savingKey.value = '' }
}

onMounted(() => reload())
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.title { font-size: 16px; font-weight: 600; }
.group-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 14px; font-weight: 600; color: #303133;
  margin: 16px 0 8px; padding-bottom: 8px; border-bottom: 1px dashed #e4e7ed;
}
.switch-list { display: flex; flex-direction: column; }
.switch-item {
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px; padding: 12px 8px; border-bottom: 1px solid #f0f2f5;
}
.switch-item:last-child { border-bottom: none; }
.switch-main { flex: 1; min-width: 0; }
.switch-label { display: flex; align-items: center; gap: 8px; font-weight: 500; color: #303133; }
.switch-desc { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.5; }
</style>
