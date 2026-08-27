<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '230px'" class="sidebar">
      <div class="logo" @click="$router.push('/home')">
        <DmsLogo :size="30" class="logo-icon" inverse />
        <span v-show="!collapsed" class="logo-text">经销商管理</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu :default-active="activeKey" :collapse="collapsed" router unique-opened
          background-color="var(--dms-sider-bg)" text-color="var(--dms-sider-text)" active-text-color="var(--dms-sider-text-active)">
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
        <el-icon class="collapse-btn" @click="toggleSidebar">
          <Fold v-if="!collapsed" /><Expand v-else />
        </el-icon>
        <span class="page-title">{{ currentTitle }}</span>
        <div class="spacer" />
        <el-button text circle title="命令面板 (Ctrl/Cmd+K)" @click="commandOpen = true">
          <el-icon><Search /></el-icon>
        </el-button>
        <div class="theme-tools">
          <button v-for="item in themePresets" :key="item.key" type="button" class="theme-chip"
            :class="{ active: currentPreset.key === item.key }" :title="item.name"
            :style="{ '--chip': item.color }" @click="setThemePreset(item.key)" />
          <el-button text circle :title="themeMode === 'dark' ? '切换浅色' : '切换深色'" @click="toggleThemeMode">
            <el-icon><Moon v-if="themeMode === 'light'" /><Sunny v-else /></el-icon>
          </el-button>
        </div>
        <el-badge :value="unread" :hidden="!unread" class="bell-badge" @click="goNotifications">
          <el-icon class="bell-icon"><Bell /></el-icon>
        </el-badge>
        <el-dropdown @command="onCommand">
          <span class="user-info">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ userTypeLabel }}</el-dropdown-item>
              <el-dropdown-item command="profile">个人设置</el-dropdown-item>
              <el-dropdown-item command="notifications">消息中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component, route: viewRoute }">
          <keep-alive :max="10" v-if="!viewRoute.meta.noCache">
            <component :is="Component" :key="viewRoute.fullPath" />
          </keep-alive>
          <component :is="Component" v-else :key="viewRoute.fullPath" />
        </router-view>
      </el-main>
    </el-container>
    <el-dialog v-model="commandOpen" title="快速跳转" width="520px" append-to-body>
      <el-autocomplete
        v-model="commandQuery"
        :fetch-suggestions="queryCommands"
        placeholder="搜索页面，例如：订单、库存、报表"
        style="width: 100%"
        @select="selectCommand"
        @keyup.enter="selectFirstCommand"
      />
      <div class="command-hint">回车打开第一项；支持快速进入审批、库存、报表、个人资料。</div>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { MENU_GROUPS } from '@/config/menu'
import { unreadCount } from '@/api/notification'
import request from '@/utils/request'
import { Bell, Moon, Sunny, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { ensurePermissions } from '@/directives/has'
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleMode as applyThemeMode, initTheme } from '@/config/theme-runtime'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(localStorage.getItem('dms:sidebar:collapsed') === '1')
const commandOpen = ref(false)
const commandQuery = ref('')
initTheme()
const unread = ref(0)
const themeMode = ref(document.documentElement.dataset.mode || 'light')
function toggleThemeMode(){ applyThemeMode(); themeMode.value = document.documentElement.dataset.mode || 'light' }
function toggleSidebar(){ collapsed.value = !collapsed.value; persistSidebar(); applySidebarVar() }
function applySidebarVar(){ document.documentElement.style.setProperty('--dms-sidebar-w', collapsed.value ? '64px' : '230px'); window.dispatchEvent(new Event('sidebar-toggle')) }
async function loadUnread(){ try { const r=await unreadCount(); unread.value=Number(r.data?.count||0) } catch(e){ unread.value=0 } }
function goNotifications(){ router.push('/notifications') }
setInterval(loadUnread, 60000)
const permissionsLoaded = ref(false)
const features = ref({ inventoryEnabled: true, purchaseEnabled: true })

onMounted(async () => { applySidebarVar();
  collapsed.value = localStorage.getItem('dms:sidebar:collapsed') === '1'
  await ensurePermissions()
  await loadUnread()
  try { const r = await request.get('/api/tenant/features'); if (r.data) Object.assign(features.value, r.data) } catch(e) {}
  permissionsLoaded.value = true
})

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
  // v4.4.0：进销存开关仅约束厂家用户；经销商用户（userType=dealer）即使关闭也保留库存/采购菜单
  const isVendor = userStore.userType === 'vendor'
  if (isVendor && item.inventoryOnly && !features.value.inventoryEnabled) return false
  if (isVendor && item.purchaseOnly && !features.value.purchaseEnabled) return false
  if (!item.permissionCode) return true
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

