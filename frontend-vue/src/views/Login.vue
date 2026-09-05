<template>
  <div class="login-wrap">
    <div class="login-hero">
      <div class="blob b1"></div>
      <div class="blob b2"></div>
      <div class="hero-inner">
        <div class="hero-brand">
          <DmsLogo :size="44" variant="light" class="hero-logo" />
          <span class="hero-brand-name">MySolMed DMS</span>
        </div>
        <h1>面向医疗器械经销商的<br>一体化业务管理平台</h1>
        <p>覆盖基础数据、合同授权、订单审批、库存追溯、手术报台与营销代金券全链路，让高值耗材流通合规、可追溯、更高效。</p>
        <div class="hero-feats">
          <div class="feat">订单 · 审批 · 出库闭环</div>
          <div class="feat">批号 / 序列号全程追溯</div>
          <div class="feat">手术植入报台管理</div>
          <div class="feat">经营数据实时看板</div>
        </div>
      </div>
    </div>

    <div class="login-panel">
      <div class="theme-dock" aria-label="主题切换">
        <button v-for="item in themePresets" :key="item.key" type="button" class="theme-dot"
          :class="{ active: item.key === currentPreset.key }"
          :style="{ background: item.gradients[0], '--dot': item.color }"
          :title="item.name" @click="setThemePreset(item.key)">
          <span class="theme-dot-core" />
        </button>
        <button type="button" class="mode-toggle" title="深浅模式" @click="toggleThemeMode">
          <el-icon><Moon v-if="themeMode === 'light'" /><Sunny v-else /></el-icon>
        </button>
      </div>

      <div class="login-box">
        <div class="box-title">欢迎登录</div>
        <div class="box-sub">MySolMed 经销商管理系统（DMS）</div>

        <div class="demo-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>演示：sys_admin / Dms@123456 · 租户 default</span>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" class="login-form" label-position="top" @keyup.enter="onSubmit">
          <el-form-item label="租户代码" prop="tenantCode">
            <el-input v-model="form.tenantCode" placeholder="请输入租户代码" size="large">
              <template #prefix><el-icon><OfficeBuilding /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名 / 手机号" size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" size="large">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <div class="form-row">
            <el-checkbox v-model="form.rememberMe">记住我 7 天</el-checkbox>
            <el-link type="primary" :underline="false" class="forget-link">忘记密码？</el-link>
          </div>

          <el-button type="primary" :loading="loading" class="btn-login" size="large" @click="onSubmit">登 录</el-button>
        </el-form>

        <div class="box-foot">
          演示账号：sys_admin / Dms@123456 &nbsp;|&nbsp; 平台后台 admin / Sh123456<br>
          登录即代表同意《服务协议》与《隐私政策》
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Moon, Sunny, User, Lock, OfficeBuilding, InfoFilled } from '@element-plus/icons-vue'
import DmsLogo from '@/components/DmsLogo.vue'
import { useUserStore } from '@/store/user'
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleMode as applyThemeMode, initTheme } from '@/config/theme-runtime'

initTheme()
const themeMode = ref(document.documentElement.dataset.mode || 'light')
function toggleThemeMode() { applyThemeMode(); themeMode.value = document.documentElement.dataset.mode || 'light' }

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ tenantCode: 'default', username: 'sys_admin', password: 'Dms@123456', rememberMe: true })
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
.login-wrap {
  min-height: 100vh;
  display: flex;
  background: #fff;
}

