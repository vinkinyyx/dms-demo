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
          <span class="tag-dot" v-if="tag.key === tagsStore.activeKey" />
          <span class="tag-title">{{ tag.title }}</span>
          <el-icon
            v-if="!tag.affix"
            class="tag-close"
            @click.stop="closeTag(tag)"
          ><Close /></el-icon>
        </div>
      </div>
    </el-scrollbar>

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
import { Close, Refresh, CircleClose, Delete } from '@element-plus/icons-vue'
import { useTagsStore } from '@/store/tags'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()

const trackRef = ref(null)
const tagRefs = ref([])

const menu = reactive({ visible: false, x: 0, y: 0, tag: null })

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
  background: var(--dms-bg-container);
  border-bottom: 1px solid var(--dms-border-2);
  padding: 6px 12px;
  flex-shrink: 0;
}
.tags-scroll { width: 100%; }
:deep(.el-scrollbar__wrap) { overflow-y: hidden; }
:deep(.el-scrollbar__view) { display: inline-block; min-width: 100%; }
.tags-track {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px;
  width: max-content;
  min-width: 100%;
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border-radius: var(--dms-radius-md);
  border: 1px solid var(--dms-border-1);
  background: var(--dms-bg-container);
  color: var(--dms-text-2);
  font-size: var(--dms-font-size-sm);
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition: all var(--dms-motion-duration-fast, .15s) var(--dms-motion-ease-out, ease);
}
.tag-chip:hover {
  color: var(--dms-color-primary);
  border-color: var(--dms-color-primary-border);
  background: var(--dms-color-primary-bg);
}
.tag-chip.active {
  background: var(--dms-color-primary);
  border-color: var(--dms-color-primary);
  color: #fff;
  box-shadow: 0 2px 6px rgba(64, 158, 255, .35);
}
.tag-chip.active:hover { color: #fff; }
.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .9);
  flex-shrink: 0;
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
.tag-close:hover { background: rgba(0, 0, 0, .15); color: #fff; }
.tag-chip.active .tag-close:hover { background: rgba(255, 255, 255, .3); }

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

:global(html[data-mode='dark']) .tags-bar { background: #111827; border-color: #243044; }
:global(html[data-mode='dark']) .tag-chip { background: #1a2334; border-color: #2c3a52; color: #c3cad6; }
:global(html[data-mode='dark']) .tag-context-menu { background: #1a2334; border-color: #2c3a52; }
</style>
