import request from '@/utils/request'

export function listNotifications(params) {
  return request({ url: '/api/notifications', method: 'get', params })
}
export function unreadCount() {
  return request({ url: '/api/notifications/unread-count', method: 'get' })
}
export function markNotificationRead(id) {
  return request({ url: `/api/notifications/${id}/read`, method: 'post' })
}
export function markAllNotificationsRead(refType) {
  return request({ url: '/api/notifications/read-all', method: 'post', params: refType ? { refType } : {} })
}
