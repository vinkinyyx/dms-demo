import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data,
    headers: { Authorization: '' },
    skipAuthRefresh: true
  })
}

export function getInfo() {
  return request({
    url: '/api/auth/me',
    method: 'get'
  })
}

export function getMyPermissions() {
  return request({
    url: '/api/me/permissions',
    method: 'get'
  })
}

export function logout() {
  return request({
    url: '/api/auth/logout',
    method: 'post',
    headers: { Authorization: '' }
  })
}

export function refreshToken(data) {
  return request({
    url: '/api/auth/refresh',
    method: 'post',
    data,
    headers: { Authorization: '' },
    skipAuthRefresh: true
  })
}

export function changePassword(data) {
  return request({
    url: '/api/auth/change-password',
    method: 'post',
    data
  })
}
