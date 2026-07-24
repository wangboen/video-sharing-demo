import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const nginx = readFileSync(
  resolve(process.cwd(), '../video-sharing-backend/src/main/resources/nginx.conf'),
  'utf8',
)
const applicationProperties = readFileSync(
  resolve(process.cwd(), '../video-sharing-backend/src/main/resources/application.properties'),
  'utf8',
)

describe('same-origin Nginx deployment contract', () => {
  it('mounts independent user and staff SPAs with their own history fallbacks', () => {
    expect(nginx).toMatch(/location\s+(?:\^~\s+)?\/staff\/\s*\{[\s\S]*?try_files\s+\$uri\s+\$uri\/\s+\/staff\/index\.html;/)
    expect(nginx).toMatch(/location\s+\/\s*\{[\s\S]*?try_files\s+\$uri\s+\$uri\/\s+\/index\.html;/)
  })

  it('proxies API calls and exposes only the approved public media directories', () => {
    expect(nginx).toMatch(/location\s+\/api\/\s*\{[\s\S]*?proxy_pass\s+http:\/\/127\.0\.0\.1:8080/)
    expect(nginx).toMatch(/location\s+\/uploads\/\s*\{[\s\S]*?alias\s+\/srv\/video-sharing\/public\/uploads\//)
    expect(nginx).toMatch(/location\s+\/thumbnails\/\s*\{[\s\S]*?alias\s+\/srv\/video-sharing\/public\/thumbnails\//)
    expect(nginx).toContain('add_header Accept-Ranges bytes')
  })

  it('denies pending media without a filesystem alias', () => {
    expect(nginx).toMatch(/location\s+\^~\s+\/pending\/\s*\{\s*return\s+404;/)
    expect(nginx).not.toMatch(/location\s+\/pending\/\s*\{[\s\S]*?alias\s+/)
  })

  it('keeps public and pending media directories configurable by environment', () => {
    expect(applicationProperties).toMatch(/^file\.upload-dir=\$\{FILE_UPLOAD_DIR:/m)
    expect(applicationProperties).toMatch(/^file\.thumbnail-dir=\$\{FILE_THUMBNAIL_DIR:/m)
    expect(applicationProperties).toMatch(/^file\.pending-upload-dir=\$\{PENDING_UPLOAD_DIR:/m)
    expect(applicationProperties).toMatch(/^file\.pending-thumbnail-dir=\$\{PENDING_THUMBNAIL_DIR:/m)
  })
})
