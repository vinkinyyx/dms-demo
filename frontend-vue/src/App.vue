<template>
  <FullScreenLoader v-model="loading" />
  <router-view />
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import FullScreenLoader from '@/components/FullScreenLoader.vue'

const router = useRouter()
const loading = ref(false)

watch(() => router.currentRoute.value, () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
  }, 300)
})
</script>
