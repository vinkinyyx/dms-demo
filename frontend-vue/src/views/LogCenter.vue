<template>
  <div class="log-center">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="操作日志" name="operation">
          <OperationLogTab v-if="loaded.operation" :jump-detail="openBizHistory" />
        </el-tab-pane>
        <el-tab-pane label="登录日志" name="login">
          <LoginLogTab v-if="loaded.login" />
        </el-tab-pane>
        <el-tab-pane label="接口调用日志" name="api">
          <ApiLogTab v-if="loaded.api" />
        </el-tab-pane>
        <el-tab-pane label="邮件发送日志" name="email">
          <EmailLogTab v-if="loaded.email" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <BizOperationHistoryDrawer v-model="historyVisible" :resource-type="historyType" :resource-id="historyId" title="业务对象操作记录" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import OperationLogTab from './log-center/OperationLogTab.vue'
import LoginLogTab from './log-center/LoginLogTab.vue'
import ApiLogTab from './log-center/ApiLogTab.vue'
import EmailLogTab from './log-center/EmailLogTab.vue'
import BizOperationHistoryDrawer from '@/components/BizOperationHistoryDrawer.vue'

const activeTab = ref('operation')
const loaded = reactive({ operation: true, login: false, api: false, email: false })
onMounted(() => { /* operation loads by default */ })
function onTabChange(name) { if (!loaded[name]) loaded[name] = true }

const historyVisible = ref(false)
const historyType = ref('')
const historyId = ref('')
function openBizHistory(bizType, bizId) {
  historyType.value = bizType
  historyId.value = bizId
  historyVisible.value = true
}
</script>
