import { defineStore } from 'pinia'
import { login as loginApi, getInfo, logout as logoutApi, getMyPermissions } from '@/api/auth'
import { getToken, setToken, setRefreshToken, getUser, setUser, clearAuth, setPermissions, getPermissions } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    user: getUser(),
    permissions: getPermissions()
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
      // 拉全量权限码，前端 v-has 指令使用
      try {
        const pres = await getMyPermissions()
        const perms = (pres && pres.data) || []
        this.permissions = perms
        setPermissions(perms)
      } catch (e) { /* ignore */ }
      return data
    },
    async fetchInfo() {
      const res = await getInfo()
      this.user = res.data || {}
      setUser(this.user)
      return this.user
    },
    async fetchPermissions() {
      const res = await getMyPermissions()
      const perms = (res && res.data) || []
      this.permissions = perms
      setPermissions(perms)
      return perms
    },
    async logout() {
      await logoutApi()
      this.reset()
    },
    reset() {
      this.token = ''
      this.user = {}
      this.permissions = []
      clearAuth()
    }
  }
})
