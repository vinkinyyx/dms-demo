<template>
  <span class="dms-logo" :style="{ width: size + 'px', height: size + 'px' }" aria-hidden="true">
    <img :src="useWhite ? markWhite : mark" class="logo-img" alt="MySolMed" />
  </span>
</template>

<script setup>
import mark from '../assets/brand/logo-mark.png'
import markWhite from '../assets/brand/logo-mark-white.png'

import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  size: { type: [Number, String], default: 36 },
  // variant: 'auto' 跟随菜单深浅（data-sider：深色菜单用白 logo、浅色菜单用深 logo）；'light' 强制白色 logo（深色底）；'dark' 强制深色 logo
  variant: { type: String, default: 'auto' },
  inverse: { type: Boolean, default: false }
})

// logo 背景深浅：深色菜单/深色底 -> true（用白 logo）；默认按菜单深浅 data-sider
const bgDark = ref(false)
function syncDark() {
  const sider = document.documentElement.dataset.sider
  bgDark.value = sider ? sider === 'dark' : document.documentElement.dataset.mode === 'dark'
}
let mo = null
onMounted(() => {
  syncDark()
  mo = new MutationObserver(syncDark)
  mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-mode', 'data-sider'] })
})
onBeforeUnmount(() => { mo && mo.disconnect() })
// inverse(旧 prop, 表示当前在深色底上需白 logo) 优先级最高；否则 variant=light 强制白、dark 强制深、auto 跟随主题
const useWhite = ref(true)
function recompute() {
  if (props.inverse) { useWhite.value = true; return }
  if (props.variant === 'light') { useWhite.value = true; return }
  if (props.variant === 'dark') { useWhite.value = false; return }
  useWhite.value = bgDark.value
}
import { watchEffect } from 'vue'
watchEffect(recompute)
</script>

<style scoped>
.dms-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 0;
  flex: 0 0 auto;
  overflow: hidden;
}
.logo-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
</style>
