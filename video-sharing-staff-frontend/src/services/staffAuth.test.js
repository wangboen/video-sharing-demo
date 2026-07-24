import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  STAFF_TOKEN_KEY,
  STAFF_USER_KEY,
  clearStaffSession,
  readStaffSession,
  saveStaffSession,
} from './staffAuth'
import { resolveApiBaseUrl } from '../config/apiBase'
import { handleStaffUnauthorized } from './api'

describe('staff authentication state', () => {
  beforeEach(() => localStorage.clear())

  it('stores staff credentials under isolated keys and reads them back', () => {
    saveStaffSession({
      token: 'review-token',
      username: 'reviewer-one',
      id: 7,
      email: 'reviewer@example.com',
      role: 'REVIEWER',
    })

    expect(localStorage.getItem(STAFF_TOKEN_KEY)).toBe('review-token')
    expect(STAFF_TOKEN_KEY).toBe('staffAuthToken')
    expect(STAFF_USER_KEY).toBe('staffUser')
    expect(readStaffSession()).toEqual({
      token: 'review-token',
      user: {
        username: 'reviewer-one',
        id: 7,
        email: 'reviewer@example.com',
        role: 'REVIEWER',
      },
    })
  })

  it('never accepts a USER session and clears malformed staff state', () => {
    localStorage.setItem(STAFF_TOKEN_KEY, 'user-token')
    localStorage.setItem(STAFF_USER_KEY, JSON.stringify({ username: 'viewer', role: 'USER' }))

    expect(readStaffSession()).toBeNull()
    expect(localStorage.getItem(STAFF_TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(STAFF_USER_KEY)).toBeNull()
    expect(() => saveStaffSession({ token: 'x', username: 'viewer', role: 'USER' }))
      .toThrow('Insufficient staff role')
  })

  it('clears both staff storage entries', () => {
    localStorage.setItem(STAFF_TOKEN_KEY, 'token')
    localStorage.setItem(STAFF_USER_KEY, '{}')

    clearStaffSession()

    expect(localStorage.getItem(STAFF_TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(STAFF_USER_KEY)).toBeNull()
  })

  it('uses the configured API origin, local fallback, and empty production same-origin', () => {
    expect(resolveApiBaseUrl('https://api.example.test/', false)).toBe('https://api.example.test')
    expect(resolveApiBaseUrl(undefined, true)).toBe('http://localhost:8080')
    expect(resolveApiBaseUrl(undefined, false)).toBe('')
    expect(resolveApiBaseUrl('', false)).toBe('')
  })

  it('clears staff auth and redirects to the staff login on 401', () => {
    localStorage.setItem(STAFF_TOKEN_KEY, 'expired-token')
    localStorage.setItem(STAFF_USER_KEY, JSON.stringify({ role: 'REVIEWER' }))
    const redirect = vi.fn()

    handleStaffUnauthorized(401, redirect, '/staff/moderation')

    expect(localStorage.getItem(STAFF_TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(STAFF_USER_KEY)).toBeNull()
    expect(redirect).toHaveBeenCalledWith('/staff/login')
  })
})
