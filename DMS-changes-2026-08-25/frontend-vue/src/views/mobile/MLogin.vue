<template>
  <div class="m-login">
    <div class="m-login-top">
      <div class="m-logo"><DmsLogo :size="44" inverse /></div>
      <div class="m-sub">经销商管理 · 移动端</div>
      <div class="m-theme">
        <button v-for="item in themePresets" :key="item.key" type="button" class="m-theme-dot"
          :class="{ active: item.key === currentPreset.key }" :style="{ '--dot': item.color }"
          @click="setThemePreset(item.key)" />
        <button type="button" class="m-mode" @click="toggleThemeMode">
          {{ themeMode === 'dark' ? '☀️' : '🌙' }}
        </button>
      </div>
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
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleMode as applyThemeMode, initTheme } from '@/config/theme-runtime'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
initTheme()
const themeMode = ref(document.documentElement.dataset.mode || 'light')
const remembered = JSON.parse(localStorage.getItem('dms_mobile_remember') || '{}')
const form = reactive({
  tenantCode: remembered.tenantCode || 'default',
  username: remembered.username || '',
  password: ''
})
function toggleThemeMode(){ applyThemeMode(); themeMode.value = document.documentElement.dataset.mode || 'light' }

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
.m-login {
  min-height: 100vh;
  padding: 0 0 32px;
  background: #eef2f7;
}
.m-login-top {
  position: relative;
  text-align: center;
  padding: 72px 20px 58px;
  color: #fff;
  background: linear-gradient(135deg, #1f2d3d 0%, #34495e 100%);
  border-bottom: 3px solid var(--dms-color-primary);
}
.m-logo {
  width: 48px;
  height: 48px;
  margin: 0 auto 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 6px 16px rgba(0,0,0,.18));
}
.m-sub { color: #d7dee8; font-size: 15px; }
.m-theme {
  position: absolute;
  right: 12px;
  top: 12px;
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 4px;
  border-radius: 4px;
  background: rgba(255,255,255,.12);
  border: 1px solid rgba(255,255,255,.18);
}
.m-theme-dot {
  width: 22px;
  height: 22px;
  border: 1px solid rgba(255,255,255,.35);
  border-radius: 3px;
  background: var(--dot);
}
.m-theme-dot.active { box-shadow: 0 0 0 2px rgba(255,255,255,.55); }
.m-mode {
  width: 30px;
  height: 26px;
  border: 1px solid rgba(255,255,255,.25);
  border-radius: 3px;
  background: rgba(255,255,255,.12);
  color: #fff;
}
.m-form {
  position: relative;
  z-index: 1;
  width: calc(100% - 32px);
  margin: -24px auto 0;
  padding: 4px 0 0;
  background: #fff;
  border: 1px solid #dfe6f0;
  border-radius: 6px;
  box-shadow: 0 10px 28px rgba(15,33,60,.10);
}
.m-form :deep(.van-cell-group--inset) {
  margin: 0;
  border-radius: 0;
  box-shadow: none;
}
.m-form :deep(.van-cell) { padding: 14px 16px; }
.m-form :deep(.van-button--primary) {
  height: 44px;
  border: 0;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 600;
  background: var(--dms-color-primary);
  box-shadow: none;
}
:global(html[data-mode='dark']) .m-login { background: #0b1220; }
:global(html[data-mode='dark']) .m-form { background: #111827; border-color: #243044; box-shadow: none; }
</style>
