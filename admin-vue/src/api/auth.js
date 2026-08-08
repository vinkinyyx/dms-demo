import request from '@/utils/request'

export const login = (data) => request.post('/api/admin/auth/login', data)
export const getMe = () => request.get('/api/admin/auth/me')
export const logout = (refreshToken) => request.post('/api/admin/auth/logout', { refreshToken })
export const changePassword = (data) => request.post('/api/admin/auth/change-password', data)