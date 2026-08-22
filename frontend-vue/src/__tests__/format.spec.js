import { describe, it, expect } from 'vitest'
import {
  pad2,
  formatDate,
  formatDateTime,
  formatAuto,
  looksLikeDateKey
} from '@/utils/format'

describe('format date/time helpers', () => {
  it('pad2 zero-pads single digits', () => {
    expect(pad2(1)).toBe('01')
    expect(pad2(12)).toBe('12')
    expect(pad2(0)).toBe('00')
  })

  it('formats a Date into YYYY-MM-DD', () => {
    expect(formatDate(new Date(2026, 0, 5))).toBe('2026-01-05')
  })

  it('formats a Date into YYYY-MM-DD HH:mm:ss', () => {
    const d = new Date(2026, 7, 22, 9, 5, 3)
    expect(formatDateTime(d)).toBe('2026-08-22 09:05:03')
  })

  it('omits seconds when withSeconds=false', () => {
    const d = new Date(2026, 7, 22, 9, 5, 3)
    expect(formatDateTime(d, false)).toBe('2026-08-22 09:05')
  })

  it('returns "-" for null/undefined/empty', () => {
    expect(formatDate(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('')).toBe('-')
  })

  it('does not render raw ISO strings with T or timezone offset', () => {
    // The historical bug: UI showed "2026-08-22T09:05:00+08:00". The auto
    // formatter must detect an ISO date key and convert it to the display form.
    const out = formatAuto('2026-08-22T09:05:00+08:00', 'createdAt')
    expect(out).not.toContain('T')
    expect(out).not.toMatch(/[+-]\d{2}:?\d{2}$/)
    expect(out).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
  })

  it('detects date-like keys', () => {
    expect(looksLikeDateKey('createdAt')).toBe(true)
    expect(looksLikeDateKey('update_time')).toBe(true)
    expect(looksLikeDateKey('orderDate')).toBe(true)
    expect(looksLikeDateKey('name')).toBe(false)
    expect(looksLikeDateKey('status')).toBe(false)
  })

  it('passes non-date values through untouched', () => {
    expect(formatAuto('普通文本', 'remark')).toBe('普通文本')
    expect(formatAuto(42, 'qty')).toBe(42)
  })
})
