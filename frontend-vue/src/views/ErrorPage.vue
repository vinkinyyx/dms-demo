<template>
  <StateView :type="type" />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import StateView from '@/components/StateView.vue'

const route = useRoute()
const type = computed(() => String(route.params.code || '404'))
</script>