<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? SIDER_COLLAPSED_W : SIDER_W" class="sidebar">
      <div class="logo" @click="$router.push('/home')">
        <DmsLogo :size="32" class="logo-icon" variant="auto" />
        <span v-show="!collapsed" class="logo-text">MySolMed DMS</span>
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
        <Breadcrumb />
        <div class="spacer" />
        <el-button text circle title="命令面板 (Ctrl/Cmd+K)" @click="commandOpen = true">
          <el-icon><Search /></el-icon>
        </el-button>
        <div class="theme-tools">
          <button v-for="item in themePresets" :key="item.key" type="button" class="theme-chip"
            :class="{ active: currentPreset.key === item.key }" :title="item.name"
            :style="{ '--chip': item.color }" @click="setThemePreset(item.key)" />
          <el-button text circle :title="siderMode === 'dark' ? '菜单切换浅色' : '菜单切换深色'" @click="toggleSiderMode">
            <el-icon><Sunny v-if="siderMode === 'dark'" /><Moon v-else /></el-icon>
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
      <TagsBar />
      <el-main class="main">
        <router-view v-slot="{ Component, route: viewRoute }">
          <template v-if="!tagsStore.reloading">
            <keep-alive :max="12" v-if="!viewRoute.meta.noCache">
              <component :is="Component" :key="viewRoute.fullPath" />
            </keep-alive>
            <component :is="Component" v-else :key="viewRoute.fullPath" />
          </template>
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
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { MENU_GROUPS } from '@/config/menu'
import { unreadCount } from '@/api/notification'
import request from '@/utils/request'
import { Bell, Moon, Sunny, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useTagsStore } from '@/store/tags'
import { ensurePermissions } from '@/directives/has'
import { THEME_PRESETS as themePresets, currentThemePreset as currentPreset, setPreset as setThemePreset, toggleSider as applyToggleSider, currentSiderMode, initTheme, forceContentLight } from '@/config/theme-runtime'
import Breadcrumb from './Breadcrumb.vue'
import TagsBar from './TagsBar.vue'
import { useTenantFeatures } from '@/composables/useTenantFeatures'
const { features, loadFeatures } = useTenantFeatures()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const tagsStore = useTagsStore()

watch(
  () => tagsStore.reloading,
  (val) => {
    if (!val) return
    setTimeout(() => tagsStore.refreshDone(), 60)
  }
)
const collapsed = ref(localStorage.getItem('dms:sidebar:collapsed') === '1')
const commandOpen = ref(false)
const commandQuery = ref('')
initTheme()
forceContentLight()
const unread = ref(0)
const siderMode = ref(document.documentElement.dataset.sider || currentSiderMode.value || 'light')
function toggleSiderMode(){ applyToggleSider(); siderMode.value = document.documentElement.dataset.sider || 'light' }
function toggleSidebar(){ collapsed.value = !collapsed.value; persistSidebar(); applySidebarVar() }
function layoutVar(name, fallback){ try { const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim(); return v || fallback } catch(e){ return fallback } }
const SIDER_W = layoutVar('--dms-layout-sider-width', '230px')
const SIDER_COLLAPSED_W = layoutVar('--dms-layout-sider-collapsed-width', '64px')
function applySidebarVar(){ document.documentElement.style.setProperty('--dms-sidebar-w', collapsed.value ? SIDER_COLLAPSED_W : SIDER_W); window.dispatchEvent(new Event('sidebar-toggle')) }
async function loadUnread(){ try { const r=await unreadCount(); unread.value=Number(r.data?.count||0) } catch(e){ unread.value=0 } }
function goNotifications(){ router.push('/notifications') }
setInterval(loadUnread, 60000)
const permissionsLoaded = ref(false)

