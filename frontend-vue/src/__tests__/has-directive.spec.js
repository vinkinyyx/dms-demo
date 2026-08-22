import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the Pinia user store and auth helpers so v-has runs deterministically.
const mockPermissions = { value: ['products:view', 'orders:create'] }
vi.mock('@/store/user', () => ({
  useUserStore: () => ({ permissions: mockPermissions.value })
}))
vi.mock('@/utils/auth', () => ({
  getToken: () => 'test-token',
  getPermissions: () => mockPermissions.value
}))

import { has } from '@/directives/has'

function nodeWithParent() {
  const parent = document.createElement('div')
  const el = document.createElement('button')
  parent.appendChild(el)
  return { parent, el }
}

function mount(value) {
  const { parent, el } = nodeWithParent()
  has.mounted(el, { value })
  return { parent, el }
}

describe('v-has permission directive', () => {
  beforeEach(() => {
    mockPermissions.value = ['products:view', 'orders:create']
  })

  it('keeps the element when the user holds the permission', () => {
    const { parent, el } = mount('products:view')
    expect(parent.contains(el)).toBe(true)
  })

  it('removes the element when the user lacks the permission (no blank space)', () => {
    const { parent, el } = mount('products:delete')
    expect(parent.contains(el)).toBe(false)
  })

  it('keeps the element when ANY code in an array is held', () => {
    const { parent, el } = mount(['nope:not-held', 'orders:create'])
    expect(parent.contains(el)).toBe(true)
  })

  it('removes the element when NONE of the array codes are held', () => {
    const { parent, el } = mount(['nope:a', 'nope:b'])
    expect(parent.contains(el)).toBe(false)
  })

  it('keeps the element when value is null/empty/false (no restriction)', () => {
    const a = mount(null)
    expect(a.parent.contains(a.el)).toBe(true)
    const b = mount('')
    expect(b.parent.contains(b.el)).toBe(true)
    const c = mount(false)
    expect(c.parent.contains(c.el)).toBe(true)
  })
})
