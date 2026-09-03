/**
 * 金额计算唯一工具（全站金额相关逻辑都应复用这里，禁止在页面里直接用浮点数 toFixed 拼金额）。
 * 用「分」(整数) 承载金额，避免 0.1+0.2 类浮点误差；分摊时把余数补给最大行，保证各行之和 == 总额。
 */

/** 任意输入安全转为 Number，非法/空值回退 0。 */
export function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 元 -> 分（四舍五入到整数分）。 */
export function yuanToFen(yuan) {
  return Math.round(num(yuan) * 100)
}

/** 分 -> 元（Number，2 位小数展示请再配合 toFixed(2)）。 */
export function fenToYuan(fen) {
  return Math.round(num(fen)) / 100
}

/** 合计：对一组对象的指定字段求和（元），内部按分累加避免浮点漂移。 */
export function sumBy(list, key) {
  const totalFen = (list || []).reduce((acc, item) => acc + yuanToFen(item ? item[key] : 0), 0)
  return fenToYuan(totalFen)
}

/**
 * 把一张代金券/整单折扣（单位：元）按各行金额占比分摊到每一行（返回元数组，保留 2 位）。
 * - 抵扣总额不超过各行金额之和；
 * - 先按比例向下取整到分，剩余的分依次补给金额最大的行，保证「各行分摊之和 == 实际抵扣总额」；
 * - 任一行分摊不超过该行金额（不会出现负数行）。
 */
export function apportion(discountYuan, lineAmounts) {
  const amounts = (lineAmounts || []).map((a) => Math.max(0, num(a)))
  const totalFen = amounts.reduce((s, a) => s + yuanToFen(a), 0)
  let discountFen = Math.max(0, Math.min(yuanToFen(discountYuan), totalFen))
  const result = new Array(amounts.length).fill(0)
  if (discountFen <= 0 || totalFen <= 0) return result.map(() => 0)

  // 按比例初分到分（向下取整，且不超过该行金额）
  const capFen = amounts.map((a) => yuanToFen(a))
  result.forEach((_, i) => {
    let share = Math.floor((discountFen * capFen[i]) / totalFen)
    share = Math.min(share, capFen[i])
    result[i] = share
  })

  // 把未分完的余数补给「金额最大且还能抵扣」的行
  let remaining = discountFen - result.reduce((s, v) => s + v, 0)
  const order = amounts
    .map((a, i) => ({ i, fen: capFen[i] }))
    .sort((x, y) => y.fen - x.fen)
  while (remaining > 0) {
    let progressed = false
    for (const { i, fen } of order) {
      if (remaining <= 0) break
      if (result[i] < fen) {
        result[i] += 1
        remaining -= 1
        progressed = true
      }
    }
    if (!progressed) break
  }

  return result.map((fen) => fenToYuan(fen))
}
