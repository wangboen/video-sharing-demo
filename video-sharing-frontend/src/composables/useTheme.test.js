import { describe, expect, it, vi } from 'vitest'

const loadUseTheme = async () => {
  vi.resetModules()
  return import('./useTheme')
}

describe('useTheme', () => {
  it('defaults to the dark theme when no preference is stored', async () => {
    const { useTheme } = await loadUseTheme()

    expect(useTheme().theme.value).toBe('dark')
  })

  it('uses the stored light theme preference', async () => {
    localStorage.setItem('vision-theme', 'light')
    const { useTheme } = await loadUseTheme()

    expect(useTheme().theme.value).toBe('light')
  })

  it('synchronizes the stored theme to the document on initialization', async () => {
    localStorage.setItem('vision-theme', 'light')

    await loadUseTheme()

    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('toggles the theme and persists the selected value', async () => {
    const { useTheme } = await loadUseTheme()
    const { theme, toggleTheme } = useTheme()

    toggleTheme()

    expect(theme.value).toBe('light')
    expect(localStorage.getItem('vision-theme')).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })
})
