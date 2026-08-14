<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-brand">
        <div class="brand-logo"><DmsLogo :size="48" inverse /></div>
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
        <div class="theme-dock" aria-label="主题切换">
          <button v-for="item in themePresets" :key="item.key" type="button" class="theme-dot"
            :class="{ active: item.key === currentPreset.key }"
            :style="{ '--dot': item.color, background: item.gradients[0] }"
            @click="setThemePreset(item.key)">
            <span class="theme-dot-core" />
          </button>
          <button type="button" class="mode-toggle" @click="toggleThemeMode">
            <el-icon><Moon v-if="themeMode === 'light'" /><Sunny v-else /></el-icon>
          </button>
        </div>
        <div class="form-title">欢迎回来</div>
        <div class="form-subtitle">请登录您的账号</div>
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px;"
          title="演示：sys_admin / Dms@123456 · 租户：default" />
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
import { Moon, Sunny } from '@element-plus/icons-vue'
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleMode as applyThemeMode, initTheme } from '@/config/theme-runtime'
initThemeModeRef()
const themeMode = ref(document.documentElement.dataset.mode || 'light')

const router = useRouter()
function initThemeModeRef(){ initTheme() }
function toggleThemeMode(){ applyThemeMode(); themeMode.value = document.documentElement.dataset.mode || 'light' }
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ tenantCode: 'default', username: 'sys_admin', password: 'Dms@123456', rememberMe: false })
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
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: #eef2f7;
}
.login-container {
  display: grid;
  grid-template-columns: 1.02fr .98fr;
  width: min(980px, 100%);
  min-height: 580px;
  background: #fff;
  border: 1px solid #d9e1ec;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 16px 40px rgba(15, 33, 60, .14);
}
.login-brand {
  position: relative;
  padding: 58px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #1f2d3d 0%, #34495e 100%);
  border-right: 3px solid var(--dms-color-primary);
}
.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  filter: drop-shadow(0 4px 12px rgba(0,0,0,.18));
}
.brand-name { font-size: 28px; font-weight: 700; margin-bottom: 14px; letter-spacing: .5px; }
.brand-desc { font-size: 14px; line-height: 1.9; color: #d7dee8; }
.brand-features { margin: 34px 0 0; padding: 0; display: grid; gap: 10px; }
.brand-features li {
  list-style: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  font-size: 14px;
  color: #c8d3e0;
}
.brand-features li::before {
  content: '✓';
  width: 20px;
  height: 20px;
  border-radius: 2px;
  display: inline-grid;
  place-items: center;
  background: rgba(255,255,255,.12);
  color: #fff;
  font-size: 12px;
}
.login-form {
  position: relative;
  padding: 58px 52px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: #fff;
}
.theme-dock {
  position: absolute;
  top: 20px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f5f7fa;
}
.theme-dot {
  width: 22px;
  height: 22px;
  border: 1px solid rgba(31,45,61,.12);
  border-radius: 3px;
  cursor: pointer;
  transition: box-shadow .15s ease, transform .15s ease;
}
.theme-dot:hover { transform: translateY(-1px); }
.theme-dot.active { box-shadow: 0 0 0 2px #fff, 0 0 0 3px var(--dot); }
.theme-dot-core { display: none; }
.mode-toggle {
  width: 30px;
  height: 24px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  cursor: pointer;
  color: #606266;
  background: #fff;
  display: grid;
  place-items: center;
}
.form-title { font-size: 28px; font-weight: 700; color: #1f2d3d; margin-bottom: 8px; }
.form-subtitle { color: #7a8699; margin-bottom: 26px; }
:deep(.el-form-item__label) { color: #303133; font-weight: 500; }
:deep(.el-input__wrapper) { border-radius: 4px; box-shadow: 0 0 0 1px #dcdfe6 inset; }
:deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--dms-color-primary) inset; }
.btn-login {
  width: 100%;
  height: 44px;
  margin-top: 14px;
  border: 0;
  border-radius: 4px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  background: var(--dms-color-primary);
  box-shadow: none;
}
.btn-login:hover { background: var(--dms-color-primary-hover); }
.footer { text-align: center; margin-top: 24px; font-size: 12px; color: #909399; }
:global(html[data-mode='dark']) .login-page { background: #0b1220; }
:global(html[data-mode='dark']) .login-container { background: #111827; border-color: #243044; box-shadow: none; }
:global(html[data-mode='dark']) .login-form { background: #111827; }
:global(html[data-mode='dark']) .form-title { color: #f8fafc; }
:global(html[data-mode='dark']) .theme-dock { background: #182235; border-color: #2b3b55; }
:global(html[data-mode='dark']) .mode-toggle { background: #182235; border-color: #2b3b55; color: #dbe4f0; }
@media (max-width: 820px) {
  .login-page { padding: 16px; }
  .login-container { grid-template-columns: 1fr; min-height: auto; border-radius: 4px; }
  .login-brand { display: none; }
  .login-form { padding: 42px 26px; }
}
</style>
