<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '230px'" class="sidebar">
      <div class="logo" @click="$router.push('/home')">
        <span class="logo-icon">DMS</span>
        <span v-show="!collapsed" class="logo-text">经销商管理</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu :default-active="activeKey" :collapse="collapsed" router unique-opened
          background-color="#001529" text-color="#c8d3e0" active-text-color="#fff">
          <el-menu-item index="/home">
            <el-icon><HomeFilled /></el-icon>
            <template #title>工作台首页</template>
          </el-menu-item>
          <el-sub-menu v-for="g in menuGroups" :key="g.group" :index="g.group">
            <template #title>
              <el-icon><Menu /></el-icon>
              <span>{{ g.group }}</span>
            </template>
            <el-menu-item v-for="it in g.items" :key="it.key" :index="menuIndex(it)">
              <el-icon><component :is="it.icon" /></el-icon>
              <template #title>{{ it.label }}</template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <el-icon class="collapse-btn" @click="collapsed = !collapsed">
          <Fold v-if="!collapsed" /><Expand v-else />
        </el-icon>
        <span class="page-title">{{ currentTitle }}</span>
        <div class="spacer" />
        <el-dropdown @command="onCommand">
          <span class="user-info">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ userTypeLabel }}</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="10">
            <component :is="Component" :key="$route.fullPath" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { MENU_GROUPS } from '@/config/menu'
import { useUserStore } from '@/store/user'
import { ensurePermissions } from '@/directives/has'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const permissionsLoaded = ref(false)

onMounted(async () => {
  await ensurePermissions()
  permissionsLoaded.value = true
})

// === D13: 按用户权限过滤菜单（菜单本身的 permissionCode 控制可见性） ===
import { getPermissions } from '@/utils/auth'
function permSet() {
  const out = new Set()
  try {
    if (Array.isArray(userStore.permissions)) userStore.permissions.forEach((p) => out.add(p))
    const u = userStore.user || {}
    if (Array.isArray(u.permissions)) u.permissions.forEach((p) => out.add(p))
    if (Array.isArray(u.roles)) u.roles.forEach((p) => out.add(p))
  } catch { /* store 未就绪 */ }
  for (const p of getPermissions()) out.add(p)
  return out
}
function menuVisible(item) {
  if (!item.permissionCode) return true // 未配 permissionCode 的菜单默认可见（向后兼容）
  return permSet().has(item.permissionCode)
}

const menuGroups = computed(() => {
  const all = MENU_GROUPS.filter((g) => !g.manufacturerOnly || userStore.userType === 'vendor')
  return all
    .map((g) => ({ ...g, items: (g.items || []).filter((it) => menuVisible(it)) }))
    .filter((g) => (g.items || []).length > 0)
})

const activeKey = computed(() => route.path)
const userTypeLabel = computed(() => (userStore.userType === 'vendor' ? '厂商用户' : userStore.userType === 'dealer' ? '经销商用户' : '用户'))

function menuIndex(it) { return it.route || '/m/' + it.key }

const currentTitle = computed(() => {
  if (route.path === '/home') return '工作台首页'
  const key = route.params.key
  for (const g of MENU_GROUPS) {
    const it = g.items.find((i) => i.key === key)
    if (it) return it.label
  }
  return 'DMS'
})

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
      .then(async () => {
        await userStore.logout()
        router.replace('/login')
      })
      .catch(() => {})
  }
}
</script>

<style scoped lang="scss">
.layout { height: 100vh; }
.sidebar { background: #001529; transition: width .2s; overflow: hidden; }
.logo {
  height: 50px; display: flex; align-items: center; gap: 10px; padding: 0 18px;
  color: #fff; cursor: pointer; background: #000c1c;
}
.logo-icon { font-size: 22px; font-weight: 700; letter-spacing: 1px; }
.logo-text { font-size: 15px; white-space: nowrap; font-weight: 600; }
.menu-scroll { height: calc(100vh - 50px); }
.el-menu { border-right: none; }
.topbar {
  display: flex; align-items: center; gap: 14px; background: #fff;
  border-bottom: 1px solid #e4e7ed; padding: 0 20px; height: 56px;
}
.collapse-btn { font-size: 20px; cursor: pointer; color: #666; }
.page-title { font-size: 16px; font-weight: 600; }
.spacer { flex: 1; }
.user-info { display: flex; align-items: center; gap: 6px; cursor: pointer; color: #333; outline: none; }
.main { background: #f5f7fa; padding: 16px; }
</style>

