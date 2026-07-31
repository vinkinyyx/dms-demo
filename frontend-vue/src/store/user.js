import { defineStore } from 'pinia'
import { login as loginApi, getInfo, logout as logoutApi } from '@/api/auth'
import { getToken, setToken, setRefreshToken, getUser, setUser, clearAuth } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    user: getUser()
  }),
  getters: {
    isLogin: (state) => !!state.token,
    username: (state) => state.user.username || '',
    userType: (state) => state.user.userType || ''
  },
  actions: {
    async login(form) {
      const res = await loginApi(form)
      const data = res.data || {}
      this.token = data.accessToken
      this.user = data.user || {}
      setToken(data.accessToken)
      if (data.refreshToken) setRefreshToken(data.refreshToken)
      setUser(this.user)
      return data
    },
    async fetchInfo() {
      const res = await getInfo()
      this.user = res.data || {}
      setUser(this.user)
      return this.user
    },
    async logout() {
      await logoutApi()
      this.reset()
    },
    reset() {
      this.token = ''
      this.user = {}
      clearAuth()
    }
  }
})
