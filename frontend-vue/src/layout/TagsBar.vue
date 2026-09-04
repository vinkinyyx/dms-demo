<template>
  <div class="tags-bar">
    <el-scrollbar class="tags-scroll">
      <div ref="trackRef" class="tags-track">
        <div
          v-for="tag in tagsStore.tags"
          :key="tag.key"
          ref="tagRefs"
          class="tag-chip"
          :class="{ active: tag.key === tagsStore.activeKey }"
          @click="openTag(tag)"
          @click.middle.prevent="closeTag(tag)"
          @contextmenu.prevent="openMenu($event, tag)"
        >
          <el-icon class="tag-ico"><component :is="tagIcon(tag)" /></el-icon>
          <span class="tag-title">{{ tag.title }}</span>
          <el-icon
            v-if="!tag.affix"
            class="tag-close"
            @click.stop="closeTag(tag)"
          ><Close /></el-icon>
        </div>
      </div>
    </el-scrollbar>

    <div class="tags-actions">
      <el-tooltip content="刷新当前页" placement="bottom">
        <button type="button" class="tags-act-btn" @click="refreshCurrent"><el-icon><Refresh /></el-icon></button>
      </el-tooltip>
      <el-dropdown trigger="click" @command="onAction">
        <el-tooltip content="页签操作" placement="bottom">
          <button type="button" class="tags-act-btn"><el-icon><ArrowDown /></el-icon></button>
        </el-tooltip>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="refresh"><el-icon><Refresh /></el-icon>刷新页面</el-dropdown-item>
            <el-dropdown-item command="closeOthers"><el-icon><CircleClose /></el-icon>关闭其他</el-dropdown-item>
            <el-dropdown-item command="closeAll"><el-icon><Delete /></el-icon>关闭全部</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <ul
      v-if="menu.visible"
      class="tag-context-menu"
      :style="{ left: menu.x + 'px', top: menu.y + 'px' }"
    >
      <li @click="refreshCurrent"><el-icon><Refresh /></el-icon>刷新页面</li>
      <li :class="{ disabled: !!menu.tag?.affix }" @click="closeCurrent">
        <el-icon><Close /></el-icon>关闭页签
      </li>
      <li @click="closeOthers"><el-icon><CircleClose /></el-icon>关闭其他</li>
      <li @click="closeAll"><el-icon><Delete /></el-icon>关闭全部</li>
    </ul>
  </div>
</template>

<script setup>
import { reactive, ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close, Refresh, CircleClose, Delete, ArrowDown } from '@element-plus/icons-vue'
import { useTagsStore } from '@/store/tags'
import { iconForRoute } from './tagIcons'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()

const trackRef = ref(null)
const tagRefs = ref([])

const menu = reactive({ visible: false, x: 0, y: 0, tag: null })

function tagIcon(tag) {
  return iconForRoute(tag) || 'Menu'
}

function onAction(cmd) {
  if (cmd === 'refresh') refreshCurrent()
  else if (cmd === 'closeOthers') closeOthers()
  else if (cmd === 'closeAll') closeAll()
}

function syncRoute() {
  tagsStore.addRoute(route)
}

watch(
  () => route.fullPath,
  () => {
    syncRoute()
    nextTick(scrollActiveIntoView)
  }
)

onMounted(() => {
  syncRoute()
  nextTick(scrollActiveIntoView)
  window.addEventListener('click', closeMenu)
  window.addEventListener('contextmenu', closeMenu, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeMenu)
  window.removeEventListener('contextmenu', closeMenu, true)
})

function scrollActiveIntoView() {
  const el = tagRefs.value.find((n) => n && n.classList.contains('active'))
  if (!el || !trackRef.value) return
  const wrap = trackRef.value.parentElement?.parentElement
  if (!wrap) return
  const target = el.offsetLeft - trackRef.value.offsetLeft - wrap.clientWidth / 2 + el.clientWidth / 2
  wrap.scrollTo({ left: Math.max(0, target), behavior: 'smooth' })
}

function openTag(tag) {
  if (tag.key === tagsStore.activeKey) return
  router.push(tag.fullPath)
}

function closeTag(tag) {
  if (tag.affix) return
  const target = tagsStore.closeTag(tag.key)
  if (target) router.push(target)
}

