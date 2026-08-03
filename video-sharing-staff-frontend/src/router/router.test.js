import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { resolveStaffAccess } from './index'

describe('staff route contract', () => {
  it('redirects unauthenticated and USER sessions to the staff login', () => {
    expect(resolveStaffAccess({ path: '/moderation', meta: {} }, null))
      .toEqual({ name: 'login' })
    expect(resolveStaffAccess(
      { path: '/moderation', meta: {} },
      { user: { role: 'USER' } },
    )).toEqual({ name: 'login' })
  })

  it('allows reviewers into moderation and admins into admin-only management pages', () => {
    const reviewer = { user: { role: 'REVIEWER' } }
    const admin = { user: { role: 'ADMIN' } }

    expect(resolveStaffAccess({ path: '/moderation', meta: {} }, reviewer)).toBeNull()
    expect(resolveStaffAccess(
      { path: '/moderation', meta: { reviewerOnly: true } },
      admin,
    )).toEqual({ name: 'reviewers' })
    expect(resolveStaffAccess(
      { path: '/reviewers', meta: { adminOnly: true } },
      reviewer,
    )).toEqual({ name: 'moderation' })
    expect(resolveStaffAccess(
      { path: '/reviewers', meta: { adminOnly: true } },
      admin,
    )).toBeNull()
    expect(resolveStaffAccess(
      { path: '/taxonomy', meta: { adminOnly: true } },
      reviewer,
    )).toEqual({ name: 'moderation' })
    expect(resolveStaffAccess(
      { path: '/taxonomy', meta: { adminOnly: true } },
      admin,
    )).toBeNull()
  })

  it('sends authenticated staff to their role home from public-only routes', () => {
    expect(resolveStaffAccess(
      { path: '/login', meta: { publicOnly: true } },
      { user: { role: 'REVIEWER' } },
    )).toEqual({ name: 'moderation' })
    expect(resolveStaffAccess(
      { path: '/login', meta: { publicOnly: true } },
      { user: { role: 'ADMIN' } },
    )).toEqual({ name: 'reviewers' })
  })

  it('uses the /staff/ Vite base and router history base contract', () => {
    const viteSource = readFileSync(resolve(process.cwd(), 'vite.config.js'), 'utf8')
    const routerSource = readFileSync(resolve(process.cwd(), 'src/router/index.js'), 'utf8')

    expect(viteSource).toMatch(/base:\s*['"]\/staff\/['"]/)
    expect(routerSource).toContain('createWebHistory(import.meta.env.BASE_URL)')
    expect(routerSource).toContain("name: 'taxonomy'")
  })
})
