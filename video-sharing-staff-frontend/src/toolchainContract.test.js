import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const packageJson = JSON.parse(readFileSync(resolve(process.cwd(), 'package.json'), 'utf8'))
const packageLock = JSON.parse(readFileSync(resolve(process.cwd(), 'package-lock.json'), 'utf8'))

function requestedMajor(range) {
  return Number(range.match(/\d+/)?.[0])
}

describe('staff toolchain contract', () => {
  it('uses port 5174 so the staff application can run alongside the user application', () => {
    const viteConfig = readFileSync(resolve(process.cwd(), 'vite.config.js'), 'utf8')
    expect(viteConfig).toMatch(/server:\s*\{\s*port:\s*5174\s*,?\s*\}/)
  })

  it('keeps Vite 5, plugin-vue 5, and a Vite-5-compatible Vitest major aligned', () => {
    expect(requestedMajor(packageJson.devDependencies.vite)).toBe(5)
    expect(requestedMajor(packageJson.devDependencies['@vitejs/plugin-vue'])).toBe(5)
    expect(requestedMajor(packageJson.devDependencies.vitest)).toBe(1)
    expect(packageLock.packages['node_modules/vite'].version).toMatch(/^5\./)
    expect(packageLock.packages['node_modules/@vitejs/plugin-vue'].version).toMatch(/^5\./)
    expect(packageLock.packages['node_modules/vitest'].version).toMatch(/^1\./)
  })

  it('declares the Node and npm runtime and contains no local dependency paths', () => {
    expect(packageJson.engines.node).toBeTruthy()
    expect(packageJson.packageManager).toMatch(/^npm@\d+\.\d+\.\d+$/)
    expect(Object.keys(packageLock.packages).some((key) => key.includes('../'))).toBe(false)
    const serializedLock = JSON.stringify(packageLock)
    expect(serializedLock).not.toContain('"link":true')
    expect(serializedLock).not.toContain('"file:')
    expect(serializedLock).not.toContain('video-sharing-frontend/node_modules')
  })
})
