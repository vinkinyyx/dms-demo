import { describe, it, expect, beforeEach } from 'vitest'
import {
  statusText,
  statusTagType,
  labelOf,
  fmt,
  LABELS
} from '@/utils/dict'

describe('statusText / statusTagType', () => {
  it('maps known status codes to Chinese labels (never leaks raw enum to users)', () => {
    expect(statusText('PENDING_APPROVAL')).toBe('审批中')
    expect(statusText('DRAFT')).toBe('草稿')
    expect(statusText('PARTIAL_RECEIVED')).toBe('部分收货')
    expect(statusText('PARTIAL_SHIPPED')).toBe('部分发货')
    expect(statusText('APPROVED')).toBe('已审批')
  })

  it('is case-insensitive', () => {
    expect(statusText('draft')).toBe('草稿')
    expect(statusText('Approved')).toBe('已审批')
  })

  it('returns "-" for empty values', () => {
    expect(statusText(null)).toBe('-')
    expect(statusText('')).toBe('-')
  })

  it('falls back to the raw value for unknown codes rather than crashing', () => {
    expect(statusText('SOME_NEW_STATUS')).toBe('SOME_NEW_STATUS')
  })

  it('assigns semantic tag types', () => {
    expect(statusTagType('APPROVED')).toBe('success')
    expect(statusTagType('DRAFT')).toBe('warning')
    expect(statusTagType('REJECTED')).toBe('danger')
    expect(statusTagType('INACTIVE')).toBe('info')
  })
})

describe('labelOf / LABELS', () => {
  it('translates known field keys to Chinese', () => {
    expect(labelOf('createdAt')).toBe('创建时间')
    expect(labelOf('finalAmount')).toBe('最终金额')
    expect(labelOf('dealerName')).toBe('经销商')
  })

  it('falls back to the raw key when unknown', () => {
    expect(labelOf('customField')).toBe('customField')
  })
})

describe('fmt cell formatter', () => {
  it('renders booleans as 是/否, not true/false', () => {
    expect(fmt(true, 'exclusive')).toBe('是')
    expect(fmt(false, 'exclusive')).toBe('否')
  })

  it('formats money-like fields with ¥ and 2 decimals', () => {
    expect(fmt(100, 'finalAmount')).toBe('¥ 100.00')
    expect(fmt(88.5, 'unitPrice')).toBe('¥ 88.50')
  })

  it('maps known enum fields to their label', () => {
    expect(fmt('active', 'status')).toBe('启用')
    expect(fmt('vendor', 'userType')).toBe('厂商')
  })

  it('normalizes ISO timestamps in known time keys (no raw T)', () => {
    const out = fmt('2026-08-22T09:05:00', 'createdAt')
    expect(out).not.toContain('T')
    expect(out).toBe('2026-08-22 09:05:00')
  })

  it('returns "-" for null', () => {
    expect(fmt(null, 'anything')).toBe('-')
  })
})
