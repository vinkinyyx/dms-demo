import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const service = axios.create({ baseURL: '/', timeout: 30000 })

async function parseError(error) {
  const data = error.response && error.response.data
  if (!data) return { message: error.message || '网络错误' }
  if (typeof data === 'string') {
    try { return JSON.parse(data) } catch (e) {
      if (data.trim().startsWith('<')) return { message: '服务暂不可用，请稍后重试' }
      return { message: data }
    }
  }
  return data
}

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_access_token')
  if (token) if (config.headers && config.headers.Authorization !== '') config.headers.Authorization = 'Bearer ' + token
  return config
})

service.interceptors.response.use(
  async (response) => {
    const res = response.data
    if (response.status >= 200 && response.status < 300 && (res.code === undefined || res.code === 0)) {
      return res
    }
    const body = res && typeof res === 'object' ? res : await parseError({ response })
    const message = body.message || '请求失败'
    const isLoginPage = router.currentRoute.value.path === '/login'
    if (response.status === 401 || body.code === 40101 || body.code === 40104) {
      localStorage.removeItem('admin_access_token')
      if (!isLoginPage) router.push('/login')
    }
    if (!isLoginPage) ElMessage.error(message)
    const err = new Error(message)
    err.response = response
    err.data = body
    return Promise.reject(err)
  },
  async (error) => {
    const status = error.response && error.response.status
    const body = await parseError(error)
    const isLoginPage = router.currentRoute.value.path === '/login'
    if (status === 401 || body.code === 40101 || body.code === 40104) {
      localStorage.removeItem('admin_access_token')
      if (!isLoginPage) router.push('/login')
    }
    if (!isLoginPage) ElMessage.error(body.message || error.message || '网络错误')
    const wrapped = new Error(body.message || error.message || '网络错误')
    wrapped.response = error.response
    wrapped.data = body
    return Promise.reject(wrapped)
  }
)

export default service