/* ---------- 左侧品牌主视觉（藏青渐变，对齐移动端/原型） ---------- */
.login-hero {
  flex: 1.12;
  position: relative;
  overflow: hidden;
  color: #fff;
  padding: 56px 60px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(150deg, #1b4470 0%, #245a8f 42%, #2e6ba8 72%, #5a95d0 100%);
}
.blob { position: absolute; border-radius: 50%; background: rgba(255,255,255,.08); pointer-events: none; }
.blob.b1 { width: 440px; height: 440px; top: -130px; right: -110px; }
.blob.b2 { width: 320px; height: 320px; bottom: -100px; left: -80px; background: rgba(255,255,255,.06); }
.hero-inner { position: relative; z-index: 2; display: flex; flex-direction: column; height: 100%; }
.hero-brand { display: flex; align-items: center; gap: 14px; }
.hero-logo { filter: drop-shadow(0 4px 12px rgba(0,0,0,.18)); }
.hero-brand-name { font-size: 22px; font-weight: 700; letter-spacing: .3px; }
.login-hero h1 {
  margin-top: auto;
  color: #fff;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 1px;
}
.login-hero p {
  margin-top: 18px;
  max-width: 460px;
  font-size: 15px;
  line-height: 1.9;
  color: rgba(255,255,255,.92);
}
.hero-feats { margin-top: 36px; display: flex; flex-wrap: wrap; gap: 14px 30px; }
.feat {
  position: relative;
  display: flex;
  align-items: center;
  font-size: 14px;
  color: rgba(255,255,255,.96);
  padding-left: 24px;
}
.feat::before {
  content: '✓';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(255,255,255,.18);
  font-size: 11px;
  font-weight: 700;
}

/* ---------- 右侧登录表单 ---------- */
.login-panel {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: #fff;
}
.login-box { width: 380px; max-width: 100%; }
.box-title { font-size: 26px; font-weight: 700; color: #1f2d3d; }
.box-sub { margin: 10px 0 22px; font-size: 14px; color: #909399; }
.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fef3c7;
  color: #b45309;
  border-radius: 6px;
  padding: 9px 12px;
  font-size: 13px;
  margin-bottom: 18px;
}
.demo-tip .el-icon { font-size: 16px; flex: 0 0 auto; }
.login-form { margin-top: 4px; }
:deep(.el-form-item__label) { color: #303133; font-weight: 500; padding-bottom: 4px; }
:deep(.el-input__wrapper) { border-radius: 6px; }
:deep(.el-input--large .el-input__wrapper) { padding: 4px 13px; }
.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 18px;
}
.forget-link { font-size: 13px; }
.btn-login {
  width: 100%;
  height: 46px;
  border: 0;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 6px;
  background: var(--dms-color-primary, #2e6ba8);
}
.btn-login:hover { background: var(--dms-color-primary-hover, #5a95d0); }
.box-foot { margin-top: 24px; text-align: center; font-size: 12px; color: #909399; line-height: 1.9; }

/* ---------- 主题切换 dock ---------- */
.theme-dock {
  position: absolute;
  top: 22px;
  right: 26px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px;
  border: 1px solid #e4e7ed;
  border-radius: 5px;
  background: #f7f9fc;
}
.theme-dot {
  width: 22px;
  height: 22px;
  border: 1px solid rgba(31,45,61,.12);
  border-radius: 4px;
  cursor: pointer;
  transition: box-shadow .15s ease, transform .15s ease;
}
.theme-dot:hover { transform: translateY(-1px); }
.theme-dot.active { box-shadow: 0 0 0 2px #fff, 0 0 0 3px var(--dot, #2e6ba8); }
.theme-dot-core { display: none; }
.mode-toggle {
  width: 30px;
  height: 26px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  color: #606266;
  background: #fff;
  display: grid;
  place-items: center;
}

/* ---------- 深色模式 / 响应式 ---------- */
:global(html[data-mode='dark']) .login-panel { background: #111827; }
:global(html[data-mode='dark']) .box-title { color: #f8fafc; }
:global(html[data-mode='dark']) .theme-dock { background: #182235; border-color: #2b3b55; }
:global(html[data-mode='dark']) .mode-toggle { background: #182235; border-color: #2b3b55; color: #dbe4f0; }
@media (max-width: 900px) {
  .login-hero { display: none; }
  .login-wrap { background: linear-gradient(180deg, #eaf3fe 0%, #f4f7fb 46%); }
  .login-panel { background: transparent; }
  .login-box {
    background: #fff;
    border-radius: 12px;
    padding: 32px 28px;
    box-shadow: 0 12px 32px rgba(36,90,143,.12);
  }
}
</style>
