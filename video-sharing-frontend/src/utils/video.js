import { API_BASE_URL } from '../config/api'

export const VIDEO_CATEGORIES = {
  全部: [],
  音乐: ['音乐', '歌曲', '演唱', 'music'],
  游戏: ['游戏', '实况', '电竞', 'game'],
  生活: ['生活', '日常', '记录', 'vlog'],
  科技: ['科技', '数码', '编程', '技术'],
  学习: ['学习', '教程', '课程', '知识'],
}

export function getThumbnailUrl(video) {
  return resolveMediaUrl(video?.thumbnailUrl)
}

export function getVideoUrl(video) {
  return resolveMediaUrl(video?.videoUrl)
}

export function getAuthorName(video) {
  return video?.uploader || video?.user?.username || ''
}

function resolveMediaUrl(value) {
  if (!value) return ''
  if (/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(value) || !API_BASE_URL) return value
  return new URL(value, API_BASE_URL).toString()
}

export function formatViews(value) {
  if (!Number.isFinite(value)) return '暂无播放数据'
  if (value >= 10000) return `${Math.floor((value / 10000) * 10) / 10}万次观看`
  return `${value}次观看`
}

export function formatDate(value) {
  if (!value) return '未知日期'

  const timezoneFreeDate = String(value).match(/^(\d{4}-\d{2}-\d{2})(?:T[\d:.]+)?$/)
  if (timezoneFreeDate) return timezoneFreeDate[1]

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未知日期'

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function matchesSearch(video, query) {
  const normalizedQuery = String(query ?? '').trim().toLowerCase()
  if (!normalizedQuery) return true

  const searchableText = [video?.title, video?.description, getAuthorName(video)]
    .map((value) => String(value ?? ''))
    .join(' ')
    .toLowerCase()

  return searchableText.includes(normalizedQuery)
}

export function matchesCategory(video, category) {
  if (category === '全部') return true

  const keywords = VIDEO_CATEGORIES[category]
  if (!keywords) return false

  const searchableText = [video?.title, video?.description]
    .map((value) => String(value ?? ''))
    .join(' ')
    .toLowerCase()

  return keywords.some((keyword) => searchableText.includes(keyword.toLowerCase()))
}
