<template>
  <span class="dms-logo" :class="{ 'is-inverse': inverse }" :style="{ width: size + 'px', height: size + 'px' }" aria-hidden="true">
    <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="DMS">
      <rect width="40" height="40" rx="8" class="logo-bg"/>
      <path d="M11 9.5h7.3c5.6 0 10.2 4.5 10.2 10.2S23.9 29.9 18.3 29.9H11V9.5z" class="logo-d"/>
      <circle cx="24.4" cy="19.7" r="3.3" class="logo-node"/>
      <circle cx="24.4" cy="19.7" r="1.1" class="logo-dot"/>
    </svg>
  </span>
</template>

<script setup>
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
  color: var(--dms-color-primary);
  line-height: 0;
  flex: 0 0 auto;
}
.dms-logo svg { width: 100%; height: 100%; display: block; }
.logo-bg { fill: currentColor; }
.logo-d { fill: #fff; }
.logo-node { fill: currentColor; }
.logo-dot { fill: #fff; opacity: .55; }
.dms-logo.is-inverse { color: #fff; }
.dms-logo.is-inverse .logo-d { fill: var(--dms-color-primary); }
.dms-logo.is-inverse .logo-node { fill: #fff; }
.dms-logo.is-inverse .logo-dot { fill: var(--dms-color-primary); opacity: .9; }
</style>
