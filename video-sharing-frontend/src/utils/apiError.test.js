import { describe, expect, it } from 'vitest'

import { friendlyApiError } from './apiError'

describe('friendlyApiError', () => {
  it('maps known backend authentication messages to stable Chinese copy', () => {
    expect(friendlyApiError({ response: { data: { message: 'Invalid username or password' } } }, '登录失败')).toBe('用户名或密码不正确。')
    expect(friendlyApiError({ response: { data: 'Username already taken!' } }, '注册失败')).toBe('该用户名已被使用。')
    expect(friendlyApiError({ response: { data: { message: 'Email already in use!' } } }, '注册失败')).toBe('该邮箱已被注册。')
  })

  it('maps HTTP status codes without exposing raw objects or English text', () => {
    expect(friendlyApiError({ response: { status: 413, data: { reason: 'Payload Too Large' } } }, '视频发布失败')).toBe('上传文件过大，请选择较小的文件。')
    expect(friendlyApiError({ response: { status: 500, data: { debug: 'stack trace' } } }, '视频发布失败')).toBe('服务暂时不可用，请稍后重试。')
  })

  it('uses the supplied Chinese fallback for unknown errors', () => {
    expect(friendlyApiError({ response: { data: { unexpected: 'Internal error' } } }, '视频发布失败，请稍后重试。')).toBe('视频发布失败，请稍后重试。')
  })
  it('maps backend service and comment errors to stable Chinese copy', () => {
    expect(friendlyApiError({ response: { data: { message: 'Comment service is temporarily unavailable' } } }, '评论发送失败')).toBe('评论服务暂时不可用，请稍后重试。')
    expect(friendlyApiError({ response: { data: { message: 'Video search is temporarily unavailable' } } }, '视频加载失败')).toBe('视频搜索暂时不可用，请稍后重试。')
    expect(friendlyApiError({ response: { data: { message: 'Requested content was not found' } } }, '下载失败')).toBe('请求的内容不存在。')
    expect(friendlyApiError({ response: { data: { message: 'Service is temporarily unavailable' } } }, '加载失败')).toBe('服务暂时不可用，请稍后重试。')
  })
})
