<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo"><DmsLogo :size="30" class="logo-mark" inverse /><span class="logo-sub">平台后台</span></div>
      <el-menu :default-active="route.path" router background-color="var(--dms-sider-bg)" text-color="var(--dms-sider-text)" active-text-color="#ffffff">
        <el-menu-item index="/">
          <el-icon><DataLine /></el-icon><span>首页总览</span>
        </el-menu-item>
        <el-sub-menu index="tenant">
          <template #title><el-icon><OfficeBuilding /></el-icon><span>租户管理</span></template>
          <el-menu-item index="/tenants/manufacturers">厂家租户</el-menu-item>
          <el-menu-item index="/tenants/dealers">经销商租户</el-menu-item>
          <el-menu-item index="/tenant-admins">租户管理员</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="config">
          <template #title><el-icon><Setting /></el-icon><span>平台配置</span></template>
          <el-menu-item index="/role-templates">角色模板</el-menu-item>
          <el-menu-item index="/menus">平台菜单</el-menu-item>
          <el-menu-item index="/ui-configs">页面配置</el-menu-item>
          <el-menu-item index="/notify-settings">通知设置</el-menu-item>
          <el-menu-item index="/dicts">全局字典</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="logs">
          <template #title><el-icon><Document /></el-icon><span>日志中心</span></template>
          <el-menu-item index="/logs/api">接口日志</el-menu-item>
          <el-menu-item index="/logs/audits">审计日志</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/reports">
          <el-icon><PieChart /></el-icon><span>报表总览</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="title-wrap">
          <span class="title">{{ route.meta.title || '' }}</span>
          <div class="theme-tools">
            <button v-for="item in themePresets" :key="item.key" type="button" class="theme-chip"
              :class="{ active: currentPreset.key === item.key }" :title="item.name"
              :style="{ '--chip': item.color }" @click="setThemePreset(item.key)" />
            <el-button text circle :title="themeMode === 'dark' ? '切换浅色' : '切换深色'" @click="toggleThemeMode">
              <el-icon><Moon v-if="themeMode === 'light'" /><Sunny v-else /></el-icon>
            </el-button>
          </div>
        </div>
        <el-dropdown @command="onCommand">
          <span class="user"><el-icon><UserFilled /></el-icon> {{ auth.user?.name || auth.user?.username || '管理员' }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
  <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
    <el-form :model="pwdForm" label-width="90px">
      <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" @click="submitPwd">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { logout as logoutApi, changePassword } from '@/api/auth'
import { Moon, Sunny, DataLine, PieChart } from '@element-plus/icons-vue'
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleMode as applyThemeMode, initTheme } from '../config/theme-runtime'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const pwdVisible = ref(false)
initTheme()
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const themeMode = ref(document.documentElement.dataset.mode || 'light')
function toggleThemeMode(){ applyThemeMode(); themeMode.value = document.documentElement.dataset.mode || 'light' }

onMounted(() => { if (!auth.user && auth.hasValidToken && auth.hasValidToken()) auth.fetchMe() })

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' }).then(() => {
      logoutApi().catch(() => {}).finally(() => { auth.clear(); router.push('/login') })
    })
  } else if (cmd === 'password') {
    pwdForm.oldPassword = ''; pwdForm.newPassword = ''; pwdVisible.value = true
  }
}
async function submitPwd() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) { ElMessage.warning('请填写完整'); return }
  await changePassword(pwdForm)
  ElMessage.success('密码已修改')
  pwdVisible.value = false
}
</script>

<style scoped>
.layout { height: 100vh; background: var(--dms-bg-page); }
.aside {
  background: linear-gradient(180deg,#243447 0%,#1f2d3d 100%);
  border-right: 1px solid #1a2533;
  overflow-y: auto;
  box-shadow: none;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
  background: #1b2736;
  border-bottom: 1px solid rgba(255,255,255,.06);
  font-weight: 700;
}
.logo-mark {
  min-width: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.logo-sub { font-size: 15px; color: #e5eaf1; }
.aside :deep(.el-menu) { border-right: none; background: transparent; padding: 8px 6px; }
.aside :deep(.el-menu-item), .aside :deep(.el-sub-menu__title) {
  height: 42px;
  margin: 2px 0;
  border-radius: 3px;
  color: #b8c4d3;
  font-weight: 500;
}
.aside :deep(.el-menu-item:hover), .aside :deep(.el-sub-menu__title:hover) { background: rgba(255,255,255,.08); color: #fff; }
.aside :deep(.el-menu-item.is-active) { color: #fff; background: var(--dms-color-primary); box-shadow: none; }
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--dms-border-2);
  box-shadow: none;
  z-index: var(--dms-z-index-sticky);
}
.title-wrap { display: flex; align-items: center; gap: 16px; }
.title { font-size: 16px; font-weight: 600; color: #1f2d3d; }
.theme-tools { display: flex; align-items: center; gap: 5px; padding: 3px; border: 1px solid #dcdfe6; border-radius: 3px; background: #f5f7fa; }
.theme-chip { width: 18px; height: 18px; border: 1px solid rgba(31,45,61,.15); border-radius: 2px; background: var(--chip); cursor: pointer; }
.theme-chip.active { box-shadow: 0 0 0 1px #fff, 0 0 0 2px var(--chip); }
.user { cursor: pointer; display: inline-flex; align-items: center; gap: 6px; color: #606266; }
.user:hover { color: var(--dms-color-primary); }
.main { background: var(--dms-bg-page); padding: var(--dms-spacing-4); }
:global(html[data-mode='dark']) .header { background: #111827; border-color: #243044; }
:global(html[data-mode='dark']) .title { color: #f8fafc; }
</style>