import { describe, expect, it } from 'vitest'

import router from './index'

describe('ordinary-user route guard', () => {
  it.each(['/dashboard', '/upload'])('rejects a stored staff identity from %s', async (path) => {
    localStorage.setItem('authToken', 'staff-token')
    localStorage.setItem('user', JSON.stringify({ id: 9, username: 'staff-user', role: 'ADMIN' }))

    await router.push(path)
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe(path)
  })
})
