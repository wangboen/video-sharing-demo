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

  it('proxies API calls and leaves public media delivery to object storage', () => {
    expect(nginx).toMatch(/location\s+\/api\/\s*\{[\s\S]*?proxy_pass\s+http:\/\/127\.0\.0\.1:8080/)
    expect(nginx).not.toMatch(/location\s+\/uploads\//)
    expect(nginx).not.toMatch(/location\s+\/thumbnails\//)
    expect(nginx).not.toMatch(/alias\s+\/srv\/video-sharing\/public\/(?:uploads|thumbnails)\//)
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

  it('does not configure legacy filesystem media directories', () => {
    expect(applicationProperties).not.toMatch(/^file\.(?:upload-dir|thumbnail-dir|pending-upload-dir|pending-thumbnail-dir)=/m)
  })

  it('does not retain legacy backend multipart media upload limits', () => {
    expect(applicationProperties).not.toMatch(/^spring\.servlet\.multipart\.(?:max-file-size|max-request-size)=/m)
    expect(nginx).not.toMatch(/client_max_body_size\s+545m;/)
  })

  it('configures MinIO compatible object storage for direct media uploads', () => {
    expect(applicationProperties).toMatch(/^object-storage\.endpoint=\$\{OBJECT_STORAGE_ENDPOINT:/m)
    expect(applicationProperties).toMatch(/^object-storage\.bucket=\$\{OBJECT_STORAGE_BUCKET:/m)
    expect(applicationProperties).toMatch(/^object-storage\.access-key=\$\{OBJECT_STORAGE_ACCESS_KEY:/m)
    expect(applicationProperties).toMatch(/^object-storage\.secret-key=\$\{OBJECT_STORAGE_SECRET_KEY:/m)
    expect(applicationProperties).toMatch(/^object-storage\.public-base-url=\$\{OBJECT_STORAGE_PUBLIC_BASE_URL:/m)
    expect(applicationProperties).toMatch(/^object-storage\.pending-prefix=\$\{OBJECT_STORAGE_PENDING_PREFIX:pending\/\}/m)
    expect(applicationProperties).toMatch(/^object-storage\.public-prefix=\$\{OBJECT_STORAGE_PUBLIC_PREFIX:public\/\}/m)
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
    expect(applicationDevProperties).toMatch(/^spring\.datasource\.url=jdbc:postgresql:\/\/localhost:5432\/video_sharing$/m)
    expect(applicationDevProperties).toMatch(/^spring\.datasource\.username=postgres$/m)
    expect(applicationDevProperties).toMatch(/^spring\.datasource\.password=root$/m)
    expect(deploymentGuide).toContain('DB_URL')
    expect(deploymentGuide).toContain('DB_USERNAME')
    expect(deploymentGuide).toContain('DB_PASSWORD')
  })

  it('documents object storage instead of legacy filesystem media paths', () => {
    expect(deploymentGuide).toContain('OBJECT_STORAGE_ENDPOINT')
    expect(deploymentGuide).toContain('OBJECT_STORAGE_BUCKET')
    expect(deploymentGuide).toContain('OBJECT_STORAGE_PUBLIC_BASE_URL')
    expect(deploymentGuide).not.toContain('FILE_UPLOAD_DIR=/srv/video-sharing/public/uploads')
    expect(deploymentGuide).not.toContain('PENDING_UPLOAD_DIR=/srv/video-sharing/private/pending/uploads')
  })
})
