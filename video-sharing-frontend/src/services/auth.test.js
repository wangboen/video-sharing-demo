import { describe, expect, it } from 'vitest'

import { getStoredUser, saveAuth, updateStoredUser } from './auth'

describe('auth storage', () => {
  it('round-trips the authenticated ordinary-user role', () => {
    saveAuth({ token: 'token', id: 1, username: 'vision-user', email: 'vision@example.com', role: 'USER' })

    expect(getStoredUser()).toEqual({
      id: 1,
      username: 'vision-user',
      email: 'vision@example.com',
      role: 'USER',
    })

    updateStoredUser({ id: 1, username: 'updated-user', email: 'updated@example.com', role: 'USER' })
    expect(getStoredUser()).toMatchObject({ username: 'updated-user', role: 'USER' })
  })
})
