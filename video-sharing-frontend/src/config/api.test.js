import { describe, expect, it } from 'vitest'

import { API_BASE_URL, resolveApiBaseUrl } from './api'

describe('API configuration', () => {
  it('provides the local backend default when no environment override is set', () => {
    expect(API_BASE_URL).toBe('http://localhost:8080')
  })

  it('uses the local backend in development and same-origin in production when not configured', () => {
    expect(resolveApiBaseUrl(undefined, true)).toBe('http://localhost:8080')
    expect(resolveApiBaseUrl(undefined, false)).toBe('')
  })

  it('preserves explicitly configured empty and absolute API base URLs', () => {
    expect(resolveApiBaseUrl('', false)).toBe('')
    expect(resolveApiBaseUrl('https://api.example.test/', false)).toBe('https://api.example.test')
  })
})
