import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, setToken, getRefreshToken, setRefreshToken, clearAuth } from '@/utils/auth'
import router from '@/router'

const service = axios.create({
  baseURL: '',
  timeout: 300000
})

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token && !(config.headers && config.headers.Authorization === '')) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

let isRefreshing = false
const pendingQueue = []

function flushQueue(error, token = null) {
  pendingQueue.forEach(({ resolve, reject, config }) => {
    if (error) {
      reject(error)
    } else {
      config.headers['Authorization'] = 'Bearer ' + token
      resolve(service(config))
    }
  })
  pendingQueue.length = 0
}

async function resolveErrorBody(error) {
  const data = error.response && error.response.data
  if (!data) return null
  if (typeof data === 'string') {
    try { return JSON.parse(data) } catch (e) { return { message: data } }
  }
  return data
}

function rejectBusinessError(error, res) {
  const message = (res && res.message) || error.message || '请求失败'
  ElMessage.error(message)
  const wrapped = new Error(message)
  wrapped.code = res && res.code
  wrapped.response = error.response
  wrapped.data = res
  return Promise.reject(wrapped)
}

function doRefresh() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return Promise.reject(new Error('no refresh token'))
  }
  return axios
    .create({ baseURL: '', timeout: 300000 })
    .post('/auth/refresh', { refreshToken })
    .then((resp) => {
      const data = resp && resp.data ? resp.data.data || resp.data : null
      if (!data || !data.accessToken) {
        throw new Error('refresh response invalid')
      }
      setToken(data.accessToken)
      if (data.refreshToken) {
        setRefreshToken(data.refreshToken)
      }
      return data.accessToken
    })
}

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res == null) return res
    if (res.code === undefined) return res
    if (res.code === 0) {
      return res
    }
    ElMessage.error(res.message || '请求失败: ' + res.code)
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const status = error.response && error.response.status
    const originalConfig = error.config || {}

    if (status === 401) {
      // 登录请求本身的 401 表示账号/密码/租户错误，应透传后端消息，不能提示“登录已过期”
      const isLoginReq = originalConfig.url && (originalConfig.url.indexOf('/auth/login') >= 0 || originalConfig.url.indexOf('/auth/admin-login') >= 0)
      if (isLoginReq) {
        const lm = error.response && error.response.data && error.response.data.message
        ElMessage.error(lm || '账号、密码或租户代码错误')
        return Promise.reject(error)
      }
      if (originalConfig.url && originalConfig.url.indexOf('/auth/refresh') >= 0) {
        ElMessage.error('登录已过期，请重新登录')
        clearAuth()
        router.replace('/login')
        return Promise.reject(error)
      }
      if (!getRefreshToken()) {
        ElMessage.error('登录已过期，请重新登录')
        clearAuth()
        router.replace('/login')
        return Promise.reject(error)
      }
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push({ resolve, reject, config: originalConfig })
        })
      }
      isRefreshing = true
      return doRefresh()
        .then((newToken) => {
          isRefreshing = false
          originalConfig.headers = originalConfig.headers || {}
          originalConfig.headers['Authorization'] = 'Bearer ' + newToken
          flushQueue(null, newToken)
          return service(originalConfig)
        })
        .catch((refreshErr) => {
          isRefreshing = false
          flushQueue(refreshErr, null)
          ElMessage.error('登录已过期，请重新登录')
          clearAuth()
          router.replace('/login')
          return Promise.reject(refreshErr)
        })
    }

    if (status === 403) {
      const msg = error.response && error.response.data && error.response.data.message
      if (msg && msg !== 'Forbidden') ElMessage.error(msg)
      else ElMessage.error('没有权限访问该资源')
      return Promise.reject(error)
    }

    if (status === 404) {
      router.push('/404')
      return Promise.reject(error)
    }

    if (status && status >= 500) {
      ElMessage.error((error.response && error.response.data && error.response.data.message) || '服务器开小差了，请稍后重试')
      return Promise.reject(error)
    }

    ElMessage.error((error.response && error.response.data && error.response.data.message) || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service
