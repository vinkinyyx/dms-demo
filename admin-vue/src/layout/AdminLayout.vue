<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">DMS 平台后台</div>
      <el-menu :default-active="route.path" router background-color="#001529" text-color="#cfd8dc" active-text-color="#409eff">
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
          <el-menu-item index="/dicts">全局字典</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="logs">
          <template #title><el-icon><Document /></el-icon><span>日志中心</span></template>
          <el-menu-item index="/logs/api">接口日志</el-menu-item>
          <el-menu-item index="/logs/audits">审计日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title || '' }}</span>
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
      <el-main><router-view /></el-main>
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

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const pwdVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

onMounted(() => { if (!auth.user) auth.fetchMe() })

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
.layout { height: 100vh; }
.aside { background: #001529; overflow-y: auto; }
.logo { color: #fff; font-size: 18px; font-weight: 600; line-height: 60px; text-align: center; }
.aside :deep(.el-menu) { border-right: none; }
.header { display: flex; justify-content: space-between; align-items: center; background: #fff; border-bottom: 1px solid #eee; }
.title { font-size: 16px; font-weight: 600; }
.user { cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
</style>