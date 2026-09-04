import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import Vant from 'vant'

import App from './App.vue'
import router from './router'

/* DMS Design Token：Layer 1/2 必须在组件库 CSS 之前定义 */
import '@/styles/tokens/base-light.scss'
import '@/styles/tokens/semantic.scss'
import '@/styles/tokens/base-dark.scss'
import '@/styles/element/index.scss'

/* 组件库原生样式 */
import 'element-plus/dist/index.css'
import 'vant/lib/index.css'

/* 运行期 CSS 变量覆盖（必须在组件库 CSS 之后） */
import '@/styles/element/runtime.scss'
import '@/styles/vant/index.scss'
import '@/styles/vant/mobile-theme.scss'  // 移动端藏青琥珀主题（作用域：.m-layout/.m-login/.m-register）

/* 全局重置与业务样式 */
import '@/styles/reset.scss'
import '@/styles/app.scss'
import '@/styles/enterprise.scss'

import hasDirective from '@/directives/has'
import { initTheme } from '@/config/theme-runtime'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.use(Vant)
app.use(hasDirective)
app.config.errorHandler = (err, instance, info) => { console.error('全局未捕获错误:', err, info) }
initTheme()
app.mount('#app')

