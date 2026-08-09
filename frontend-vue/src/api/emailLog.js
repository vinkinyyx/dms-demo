import request from '@/utils/request'

export function listEmailLogs(params) {
  return request({ url: '/api/email-logs', method: 'get', params })
}