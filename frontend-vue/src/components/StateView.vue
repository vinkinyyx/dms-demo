<template>
  <div class="state-view" :class="{ compact }">
    <div v-if="type === 'loading'" class="state-body">
      <el-icon class="is-loading state-icon" :size="iconSize"><Loading /></el-icon>
      <p class="state-text">{{ text || '加载中...' }}</p>
    </div>
    <div v-else-if="type === 'empty'" class="state-body">
      <el-empty :description="text || '暂无数据'" :image-size="imageSize" />
      <div v-if="$slots.action" class="state-action"><slot name="action" /></div>
    </div>
    <div v-else-if="type === 'error'" class="state-body">
      <el-result icon="error" :title="text || '加载失败'" :sub-title="subTitle">
        <template #extra>
          <el-button type="primary" @click="$emit('retry')">重试</el-button>
        </template>
      </el-result>
    </div>
    <div v-else-if="type === '403'" class="state-body">
      <el-result icon="403" title="403" sub-title="抱歉，你无权访问该页面">
        <template #extra><el-button type="primary" @click="$router.push('/home')">返回首页</el-button></template>
      </el-result>
    </div>
    <div v-else-if="type === '404'" class="state-body">
      <el-result icon="404" title="404" sub-title="抱歉，你访问的页面不存在">
        <template #extra><el-button type="primary" @click="$router.push('/home')">返回首页</el-button></template>
      </el-result>
    </div>
    <div v-else-if="type === '500'" class="state-body">
      <el-result icon="500" title="500" sub-title="抱歉，服务器出错了">
        <template #extra>
          <el-button @click="$router.back()">返回上一页</el-button>
          <el-button type="primary" @click="$router.push('/home')">返回首页</el-button>
        </template>
      </el-result>
    </div>
    <slot />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
const props = defineProps({
  type: { type: String, default: 'empty' },
  text: { type: String, default: '' },
  subTitle: { type: String, default: '' },
  compact: { type: Boolean, default: false }
})
defineEmits(['retry'])
const iconSize = computed(() => (props.compact ? 28 : 40))
const imageSize = computed(() => (props.compact ? 60 : 100))
</script>

<style scoped>
.state-view { width: 100%; padding: 24px 0; display: flex; justify-content: center; }
.state-view.compact { padding: 12px 0; }
.state-body { text-align: center; width: 100%; }
.state-icon { color: #409eff; }
.state-text { color: #909399; margin-top: 12px; font-size: 14px; }
.state-action { margin-top: 8px; }
</style>