export function generateThumbnailFromVideo(file, { captureSecond = 1 } = {}) {
  return new Promise((resolve, reject) => {
    if (!file) {
      reject(new Error('Video file is required'))
      return
    }

    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    const canvas = document.createElement('canvas')
    let settled = false

    const cleanup = () => {
      URL.revokeObjectURL(url)
      video.removeAttribute('src')
      video.load?.()
    }

    const fail = (message) => {
      if (settled) return
      settled = true
      cleanup()
      reject(new Error(message))
    }

    video.muted = true
    video.playsInline = true
    video.preload = 'metadata'
    video.onerror = () => fail('无法读取视频以生成封面。')
    video.onloadedmetadata = () => {
      const duration = Number.isFinite(video.duration) ? video.duration : 0
      video.currentTime = Math.min(Math.max(captureSecond, 0), Math.max(duration - 0.1, 0))
    }
    video.onseeked = () => {
      if (settled) return
      const width = video.videoWidth || 1280
      const height = video.videoHeight || 720
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d')
      if (!context) {
        fail('无法创建封面画布。')
        return
      }
      context.drawImage(video, 0, 0, width, height)
      canvas.toBlob((blob) => {
        if (settled) return
        if (!blob) {
          fail('封面生成失败。')
          return
        }
        settled = true
        cleanup()
        resolve(new File([blob], 'auto-cover.jpg', { type: 'image/jpeg' }))
      }, 'image/jpeg', 0.9)
    }

    video.src = url
  })
}
