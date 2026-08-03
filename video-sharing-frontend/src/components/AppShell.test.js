import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: '<a :href="typeof to === \'string\' ? to : to.path"><slot /></a>',
    },
  },
}

describe('application shell', () => {
  it('shows the Vision brand in the header', () => {
    const wrapper = mount(AppHeader, {
      props: { user: null, sidebarCollapsed: false },
      global,
    })

    expect(wrapper.text()).toContain('视界')
  })

  it('emits a toggle request from the menu button', async () => {
    const wrapper = mount(AppHeader, {
      props: { user: null, sidebarCollapsed: false },
      global,
    })

    await wrapper.get('[aria-label="切换侧边栏"]').trigger('click')

    expect(wrapper.emitted('toggle-sidebar')).toHaveLength(1)
  })

  it('shows a login action for signed-out visitors', () => {
    const wrapper = mount(AppHeader, {
      props: { user: null, sidebarCollapsed: false },
      global,
    })

    expect(wrapper.text()).toContain('登录')
  })

  it('provides clickable search and upload actions in the header', () => {
    const wrapper = mount(AppHeader, {
      props: { user: null, sidebarCollapsed: false },
      global,
    })

    expect(wrapper.get('.app-header__search button').attributes('aria-label')).toBe('搜索')
    expect(wrapper.get('.app-header__upload').attributes('href')).toBe('/upload')
  })

  it('provides both login and registration routes to signed-out visitors', () => {
    const wrapper = mount(AppSidebar, {
      props: { user: null, collapsed: false },
      global,
    })

    expect(wrapper.get('.app-sidebar__link[href="/login"]').exists()).toBe(true)
    expect(wrapper.get('.app-sidebar__link[href="/register"]').exists()).toBe(true)
  })

  it('keeps personal and upload actions out of the signed-in sidebar', () => {
    const wrapper = mount(AppSidebar, {
      props: { user: { username: 'viewer' }, collapsed: false },
      global,
    })

    expect(wrapper.find('.app-sidebar__link[href="/dashboard"]').exists()).toBe(false)
    expect(wrapper.find('.app-sidebar__link[href="/upload"]').exists()).toBe(false)
  })

  it('keeps labeled route entries when the sidebar is collapsed', () => {
    const wrapper = mount(AppSidebar, {
      props: { user: null, collapsed: true },
      global,
    })

    const routeLinks = wrapper.findAll('.app-sidebar__link')
    const labeledRouteLinks = wrapper.findAll('.app-sidebar__link[aria-label]')

    expect(labeledRouteLinks).toHaveLength(routeLinks.length)
    expect(wrapper.get('.app-sidebar__link[href="/"]').attributes('aria-label')).toBe('首页')
  })

  it('hides the ineffective mobile sidebar toggle and reaches the scoped brand wordmark', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/components/AppHeader.vue'), 'utf8')

    expect(source).toContain('class="icon-button sidebar-toggle"')
    expect(source).toMatch(/@media \(max-width: 680px\)[\s\S]*\.sidebar-toggle\s*{\s*display:\s*none/)
    expect(source).toContain(':deep(.brand-logo__wordmark)')
  })
})
