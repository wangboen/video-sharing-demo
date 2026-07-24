import { describe, expect, it } from 'vitest'

import { API_BASE_URL } from './api'

describe('API configuration', () => {
  it('provides the local backend default when no environment override is set', () => {
    expect(API_BASE_URL).toBe('http://localhost:8080')
  })
})
