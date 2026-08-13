<template>
  <transition name="loader-fade">
    <div v-if="visible" class="full-screen-loader">
      <div class="loader-content">
        <div class="spinner-container">
          <div class="spinner"></div>
        </div>
        <div class="loader-text">加载中...</div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  if (val) {
    visible.value = true
  } else {
    setTimeout(() => {
      visible.value = false
      emit('update:modelValue', false)
    }, 200)
  }
})
</script>

<style scoped>
.full-screen-loader {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 9999;
  pointer-events: none;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
}

.loader-content {
  text-align: center;
  color: var(--dms-text-2);
}

.spinner-container {
  position: relative;
  width: 40px;
  height: 40px;
  margin: 0 auto 16px;
}

.spinner {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid var(--dms-border-1);
  border-top-color: var(--dms-color-primary);
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loader-text {
  font-size: 14px;
  color: var(--dms-text-3);
}

.loader-fade-enter-active,
.loader-fade-leave-active {
  transition: opacity 0.2s;
}
.loader-fade-enter-from,
.loader-fade-leave-to {
  opacity: 0;
}
</style>
