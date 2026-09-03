import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{spec,test}.js'],
    coverage: {
      reporter: ['text', 'html'],
      include: ['src/utils/**/*.{js,vue}'],
      // 阈值随用例补充逐步上调；当前先锁定纯函数工具层（计价/金额/格式化）的下限。
      thresholds: {
        statements: 60,
        branches: 60,
        functions: 60,
        lines: 60
      }
    }
  }
})
