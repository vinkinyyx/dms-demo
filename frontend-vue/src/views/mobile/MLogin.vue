<template>
  <div class="m-login">
    <div class="lg-theme">
      <button v-for="item in themePresets" :key="item.key" type="button" class="lg-theme-dot"
        :class="{ active: item.key === currentPreset.key }" :style="{ '--dot': item.color }"
        @click="setThemePreset(item.key)" />
      <button type="button" class="lg-mode" @click="toggleThemeMode">
        {{ themeMode === 'dark' ? '☀️' : '🌙' }}
      </button>
    </div>

    <div class="lg-top">
      <div class="brand-logo"><DmsLogo :size="88" /></div>
      <div class="lg-name">MySolMed DMS</div>
      <div class="lg-sub">经销商移动工作台 · 下单 / 审批 / 报台 / 收货</div>
    </div>

    <van-form @submit="onSubmit" class="lg-card">
      <van-field v-model="form.tenantCode" label="租户" placeholder="租户代码">
        <template #left-icon><van-icon name="shop-o" class="lg-field-ic" /></template>
      </van-field>
      <van-field v-model="form.username" label="账号" placeholder="请输入账号" :rules="[{ required: true, message: '请输入账号' }]">
        <template #left-icon><van-icon name="user-o" class="lg-field-ic" /></template>
      </van-field>
      <van-field v-model="form.password" type="password" label="密码" placeholder="请输入密码" :rules="[{ required: true, message: '请输入密码' }]">
        <template #left-icon><van-icon name="lock" class="lg-field-ic" /></template>
      </van-field>
      <div class="lg-row">
        <span class="ck on">✓</span>记住我
        <button type="button" class="fr fr-btn" @click="showToast('请联系管理员重置密码')">忘记密码？</button>
      </div>
      <van-button round block type="primary" native-type="submit" :loading="loading" class="lg-btn">登 录</van-button>
    </van-form>

    <div class="lg-foot">还没有账号？经销商准入请<router-link to="/mobile/register" class="lg-reg"> 自助注册 ›</router-link></div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/store/user'
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleMode as applyThemeMode, initTheme } from '@/config/theme-runtime'
import DmsLogo from '@/components/DmsLogo.vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
initTheme()
const themeMode = ref(document.documentElement.dataset.mode || 'light')
const remembered = JSON.parse(localStorage.getItem('dms_mobile_remember') || '{}')
const form = reactive({
  tenantCode: remembered.tenantCode || 'default',
  username: remembered.username || 'sys_admin',
  password: remembered.password || 'Dms@123456'
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
  background: linear-gradient(180deg, #EAF3FE 0%, #F4F7FB 42%);
  display: flex;
  flex-direction: column;
  padding: 0 26px 32px;
  color: var(--dms-m-navy, #2e6ba8);
}
.lg-theme {
  position: absolute; right: 14px; top: 14px; display: flex; gap: 6px; align-items: center;
  padding: 4px; border-radius: 8px; background: rgba(255,255,255,.6);
}
.lg-theme-dot { width: 20px; height: 20px; border: 1px solid rgba(46,107,168,.2); border-radius: 50%; background: var(--dot); }
.lg-theme-dot.active { box-shadow: 0 0 0 2px rgba(46,107,168,.4); }
.lg-mode { width: 30px; height: 26px; border: 1px solid rgba(46,107,168,.2); border-radius: 6px; background: #fff; }
.lg-top { margin-top: 58px; text-align: center; }
.brand-logo {
  width: 96px; height: 96px; margin: 0 auto 14px; display: flex; align-items: center; justify-content: center;
  filter: drop-shadow(0 4px 12px rgba(46,107,168,.25));
}
.lg-name { font-size: 22px; font-weight: 800; color: var(--dms-m-navy, #2e6ba8); letter-spacing: .5px; }
.lg-sub { font-size: 12px; color: #74839a; margin-top: 8px; }
.lg-card {
  margin-top: 26px; background: #fff; border-radius: 18px; padding: 8px 16px 18px;
  box-shadow: 0 10px 30px rgba(46,107,168,.12);
}
.lg-card :deep(.van-cell) { padding: 14px 0; background: transparent; }
.lg-card :deep(.van-cell::after) { left: 0; right: 0; }
.lg-card :deep(.van-field__label) { color: var(--dms-m-navy, #2e6ba8); font-weight: 600; width: 52px; }
.lg-field-ic { color: var(--dms-m-navy, #2e6ba8); font-size: 16px; margin-right: 6px; }
.lg-row { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #74839a; margin: 10px 0 16px; }
.lg-row .ck { width: 16px; height: 16px; border-radius: 4px; background: var(--dms-m-navy, #2e6ba8); color: #fff; font-size: 11px; display: grid; place-items: center; }
.lg-row .fr { margin-left: auto; color: var(--dms-m-navy, #2e6ba8); }
.lg-row .fr-btn { background:none; border:0; padding:0; font:inherit; cursor:pointer; }
.lg-btn { height: 46px; font-size: 16px; font-weight: 800; border-radius: 12px; }
.lg-foot { margin-top: 22px; text-align: center; font-size: 12px; color: #74839a; }
.lg-reg { color: var(--dms-m-navy, #2e6ba8); font-weight: 700; text-decoration: none; }
:global(html[data-mode='dark']) .m-login { background: linear-gradient(180deg, #10233c 0%, #0b1220 42%); color: #c7d3e2; }
:global(html[data-mode='dark']) .lg-card { background: #111827; }
:global(html[data-mode='dark']) .lg-name, :global(html[data-mode='dark']) .lg-card :deep(.van-field__label) { color: #c7d3e2; }
</style>