function openMenu(e, tag) {
  menu.visible = true
  menu.tag = tag
  const wrap = e.currentTarget.closest('.tags-bar')?.getBoundingClientRect()
  const x = e.clientX - (wrap?.left || 0)
  const y = e.clientY - (wrap?.top || 0) + 6
  const maxX = (wrap?.width || 300) - 130
  menu.x = Math.max(4, Math.min(x, maxX))
  menu.y = Math.max(4, y)
}

function closeMenu() {
  menu.visible = false
}

function refreshCurrent() {
  closeMenu()
  tagsStore.refreshSignal()
}

function closeCurrent() {
  closeMenu()
  if (menu.tag) closeTag(menu.tag)
}

function closeOthers() {
  closeMenu()
  if (!menu.tag) return
  tagsStore.closeOthers(menu.tag.key)
  if (route.fullPath !== menu.tag.fullPath) router.push(menu.tag.fullPath)
}

function closeAll() {
  closeMenu()
  const target = tagsStore.closeAll()
  if (route.fullPath !== target) router.push(target)
}
</script>

<style scoped lang="scss">
.tags-bar {
  position: relative;
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--dms-border-2);
  padding: 5px 8px;
  flex-shrink: 0;
}
.tags-scroll { flex: 1; min-width: 0; }
:deep(.el-scrollbar__wrap) { overflow-y: hidden; }
:deep(.el-scrollbar__view) { display: inline-block; min-width: 100%; }
.tags-track {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 4px;
  width: max-content;
  min-width: 100%;
}
.tag-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  border-radius: 3px;
  border: 1px solid var(--dms-border-1);
  background: #fff;
  color: #495060;
  font-size: var(--dms-font-size-sm);
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition: all var(--dms-motion-duration-fast, .15s) var(--dms-motion-ease-out, ease);
}
.tag-ico { font-size: 13px; color: #a0a6b2; flex-shrink: 0; }
.tag-chip:hover {
  color: var(--dms-color-primary);
  border-color: var(--dms-color-primary-border);
  background: var(--dms-color-primary-bg);
}
.tag-chip:hover .tag-ico { color: var(--dms-color-primary); }
.tag-chip.active {
  background: var(--dms-color-primary-bg);
  border-color: var(--dms-color-primary);
  color: var(--dms-color-primary);
  font-weight: 600;
}
.tag-chip.active .tag-ico { color: var(--dms-color-primary); }
.tag-chip.active::before {
  content: '';
  position: absolute;
  left: -1px; top: 50%;
  transform: translateY(-50%);
  width: 3px; height: 16px;
  border-radius: 0 2px 2px 0;
  background: var(--dms-color-primary);
}
.tag-title { line-height: 1; }
.tag-close {
  font-size: 12px;
  border-radius: 50%;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tag-close:hover { background: var(--dms-color-primary); color: #fff; }

.tags-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-left: 8px;
  margin-left: 4px;
  border-left: 1px solid var(--dms-border-2);
  flex-shrink: 0;
}
.tags-act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px; height: 28px;
  border: 1px solid var(--dms-border-1);
  border-radius: 3px;
  background: #fff;
  color: #606266;
  cursor: pointer;
  transition: all .15s;
}
.tags-act-btn:hover { color: var(--dms-color-primary); border-color: var(--dms-color-primary-border); background: var(--dms-color-primary-bg); }
.tags-act-btn .el-icon { font-size: 15px; }

.tag-context-menu {
  position: absolute;
  z-index: 3000;
  margin: 0;
  padding: 5px;
  list-style: none;
  background: var(--dms-bg-elevated);
  border: 1px solid var(--dms-border-2);
  border-radius: var(--dms-radius-md);
  box-shadow: 0 6px 20px rgba(0, 0, 0, .12);
  min-width: 130px;
}
.tag-context-menu li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  font-size: var(--dms-font-size-sm);
  color: var(--dms-text-2);
  border-radius: var(--dms-radius-sm);
  cursor: pointer;
}
.tag-context-menu li:hover { background: var(--dms-color-primary-bg); color: var(--dms-color-primary); }
.tag-context-menu li.disabled { color: var(--dms-text-disabled); cursor: not-allowed; }
.tag-context-menu li.disabled:hover { background: transparent; color: var(--dms-text-disabled); }

:global(html[data-sider='dark']) .tags-bar { background: #fff; }
</style>
