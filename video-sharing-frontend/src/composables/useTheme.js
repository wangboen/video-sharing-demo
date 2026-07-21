import { ref } from 'vue'

const storedTheme = localStorage.getItem('vision-theme')
const theme = ref(storedTheme === 'light' ? 'light' : 'dark')

document.documentElement.dataset.theme = theme.value

export function useTheme() {
  const toggleTheme = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = theme.value
    localStorage.setItem('vision-theme', theme.value)
  }

  return { theme, toggleTheme }
}
