<template>
  <span class="surgery-icon" :style="{ width: size + 'px', height: size + 'px' }" aria-hidden="true">
    <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect x="4" y="4" width="40" height="40" rx="10" class="si-bg" :fill="bg"/>
      <path class="si-cross" d="M24 13v14M17 20h14" stroke-linecap="round"/>
      <path class="si-blade" d="M16 31l11-11 3.5 3.5L19.5 34.5z" stroke-linejoin="round"/>
      <path class="si-handle" d="M30.5 19.5l4-4 2.5 2.5-4 4z" stroke-linejoin="round"/>
    </svg>
  </span>
</template>

<script setup>
defineProps({
  size: { type: [Number, String], default: 24 },
  bg: { type: String, default: 'currentColor' }
})
</script>

<style scoped>
.surgery-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 0;
  flex: 0 0 auto;
}
.surgery-icon svg { width: 100%; height: 100%; display: block; }
.si-cross { stroke: #fff; stroke-width: 3; }
.si-blade { fill: rgba(255,255,255,.18); stroke: #fff; stroke-width: 2; }
.si-handle { fill: #fff; stroke: #fff; stroke-width: 1.5; }
</style>
