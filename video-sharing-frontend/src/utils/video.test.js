import { describe, expect, it } from 'vitest'

import {
  formatDate,
  formatViews,
  getThumbnailUrl,
  getVideoUrl,
  matchesCategory,
  matchesSearch,
} from './video'

describe('video utilities', () => {
  it('uses safe API URLs and handles missing URLs', () => {
    expect(getThumbnailUrl({ thumbnailUrl: '/media/cover.jpg' })).toBe('http://localhost:8080/media/cover.jpg')
    expect(getThumbnailUrl({ thumbnailUrl: null })).toBe('')
    expect(getVideoUrl({ videoUrl: '/media/clip.mp4' })).toBe('http://localhost:8080/media/clip.mp4')
    expect(getVideoUrl({ videoUrl: undefined })).toBe('')
  })

  it('formats view counts with Chinese labels', () => {
    expect(formatViews(12800)).toBe('1.2万次观看')
    expect(formatViews(42)).toBe('42次观看')
    expect(formatViews(undefined)).toBe('暂无播放数据')
  })

  it('formats valid dates and handles missing dates', () => {
    expect(formatDate('2026-07-20T00:00:00.000Z')).toBe('2026-07-20')
    expect(formatDate(undefined)).toBe('未知日期')
  })

  it('preserves the calendar date from a timezone-free backend LocalDateTime', () => {
    expect(formatDate('2026-07-20T00:15:00')).toBe('2026-07-20')
  })

  it('matches a case-insensitive query against title, description, and safe uploader', () => {
    expect(matchesSearch({ title: '城市漫步', description: '', uploader: 'Safe Uploader', user: { username: 'legacy-user' } }, 'safe uploader')).toBe(true)
    expect(matchesSearch({ title: '城市漫步', description: '', user: { username: '小林' } }, '小林')).toBe(true)
    expect(matchesSearch({ title: 'Vue 教程', description: '组件实战', user: { username: 'Codex' } }, 'vue')).toBe(true)
    expect(matchesSearch({ title: '城市漫步', description: '', user: { username: '小林' } }, '摄影')).toBe(false)
  })

  it('matches categories using their configured keywords', () => {
    expect(matchesCategory({ title: '游戏实况', description: '' }, '游戏')).toBe(true)
    expect(matchesCategory({ title: '编程小技巧', description: '' }, '科技')).toBe(true)
    expect(matchesCategory({ title: '城市漫步', description: '' }, '全部')).toBe(true)
    expect(matchesCategory({ title: '城市漫步', description: '' }, '游戏')).toBe(false)
  })
})
