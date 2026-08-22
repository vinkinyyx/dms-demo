import { describe, it, expect, beforeEach } from 'vitest'
import {
  getToken, setToken, clearAuth,
  getPermissions, setPermissions, clearPermissions,
  getPrefs, updatePrefs, getUser, setUser
} from '@/utils/auth'

beforeEach(() => {
  localStorage.clear()
})

describe('auth token storage', () => {
  it('round-trips the access token', () => {
    expect(getToken()).toBeNull()
    setToken('abc123')
    expect(getToken()).toBe('abc123')
  })

  it('clearAuth removes token', () => {
    setToken('x')
    setPermissions(['a'])
    clearAuth()
    expect(getToken()).toBeNull()
  })
})

describe('permissions storage', () => {
  it('round-trips a permission array', () => {
    setPermissions(['order:create', 'order:search'])
    expect(getPermissions()).toEqual(['order:create', 'order:search'])
  })

  it('returns [] when nothing stored (never null/undefined)', () => {
    expect(getPermissions()).toEqual([])
  })

  it('returns [] when stored value is corrupted JSON', () => {
    localStorage.setItem('dms:user:permissions', '{not json')
    expect(getPermissions()).toEqual([])
  })

  it('coerces a non-array value to []', () => {
    localStorage.setItem('dms:user:permissions', JSON.stringify('order:create'))
    expect(getPermissions()).toEqual([])
  })

  it('clearPermissions removes the key', () => {
    setPermissions(['x'])
    clearPermissions()
    expect(getPermissions()).toEqual([])
  })
})

describe('user storage', () => {
  it('returns {} when missing or corrupted', () => {
    expect(getUser()).toEqual({})
    localStorage.setItem('dms_user', '{bad')
    expect(getUser()).toEqual({})
  })

  it('round-trips the user object', () => {
    setUser({ id: 1, username: 'admin' })
    expect(getUser().username).toBe('admin')
  })
})

describe('prefs', () => {
  it('returns {} by default', () => {
    expect(getPrefs()).toEqual({})
  })

  it('updatePrefs merges a patch', () => {
    updatePrefs({ theme: 'dark' })
    updatePrefs({ pageSize: 50 })
    expect(getPrefs()).toMatchObject({ theme: 'dark', pageSize: 50 })
  })
})
