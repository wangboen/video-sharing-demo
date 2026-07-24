import { describe, expect, it } from 'vitest'

import { getThumbnailUrl, getVideoUrl } from './video'

describe('safe video DTO URLs', () => {
  it('uses only the safe URL fields and never reconstructs private paths', () => {
    expect(getThumbnailUrl({ thumbnailUrl: '/media/cover.jpg', thumbnailPath: 'private-cover.jpg' })).toBe('/media/cover.jpg')
    expect(getThumbnailUrl({ thumbnailPath: 'private-cover.jpg' })).toBe('')
    expect(getVideoUrl({ videoUrl: '/media/clip.mp4', filePath: 'private-clip.mp4' })).toBe('/media/clip.mp4')
    expect(getVideoUrl({ filePath: 'private-clip.mp4' })).toBe('')
  })
})
