import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const readSource = (relativePath) => readFileSync(new URL(relativePath, import.meta.url), 'utf8')

function contrastRatio(hexA, hexB) {
  const luminance = (hex) => {
    const channels = hex.match(/[\da-f]{2}/gi).map((value) => parseInt(value, 16) / 255)
    const linear = channels.map((value) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
  }
  const [light, dark] = [luminance(hexA), luminance(hexB)].sort((a, b) => b - a)
  return (light + 0.05) / (dark + 0.05)
}

describe('主题和页面结构契约', () => {
  it('四个重点页面只使用主题令牌，不保留旧蓝白硬编码', () => {
    const sources = ['LoginView.vue', 'RegisterView.vue', 'VideoUpload.vue', 'UserDashboard.vue']
      .map((file) => readSource(`./${file}`))
      .join('\n')

    expect(sources).not.toMatch(/#[\da-f]{3,8}\b/i)
    expect(sources).toContain('var(--surface)')
    expect(sources).toContain('var(--text)')
    expect(sources).toContain('var(--brand-action)')
  })

  it('登录与注册复用同一份轻量认证样式', () => {
    expect(readSource('./LoginView.vue')).toContain('<style src="../assets/auth.css" scoped></style>')
    expect(readSource('./RegisterView.vue')).toContain('<style src="../assets/auth.css" scoped></style>')
  })

  it('品牌操作底色与白字达到 WCAG AA 普通文本对比度', () => {
    const css = readSource('../assets/main.css')
    const actionColor = css.match(/--brand-action:\s*(#[\da-f]{6})/i)?.[1]

    expect(actionColor).toBeTruthy()
    expect(contrastRatio(actionColor, '#ffffff')).toBeGreaterThanOrEqual(4.5)
  })

  it('页面视图不在全局 app-main 内嵌套 main 地标', () => {
    for (const file of ['LoginView.vue', 'RegisterView.vue', 'VideoUpload.vue', 'UserDashboard.vue', 'VideoPlayer.vue']) {
      expect(readSource(`./${file}`), file).not.toMatch(/<\/?main\b/)
    }
  })
})
