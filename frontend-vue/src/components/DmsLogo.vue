<template>
  <span class="dms-logo" :style="{ width: size + 'px', height: size + 'px' }" aria-hidden="true">
    <img :src="inverse ? markWhite : mark" class="logo-img" alt="MySolMed" />
  </span>
</template>

<script setup>
import mark from '../assets/brand/logo-mark.png'
import markWhite from '../assets/brand/logo-mark-white.png'

defineProps({
  size: { type: [Number, String], default: 36 },
  inverse: { type: Boolean, default: false }
})
</script>

<style scoped>
.dms-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 0;
  flex: 0 0 auto;
}
.logo-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
</style>
