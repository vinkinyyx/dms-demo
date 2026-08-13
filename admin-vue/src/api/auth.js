import request from '@/utils/request'

export const login = (data) => request({
  url: '/api/admin/auth/login',
  method: 'POST',
  data,
  headers: { Authorization: '' }
})

export const getMe = () => request({
  url: '/api/admin/auth/me',
  method: 'GET'
})

export const refreshToken = (data) => request({
  url: '/api/admin/auth/refresh',
  method: 'POST',
  data,
  headers: { Authorization: '' }
})

export const logout = () => request({
  url: '/api/admin/auth/logout',
  method: 'POST',
  headers: { Authorization: '' }
})

export const changePassword = (data) => request({
  url: '/api/admin/auth/change-password',
  method: 'POST',
  data
})
