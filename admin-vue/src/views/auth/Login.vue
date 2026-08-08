<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">DMS 平台后台</h2>
      <el-form :model="form" @keyup.enter="onSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" prefix-icon="Lock" size="large" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="onSubmit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: '' })
const loading = ref(false)

async function onSubmit() {
  if (!form.username || !form.password) { ElMessage.warning('请输入账号和密码'); return }
  loading.value = true
  try {
    await auth.login(form)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // request interceptor already surfaced error
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1e3c72, #2a5298); }
.login-card { width: 380px; }
.title { text-align: center; margin: 0 0 24px; color: #303133; }
</style>