import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(resolve(process.cwd(), 'src/assets/main.css'), 'utf8')

describe('staff accessibility style contract', () => {
  it('uses a solid high-contrast keyboard focus indicator', () => {
    expect(css).toMatch(/:focus-visible[\s\S]*outline:\s*3px solid #173b8f/)
    expect(css).not.toMatch(/:focus-visible[\s\S]*outline:[^;]*rgba/)
  })

  it('reserves amber for the lease accent and keeps lease telemetry dark', () => {
    expect(css.match(/var\(--lease-amber\)/g)).toHaveLength(1)
    expect(css).toMatch(/\.lease-rail\s*{[\s\S]*?color:\s*var\(--ink\)/)
    expect(css).toMatch(/\.lease-rail strong\s*{[\s\S]*?color:\s*var\(--ink\)/)
    expect(css).toMatch(/\.lease-rail__line\s*{[\s\S]*?background:\s*var\(--lease-amber\)/)
  })
})
