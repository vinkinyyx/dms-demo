<template>
  <div>
    <CrudView v-if="config" :config="config" />
    <el-empty v-else :description="'模块「' + moduleKey + '」尚未迁移'" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import CrudView from '@/components/CrudView.vue'
import { MODULE_CONFIGS } from '@/config/modules'

const route = useRoute()
const moduleKey = computed(() => route.params.key)
const config = computed(() => MODULE_CONFIGS[moduleKey.value] || null)
const showWizard = computed(() => ['orders', 'purchase-orders'].includes(moduleKey.value))
</script>
