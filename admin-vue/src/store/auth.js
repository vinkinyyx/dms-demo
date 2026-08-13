import { defineStore } from 'pinia'
import { login as loginApi, getMe } from '@/api/auth'
import router from '@/router'

function isTokenValid(token) {
  if (!token) return false
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return !payload.exp || payload.exp * 1000 > Date.now()
  } catch (e) {
    return false
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: isTokenValid(localStorage.getItem('admin_access_token')) ? localStorage.getItem('admin_access_token') : '',
    user: null
  }),
  actions: {
    async login(payload) {
      this.clear()
      const res = await loginApi(payload)
      this.token = res.data.accessToken
      localStorage.setItem('admin_access_token', this.token)
      this.user = res.data.user
      return res.data
    },
    async fetchMe() {
      if (router.currentRoute.value.meta.public) return null
      const res = await getMe()
      this.user = res.data
      return res.data
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem('admin_access_token')
    },
    hasValidToken() {
      return isTokenValid(this.token || localStorage.getItem('admin_access_token'))
    }
  }
})