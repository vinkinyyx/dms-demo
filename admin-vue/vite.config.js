import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  base: '/admin/',
  plugins: [
    vue(),
    AutoImport({ imports: ['vue', 'vue-router', 'pinia'], resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] })
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
    proxy: {
      '/api': { target: process.env.VITE_API_TARGET || 'http://localhost:8080', changeOrigin: true }
    }
  }
})