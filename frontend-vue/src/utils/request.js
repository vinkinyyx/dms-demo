import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, setToken, getRefreshToken, setRefreshToken, clearAuth } from '@/utils/auth'
import router from '@/router'

const service = axios.create({
  baseURL: '',
  timeout: 300000
})

const pendingQueue = []
const inflightMutations = new Map()

function mutationKey(config) {
  if (!config || !config.method) return ''
  const method = String(config.method).toLowerCase()
  if (!['post', 'put', 'patch', 'delete'].includes(method)) return ''
  if (config.skipDuplicate) return ''
  const url = String(config.url || '').replace(/\/\d+(?=\?|$)/, '/:id')
  return [method, url, JSON.stringify(config.params || {})].join('|')
}

function removeInflight(config) {
  const key = mutationKey(config)
  if (key) inflightMutations.delete(key)
}

function isDuplicateError(error) {
  return Boolean(error && (error.duplicate || error.code === 'DUPLICATE_REQUEST'))
}

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token && !(config.headers && config.headers.Authorization === '')) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    const key = mutationKey(config)
    if (key) {
      if (inflightMutations.has(key)) {
        const wrapped = new Error('请求正在处理中，请勿重复提交')
        wrapped.code = 'DUPLICATE_REQUEST'
        wrapped.duplicate = true
        return Promise.reject(wrapped)
      }
      inflightMutations.set(key, true)
    }
    return config
  },
  (error) => Promise.reject(error)
)

function flushQueue(error, token = null) {
  pendingQueue.forEach(({ resolve, reject, config }) => {
    if (error) reject(error)
    else {
      config.headers['Authorization'] = 'Bearer ' + token
      resolve(service(config))
    }
  })
  pendingQueue.length = 0
}

let isRefreshing = false

function doRefresh() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return Promise.reject(new Error('no refresh token'))
  return axios
    .create({ baseURL: '', timeout: 300000 })
    .post('/auth/refresh', { refreshToken })
    .then((resp) => {
      const data = resp && resp.data ? resp.data.data || resp.data : null
      if (!data || !data.accessToken) throw new Error('refresh response invalid')
      setToken(data.accessToken)
      if (data.refreshToken) setRefreshToken(data.refreshToken)
      return data.accessToken
    })
}

service.interceptors.response.use(
  (response) => {
    removeInflight(response.config)
    const res = response.data
    if (res == null) return res
    if (res.code === undefined) return res
    if (res.code === 0) return res
    ElMessage.error(res.message || '请求失败: ' + res.code)
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const originalConfig = error.config || {}
    removeInflight(originalConfig)
    if (isDuplicateError(error)) {
      ElMessage.warning(error.message)
      return Promise.reject(error)
    }
    const status = error.response && error.response.status

    if (status === 401) {
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
        return new Promise((resolve, reject) => pendingQueue.push({ resolve, reject, config: originalConfig }))
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
      ElMessage.error((msg && msg !== 'Forbidden') ? msg : '没有权限访问该资源')
      return Promise.reject(error)
    }
    if (status === 404) {
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
