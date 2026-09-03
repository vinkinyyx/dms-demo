import { describe, it, expect } from 'vitest'
import { num, yuanToFen, fenToYuan, sumBy, apportion } from '@/utils/money'

describe('money.num 安全转数', () => {
  it('非法/空值回退 0', () => {
    expect(num(undefined)).toBe(0)
    expect(num(null)).toBe(0)
    expect(num('')).toBe(0)
    expect(num('abc')).toBe(0)
  })
  it('数字与数字字符串正常解析', () => {
    expect(num('12.5')).toBe(12.5)
    expect(num(3)).toBe(3)
  })
})

describe('money 元<->分', () => {
  it('元转分四舍五入到整数分', () => {
    expect(yuanToFen(1)).toBe(100)
    expect(yuanToFen(12.345)).toBe(1235)
  })
  it('分转元', () => {
    expect(fenToYuan(1234)).toBe(12.34)
  })
})

describe('money.sumBy 按字段求和（按分累加，无浮点漂移）', () => {
  it('0.1+0.2 类场景得到精确值', () => {
    const rows = [{ amount: 0.1 }, { amount: 0.2 }]
    expect(sumBy(rows, 'amount')).toBe(0.3)
  })
})

describe('money.apportion 代金券/整单折扣按行分摊（对应 D1~D8）', () => {
  const sum2 = (arr) => Math.round(arr.reduce((s, v) => s + v * 100, 0)) / 100

  it('D1 全场折扣：三行均摊后总和 == 抵扣额', () => {
    const shares = apportion(30, [100, 100, 100])
    expect(sum2(shares)).toBe(30)
    expect(shares.every((v) => v <= 100)).toBe(true)
  })

  it('D3 代金券面值 > 订单金额：只抵扣订单金额，不超额', () => {
    const shares = apportion(500, [10, 20])
    expect(sum2(shares)).toBe(30)
  })

  it('D4 多产品按行金额比例分摊，余数补给最大行且无负数', () => {
    const lines = [99.99, 50.01, 30.0]
    const shares = apportion(80, lines)
    expect(sum2(shares)).toBe(80)
    shares.forEach((s, i) => {
      expect(s).toBeGreaterThanOrEqual(0)
      expect(s).toBeLessThanOrEqual(lines[i])
    })
    // 最大行应分得最多
    expect(shares[0]).toBeGreaterThanOrEqual(shares[1])
    expect(shares[1]).toBeGreaterThanOrEqual(shares[2])
  })

  it('空行/零金额安全返回全 0', () => {
    expect(apportion(100, [])).toEqual([])
    expect(sum2(apportion(50, [0, 0]))).toBe(0)
  })
})
