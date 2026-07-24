<template>
  <div class="m-login">
    <div class="m-login-top">
      <div class="m-logo">DMS</div>
      <div class="m-sub">经销商管理 · 移动端</div>
    </div>
    <van-form @submit="onSubmit" class="m-form">
      <van-cell-group inset>
        <van-field v-model="form.tenantCode" label="租户" placeholder="租户代码" />
        <van-field v-model="form.username" label="账号" placeholder="请输入账号" :rules="[{ required: true, message: '请输入账号' }]" />
        <van-field v-model="form.password" type="password" label="密码" placeholder="请输入密码" :rules="[{ required: true, message: '请输入密码' }]" />
      </van-cell-group>
      <div style="margin: 20px 16px;">
        <van-button round block type="primary" native-type="submit" :loading="loading">登 录</van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ tenantCode: 'default', username: 'admin', password: 'Sh123456' })

async function onSubmit() {
  loading.value = true
  try {
    await userStore.login({ ...form })
    showToast('登录成功')
    router.replace('/mobile/home')
  } catch (e) { /* 拦截器已提示 */ } finally { loading.value = false }
}
</script>

<style scoped>
.m-login { min-height: 100vh; background: linear-gradient(160deg, #2C4B8E, #1E3A5F); }
.m-login-top { text-align: center; padding: 80px 0 40px; color: #fff; }
.m-logo { font-size: 44px; font-weight: 700; }
.m-sub { margin-top: 10px; opacity: .85; }
.m-form { padding-top: 20px; }
</style>
