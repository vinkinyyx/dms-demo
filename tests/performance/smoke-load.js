/**
 * k6 性能冒烟：对核心只读接口做阶梯压测。
 * 运行（需本机/CI 安装 k6，https://k6.io）：
 *   BASE=http://dms-dev.mysolmed.com TOKEN=<accessToken> k6 run tests/performance/smoke-load.js
 *
 * 阈值即验收线：p95 < 800ms、错误率 < 1%。发版前/容量评估时手动触发，不进每次提交。
 */
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = (__ENV.BASE || 'http://dms-dev.mysolmed.com').replace(/\/$/, '')
const TOKEN = __ENV.TOKEN || ''

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 }
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.01']
  }
}

const endpoints = [
  '/api/products/page?current=1&size=20',
  '/api/sales-orders/page?current=1&size=20',
  '/api/dealers/page?current=1&size=20',
  '/actuator/health'
]

export default function () {
  const ep = endpoints[Math.floor(Math.random() * endpoints.length)]
  const params = { headers: { Authorization: `Bearer ${TOKEN}` }, tags: { endpoint: ep } }
  const res = http.get(BASE + ep, params)
  check(res, {
    'status 200/2xx': (r) => r.status >= 200 && r.status < 300,
    'p95 within budget': (r) => r.timings.duration < 2000
  })
  sleep(1)
}

