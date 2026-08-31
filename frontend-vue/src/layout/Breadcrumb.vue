<template>
  <el-breadcrumb class="app-breadcrumb" separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="(item, idx) in crumbs" :key="item.label + idx">
        <span v-if="!item.path || idx === crumbs.length - 1" class="crumb-current">{{ item.label }}</span>
        <a v-else class="crumb-link" @click.prevent="go(item.path)">{{ item.label }}</a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolvePageMeta } from '@/utils/pageMeta'

const route = useRoute()
const router = useRouter()

const crumbs = computed(() => resolvePageMeta(route).crumbs)

function go(path) {
  if (path && path !== route.fullPath && path !== route.path) router.push(path)
}
</script>

<style scoped lang="scss">
.app-breadcrumb {
  font-size: var(--dms-font-size-base);
  line-height: 1;
  white-space: nowrap;
}
:deep(.el-breadcrumb__inner) {
  font-weight: var(--dms-font-weight-semibold, 600);
  color: var(--dms-text-1);
}
:deep(.el-breadcrumb__separator) {
  color: var(--dms-text-4);
  font-weight: 400;
}
.crumb-current { color: var(--dms-text-1); font-weight: 600; }
.crumb-link {
  color: var(--dms-text-3);
  font-weight: 500;
  cursor: pointer;
}
.crumb-link:hover { color: var(--dms-color-primary); }
.breadcrumb-enter-active, .breadcrumb-leave-active { transition: all .2s; }
.breadcrumb-enter-from, .breadcrumb-leave-to { opacity: 0; transform: translateX(8px); }
</style>
