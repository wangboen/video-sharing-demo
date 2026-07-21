import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import router from './index'
import AboutView from '../views/AboutView.vue'

const routeTitles = {
  '/': '发现',
  '/about': '关于',
  '/register': '注册',
  '/login': '登录',
  '/dashboard': '个人中心',
  '/upload': '上传视频',
  '/videos': '视频库',
  '/video/:id': '播放视频',
}

describe('视界路由', () => {
  it('保留八个既有页面路径', () => {
    const paths = router.getRoutes().map((route) => route.path)

    expect(paths).toEqual(expect.arrayContaining([
      '/',
      '/about',
      '/register',
      '/login',
      '/dashboard',
      '/upload',
      '/videos',
      '/video/:id',
    ]))
  })

  it('保留受保护页面的认证要求', () => {
    const protectedRoutes = router.getRoutes()
      .filter((route) => ['/dashboard', '/upload'].includes(route.path))

    expect(protectedRoutes).toHaveLength(2)
    expect(protectedRoutes.every((route) => route.meta.requiresAuth)).toBe(true)
  })

  it('为八个既有路由提供预期的中文页面标题', () => {
    const titlesByPath = Object.fromEntries(
      router.getRoutes().map((route) => [route.path, route.meta.title]),
    )

    expect(titlesByPath).toMatchObject(routeTitles)
  })

  it('导航后将中文路由标题写入 document.title', async () => {
    const navigations = [
      ['/about', '关于'],
      ['/register', '注册'],
      ['/login', '登录'],
      ['/videos', '视频库'],
      ['/video/title-check', '播放视频'],
      ['/', '发现'],
      ['/dashboard', '个人中心'],
      ['/upload', '上传视频'],
    ]

    localStorage.setItem('authToken', 'title-test-token')
    localStorage.setItem('user', JSON.stringify({ id: 1, username: '标题测试' }))

    for (const [path, title] of navigations) {
      await router.push(path)
      await router.isReady()
      expect(document.title).toBe(`${title} · 视界`)
    }
  })

  it('以中文说明视界的定位', () => {
    const wrapper = mount(AboutView)

    expect(wrapper.text()).toContain('关于视界')
    expect(wrapper.text()).toContain('发现每一种热爱')
  })
})