onMounted(async () => { applySidebarVar();
  collapsed.value = localStorage.getItem('dms:sidebar:collapsed') === '1'
  await ensurePermissions()
  await loadUnread()
  await loadFeatures()
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

const commandItems = computed(() => {
  const items = [
    { title: '工作台首页', path: '/home' },
    { title: '消息中心', path: '/notifications' },
    { title: '个人资料', path: '/profile' },
    { title: '销售订单', path: '/m/orders' },
    { title: '库存查询', path: '/m/inventory', inventoryOnly: true },
    { title: '收货入库', path: '/m/receipts', inventoryOnly: true },
    { title: '销售出库', path: '/m/sales-outs' },
    { title: '库存移动', path: '/m/stock-moves', inventoryOnly: true },
    { title: '库存盘点', path: '/stocktakes', inventoryOnly: true },
    { title: '效期预警', path: '/expiry-alerts', inventoryOnly: true },
    { title: '序列号追溯', path: '/traceability', inventoryOnly: true },
    { title: '报表中心', path: '/reports' },
    { title: '报表订阅', path: '/report-subscriptions' },
    { title: '审批中心', path: '/approval/todo' },
    { title: '审批监控', path: '/approval/admin' },
    { title: '日志中心', path: '/log-center' },
    { title: '导入导出任务', path: '/async-tasks' }
  ]
  for (const group of menuGroups.value) for (const item of group.items) items.push({ title: item.label, path: item.route || '/m/' + item.key })
  const isVendor = userStore.userType === 'vendor'
  return items.filter(it => {
    if (isVendor && it.inventoryOnly && !features.value.inventoryEnabled) return false
    return true
  })
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
        tagsStore.reset()
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
  background: var(--dms-sider-bg);
  border-right: 1px solid var(--dms-sider-border, #eef0f4);
  transition: width var(--dms-motion-duration-medium) var(--dms-motion-ease-out);
  overflow: hidden;
  box-shadow: none;
}
.logo {
  height: var(--dms-layout-logo-height, 60px);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: var(--dms-sider-text-active, var(--dms-color-primary));
  cursor: pointer;
  background: var(--dms-sider-bg);
  border-bottom: 1px solid var(--dms-sider-border, #eef0f4);
  font-family: var(--dms-font-family-number);
}
.logo-icon {
  min-width: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.logo-text { font-size: 16px; white-space: nowrap; font-weight: 700; color: var(--dms-sider-text-hover, #0f172a); letter-spacing: .2px; }
.menu-scroll { height: calc(100vh - var(--dms-layout-logo-height, 60px)); }
:deep(.el-menu) {
  border-right: none;
  background: transparent;
  padding: 10px 8px;
}
:deep(.el-menu-item), :deep(.el-sub-menu__title) {
  height: var(--dms-layout-menu-item-height, 42px);
  margin: 3px 0;
  border-radius: 8px;
  color: var(--dms-sider-text);
  font-weight: 500;
  font-size: 14px;
}
:deep(.el-menu-item .el-icon), :deep(.el-sub-menu__title .el-icon) { color: inherit; font-size: var(--dms-layout-menu-icon-size, 17px); }
:deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) {
  background: var(--dms-sider-bg-deep, #f5f7fa);
  color: var(--dms-sider-text-hover, #1f2937);
}
:deep(.el-menu-item.is-active) {
  color: var(--dms-sider-text-active, var(--dms-color-primary));
  background: var(--dms-sider-active-bg, var(--dms-color-primary-bg));
  font-weight: 600;
  box-shadow: none;
}
:deep(.el-menu-item.is-active .el-icon) { color: var(--dms-sider-text-active, var(--dms-color-primary)); }
:deep(.el-sub-menu.is-active > .el-sub-menu__title) { color: var(--dms-sider-text-active, var(--dms-color-primary)); }
:deep(.el-sub-menu .el-menu) { background: transparent; padding: 2px 0 2px 14px; }
:deep(.el-sub-menu .el-menu-item) { min-width: auto; height: var(--dms-layout-submenu-item-height, 38px); margin: 2px 0; border-radius: 8px; }
:deep(.el-menu--collapse .el-menu-item), :deep(.el-menu--collapse .el-sub-menu__title) { border-radius: 8px; }
.topbar {
  display: flex;
  align-items: center;
  gap: var(--dms-spacing-3);
  background: #fff;
  border-bottom: 1px solid var(--dms-border-2);
  padding: 0 var(--dms-spacing-5);
  height: var(--dms-layout-header-height, 56px);
  box-shadow: none;
  z-index: var(--dms-z-index-sticky);
}
.collapse-btn { font-size: 20px; cursor: pointer; color: #606266; transition: color var(--dms-motion-duration-fast) var(--dms-motion-ease-out); }
.collapse-btn:hover { color: var(--dms-color-primary); }
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
.main { background: var(--dms-bg-page); padding: var(--dms-layout-content-padding, var(--dms-padding-page)); }
:global(html[data-mode='dark']) .topbar { background: #111827; border-color: #243044; }
.command-hint { margin-top: 10px; color: var(--dms-text-3); font-size: 12px; }
</style>
