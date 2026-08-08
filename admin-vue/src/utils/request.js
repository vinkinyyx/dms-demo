import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const service = axios.create({ baseURL: '/', timeout: 30000 })

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_access_token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== undefined && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 40101 || res.code === 40104) {
        localStorage.removeItem('admin_access_token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      localStorage.removeItem('admin_access_token')
      router.push('/login')
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service