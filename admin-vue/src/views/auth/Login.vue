<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand-mark"><DmsLogo :size="34" /></div>
      <h2 class="title">DMS 平台后台</h2>
      <p class="subtitle">统一租户、权限、配置与审计中心</p>
      <div class="theme-dock">
        <button v-for="item in themePresets" :key="item.key" type="button" class="theme-dot"
          :class="{ active: item.key === currentPreset.key }" :title="item.name"
          :style="{ '--dot': item.color, background: item.gradients[0] }" @click="setThemePreset(item.key)">
          <span />
        </button>
        <button type="button" class="mode-toggle" @click="toggleThemeMode">
          <el-icon><Moon v-if="themeMode === 'light'" /><Sunny v-else /></el-icon>
        </button>
      </div>
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
import { Moon, Sunny } from '@element-plus/icons-vue'
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleMode as applyThemeMode, initTheme } from '../../config/theme-runtime'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: '' })
initTheme()
const loading = ref(false)
const themeMode = ref(document.documentElement.dataset.mode || 'light')
function toggleThemeMode(){ applyThemeMode(); themeMode.value = document.documentElement.dataset.mode || 'light' }

async function onSubmit() {
  if (!form.username || !form.password) { ElMessage.warning('请输入账号和密码'); return }
  loading.value = true
  try {
    auth.clear()
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
.login-wrap {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #eef2f7;
}
.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 18px 14px;
  border: 1px solid #d9e1ec;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 14px 36px rgba(15, 33, 60, .14);
}
.brand-mark {
  width: 44px;
  height: 44px;
  margin: 4px auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.title { text-align: center; margin: 0; color: #1f2d3d; font-size: 24px; font-weight: 700; }
.subtitle { margin: 8px 0 18px; text-align: center; color: #7a8699; font-size: 13px; }
.theme-dock { display: flex; justify-content: center; gap: 6px; margin-bottom: 20px; padding: 4px; border: 1px solid #e4e7ed; border-radius: 3px; background: #f5f7fa; width: fit-content; margin-left: auto; margin-right: auto; }
.theme-dot { width: 22px; height: 22px; border: 1px solid rgba(31,45,61,.12); border-radius: 2px; display: grid; place-items: center; cursor: pointer; }
.theme-dot span { width: 10px; height: 10px; border-radius: 2px; background: var(--dot); }
.theme-dot.active { box-shadow: 0 0 0 2px #fff, 0 0 0 3px var(--dot); }
.mode-toggle { width: 30px; height: 24px; border: 1px solid #dcdfe6; border-radius: 2px; background: #fff; color: #606266; cursor: pointer; }
:deep(.el-input__wrapper) { border-radius: 4px; box-shadow: 0 0 0 1px #dcdfe6 inset; }
:deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--dms-color-primary) inset; }
:deep(.el-button--primary) {
  height: 42px;
  border: 0;
  border-radius: 4px;
  font-weight: 600;
  letter-spacing: 4px;
  background: var(--dms-color-primary);
  box-shadow: none;
}
:global(html[data-mode='dark']) .login-wrap { background: #0b1220; }
:global(html[data-mode='dark']) .login-card { background: #111827; border-color: #243044; box-shadow: none; }
:global(html[data-mode='dark']) .title { color: #f8fafc; }
:global(html[data-mode='dark']) .theme-dock { background: #182235; border-color: #2b3b55; }
:global(html[data-mode='dark']) .mode-toggle { background: #182235; border-color: #2b3b55; color: #dbe4f0; }
</style>