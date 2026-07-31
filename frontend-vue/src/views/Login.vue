<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-brand">
        <div class="brand-logo">DMS</div>
        <div class="brand-name">通用经销商管理系统</div>
        <div class="brand-desc">医疗器械 / 快消 / 零售<br>经销商全生命周期管理平台</div>
        <ul class="brand-features">
          <li>合同管理与电子签章</li>
          <li>订单/库存/销售一体化</li>
          <li>促销引擎与返利自动化</li>
          <li>多租户 SaaS 架构</li>
          <li>后台审计与操作日志</li>
        </ul>
      </div>
      <div class="login-form">
        <div class="form-title">欢迎回来</div>
        <div class="form-subtitle">请登录您的账号</div>
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px;"
          title="演示：admin / Sh123456 · 租户：default" />
        <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @keyup.enter="onSubmit">
          <el-form-item label="租户代码" prop="tenantCode">
            <el-input v-model="form.tenantCode" placeholder="租户代码" />
          </el-form-item>
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="账号" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="密码" />
          </el-form-item>
          <el-checkbox v-model="form.rememberMe">记住我 7 天</el-checkbox>
          <el-button type="primary" :loading="loading" class="btn-login" @click="onSubmit">登 录</el-button>
        </el-form>
        <div class="footer">© 2026 DMS · Vue 版</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ tenantCode: 'default', username: 'admin', password: 'Sh123456', rememberMe: false })
const rules = {
  tenantCode: [{ required: true, message: '请输入租户代码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function onSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login({ ...form })
      ElMessage.success('登录成功')
      router.replace('/home')
    } catch (e) {
      // 错误提示已由拦截器处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #1E3A5F, #2C4B8E, #4568AE);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.login-container {
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / .1), 0 2px 4px -2px rgb(0 0 0 / .1);
}
.login-brand {
  flex: 1;
  background: linear-gradient(135deg, #2C4B8E, #1E3A5F);
  color: #fff;
  padding: 60px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.brand-logo { font-size: 42px; font-weight: 700; margin-bottom: 16px; }
.brand-name { font-size: 24px; margin-bottom: 12px; }
.brand-desc { font-size: 14px; opacity: .8; line-height: 1.8; }
.brand-features { margin-top: 40px; padding: 0; }
.brand-features li { list-style: none; padding: 8px 0; font-size: 14px; opacity: .9; }
.brand-features li::before {
  content: '✓'; display: inline-block; width: 20px; height: 20px;
  background: rgba(255, 255, 255, .2); border-radius: 50%; text-align: center;
  line-height: 20px; margin-right: 12px; font-size: 12px;
}
.login-form { flex: 1; padding: 60px 48px; display: flex; flex-direction: column; justify-content: center; }
.form-title { font-size: 28px; font-weight: 700; color: #333; margin-bottom: 8px; }
.form-subtitle { font-size: 14px; color: #999; margin-bottom: 24px; }
.btn-login { width: 100%; margin-top: 20px; letter-spacing: 4px; }
.footer { text-align: center; margin-top: 24px; font-size: 12px; color: #999; }
@media (max-width: 768px) {
  .login-brand { display: none; }
  .login-container { min-height: auto; }
}
</style>
