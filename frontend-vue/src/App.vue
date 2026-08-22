<template>
  <FullScreenLoader v-model="loading" />
  <router-view />
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import FullScreenLoader from '@/components/FullScreenLoader.vue'

const router = useRouter()
const loading = ref(false)
let loadingTimer = null

function showLoading() {
  if (loadingTimer) clearTimeout(loadingTimer)
  loading.value = true
}
function hideLoading() {
  if (loadingTimer) clearTimeout(loadingTimer)
  loading.value = false
}

router.beforeEach((to, from, next) => {
  showLoading()
  next()
})
router.afterEach(() => {
  if (loadingTimer) clearTimeout(loadingTimer)
  loadingTimer = setTimeout(hideLoading, 150)
})
router.onError((err) => {
  hideLoading()
  console.error('路由切换失败:', err)
})
</script>