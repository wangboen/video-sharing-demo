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
const applicationDevProperties = readFileSync(applicationDevPropertiesPath, 'utf8')
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

  it('adds nosniff to every approved public media response', () => {
    const uploadsBlock = nginx.match(/location\s+\/uploads\/\s*\{([\s\S]*?)\}/)
    const thumbnailsBlock = nginx.match(/location\s+\/thumbnails\/\s*\{([\s\S]*?)\}/)

    expect(uploadsBlock?.[1]).toMatch(/add_header\s+X-Content-Type-Options\s+nosniff\s+always;/)
    expect(thumbnailsBlock?.[1]).toMatch(/add_header\s+X-Content-Type-Options\s+nosniff\s+always;/)
  })

  it('binds only to a loopback non-public port behind the mandatory TLS terminator', () => {
    expect(nginx).toMatch(/listen\s+127\.0\.0\.1:8081;/)
    expect(nginx).not.toMatch(/listen\s+(?:80|0\.0\.0\.0)/)
    expect(nginx).toMatch(/proxy_set_header\s+Host\s+\$http_host;/)
    expect(nginx).toMatch(/proxy_set_header\s+X-Forwarded-Proto\s+\$http_x_forwarded_proto;/)
    expect(deploymentGuide).toContain('TLS terminator or load balancer is mandatory')
    expect(deploymentGuide).toContain('127.0.0.1:8081')
    expect(deploymentGuide).toContain('Host')
    expect(deploymentGuide).toContain('X-Forwarded-Proto')
    expect(deploymentGuide).toContain('must not be publicly exposed')
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

  it('permits a 512MB video plus a 32MB thumbnail without widening thumbnail validation', () => {
    expect(applicationProperties).toMatch(/^spring\.servlet\.multipart\.max-file-size=512MB$/m)
    expect(applicationProperties).toMatch(/^spring\.servlet\.multipart\.max-request-size=545MB$/m)
    expect(nginx).toMatch(/client_max_body_size\s+545m;/)
  })

  it('requires JWT_SECRET by default and confines the development secret to the dev profile', () => {
    expect(applicationProperties).toMatch(/^jwt\.secret=\$\{JWT_SECRET\}$/m)
    expect(existsSync(applicationDevPropertiesPath)).toBe(true)
    expect(applicationDevProperties).toMatch(/^jwt\.secret=development-only-/m)
    expect(deploymentGuide).toContain('SPRING_PROFILES_ACTIVE=dev')
  })

  it('requires production datasource variables and confines local root defaults to dev', () => {
    expect(applicationProperties).toMatch(/^spring\.datasource\.url=\$\{DB_URL\}$/m)
    expect(applicationProperties).toMatch(/^spring\.datasource\.username=\$\{DB_USERNAME\}$/m)
    expect(applicationProperties).toMatch(/^spring\.datasource\.password=\$\{DB_PASSWORD\}$/m)
    expect(applicationProperties).not.toMatch(/^spring\.datasource\..*(?:localhost|useSSL=false|=root)$/m)
    expect(applicationDevProperties).toMatch(/^spring\.datasource\.url=jdbc:mysql:\/\/localhost:/m)
    expect(applicationDevProperties).toMatch(/^spring\.datasource\.username=root$/m)
    expect(applicationDevProperties).toMatch(/^spring\.datasource\.password=root$/m)
    expect(deploymentGuide).toContain('DB_URL')
    expect(deploymentGuide).toContain('DB_USERNAME')
    expect(deploymentGuide).toContain('DB_PASSWORD')
  })

  it('documents media paths that match the Nginx public mounts and keep pending files private', () => {
    expect(deploymentGuide).toContain('FILE_UPLOAD_DIR=/srv/video-sharing/public/uploads')
    expect(deploymentGuide).toContain('FILE_THUMBNAIL_DIR=/srv/video-sharing/public/thumbnails')
    expect(deploymentGuide).toContain('PENDING_UPLOAD_DIR=/srv/video-sharing/private/pending/uploads')
    expect(deploymentGuide).toContain('PENDING_THUMBNAIL_DIR=/srv/video-sharing/private/pending/thumbnails')
  })
})
