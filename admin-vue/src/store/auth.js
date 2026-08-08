import { defineStore } from 'pinia'
import { login as loginApi, getMe } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('admin_access_token') || '',
    user: null
  }),
  actions: {
    async login(payload) {
      const res = await loginApi(payload)
      this.token = res.data.accessToken
      localStorage.setItem('admin_access_token', this.token)
      this.user = res.data.user
      return res.data
    },
    async fetchMe() {
      const res = await getMe()
      this.user = res.data
      return res.data
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem('admin_access_token')
    }
  }
})