const commandItems = computed(() => {
  const items = [
    { title: '工作台首页', path: '/home' },
    { title: '消息中心', path: '/notifications' },
    { title: '个人资料', path: '/profile' },
    { title: '销售订单', path: '/m/orders' },
    { title: '库存查询', path: '/m/inventory' },
    { title: '收货入库', path: '/m/receipts' },
    { title: '销售出库', path: '/m/sales-outs' },
    { title: '库存移动', path: '/m/stock-moves' },
    { title: '库存盘点', path: '/stocktakes' },
    { title: '效期预警', path: '/expiry-alerts' },
    { title: '序列号追溯', path: '/traceability' },
    { title: '报表中心', path: '/reports' },
    { title: '报表订阅', path: '/report-subscriptions' },
    { title: '审批中心', path: '/approval/todo' },
    { title: '审批监控', path: '/approval/admin' },
    { title: '日志中心', path: '/log-center' },
    { title: '导入导出任务', path: '/async-tasks' }
  ]
  for (const group of menuGroups.value) for (const item of group.items) items.push({ title: item.label, path: item.route || '/m/' + item.key })
  return items
})
function queryCommands(query, cb) {
  const q = String(query || '').trim().toLowerCase()
  cb(commandItems.value.filter(item => !q || item.title.toLowerCase().includes(q)).slice(0, 12))
}
function selectCommand(item) {
  if (!item || !item.path) return
  commandOpen.value = false
  commandQuery.value = ''
  router.push(item.path)
}
function selectFirstCommand() {
  const list = commandItems.value.filter(item => !commandQuery.value || item.title.includes(commandQuery.value))
  if (list[0]) selectCommand(list[0])
}
function openCommandPalette(e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    commandOpen.value = true
  }
}
function persistSidebar() { localStorage.setItem('dms:sidebar:collapsed', collapsed.value ? '1' : '0') }
onBeforeUnmount(() => window.removeEventListener('keydown', openCommandPalette))
window.addEventListener('keydown', openCommandPalette)
function onCommand(cmd) {
  if (cmd === 'notifications') { router.push('/notifications'); return }
  if (cmd === 'profile') { router.push('/profile'); return }
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
.layout { height: 100vh; background: var(--dms-bg-page); }
.layout > .el-container { min-height: 0; }
.sidebar {
  background: linear-gradient(180deg,#243447 0%,#1f2d3d 100%);
  border-right: 1px solid #1a2533;
  transition: width var(--dms-motion-duration-medium) var(--dms-motion-ease-out);
  overflow: hidden;
  box-shadow: none;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: var(--dms-spacing-2);
  padding: 0 var(--dms-spacing-5);
  color: #fff;
  cursor: pointer;
  background: #1b2736;
  border-bottom: 1px solid rgba(255,255,255,.06);
  font-family: var(--dms-font-family-number);
}
.logo-icon {
  min-width: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.logo-text { font-size: var(--dms-font-size-sm); white-space: nowrap; font-weight: var(--dms-font-weight-semibold); color: #e5eaf1; }
.menu-scroll { height: calc(100vh - 56px); }
:deep(.el-menu) {
  border-right: none;
  background: transparent;
  padding: 8px 6px;
}
:deep(.el-menu-item), :deep(.el-sub-menu__title) {
  height: 42px;
  margin: 2px 0;
  border-radius: 3px;
  color: #b8c4d3;
  font-weight: 500;
}
:deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) {
  background: rgba(255,255,255,.08);
  color: #fff;
}
:deep(.el-menu-item.is-active) {
  color: #fff;
  background: var(--dms-color-primary);
  box-shadow: none;
}
:deep(.el-menu-item.is-active .el-icon), :deep(.el-menu-item.is-active) { color: #fff; }
:deep(.el-sub-menu .el-menu) { background: rgba(0,0,0,.12); padding: 4px; }
:deep(.el-sub-menu .el-menu-item) { min-width: auto; height: 38px; }
.topbar {
  display: flex;
  align-items: center;
  gap: var(--dms-spacing-3);
  background: #fff;
  border-bottom: 1px solid var(--dms-border-2);
  padding: 0 var(--dms-spacing-5);
  height: 56px;
  box-shadow: none;
  z-index: var(--dms-z-index-sticky);
}
.collapse-btn { font-size: 20px; cursor: pointer; color: #606266; transition: color var(--dms-motion-duration-fast) var(--dms-motion-ease-out); }
.collapse-btn:hover { color: var(--dms-color-primary); }
.page-title { font-size: var(--dms-font-size-md); font-weight: var(--dms-font-weight-semibold); color: #1f2d3d; }
.spacer { flex: 1; }
.theme-tools {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #f5f7fa;
}
.theme-chip {
  width: 18px;
  height: 18px;
  border: 1px solid rgba(31,45,61,.15);
  border-radius: 2px;
  background: var(--chip);
  cursor: pointer;
}
.theme-chip:hover { filter: brightness(.96); }
.theme-chip.active { box-shadow: 0 0 0 1px #fff, 0 0 0 2px var(--chip); }
.bell-badge{cursor:pointer;margin-right:12px}.bell-icon{font-size:20px;color:#606266}.bell-icon:hover{color:var(--dms-color-primary)}.user-info { display: flex; align-items: center; gap: 6px; cursor: pointer; color: #606266; outline: none; }
.user-info:hover { color: var(--dms-color-primary); }
.main { background: var(--dms-bg-page); padding: var(--dms-padding-page); }
:global(html[data-mode='dark']) .topbar { background: #111827; border-color: #243044; }
:global(html[data-mode='dark']) .page-title { color: #f8fafc; }
.command-hint { margin-top: 10px; color: var(--dms-text-3); font-size: 12px; }
</style>
