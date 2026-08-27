<template>
  <CrudView :config="MODULE_CONFIGS['product-global-discounts']" />
</template>

<script setup>
import CrudView from '@/components/CrudView.vue'
import { MODULE_CONFIGS } from '@/config/modules'
</script>
