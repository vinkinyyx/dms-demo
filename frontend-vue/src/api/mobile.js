import request from '@/utils/request'

export function traceBySerial(serialNo) {
  return request({ url: '/api/traceability/by-serial', method: 'get', params: { serialNo } })
}
