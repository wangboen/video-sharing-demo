import { existsSync, readFileSync } from 'node:fs'
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
const applicationDevPropertiesPath = resolve(
  process.cwd(),
  '../video-sharing-backend/src/main/resources/application-dev.properties',
)
const deploymentGuide = readFileSync(resolve(process.cwd(), '../docs/deployment.md'), 'utf8')

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
    const pendingBlock = nginx.match(/location\s+(?:\^~\s+)?\/pending\/\s*\{([\s\S]*?)\}/)

    expect(pendingBlock?.[1]).toMatch(/return\s+404;/)
    expect(pendingBlock?.[1]).not.toMatch(/\balias\b/)
  })

  it('keeps public and pending media directories configurable by environment', () => {
    expect(applicationProperties).toMatch(/^file\.upload-dir=\$\{FILE_UPLOAD_DIR:/m)
    expect(applicationProperties).toMatch(/^file\.thumbnail-dir=\$\{FILE_THUMBNAIL_DIR:/m)
    expect(applicationProperties).toMatch(/^file\.pending-upload-dir=\$\{PENDING_UPLOAD_DIR:/m)
    expect(applicationProperties).toMatch(/^file\.pending-thumbnail-dir=\$\{PENDING_THUMBNAIL_DIR:/m)
  })

  it('requires JWT_SECRET by default and confines the development secret to the dev profile', () => {
    expect(applicationProperties).toMatch(/^jwt\.secret=\$\{JWT_SECRET\}$/m)
    expect(existsSync(applicationDevPropertiesPath)).toBe(true)
    const applicationDevProperties = readFileSync(applicationDevPropertiesPath, 'utf8')
    expect(applicationDevProperties).toMatch(/^jwt\.secret=development-only-/m)
    expect(deploymentGuide).toContain('SPRING_PROFILES_ACTIVE=dev')
  })

  it('documents media paths that match the Nginx public mounts and keep pending files private', () => {
    expect(deploymentGuide).toContain('FILE_UPLOAD_DIR=/srv/video-sharing/public/uploads')
    expect(deploymentGuide).toContain('FILE_THUMBNAIL_DIR=/srv/video-sharing/public/thumbnails')
    expect(deploymentGuide).toContain('PENDING_UPLOAD_DIR=/srv/video-sharing/private/pending/uploads')
    expect(deploymentGuide).toContain('PENDING_THUMBNAIL_DIR=/srv/video-sharing/private/pending/thumbnails')
  })
})
